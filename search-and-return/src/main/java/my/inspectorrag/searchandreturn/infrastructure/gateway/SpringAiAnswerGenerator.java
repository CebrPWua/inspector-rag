package my.inspectorrag.searchandreturn.infrastructure.gateway;

import my.inspectorrag.searchandreturn.domain.model.ConversationContextTurn;
import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;
import my.inspectorrag.searchandreturn.domain.service.AnswerGenerator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SpringAiAnswerGenerator implements AnswerGenerator {

    private final ChatClient chatClient;

    public SpringAiAnswerGenerator(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String generate(
            String originalQuestion,
            String effectiveRewrittenQuestion,
            List<ConversationContextTurn> contextTurns,
            List<RecallCandidate> candidates
    ) {
        StringBuilder evidence = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            RecallCandidate c = candidates.get(i);
            evidence.append(i + 1)
                    .append(". 《")
                    .append(c.lawName())
                    .append("》")
                    .append(c.articleNo())
                    .append("：")
                    .append(c.content())
                    .append("\n");
        }
        String contextBlock = buildContextBlock(contextTurns);

        String userPrompt = """
                你是法规问答助手。你必须仅基于给定证据回答，不能编造。
                用户原始问题：%s
                改写后用于检索的问题：%s
                最近对话上下文：
                %s
                证据：
                %s
                输出要求：
                1) 开头必须是这一句：我理解你想要问的问题是“%s”，我按照这个在知识库中查询后得到了以下结果。
                2) 严格按以下结构继续输出：
                问题描述：
                法规依据：
                风险说明：
                整改建议：
                """.formatted(originalQuestion, effectiveRewrittenQuestion, contextBlock, evidence, effectiveRewrittenQuestion);

        return invoke(userPrompt);
    }

    @Override
    public String generateLowConfidenceGuidance(
            String originalQuestion,
            String effectiveRewrittenQuestion,
            List<ConversationContextTurn> contextTurns
    ) {
        String contextBlock = buildContextBlock(contextTurns);
        String userPrompt = """
                你是法规问答助手。当前检索结果置信度不足，不能给出明确法规依据。
                用户原始问题：%s
                改写后用于检索的问题：%s
                最近对话上下文：
                %s

                请输出一段简洁中文回答，要求：
                1) 明确说明当前未检索到足够明确的法规依据；
                2) 引导用户换一种问法继续提问（给出2-3条具体改问建议）；
                3) 不要编造任何法规名称、条号和原文。
                """.formatted(originalQuestion, effectiveRewrittenQuestion, contextBlock);
        return invoke(userPrompt);
    }

    private String invoke(String userPrompt) {
        String content = chatClient.prompt()
                .system("你是严谨的法规合规助手。")
                .user(userPrompt)
                .call()
                .content();
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("empty spring-ai chat response");
        }
        return content;
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
}
