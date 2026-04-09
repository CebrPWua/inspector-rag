package my.inspectorrag.searchandreturn.application.service;

import my.inspectorrag.searchandreturn.domain.model.QaDetail;
import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;
import my.inspectorrag.searchandreturn.domain.repository.QaRepository;
import my.inspectorrag.searchandreturn.domain.service.MockEmbeddingService;
import my.inspectorrag.searchandreturn.interfaces.dto.AskResponse;
import my.inspectorrag.searchandreturn.interfaces.dto.EvidenceDto;
import my.inspectorrag.searchandreturn.interfaces.dto.QaDetailResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class QaApplicationService {

    private final QaRepository qaRepository;
    private final MockEmbeddingService embeddingService;
    private final String embeddingModelName;
    private final int topK;

    public QaApplicationService(
            QaRepository qaRepository,
            MockEmbeddingService embeddingService,
            @Value("${inspector.embedding.model-name}") String embeddingModelName,
            @Value("${inspector.embedding.top-k}") int topK
    ) {
        this.qaRepository = qaRepository;
        this.embeddingService = embeddingService;
        this.embeddingModelName = embeddingModelName;
        this.topK = topK;
    }

    @Transactional
    public AskResponse ask(String question) {
        long start = System.currentTimeMillis();
        String normalized = normalizeQuestion(question);
        String vectorLiteral = embeddingService.toVectorLiteral(normalized, 1536);
        List<RecallCandidate> candidates = qaRepository.vectorRecall(vectorLiteral, topK);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("no evidence found for current question");
        }

        String answer = buildAnswer(normalized, candidates);
        OffsetDateTime now = OffsetDateTime.now();
        Long qaId = newId();
        qaRepository.insertQaRecord(qaId, question, normalized, answer, (int) (System.currentTimeMillis() - start), now);
        qaRepository.insertRetrievalSnapshot(newId(), qaId, embeddingModelName, topK, candidates.size(), now);

        int rank = 1;
        int citeNo = 1;
        for (RecallCandidate candidate : candidates) {
            qaRepository.insertCandidate(newId(), qaId, candidate, rank++, now);
            qaRepository.insertEvidence(newId(), qaId, candidate, citeNo++, now);
        }

        List<EvidenceDto> evidences = candidates.stream().map(c -> new EvidenceDto(
                0,
                c.chunkId(),
                c.lawName(),
                c.articleNo(),
                c.content(),
                c.score()
        )).toList();
        return new AskResponse(qaId, normalized, answer, evidences);
    }

    @Transactional(readOnly = true)
    public QaDetailResponse getQa(Long qaId) {
        QaDetail detail = qaRepository.findQaDetail(qaId)
                .orElseThrow(() -> new IllegalArgumentException("qa record not found: " + qaId));
        List<EvidenceDto> evidenceDtos = detail.evidences().stream()
                .map(e -> new EvidenceDto(0, e.chunkId(), e.lawName(), e.articleNo(), e.content(), e.score()))
                .toList();
        return new QaDetailResponse(
                detail.qaId(),
                detail.question(),
                detail.normalizedQuestion(),
                detail.answer(),
                detail.answerStatus(),
                detail.createdAt(),
                evidenceDtos
        );
    }

    private String normalizeQuestion(String question) {
        return question == null ? "" : question.trim().replaceAll("\\s+", " ");
    }

    private String buildAnswer(String normalizedQuestion, List<RecallCandidate> candidates) {
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

    private Long newId() {
        long ts = System.currentTimeMillis();
        int rand = ThreadLocalRandom.current().nextInt(1_000_000);
        return ts * 1_000_000L + rand;
    }
}
