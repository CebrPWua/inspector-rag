package my.inspectorrag.searchandreturn.infrastructure.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import my.inspectorrag.searchandreturn.domain.model.ConversationContextTurn;
import my.inspectorrag.searchandreturn.domain.model.RewriteResult;
import my.inspectorrag.searchandreturn.domain.service.QuestionRewriteService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

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
            List<ConversationContextTurn> contextTurns
    ) {
        String contextBlock = buildContextBlock(contextTurns);
        String userPrompt = """
                你是法律法规检索查询改写助手。
                你需要先理解用户问题的风险场景，再输出适合法规检索的改写结果。

                输入：
                - 原始问题：%s
                - 规范化问题：%s
                - 最近对话上下文：
                %s

                输出要求：
                1) 使用法律法规常见表达（应当/不得/应符合/依据/条款要求等）。
                2) 推断可能涉及的法规约束类型（设备安全、作业许可、防护措施、验收标准等）。
                3) 输出2到4条候选查询，覆盖不同但相关的法规检索角度。
                4) 禁止编造具体法规名称和条号；若不确定，用“可能涉及XXX类规定”。
                5) 仅输出JSON，不要输出Markdown或解释。

                JSON schema:
                {
                  "rewrittenQuestion": "string",
                  "candidateQueries": ["string", "string"],
                  "reasoningSummary": "string"
                }
                """.formatted(
                nullToEmpty(originalQuestion),
                nullToEmpty(normalizedQuestion),
                contextBlock
        );

        String content = chatClient.prompt()
                .system("你是严谨的法律法规查询改写助手。")
                .user(userPrompt)
                .call()
                .content();

        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("empty question rewrite response");
        }
        return parseRewriteResultWithRepair(content);
    }

    private RewriteResult parseRewriteResultWithRepair(String content) {
        RewritePayload payload;
        try {
            payload = objectMapper.readValue(content, RewritePayload.class);
        } catch (Exception ex) {
            payload = tryLocalRepair(content);
            if (payload == null) {
                payload = tryLlmRepair(content);
            }
            if (payload == null) {
                throw new IllegalArgumentException("invalid rewrite json response", ex);
            }
        }
        return new RewriteResult(
                payload.rewrittenQuestion(),
                payload.candidateQueries() == null ? List.of() : payload.candidateQueries(),
                payload.reasoningSummary()
        );
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
                  "rewrittenQuestion": "string",
                  "candidateQueries": ["string", "string"],
                  "reasoningSummary": "string"
                }
                待修复内容：
                %s
                """.formatted(content);
        String repaired = chatClient.prompt()
                .system("你是JSON修复助手。")
                .user(repairPrompt)
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
            return objectMapper.readValue(text, RewritePayload.class);
        } catch (Exception ex) {
            return null;
        }
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
            String rewrittenQuestion,
            List<String> candidateQueries,
            String reasoningSummary
    ) {
    }
}
