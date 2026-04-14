package my.inspectorrag.searchandreturn.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import my.inspectorrag.searchandreturn.application.exception.NoEvidenceFoundException;
import my.inspectorrag.searchandreturn.domain.model.QaDetail;
import my.inspectorrag.searchandreturn.domain.model.QaEvidence;
import my.inspectorrag.searchandreturn.domain.model.QaFilters;
import my.inspectorrag.searchandreturn.domain.model.ConversationContextTurn;
import my.inspectorrag.searchandreturn.domain.model.ConversationMessage;
import my.inspectorrag.searchandreturn.domain.model.RejectThresholdConfig;
import my.inspectorrag.searchandreturn.domain.model.RecallCandidate;
import my.inspectorrag.searchandreturn.domain.model.RewriteMode;
import my.inspectorrag.searchandreturn.domain.model.RewriteResult;
import my.inspectorrag.searchandreturn.domain.repository.QaRepository;
import my.inspectorrag.searchandreturn.domain.service.AnswerGenerator;
import my.inspectorrag.searchandreturn.domain.service.QuestionRewriteService;
import my.inspectorrag.searchandreturn.domain.service.RecallService;
import my.inspectorrag.searchandreturn.interfaces.dto.AskFilters;
import my.inspectorrag.searchandreturn.interfaces.dto.AskResponse;
import my.inspectorrag.searchandreturn.interfaces.dto.EvidenceDto;
import my.inspectorrag.searchandreturn.interfaces.dto.QaDetailResponse;
import my.inspectorrag.searchandreturn.interfaces.dto.ConversationMessageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class QaApplicationService {

    private static final int VECTOR_RECALL_MULTIPLIER = 3;
    private static final String REJECT_USER_MESSAGE = "没有在数据库中找到合适的法律法规";
    private static final String LOW_TOP1_SCORE_CODE = "LOW_TOP1_SCORE";
    private static final String LOW_SCORE_GAP_CODE = "LOW_SCORE_GAP";
    private static final Logger log = LoggerFactory.getLogger(QaApplicationService.class);

    private final QaRepository qaRepository;
    private final RecallService recallService;
    private final AnswerGenerator answerGenerator;
    private final QuestionRewriteService questionRewriteService;
    private final RejectThresholdConfigApplicationService rejectThresholdConfigApplicationService;
    private final ObjectMapper objectMapper;
    private final String embeddingModelName;
    private final int topK;
    private final int keywordTopK;
    private final int finalTopN;
    private final String ftsLanguage;
    private final boolean scoreNormalizationEnabled;
    private final double vectorWeight;
    private final double keywordWeight;
    private final double titleWeight;
    private final double tagWeight;
    private final int rewriteMaxAttempts;
    private final int rewriteMaxCandidateQueries;
    private final int rewriteMaxQueryLength;
    private final int contextTurns;

    public QaApplicationService(
            QaRepository qaRepository,
            RecallService recallService,
            AnswerGenerator answerGenerator,
            QuestionRewriteService questionRewriteService,
            RejectThresholdConfigApplicationService rejectThresholdConfigApplicationService,
            @Value("${inspector.embedding.model-name}") String embeddingModelName,
            @Value("${inspector.embedding.top-k}") int topK,
            @Value("${inspector.retrieval.phase2.keyword-topk:20}") int keywordTopK,
            @Value("${inspector.retrieval.phase2.final-topn:8}") int finalTopN,
            @Value("${inspector.retrieval.phase2.fts-language:simple}") String ftsLanguage,
            @Value("${inspector.retrieval.phase2.score-normalization.enabled:true}") boolean scoreNormalizationEnabled,
            @Value("${inspector.retrieval.phase2.weights.vector:0.55}") double vectorWeight,
            @Value("${inspector.retrieval.phase2.weights.keyword:0.25}") double keywordWeight,
            @Value("${inspector.retrieval.phase2.weights.title:0.10}") double titleWeight,
            @Value("${inspector.retrieval.phase2.weights.tag:0.10}") double tagWeight,
            @Value("${inspector.retrieval.rewrite.max-attempts:3}") int rewriteMaxAttempts,
            @Value("${inspector.retrieval.rewrite.max-candidate-queries:3}") int rewriteMaxCandidateQueries,
            @Value("${inspector.retrieval.rewrite.max-query-length:120}") int rewriteMaxQueryLength,
            @Value("${inspector.retrieval.chat.context-turns:6}") int contextTurns
    ) {
        this.qaRepository = qaRepository;
        this.recallService = recallService;
        this.answerGenerator = answerGenerator;
        this.questionRewriteService = questionRewriteService;
        this.rejectThresholdConfigApplicationService = rejectThresholdConfigApplicationService;
        this.objectMapper = JsonMapper.builder().findAndAddModules().build();
        this.embeddingModelName = embeddingModelName;
        this.topK = topK;
        this.keywordTopK = keywordTopK;
        this.finalTopN = finalTopN;
        this.ftsLanguage = ftsLanguage;
        this.scoreNormalizationEnabled = scoreNormalizationEnabled;
        this.vectorWeight = vectorWeight;
        this.keywordWeight = keywordWeight;
        this.titleWeight = titleWeight;
        this.tagWeight = tagWeight;
        this.rewriteMaxAttempts = rewriteMaxAttempts;
        this.rewriteMaxCandidateQueries = rewriteMaxCandidateQueries;
        this.rewriteMaxQueryLength = rewriteMaxQueryLength;
        this.contextTurns = contextTurns;
    }

    @Transactional(noRollbackFor = NoEvidenceFoundException.class)
    public AskResponse ask(String question, String conversationIdText, AskFilters askFilters) {
        long start = System.currentTimeMillis();
        String normalized = normalizeQuestion(question);
        QaFilters filters = normalizeFilters(askFilters);
        ConversationScope conversationScope = resolveConversationScope(conversationIdText);
        List<ConversationContextTurn> context = qaRepository.findConversationContext(
                conversationScope.conversationId(),
                contextTurns
        );
        String routeKey = String.valueOf(conversationScope.conversationId());
        String embeddingProfileKey = recallService.resolveProfileKey(routeKey);
        RewriteContext autoRewriteContext = resolveRewriteContext(question, normalized, context, RewriteMode.AUTO);
        RetrievalRound autoRound = executeRetrievalRound(autoRewriteContext, filters, routeKey);
        RejectThresholdConfig rejectThresholdConfig = rejectThresholdConfigApplicationService.resolveForAsk();
        String autoRejectReason = evaluateRoundRejectReason(autoRound.mergedCandidates(), rejectThresholdConfig);

        RetrievalRound selectedRound = autoRound;
        String selectedRejectReason = autoRejectReason;
        boolean forceFallbackTriggered = false;

        if (shouldForceFallback(autoRound.rewriteContext(), autoRejectReason)) {
            forceFallbackTriggered = true;
            RewriteContext forceRewriteContext = resolveRewriteContext(question, normalized, context, RewriteMode.FORCE);
            RetrievalRound forceRound = executeRetrievalRound(forceRewriteContext, filters, routeKey);
            String forceRejectReason = evaluateRoundRejectReason(forceRound.mergedCandidates(), rejectThresholdConfig);
            RoundSelection selected = choosePreferredRound(
                    autoRound,
                    autoRejectReason,
                    forceRound,
                    forceRejectReason
            );
            selectedRound = selected.round();
            selectedRejectReason = selected.rejectReason();
            logRoundComparison(conversationScope.conversationId(), autoRound, autoRejectReason, forceRound, forceRejectReason, selected);
        } else {
            log.info("qa rewrite decision: conversationId={}, rewriteMode={}, rewriteNeeded={}, effectiveQuery={}",
                    conversationScope.conversationId(),
                    autoRewriteContext.mode(),
                    autoRewriteContext.rewriteNeeded(),
                    autoRewriteContext.effectiveQuery());
        }

        List<MergedCandidate> mergedCandidates = selectedRound.mergedCandidates();
        RewriteContext rewriteContext = selectedRound.rewriteContext();
        List<String> mergedKeywords = selectedRound.mergedKeywords();

        if (mergedCandidates.isEmpty()) {
            String rejectReason = buildRejectReason(
                    "NO_EVIDENCE",
                    "no evidence found for current question"
            );
            OffsetDateTime now = OffsetDateTime.now();
            qaRepository.insertRejectedQaRecord(
                    newId(),
                    conversationScope.conversationId(),
                    conversationScope.turnNo(),
                    question,
                    normalized,
                    rewriteContext.rewrittenQuestion(),
                    null,
                    rejectReason,
                    (int) (System.currentTimeMillis() - start),
                    now
            );
            throw new NoEvidenceFoundException(REJECT_USER_MESSAGE);
        }
        if (selectedRejectReason != null) {
            OffsetDateTime now = OffsetDateTime.now();
            Long qaId = newId();
            String guidanceAnswer = answerGenerator.generateLowConfidenceGuidance(
                    question,
                    rewriteContext.effectiveQuery(),
                    context
            );
            qaRepository.insertRejectedQaRecord(
                    qaId,
                    conversationScope.conversationId(),
                    conversationScope.turnNo(),
                    question,
                    normalized,
                    rewriteContext.rewrittenQuestion(),
                    guidanceAnswer,
                    selectedRejectReason,
                    (int) (System.currentTimeMillis() - start),
                    now
            );
            qaRepository.insertRetrievalSnapshot(
                newId(),
                    qaId,
                    embeddingModelName,
                    embeddingProfileKey,
                    topK,
                    mergedCandidates.size(),
                    toFiltersJson(filters),
                    String.join(" ", mergedKeywords),
                    rewriteContext.effectiveQuery(),
                    toRewriteQueriesJson(rewriteContext.candidateQueries()),
                    now
            );
            persistCandidatesAndEvidences(qaId, mergedCandidates, now);
            log.info("qa low-confidence guidance returned: conversationId={}, qaId={}, turnNo={}, rejectReason={}, rewriteMode={}, rewriteNeeded={}, forceFallbackTriggered={}",
                    conversationScope.conversationId(),
                    qaId,
                    conversationScope.turnNo(),
                    selectedRejectReason,
                    rewriteContext.mode(),
                    rewriteContext.rewriteNeeded(),
                    forceFallbackTriggered);
            return new AskResponse(
                    toIdString(qaId),
                    toIdString(conversationScope.conversationId()),
                    conversationScope.turnNo(),
                    normalized,
                    rewriteContext.rewrittenQuestion(),
                    rewriteContext.candidateQueries(),
                    "reject",
                    guidanceAnswer,
                    toEvidenceDtos(mergedCandidates)
            );
        }

        String answer = answerGenerator.generate(
                question,
                rewriteContext.effectiveQuery(),
                context,
                mergedCandidates.stream().map(MergedCandidate::candidate).toList()
        );

        OffsetDateTime now = OffsetDateTime.now();
        Long qaId = newId();
        qaRepository.insertQaRecord(
                qaId,
                conversationScope.conversationId(),
                conversationScope.turnNo(),
                question,
                normalized,
                rewriteContext.rewrittenQuestion(),
                answer,
                (int) (System.currentTimeMillis() - start),
                now
        );
        qaRepository.insertRetrievalSnapshot(
                newId(),
                qaId,
                embeddingModelName,
                embeddingProfileKey,
                topK,
                mergedCandidates.size(),
                toFiltersJson(filters),
                String.join(" ", mergedKeywords),
                rewriteContext.effectiveQuery(),
                toRewriteQueriesJson(rewriteContext.candidateQueries()),
                now
        );

        persistCandidatesAndEvidences(qaId, mergedCandidates, now);

        return new AskResponse(
                toIdString(qaId),
                toIdString(conversationScope.conversationId()),
                conversationScope.turnNo(),
                normalized,
                rewriteContext.rewrittenQuestion(),
                rewriteContext.candidateQueries(),
                "success",
                answer,
                toEvidenceDtos(mergedCandidates)
        );
    }

    @Transactional(readOnly = true)
    public QaDetailResponse getQa(Long qaId) {
        QaDetail detail = qaRepository.findQaDetail(qaId)
                .orElseThrow(() -> new IllegalArgumentException("qa record not found: " + qaId));
        return new QaDetailResponse(
                toIdString(detail.qaId()),
                toIdString(detail.conversationId()),
                detail.turnNo() == null ? 0 : detail.turnNo(),
                detail.question(),
                detail.normalizedQuestion(),
                detail.rewrittenQuestion(),
                detail.rewriteQueries(),
                detail.answer(),
                detail.answerStatus(),
                detail.createdAt(),
                detail.evidences().stream().map(this::toEvidenceDto).toList()
        );
    }

    @Transactional(readOnly = true)
    public List<ConversationMessageResponse> getConversationMessages(Long conversationId) {
        List<ConversationMessage> messages = qaRepository.findConversationMessages(conversationId);
        return messages.stream()
                .map(message -> new ConversationMessageResponse(
                        toIdString(message.qaId()),
                        message.turnNo() == null ? 0 : message.turnNo(),
                        message.question(),
                        message.normalizedQuestion(),
                        message.rewrittenQuestion(),
                        message.rewriteQueries(),
                        message.answer(),
                        message.answerStatus(),
                        message.createdAt(),
                        message.evidences().stream().map(this::toEvidenceDto).toList()
                ))
                .toList();
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
        double rawWeightedScore = vectorWeight * valueOrZero(vectorScore)
                + keywordWeight * valueOrZero(keywordScore)
                + titleWeight * titleScore
                + tagWeight * tagScore;
        double effectiveWeightSum = 0d;
        if (vectorScore != null) {
            effectiveWeightSum += vectorWeight;
        }
        if (keywordScore != null) {
            effectiveWeightSum += keywordWeight;
        }
        if (titleScore > 0d) {
            effectiveWeightSum += titleWeight;
        }
        if (tagScore > 0d) {
            effectiveWeightSum += tagWeight;
        }
        double finalScore = rawWeightedScore;
        if (scoreNormalizationEnabled && effectiveWeightSum > 0d) {
            finalScore = rawWeightedScore / effectiveWeightSum;
        }
        finalScore = valueOrZero(normalizeScore(finalScore));

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
                finalScore,
                rawWeightedScore,
                effectiveWeightSum
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

    private boolean hasMetadataFilters(QaFilters filters) {
        return !(filters.industryTags().isEmpty()
                && filters.docTypes().isEmpty()
                && filters.publishOrg() == null
                && filters.effectiveOn() == null);
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

    private String toRewriteQueriesJson(List<String> rewriteQueries) {
        try {
            return objectMapper.writeValueAsString(rewriteQueries == null ? List.of() : rewriteQueries);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<EvidenceDto> toEvidenceDtos(List<MergedCandidate> mergedCandidates) {
        List<EvidenceDto> result = new ArrayList<>(mergedCandidates.size());
        for (int i = 0; i < mergedCandidates.size(); i++) {
            MergedCandidate merged = mergedCandidates.get(i);
            result.add(new EvidenceDto(
                    i + 1,
                    toIdString(merged.candidate().chunkId()),
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
                toIdString(evidence.chunkId()),
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

    private String toIdString(Long id) {
        return id == null ? null : String.valueOf(id);
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

    private RewriteContext resolveRewriteContext(
            String question,
            String normalizedQuestion,
            List<ConversationContextTurn> context,
            RewriteMode mode
    ) {
        int attempts = Math.max(1, rewriteMaxAttempts);
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                RewriteResult rewriteResult = questionRewriteService.rewrite(question, normalizedQuestion, context, mode);
                return sanitizeRewriteResult(rewriteResult, normalizedQuestion, mode);
            } catch (RuntimeException ex) {
                lastError = ex;
                log.warn("question rewrite failed on attempt {}/{}. fallback pending. mode={}, normalizedQuestion={}, error={}",
                        attempt, attempts, mode, normalizedQuestion, ex.getMessage());
            }
        }
        if (lastError != null) {
            log.warn("question rewrite exhausted all attempts. use normalized question as fallback. mode={}, normalizedQuestion={}, error={}",
                    mode, normalizedQuestion, lastError.getMessage());
        }
        return fallbackRewriteContext(normalizedQuestion, mode);
    }

    private RetrievalRound executeRetrievalRound(RewriteContext rewriteContext, QaFilters filters, String routeKey) {
        List<RecallCandidate> vectorCandidates = new ArrayList<>();
        List<RecallCandidate> keywordCandidates = new ArrayList<>();
        LinkedHashSet<String> allKeywords = new LinkedHashSet<>();
        for (String query : rewriteContext.candidateQueries()) {
            List<String> keywords = extractKeywords(query);
            allKeywords.addAll(keywords);
            List<RecallCandidate> queryVectorCandidates = recallService.recall(
                    query,
                    topK * VECTOR_RECALL_MULTIPLIER,
                    filters,
                    routeKey
            );
            if (!queryVectorCandidates.isEmpty() && hasMetadataFilters(filters)) {
                queryVectorCandidates = applyMetadataFilter(queryVectorCandidates, filters);
            }
            vectorCandidates.addAll(queryVectorCandidates);
            keywordCandidates.addAll(qaRepository.keywordRecall(
                    query,
                    keywords,
                    keywordTopK,
                    filters,
                    ftsLanguage
            ));
        }
        List<MergedCandidate> mergedCandidates = mergeAndRank(
                rewriteContext.titleScoreQueryText(),
                filters,
                vectorCandidates,
                keywordCandidates
        );
        return new RetrievalRound(
                rewriteContext,
                List.copyOf(allKeywords),
                mergedCandidates
        );
    }

    private String evaluateRoundRejectReason(List<MergedCandidate> mergedCandidates, RejectThresholdConfig rejectThresholdConfig) {
        if (mergedCandidates.isEmpty()) {
            return buildRejectReason("NO_EVIDENCE", "no evidence found for current question");
        }
        return evaluateLowConfidenceRejectReason(mergedCandidates, rejectThresholdConfig);
    }

    private boolean shouldForceFallback(RewriteContext rewriteContext, String rejectReason) {
        if (rewriteContext.rewriteNeeded()) {
            return false;
        }
        if (rejectReason == null) {
            return false;
        }
        String code = extractRejectCode(rejectReason);
        return LOW_TOP1_SCORE_CODE.equals(code) || LOW_SCORE_GAP_CODE.equals(code);
    }

    private RoundSelection choosePreferredRound(
            RetrievalRound firstRound,
            String firstRejectReason,
            RetrievalRound secondRound,
            String secondRejectReason
    ) {
        boolean firstPass = firstRejectReason == null;
        boolean secondPass = secondRejectReason == null;
        if (firstPass && !secondPass) {
            return new RoundSelection(firstRound, firstRejectReason);
        }
        if (!firstPass && secondPass) {
            return new RoundSelection(secondRound, secondRejectReason);
        }
        double firstTop1 = top1Score(firstRound.mergedCandidates());
        double secondTop1 = top1Score(secondRound.mergedCandidates());
        if (secondTop1 > firstTop1) {
            return new RoundSelection(secondRound, secondRejectReason);
        }
        return new RoundSelection(firstRound, firstRejectReason);
    }

    private void logRoundComparison(
            Long conversationId,
            RetrievalRound firstRound,
            String firstRejectReason,
            RetrievalRound secondRound,
            String secondRejectReason,
            RoundSelection selected
    ) {
        log.info("qa rewrite fallback compare: conversationId={}, firstMode={}, firstRewriteNeeded={}, firstEffectiveQuery={}, firstTop1={}, firstTop2={}, firstGap={}, firstReject={}, secondMode={}, secondRewriteNeeded={}, secondEffectiveQuery={}, secondTop1={}, secondTop2={}, secondGap={}, secondReject={}, selectedMode={}, selectedEffectiveQuery={}",
                conversationId,
                firstRound.rewriteContext().mode(),
                firstRound.rewriteContext().rewriteNeeded(),
                firstRound.rewriteContext().effectiveQuery(),
                formatScore(top1Score(firstRound.mergedCandidates())),
                formatScore(top2Score(firstRound.mergedCandidates())),
                formatScore(topGap(firstRound.mergedCandidates())),
                firstRejectReason,
                secondRound.rewriteContext().mode(),
                secondRound.rewriteContext().rewriteNeeded(),
                secondRound.rewriteContext().effectiveQuery(),
                formatScore(top1Score(secondRound.mergedCandidates())),
                formatScore(top2Score(secondRound.mergedCandidates())),
                formatScore(topGap(secondRound.mergedCandidates())),
                secondRejectReason,
                selected.round().rewriteContext().mode(),
                selected.round().rewriteContext().effectiveQuery());
    }

    private double top1Score(List<MergedCandidate> mergedCandidates) {
        if (mergedCandidates == null || mergedCandidates.isEmpty()) {
            return -1d;
        }
        return valueOrZero(mergedCandidates.getFirst().finalScore());
    }

    private double top2Score(List<MergedCandidate> mergedCandidates) {
        if (mergedCandidates == null || mergedCandidates.size() < 2) {
            return -1d;
        }
        return valueOrZero(mergedCandidates.get(1).finalScore());
    }

    private double topGap(List<MergedCandidate> mergedCandidates) {
        double top1 = top1Score(mergedCandidates);
        double top2 = top2Score(mergedCandidates);
        if (top1 < 0d || top2 < 0d) {
            return -1d;
        }
        return top1 - top2;
    }

    private String extractRejectCode(String rejectReason) {
        if (rejectReason == null) {
            return "";
        }
        int idx = rejectReason.indexOf(':');
        if (idx <= 0) {
            return rejectReason.trim();
        }
        return rejectReason.substring(0, idx).trim();
    }

    private ConversationScope resolveConversationScope(String conversationIdText) {
        Long conversationId = parseConversationId(conversationIdText);
        OffsetDateTime now = OffsetDateTime.now();
        if (conversationId == null || !qaRepository.existsConversation(conversationId)) {
            Long newConversationId = newId();
            qaRepository.insertConversation(newConversationId, now);
            return new ConversationScope(newConversationId, 1);
        }
        return new ConversationScope(conversationId, qaRepository.nextTurnNo(conversationId));
    }

    private Long parseConversationId(String conversationIdText) {
        if (conversationIdText == null || conversationIdText.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(conversationIdText.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private void persistCandidatesAndEvidences(Long qaId, List<MergedCandidate> mergedCandidates, OffsetDateTime now) {
        for (int i = 0; i < mergedCandidates.size(); i++) {
            int rankNo = i + 1;
            int citeNo = i + 1;
            MergedCandidate merged = mergedCandidates.get(i);
            log.info(
                    "qa score before candidate persist: qaId={}, rankNo={}, chunkId={}, sourceType={}, rawWeightedScore={}, effectiveWeightSum={}, finalScore={}, normalizationEnabled={}",
                    qaId,
                    rankNo,
                    merged.candidate().chunkId(),
                    merged.sourceType(),
                    formatScore(merged.rawWeightedScore()),
                    formatScore(merged.effectiveWeightSum()),
                    formatScore(merged.finalScore()),
                    scoreNormalizationEnabled
            );
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
    }

    private RewriteContext sanitizeRewriteResult(RewriteResult rewriteResult, String normalizedQuestion, RewriteMode mode) {
        String normalizedAnchor = sanitizeQueryText(normalizedQuestion);
        if (rewriteResult == null) {
            return fallbackRewriteContext(normalizedAnchor, mode);
        }
        boolean rewriteNeeded = rewriteResult.rewriteNeeded();
        String rewriteReason = sanitizeString(rewriteResult.rewriteReason());
        List<String> preserveTerms = sanitizeList(rewriteResult.preserveTerms());
        if (!rewriteNeeded) {
            String anchor = Objects.requireNonNullElse(normalizedAnchor, normalizeQuestion(normalizedQuestion));
            return new RewriteContext(
                    null,
                    List.of(anchor),
                    anchor,
                    anchor,
                    false,
                    rewriteReason,
                    preserveTerms,
                    mode
            );
        }
        String rewrittenQuestion = sanitizeQueryText(rewriteResult.rewrittenQuestion());
        LinkedHashSet<String> dedupQueries = new LinkedHashSet<>();
        addQueryWithinLimit(dedupQueries, normalizedAnchor);
        addQueryWithinLimit(dedupQueries, rewrittenQuestion);
        if (rewriteResult.candidateQueries() != null) {
            for (String candidateQuery : rewriteResult.candidateQueries()) {
                String sanitized = sanitizeQueryText(candidateQuery);
                if (!addQueryWithinLimit(dedupQueries, sanitized)) {
                    break;
                }
            }
        }
        if (dedupQueries.isEmpty()) {
            dedupQueries.add(normalizedQuestion);
            rewrittenQuestion = null;
        }
        String effectiveQuery = rewrittenQuestion == null ? dedupQueries.iterator().next() : rewrittenQuestion;
        String titleScoreQueryText = String.join(" ", dedupQueries);
        return new RewriteContext(
                rewrittenQuestion,
                List.copyOf(dedupQueries),
                effectiveQuery,
                titleScoreQueryText,
                true,
                rewriteReason,
                preserveTerms,
                mode
        );
    }

    private RewriteContext fallbackRewriteContext(String normalizedQuestion, RewriteMode mode) {
        String fallbackQuery = normalizeQuestion(normalizedQuestion);
        return new RewriteContext(
                null,
                List.of(fallbackQuery),
                fallbackQuery,
                fallbackQuery,
                false,
                "rewrite-fallback",
                List.of(),
                mode
        );
    }

    private boolean addQueryWithinLimit(LinkedHashSet<String> dedupQueries, String query) {
        if (query == null || dedupQueries.contains(query)) {
            return true;
        }
        if (dedupQueries.size() >= rewriteMaxCandidateQueries) {
            return false;
        }
        dedupQueries.add(query);
        return true;
    }

    private String sanitizeQueryText(String value) {
        String cleaned = sanitizeString(value);
        if (cleaned == null) {
            return null;
        }
        String compacted = cleaned.replaceAll("\\s+", " ");
        if (compacted.length() > rewriteMaxQueryLength) {
            compacted = compacted.substring(0, rewriteMaxQueryLength);
        }
        return compacted;
    }

    private String evaluateLowConfidenceRejectReason(
            List<MergedCandidate> mergedCandidates,
            RejectThresholdConfig rejectThresholdConfig
    ) {
        if (mergedCandidates.size() < rejectThresholdConfig.minEvidenceCount()) {
            return buildRejectReason(
                    "INSUFFICIENT_EVIDENCE_COUNT",
                    "required min evidence count=" + rejectThresholdConfig.minEvidenceCount() + ", actual=" + mergedCandidates.size()
            );
        }
        double top1 = mergedCandidates.get(0).finalScore();
        double threshold = minTop1ThresholdBySourceType(mergedCandidates.get(0).sourceType(), rejectThresholdConfig);
        if (top1 < threshold) {
            return buildRejectReason(
                    "LOW_TOP1_SCORE",
                    "top1 final score=" + formatScore(top1) + " below threshold=" + formatScore(threshold)
            );
        }
        if (mergedCandidates.size() >= 2) {
            double top2 = mergedCandidates.get(1).finalScore();
            double gap = top1 - top2;
            if (gap < rejectThresholdConfig.minTopGap() && top1 < rejectThresholdConfig.minConfidentScore()) {
                return buildRejectReason(
                        "LOW_SCORE_GAP",
                        "top1-top2 gap=" + formatScore(gap)
                                + " below threshold=" + formatScore(rejectThresholdConfig.minTopGap())
                                + " and top1=" + formatScore(top1)
                                + " below confident threshold=" + formatScore(rejectThresholdConfig.minConfidentScore())
                );
            }
        }
        return null;
    }

    private double minTop1ThresholdBySourceType(String sourceType, RejectThresholdConfig rejectThresholdConfig) {
        if ("vector".equalsIgnoreCase(sourceType)) {
            return rejectThresholdConfig.minTop1ScoreVectorOnly();
        }
        return rejectThresholdConfig.minTop1Score();
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
            Double finalScore,
            Double rawWeightedScore,
            Double effectiveWeightSum
    ) {
    }

    private record RewriteContext(
            String rewrittenQuestion,
            List<String> candidateQueries,
            String effectiveQuery,
            String titleScoreQueryText,
            boolean rewriteNeeded,
            String rewriteReason,
            List<String> preserveTerms,
            RewriteMode mode
    ) {
    }

    private record RetrievalRound(
            RewriteContext rewriteContext,
            List<String> mergedKeywords,
            List<MergedCandidate> mergedCandidates
    ) {
    }

    private record RoundSelection(
            RetrievalRound round,
            String rejectReason
    ) {
    }

    private record ConversationScope(
            Long conversationId,
            int turnNo
    ) {
    }
}
