package my.inspectorrag.filemanagement.domain.repository;

import my.inspectorrag.filemanagement.domain.model.LawDocument;
import my.inspectorrag.filemanagement.domain.model.value.DocumentId;
import my.inspectorrag.filemanagement.domain.model.value.FileHash;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface DocumentAggregateRepository {

    Optional<DocumentId> findDocIdByFileHash(FileHash fileHash);

    void saveForUpload(LawDocument document, OffsetDateTime now);

    Long createImportTask(Long id, DocumentId docId, String taskType, OffsetDateTime now);

    Optional<LawDocument> findById(DocumentId docId);

    void deleteVectorsByDocId(DocumentId docId);

    int deleteSourceDocument(DocumentId docId);
}
