package my.inspectorrag.searchandreturn.application.service;

import my.inspectorrag.searchandreturn.domain.model.RejectThresholdConfig;
import my.inspectorrag.searchandreturn.domain.repository.RejectThresholdConfigRepository;
import my.inspectorrag.searchandreturn.interfaces.dto.RejectThresholdConfigResponse;
import my.inspectorrag.searchandreturn.interfaces.dto.UpdateRejectThresholdConfigRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

@Service
public class RejectThresholdConfigApplicationService {

    private static final Logger log = LoggerFactory.getLogger(RejectThresholdConfigApplicationService.class);
    private static final String SOURCE_DB = "db";
    private static final String SOURCE_DEFAULT = "default";
    private static final String DEFAULT_OPERATOR = "anonymous";

    private final RejectThresholdConfigRepository repository;
    private final double defaultMinTop1Score;
    private final double defaultMinTop1ScoreVectorOnly;
    private final double defaultMinTopGap;
    private final double defaultMinConfidentScore;
    private final int defaultMinEvidenceCount;

    public RejectThresholdConfigApplicationService(
            RejectThresholdConfigRepository repository,
            @Value("${inspector.retrieval.phase3.reject.min-top1-score:0.50}") double defaultMinTop1Score,
            @Value("${inspector.retrieval.phase3.reject.min-top1-score-vector-only:0.58}") double defaultMinTop1ScoreVectorOnly,
            @Value("${inspector.retrieval.phase3.reject.min-top-gap:0.005}") double defaultMinTopGap,
            @Value("${inspector.retrieval.phase3.reject.min-confident-score:0.60}") double defaultMinConfidentScore,
            @Value("${inspector.retrieval.phase3.reject.min-evidence-count:2}") int defaultMinEvidenceCount
    ) {
        this.repository = repository;
        this.defaultMinTop1Score = defaultMinTop1Score;
        this.defaultMinTop1ScoreVectorOnly = defaultMinTop1ScoreVectorOnly;
        this.defaultMinTopGap = defaultMinTopGap;
        this.defaultMinConfidentScore = defaultMinConfidentScore;
        this.defaultMinEvidenceCount = defaultMinEvidenceCount;
    }

    public RejectThresholdConfig resolveForAsk() {
        return loadCurrentWithSource().config();
    }

    public RejectThresholdConfigResponse getCurrent() {
        LoadedConfig loaded = loadCurrentWithSource();
        return toResponse(loaded.config(), loaded.source());
    }

    public RejectThresholdConfigResponse update(UpdateRejectThresholdConfigRequest request, String operatorHeader) {
        String operator = normalizeOperator(operatorHeader);
        OffsetDateTime now = OffsetDateTime.now();
        repository.upsert(
                request.minTop1Score(),
                request.minTop1ScoreVectorOnly(),
                request.minTopGap(),
                request.minConfidentScore(),
                request.minEvidenceCount(),
                operator,
                now
        );
        RejectThresholdConfig config = new RejectThresholdConfig(
                request.minTop1Score(),
                request.minTop1ScoreVectorOnly(),
                request.minTopGap(),
                request.minConfidentScore(),
                request.minEvidenceCount(),
                operator,
                now
        );
        return toResponse(config, SOURCE_DB);
    }

    private LoadedConfig loadCurrentWithSource() {
        try {
            return repository.findCurrent()
                    .map(config -> new LoadedConfig(config, SOURCE_DB))
                    .orElseGet(() -> new LoadedConfig(defaultConfig(), SOURCE_DEFAULT));
        } catch (RuntimeException ex) {
            log.warn("load reject threshold config failed. fallback to defaults. error={}", ex.getMessage());
            return new LoadedConfig(defaultConfig(), SOURCE_DEFAULT);
        }
    }

    private RejectThresholdConfig defaultConfig() {
        return new RejectThresholdConfig(
                defaultMinTop1Score,
                defaultMinTop1ScoreVectorOnly,
                defaultMinTopGap,
                defaultMinConfidentScore,
                defaultMinEvidenceCount,
                null,
                null
        );
    }

    private RejectThresholdConfigResponse toResponse(RejectThresholdConfig config, String source) {
        return new RejectThresholdConfigResponse(
                config.minTop1Score(),
                config.minTop1ScoreVectorOnly(),
                config.minTopGap(),
                config.minConfidentScore(),
                config.minEvidenceCount(),
                config.updatedBy(),
                config.updatedAt(),
                source
        );
    }

    private String normalizeOperator(String operatorHeader) {
        if (operatorHeader == null || operatorHeader.isBlank()) {
            return DEFAULT_OPERATOR;
        }
        String trimmed = operatorHeader.trim();
        return trimmed.length() <= 128 ? trimmed : trimmed.substring(0, 128);
    }

    private record LoadedConfig(RejectThresholdConfig config, String source) {
    }
}
