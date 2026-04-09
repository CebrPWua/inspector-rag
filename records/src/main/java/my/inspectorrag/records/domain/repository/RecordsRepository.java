package my.inspectorrag.records.domain.repository;

import my.inspectorrag.records.domain.model.QaRecordItem;
import my.inspectorrag.records.domain.model.QaReplay;

import java.util.List;
import java.util.Optional;

public interface RecordsRepository {

    List<QaRecordItem> listQa(int limit);

    Optional<QaReplay> replay(Long qaId);
}
