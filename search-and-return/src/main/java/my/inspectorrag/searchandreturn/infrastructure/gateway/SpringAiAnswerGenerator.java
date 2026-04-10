package my.inspectorrag.searchandreturn.infrastructure.gateway;

import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;
import my.inspectorrag.searchandreturn.domain.service.AnswerGenerator;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "inspector.ai", name = "provider", havingValue = "springai")
public class SpringAiAnswerGenerator implements AnswerGenerator {

    private final ChatClient chatClient;

    public SpringAiAnswerGenerator(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String generate(String normalizedQuestion, List<RecallCandidate> candidates) {
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

        String userPrompt = """
                你是法规问答助手。你必须仅基于给定证据回答，不能编造。
                问题：%s
                证据：
                %s
                按以下结构输出：
                问题描述：
                法规依据：
                风险说明：
                整改建议：
                """.formatted(normalizedQuestion, evidence);

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
}
