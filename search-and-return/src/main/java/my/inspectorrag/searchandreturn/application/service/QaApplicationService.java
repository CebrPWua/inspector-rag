package my.inspectorrag.searchandreturn.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import my.inspectorrag.searchandreturn.application.exception.NoEvidenceFoundException;
import my.inspectorrag.searchandreturn.domain.model.QaDetail;
import my.inspectorrag.searchandreturn.domain.model.QaEvidence;
import my.inspectorrag.searchandreturn.domain.model.QaFilters;
import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;
import my.inspectorrag.searchandreturn.domain.repository.QaRepository;
import my.inspectorrag.searchandreturn.domain.service.AnswerGenerator;
import my.inspectorrag.searchandreturn.domain.service.RecallService;
import my.inspectorrag.searchandreturn.interfaces.dto.AskFilters;
import my.inspectorrag.searchandreturn.interfaces.dto.AskResponse;
import my.inspectorrag.searchandreturn.interfaces.dto.EvidenceDto;
import my.inspectorrag.searchandreturn.interfaces.dto.QaDetailResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class QaApplicationService {

    private static final int VECTOR_RECALL_MULTIPLIER = 3;
    private static final String REJECT_USER_MESSAGE = "没有在数据库中找到合适的法律法规";

    private final QaRepository qaRepository;
    private final RecallService recallService;
    private final AnswerGenerator answerGenerator;
    private final ObjectMapper objectMapper;
    private final String embeddingModelName;
    private final String retrievalProvider;
    private final int topK;
    private final int keywordTopK;
    private final int finalTopN;
    private final String ftsLanguage;
    private final double vectorWeight;
    private final double keywordWeight;
    private final double titleWeight;
    private final double tagWeight;
    private final double minTop1Score;
    private final double minTopGap;
    private final double minConfidentScore;
    private final int minEvidenceCount;

    public QaApplicationService(
            QaRepository qaRepository,
            RecallService recallService,
            AnswerGenerator answerGenerator,
            @Value("${inspector.embedding.model-name}") String embeddingModelName,
            @Value("${inspector.embedding.top-k}") int topK,
            @Value("${inspector.retrieval.provider:jdbc}") String retrievalProvider,
            @Value("${inspector.retrieval.phase2.keyword-topk:20}") int keywordTopK,
            @Value("${inspector.retrieval.phase2.final-topn:8}") int finalTopN,
            @Value("${inspector.retrieval.phase2.fts-language:simple}") String ftsLanguage,
            @Value("${inspector.retrieval.phase2.weights.vector:0.55}") double vectorWeight,
            @Value("${inspector.retrieval.phase2.weights.keyword:0.25}") double keywordWeight,
            @Value("${inspector.retrieval.phase2.weights.title:0.10}") double titleWeight,
            @Value("${inspector.retrieval.phase2.weights.tag:0.10}") double tagWeight,
            @Value("${inspector.retrieval.phase3.reject.min-top1-score:0.55}") double minTop1Score,
            @Value("${inspector.retrieval.phase3.reject.min-top-gap:0.08}") double minTopGap,
            @Value("${inspector.retrieval.phase3.reject.min-confident-score:0.70}") double minConfidentScore,
            @Value("${inspector.retrieval.phase3.reject.min-evidence-count:2}") int minEvidenceCount
    ) {
        this.qaRepository = qaRepository;
        this.recallService = recallService;
        this.answerGenerator = answerGenerator;
        this.objectMapper = JsonMapper.builder().findAndAddModules().build();
        this.embeddingModelName = embeddingModelName;
        this.retrievalProvider = retrievalProvider;
        this.topK = topK;
        this.keywordTopK = keywordTopK;
        this.finalTopN = finalTopN;
        this.ftsLanguage = ftsLanguage;
        this.vectorWeight = vectorWeight;
        this.keywordWeight = keywordWeight;
        this.titleWeight = titleWeight;
        this.tagWeight = tagWeight;
        this.minTop1Score = minTop1Score;
        this.minTopGap = minTopGap;
        this.minConfidentScore = minConfidentScore;
        this.minEvidenceCount = minEvidenceCount;
    }

    @Transactional(noRollbackFor = NoEvidenceFoundException.class)
    public AskResponse ask(String question, AskFilters askFilters) {
        long start = System.currentTimeMillis();
        String normalized = normalizeQuestion(question);
        QaFilters filters = normalizeFilters(askFilters);
        List<String> keywords = extractKeywords(normalized);

        List<RecallCandidate> vectorCandidates = recallService.recall(normalized, topK * VECTOR_RECALL_MULTIPLIER, filters);
        if ("springai".equalsIgnoreCase(retrievalProvider) && !vectorCandidates.isEmpty()) {
            vectorCandidates = applyMetadataFilter(vectorCandidates, filters);
        }
        List<RecallCandidate> keywordCandidates = qaRepository.keywordRecall(
                normalized,
                keywords,
                keywordTopK,
                filters,
                ftsLanguage
        );

        List<MergedCandidate> mergedCandidates = mergeAndRank(normalized, filters, vectorCandidates, keywordCandidates);
        if (mergedCandidates.isEmpty()) {
            String rejectReason = buildRejectReason(
                    "NO_EVIDENCE",
                    "no evidence found for current question"
            );
            OffsetDateTime now = OffsetDateTime.now();
            qaRepository.insertRejectedQaRecord(
                    newId(),
                    question,
                    normalized,
                    rejectReason,
                    (int) (System.currentTimeMillis() - start),
                    now
            );
            throw new NoEvidenceFoundException(REJECT_USER_MESSAGE);
        }
        String lowConfidenceRejectReason = evaluateLowConfidenceRejectReason(mergedCandidates);
        if (lowConfidenceRejectReason != null) {
            OffsetDateTime now = OffsetDateTime.now();
            qaRepository.insertRejectedQaRecord(
                    newId(),
                    question,
                    normalized,
                    lowConfidenceRejectReason,
                    (int) (System.currentTimeMillis() - start),
                    now
            );
            throw new NoEvidenceFoundException(REJECT_USER_MESSAGE);
        }

        String answer = answerGenerator.generate(
                normalized,
                mergedCandidates.stream().map(MergedCandidate::candidate).toList()
        );

        OffsetDateTime now = OffsetDateTime.now();
        Long qaId = newId();
        qaRepository.insertQaRecord(qaId, question, normalized, answer, (int) (System.currentTimeMillis() - start), now);
        qaRepository.insertRetrievalSnapshot(
                newId(),
                qaId,
                embeddingModelName,
                topK,
                mergedCandidates.size(),
                toFiltersJson(filters),
                String.join(" ", keywords),
                now
        );

        for (int i = 0; i < mergedCandidates.size(); i++) {
            int rankNo = i + 1;
            int citeNo = i + 1;
            MergedCandidate merged = mergedCandidates.get(i);
            qaRepository.insertCandidate(
                    newId(),
                    qaId,
                    merged.candidate().chunkId(),
                    merged.sourceType(),
                    merged.rawScore(),
                    merged.rerankScore(),
                    merged.finalScore(),
                    rankNo,
                    now
            );
            qaRepository.insertEvidence(newId(), qaId, merged.candidate(), citeNo, now);
        }

        return new AskResponse(qaId, normalized, answer, toEvidenceDtos(mergedCandidates));
    }

    @Transactional(readOnly = true)
    public QaDetailResponse getQa(Long qaId) {
        QaDetail detail = qaRepository.findQaDetail(qaId)
                .orElseThrow(() -> new IllegalArgumentException("qa record not found: " + qaId));
        return new QaDetailResponse(
                detail.qaId(),
                detail.question(),
                detail.normalizedQuestion(),
                detail.answer(),
                detail.answerStatus(),
                detail.createdAt(),
                detail.evidences().stream().map(this::toEvidenceDto).toList()
        );
    }

    private List<MergedCandidate> mergeAndRank(
            String normalizedQuestion,
            QaFilters filters,
            List<RecallCandidate> vectorCandidates,
            List<RecallCandidate> keywordCandidates
    ) {
        Map<Long, CandidateAccumulator> accMap = new LinkedHashMap<>();
        for (RecallCandidate candidate : vectorCandidates) {
            if (candidate.chunkId() == null) {
                continue;
            }
            CandidateAccumulator current = accMap.computeIfAbsent(candidate.chunkId(), ignore -> new CandidateAccumulator(candidate));
            accMap.put(candidate.chunkId(), current.withVectorScore(normalizeScore(candidate.score())));
        }
        for (RecallCandidate candidate : keywordCandidates) {
            if (candidate.chunkId() == null) {
                continue;
            }
            CandidateAccumulator current = accMap.computeIfAbsent(candidate.chunkId(), ignore -> new CandidateAccumulator(candidate));
            CandidateAccumulator updated = current.withKeywordScore(normalizeScore(candidate.score()));
            if (isBlank(updated.candidate().content()) && !isBlank(candidate.content())) {
                updated = updated.withCandidate(candidate);
            }
            accMap.put(candidate.chunkId(), updated);
        }
        if (accMap.isEmpty()) {
            return List.of();
        }

        final Set<Long> tagMatchedIds;
        if (!filters.industryTags().isEmpty()) {
            tagMatchedIds = qaRepository.findChunkIdsByIndustryTags(new ArrayList<>(accMap.keySet()), filters.industryTags());
        } else {
            tagMatchedIds = Set.of();
        }

        return accMap.values().stream()
                .map(acc -> toMergedCandidate(normalizedQuestion, acc, tagMatchedIds.contains(acc.candidate().chunkId())))
                .sorted(Comparator.comparing(MergedCandidate::finalScore, Comparator.reverseOrder()))
                .limit(finalTopN)
                .toList();
    }

    private MergedCandidate toMergedCandidate(String normalizedQuestion, CandidateAccumulator acc, boolean tagMatched) {
        Double vectorScore = acc.vectorScore();
        Double keywordScore = acc.keywordScore();
        double titleScore = computeTitleMatchScore(normalizedQuestion, acc.candidate());
        double tagScore = tagMatched ? 1.0 : 0.0;
        double finalScore = vectorWeight * valueOrZero(vectorScore)
                + keywordWeight * valueOrZero(keywordScore)
                + titleWeight * titleScore
                + tagWeight * tagScore;

        RecallCandidate finalCandidate = new RecallCandidate(
                acc.candidate().chunkId(),
                acc.candidate().lawName(),
                acc.candidate().articleNo(),
                acc.candidate().content(),
                finalScore,
                acc.candidate().pageStart(),
                acc.candidate().pageEnd(),
                acc.candidate().versionNo()
        );

        return new MergedCandidate(
                finalCandidate,
                sourceType(vectorScore, keywordScore),
                Math.max(valueOrZero(vectorScore), valueOrZero(keywordScore)),
                keywordScore,
                finalScore
        );
    }

    private List<RecallCandidate> applyMetadataFilter(List<RecallCandidate> candidates, QaFilters filters) {
        List<Long> chunkIds = candidates.stream().map(RecallCandidate::chunkId).filter(id -> id != null).toList();
        if (chunkIds.isEmpty()) {
            return List.of();
        }
        Set<Long> allowedIds = qaRepository.filterChunkIdsByMetadata(chunkIds, filters);
        if (allowedIds.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .filter(candidate -> candidate.chunkId() != null && allowedIds.contains(candidate.chunkId()))
                .toList();
    }

    private QaFilters normalizeFilters(AskFilters filters) {
        if (filters == null) {
            return QaFilters.empty();
        }
        return new QaFilters(
                sanitizeList(filters.industryTags()),
                sanitizeList(filters.docTypes()),
                sanitizeString(filters.publishOrg()),
                filters.effectiveOn()
        );
    }

    private List<String> sanitizeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> dedup = new LinkedHashSet<>();
        for (String value : values) {
            String cleaned = sanitizeString(value);
            if (cleaned != null) {
                dedup.add(cleaned);
            }
        }
        return List.copyOf(dedup);
    }

    private String sanitizeString(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private List<String> extractKeywords(String normalizedQuestion) {
        if (isBlank(normalizedQuestion)) {
            return List.of();
        }
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        keywords.add(normalizedQuestion);
        String[] tokens = normalizedQuestion.split("[\\s,，。;；:：()（）【】\\[\\]、]+");
        for (String token : tokens) {
            String cleaned = sanitizeString(token);
            if (cleaned != null && cleaned.length() >= 2) {
                keywords.add(cleaned);
            }
            if (keywords.size() >= 8) {
                break;
            }
        }
        return new ArrayList<>(keywords);
    }

    private String toFiltersJson(QaFilters filters) {
        try {
            return objectMapper.writeValueAsString(filters);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private List<EvidenceDto> toEvidenceDtos(List<MergedCandidate> mergedCandidates) {
        List<EvidenceDto> result = new ArrayList<>(mergedCandidates.size());
        for (int i = 0; i < mergedCandidates.size(); i++) {
            MergedCandidate merged = mergedCandidates.get(i);
            result.add(new EvidenceDto(
                    i + 1,
                    merged.candidate().chunkId(),
                    merged.candidate().lawName(),
                    merged.candidate().articleNo(),
                    merged.candidate().content(),
                    merged.sourceType(),
                    merged.finalScore(),
                    merged.candidate().pageStart(),
                    merged.candidate().pageEnd(),
                    merged.candidate().versionNo()
            ));
        }
        return result;
    }

    private EvidenceDto toEvidenceDto(QaEvidence evidence) {
        return new EvidenceDto(
                evidence.citeNo() == null ? 0 : evidence.citeNo(),
                evidence.chunkId(),
                evidence.lawName(),
                evidence.articleNo(),
                evidence.quotedText(),
                evidence.sourceType(),
                evidence.finalScore(),
                evidence.pageStart(),
                evidence.pageEnd(),
                evidence.fileVersion()
        );
    }

    private double computeTitleMatchScore(String normalizedQuestion, RecallCandidate candidate) {
        String lawName = candidate.lawName() == null ? "" : candidate.lawName();
        String articleNo = candidate.articleNo() == null ? "" : candidate.articleNo();
        if ((!lawName.isBlank() && normalizedQuestion.contains(lawName))
                || (!articleNo.isBlank() && normalizedQuestion.contains(articleNo))) {
            return 1.0;
        }
        return 0.0;
    }

    private String sourceType(Double vectorScore, Double keywordScore) {
        boolean hasVector = vectorScore != null;
        boolean hasKeyword = keywordScore != null;
        if (hasVector && hasKeyword) {
            return "hybrid";
        }
        if (hasKeyword) {
            return "keyword";
        }
        return "vector";
    }

    private Double normalizeScore(Double score) {
        if (score == null) {
            return null;
        }
        return Math.max(0d, Math.min(1d, score));
    }

    private double valueOrZero(Double score) {
        return score == null ? 0d : score;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeQuestion(String question) {
        return question == null ? "" : question.trim().replaceAll("\\s+", " ");
    }

    private String evaluateLowConfidenceRejectReason(List<MergedCandidate> mergedCandidates) {
        if (mergedCandidates.size() < minEvidenceCount) {
            return buildRejectReason(
                    "INSUFFICIENT_EVIDENCE_COUNT",
                    "required min evidence count=" + minEvidenceCount + ", actual=" + mergedCandidates.size()
            );
        }
        double top1 = mergedCandidates.get(0).finalScore();
        if (top1 < minTop1Score) {
            return buildRejectReason(
                    "LOW_TOP1_SCORE",
                    "top1 final score=" + formatScore(top1) + " below threshold=" + formatScore(minTop1Score)
            );
        }
        if (mergedCandidates.size() >= 2) {
            double top2 = mergedCandidates.get(1).finalScore();
            double gap = top1 - top2;
            if (gap < minTopGap && top1 < minConfidentScore) {
                return buildRejectReason(
                        "LOW_SCORE_GAP",
                        "top1-top2 gap=" + formatScore(gap)
                                + " below threshold=" + formatScore(minTopGap)
                                + " and top1=" + formatScore(top1)
                                + " below confident threshold=" + formatScore(minConfidentScore)
                );
            }
        }
        return null;
    }

    private String buildRejectReason(String code, String detail) {
        return code + ": " + detail;
    }

    private String formatScore(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private Long newId() {
        long ts = System.currentTimeMillis();
        int rand = ThreadLocalRandom.current().nextInt(1_000_000);
        return ts * 1_000_000L + rand;
    }

    private record CandidateAccumulator(RecallCandidate candidate, Double vectorScore, Double keywordScore) {
        private CandidateAccumulator(RecallCandidate candidate) {
            this(candidate, null, null);
        }

        private CandidateAccumulator withCandidate(RecallCandidate candidate) {
            return new CandidateAccumulator(candidate, vectorScore, keywordScore);
        }

        private CandidateAccumulator withVectorScore(Double vectorScore) {
            return new CandidateAccumulator(candidate, vectorScore, keywordScore);
        }

        private CandidateAccumulator withKeywordScore(Double keywordScore) {
            return new CandidateAccumulator(candidate, vectorScore, keywordScore);
        }
    }

    private record MergedCandidate(
            RecallCandidate candidate,
            String sourceType,
            Double rawScore,
            Double rerankScore,
            Double finalScore
    ) {
    }
}
