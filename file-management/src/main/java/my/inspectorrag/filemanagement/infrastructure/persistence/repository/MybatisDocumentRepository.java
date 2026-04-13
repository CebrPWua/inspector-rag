package my.inspectorrag.filemanagement.infrastructure.persistence.repository;

import my.inspectorrag.filemanagement.domain.model.FileDetail;
import my.inspectorrag.filemanagement.domain.model.FileListItem;
import my.inspectorrag.filemanagement.domain.repository.DocumentRepository;
import my.inspectorrag.filemanagement.infrastructure.persistence.mapper.DocumentCommandMapper;
import my.inspectorrag.filemanagement.infrastructure.persistence.mapper.DocumentQueryMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Primary
@Repository
public class MybatisDocumentRepository implements DocumentRepository {

    private final DocumentCommandMapper commandMapper;
    private final DocumentQueryMapper queryMapper;

    public MybatisDocumentRepository(DocumentCommandMapper commandMapper, DocumentQueryMapper queryMapper) {
        this.commandMapper = commandMapper;
        this.queryMapper = queryMapper;
    }

    @Override
    public Optional<Long> findDocIdByFileHash(String fileHash) {
        return Optional.ofNullable(commandMapper.findDocIdByFileHash(fileHash));
    }

    @Override
    public void insertSourceDocument(
            Long id,
            String lawName,
            String lawCode,
            String docType,
            String sourceFileName,
            String fileHash,
            String versionNo,
            String status,
            OffsetDateTime now
    ) {
        commandMapper.insertSourceDocument(id, lawName, lawCode, docType, sourceFileName, fileHash, versionNo, status, now);
    }

    @Override
    public void insertDocumentFile(
            Long id,
            Long docId,
            String storagePath,
            String mimeType,
            long fileSize,
            String sha256,
            String uploadBatchNo,
            OffsetDateTime now
    ) {
        commandMapper.insertDocumentFile(id, docId, storagePath, mimeType, fileSize, sha256, uploadBatchNo, now);
    }

    @Override
    public Long createImportTask(Long id, Long docId, String taskType, OffsetDateTime now) {
        commandMapper.insertImportTask(id, docId, taskType, now);
        return id;
    }

    @Override
    public Optional<FileDetail> findFileDetail(Long docId) {
        return Optional.ofNullable(queryMapper.findFileDetail(docId))
                .map(row -> new FileDetail(
                        row.docId(),
                        row.lawName(),
                        row.lawCode(),
                        row.docType(),
                        row.versionNo(),
                        row.status(),
                        row.parseStatus(),
                        row.sourceFileName(),
                        row.fileHash(),
                        row.storagePath(),
                        row.createdAt()
                ));
    }

    @Override
    public List<FileListItem> listFiles(int limit) {
        return queryMapper.listFiles(limit).stream()
                .map(row -> new FileListItem(
                        row.docId(),
                        row.lawName(),
                        row.lawCode(),
                        row.docType(),
                        row.versionNo(),
                        row.status(),
                        row.parseStatus(),
                        row.createdAt()
                ))
                .toList();
    }
}
