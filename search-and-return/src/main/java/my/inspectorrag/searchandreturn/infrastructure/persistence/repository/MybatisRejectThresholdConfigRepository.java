package my.inspectorrag.searchandreturn.infrastructure.persistence.repository;

import my.inspectorrag.searchandreturn.domain.model.RejectThresholdConfig;
import my.inspectorrag.searchandreturn.domain.repository.RejectThresholdConfigRepository;
import my.inspectorrag.searchandreturn.infrastructure.persistence.mapper.QaCommandMapper;
import my.inspectorrag.searchandreturn.infrastructure.persistence.mapper.QaQueryMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class MybatisRejectThresholdConfigRepository implements RejectThresholdConfigRepository {

    private final QaQueryMapper queryMapper;
    private final QaCommandMapper commandMapper;

    public MybatisRejectThresholdConfigRepository(QaQueryMapper queryMapper, QaCommandMapper commandMapper) {
        this.queryMapper = queryMapper;
        this.commandMapper = commandMapper;
    }

    @Override
    public Optional<RejectThresholdConfig> findCurrent() {
        var row = queryMapper.findCurrentRejectThresholdConfig();
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(new RejectThresholdConfig(
                row.minTop1Score().doubleValue(),
                row.minTop1ScoreVectorOnly().doubleValue(),
                row.minTopGap().doubleValue(),
                row.minConfidentScore().doubleValue(),
                row.minEvidenceCount(),
                row.updatedBy(),
                row.updatedAt()
        ));
    }

    @Override
    public void upsert(
            double minTop1Score,
            double minTop1ScoreVectorOnly,
            double minTopGap,
            double minConfidentScore,
            int minEvidenceCount,
            String updatedBy,
            OffsetDateTime now
    ) {
        commandMapper.upsertRejectThresholdConfig(
                minTop1Score,
                minTop1ScoreVectorOnly,
                minTopGap,
                minConfidentScore,
                minEvidenceCount,
                updatedBy,
                now
        );
    }
}
