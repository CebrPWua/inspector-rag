package my.inspectorrag.searchandreturn.infrastructure.gateway;

import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;
import my.inspectorrag.searchandreturn.domain.service.AnswerGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "inspector.ai", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockAnswerGenerator implements AnswerGenerator {

    @Override
    public String generate(String normalizedQuestion, List<RecallCandidate> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("问题描述：").append(normalizedQuestion).append("\n\n法规依据：\n");
        for (int i = 0; i < candidates.size(); i++) {
            RecallCandidate c = candidates.get(i);
            sb.append(i + 1)
                    .append(". 《")
                    .append(c.lawName())
                    .append("》 ")
                    .append(c.articleNo())
                    .append("：")
                    .append(c.content())
                    .append("\n");
        }
        sb.append("\n风险说明：请结合现场进一步核验并整改。\n整改建议：优先按上述条款执行，并保留整改记录。\n");
        return sb.toString();
    }
}

