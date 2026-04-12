package my.inspectorrag.records.domain.repository;

import my.inspectorrag.records.domain.model.QaRecordItem;
import my.inspectorrag.records.domain.model.QaReplay;
import my.inspectorrag.records.domain.model.QaQualityReport;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface RecordsRepository {

    List<QaRecordItem> listQa(int limit);

    Optional<QaReplay> replay(Long qaId);

    QaQualityReport qualityReport(OffsetDateTime from, OffsetDateTime to);
}
