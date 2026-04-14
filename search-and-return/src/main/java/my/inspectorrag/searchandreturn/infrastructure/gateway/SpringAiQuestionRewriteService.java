package my.inspectorrag.searchandreturn.infrastructure.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import my.inspectorrag.searchandreturn.domain.model.ConversationContextTurn;
import my.inspectorrag.searchandreturn.domain.model.RewriteMode;
import my.inspectorrag.searchandreturn.domain.model.RewriteResult;
import my.inspectorrag.searchandreturn.domain.service.QuestionRewriteService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
public class SpringAiQuestionRewriteService implements QuestionRewriteService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public SpringAiQuestionRewriteService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = JsonMapper.builder().findAndAddModules().build();
    }

    @Override
    public RewriteResult rewrite(
            String originalQuestion,
            String normalizedQuestion,
            List<ConversationContextTurn> contextTurns,
            RewriteMode mode
    ) {
        RewriteMode effectiveMode = mode == null ? RewriteMode.AUTO : mode;
        String contextBlock = buildContextBlock(contextTurns);
        String userPrompt = buildRewritePrompt(
                effectiveMode,
                originalQuestion,
                normalizedQuestion,
                contextBlock
        );

        String content = chatClient.prompt()
                .system("你是严谨的法律法规查询改写助手。")
                .user(userPrompt)
                .options(OpenAiChatOptions.builder().temperature(0.0).build())
                .call()
                .content();

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("empty question rewrite response");
        }
        return parseRewriteResultWithRepair(content, effectiveMode);
    }

    private RewriteResult parseRewriteResultWithRepair(String content, RewriteMode mode) {
        RewritePayload payload = parsePayload(content);
        if (payload == null) {
            payload = tryLocalRepair(content);
            if (payload == null) {
                payload = tryLlmRepair(content);
            }
            if (payload == null) {
                throw new IllegalArgumentException("invalid rewrite json response");
            }
        }
        RewriteResult parsed = payload.toResult();
        if (mode == RewriteMode.FORCE && !parsed.rewriteNeeded()) {
            return new RewriteResult(
                    true,
                    Objects.requireNonNullElse(parsed.rewriteReason(), "force-mode-override"),
                    parsed.rewrittenQuestion(),
                    parsed.candidateQueries(),
                    parsed.preserveTerms(),
                    parsed.reasoningSummary()
            );
        }
        return parsed;
    }

    private RewritePayload tryLocalRepair(String content) {
        String trimmed = content == null ? "" : content.trim();
        String fromCodeFence = extractJsonFromCodeFence(trimmed);
        if (fromCodeFence != null) {
            RewritePayload parsed = parsePayload(fromCodeFence);
            if (parsed != null) {
                return parsed;
            }
        }
        String fromJsonObject = extractJsonObject(trimmed);
        if (fromJsonObject != null) {
            return parsePayload(fromJsonObject);
        }
        return null;
    }

    private RewritePayload tryLlmRepair(String content) {
        String repairPrompt = """
                请将下面内容修复为严格 JSON，且仅输出 JSON，不要任何解释。
                JSON schema:
                {
                  "rewriteNeeded": true,
                  "rewriteReason": "string",
                  "rewrittenQuestion": "string|null",
                  "candidateQueries": ["string"],
                  "preserveTerms": ["string"],
                  "reasoningSummary": "string"
                }
                待修复内容：
                %s
                """.formatted(content);
        String repaired = chatClient.prompt()
                .system("你是JSON修复助手。")
                .user(repairPrompt)
                .options(OpenAiChatOptions.builder().temperature(0.0).build())
                .call()
                .content();
        if (repaired == null || repaired.isBlank()) {
            return null;
        }
        RewritePayload parsed = parsePayload(repaired.trim());
        if (parsed != null) {
            return parsed;
        }
        String extracted = extractJsonObject(repaired);
        return extracted == null ? null : parsePayload(extracted);
    }

    private RewritePayload parsePayload(String text) {
        try {
            var root = objectMapper.readTree(text);
            boolean rewriteNeeded = root.path("rewriteNeeded").asBoolean(true);
            String rewriteReason = toNullableText(root.path("rewriteReason"));
            String rewrittenQuestion = toNullableText(root.path("rewrittenQuestion"));
            List<String> candidateQueries = toStringList(root.path("candidateQueries"));
            List<String> preserveTerms = toStringList(root.path("preserveTerms"));
            String reasoningSummary = toNullableText(root.path("reasoningSummary"));
            // Backward compatibility for old payload without rewriteNeeded.
            if (!root.has("rewriteNeeded")) {
                rewriteNeeded = rewrittenQuestion != null || !candidateQueries.isEmpty();
                if (rewriteReason == null) {
                    rewriteReason = "legacy-schema";
                }
            }
            return new RewritePayload(
                    rewriteNeeded,
                    rewriteReason,
                    rewrittenQuestion,
                    candidateQueries,
                    preserveTerms,
                    reasoningSummary
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private String buildRewritePrompt(
            RewriteMode mode,
            String originalQuestion,
            String normalizedQuestion,
            String contextBlock
    ) {
        String modeInstruction = mode == RewriteMode.FORCE
                ? """
                当前模式：FORCE（兜底强制改写）
                你必须输出 rewriteNeeded=true。
                你必须改写为更贴合法规条文义务语气的查询（可将“未明确”规范化为“应当明确”）。
                你必须保留原问题核心实体和行为，禁止跨主题扩展（例如改写到技术交底等不相关场景）。
                输出2到3条候选查询，第一条必须是最贴近法规条文义务表达的一条。
                """
                : """
                当前模式：AUTO（自判改写）
                你必须先判断问题是否已足够贴合法规检索：
                - 若已包含主体+行为+规范义务/依据目标，且语义无歧义，则输出 rewriteNeeded=false；
                - 若表述口语化、含糊或缺乏法规表达，再输出 rewriteNeeded=true 并给出改写。
                rewriteNeeded=false 时，rewrittenQuestion 必须为 null，candidateQueries 必须为空数组。
                rewriteNeeded=true 时，候选查询2到3条，且必须保留核心实体与行为。
                """
                ;
        return """
                你是法律法规检索查询改写助手。
                你的唯一目标是输出更稳定、更贴合法规条文表达的检索查询。

                输入：
                - 原始问题：%s
                - 规范化问题：%s
                - 最近对话上下文：
                %s

                %s

                全局约束：
                1) 禁止编造具体法规名称和条号。
                2) 禁止引入原问题不存在的无关场景。
                3) 仅输出JSON，不要输出Markdown或解释。

                JSON schema:
                {
                  "rewriteNeeded": true,
                  "rewriteReason": "string",
                  "rewrittenQuestion": "string|null",
                  "candidateQueries": ["string"],
                  "preserveTerms": ["string"],
                  "reasoningSummary": "string"
                }
                """.formatted(
                nullToEmpty(originalQuestion),
                nullToEmpty(normalizedQuestion),
                contextBlock,
                modeInstruction
        );
    }

    private String toNullableText(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        String value = node.asText(null);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> toStringList(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (var child : node) {
            String value = toNullableText(child);
            if (value != null) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private String extractJsonFromCodeFence(String text) {
        int firstFence = text.indexOf("```");
        if (firstFence < 0) {
            return null;
        }
        int secondFence = text.indexOf("```", firstFence + 3);
        if (secondFence <= firstFence) {
            return null;
        }
        String fenced = text.substring(firstFence + 3, secondFence).trim();
        if (fenced.startsWith("json")) {
            fenced = fenced.substring(4).trim();
        }
        return fenced.isEmpty() ? null : fenced;
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return text.substring(start, end + 1);
    }

    private String buildContextBlock(List<ConversationContextTurn> contextTurns) {
        if (contextTurns == null || contextTurns.isEmpty()) {
            return "无";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < contextTurns.size(); i++) {
            ConversationContextTurn turn = contextTurns.get(i);
            sb.append(i + 1)
                    .append(") 用户问题：")
                    .append(nullToEmpty(turn.question()))
                    .append("\n   改写问题：")
                    .append(nullToEmpty(turn.rewrittenQuestion()))
                    .append("\n   回答状态：")
                    .append(nullToEmpty(turn.answerStatus()))
                    .append("\n   助手回答：")
                    .append(nullToEmpty(turn.answer()))
                    .append("\n");
        }
        return sb.toString().trim();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record RewritePayload(
            boolean rewriteNeeded,
            String rewriteReason,
            String rewrittenQuestion,
            List<String> candidateQueries,
            List<String> preserveTerms,
            String reasoningSummary
    ) {
        private RewriteResult toResult() {
            return new RewriteResult(
                    rewriteNeeded,
                    rewriteReason,
                    rewrittenQuestion,
                    candidateQueries == null ? List.of() : candidateQueries,
                    preserveTerms == null ? List.of() : preserveTerms,
                    reasoningSummary
            );
        }
    }
}
