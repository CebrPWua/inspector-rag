package my.inspectorrag.filemanagement.infrastructure.persistence.repository;

import my.inspectorrag.filemanagement.domain.model.DocumentFile;
import my.inspectorrag.filemanagement.domain.model.LawDocument;
import my.inspectorrag.filemanagement.domain.model.value.DocType;
import my.inspectorrag.filemanagement.domain.model.value.DocumentId;
import my.inspectorrag.filemanagement.domain.model.value.DocumentStatus;
import my.inspectorrag.filemanagement.domain.model.value.FileHash;
import my.inspectorrag.filemanagement.domain.model.value.FileSizeBytes;
import my.inspectorrag.filemanagement.domain.model.value.LawCode;
import my.inspectorrag.filemanagement.domain.model.value.LawName;
import my.inspectorrag.filemanagement.domain.model.value.MimeType;
import my.inspectorrag.filemanagement.domain.model.value.ParseStatus;
import my.inspectorrag.filemanagement.domain.model.value.SourceFileName;
import my.inspectorrag.filemanagement.domain.model.value.StoragePath;
import my.inspectorrag.filemanagement.domain.model.value.UploadBatchNo;
import my.inspectorrag.filemanagement.domain.model.value.VersionNo;
import my.inspectorrag.filemanagement.domain.repository.DocumentAggregateRepository;
import my.inspectorrag.filemanagement.infrastructure.persistence.mapper.DocumentCommandMapper;
import my.inspectorrag.filemanagement.infrastructure.persistence.mapper.DocumentQueryMapper;
import my.inspectorrag.filemanagement.infrastructure.persistence.mapper.FileDetailRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Primary
@Repository
public class MybatisDocumentAggregateRepository implements DocumentAggregateRepository {

    private static final Logger log = LoggerFactory.getLogger(MybatisDocumentAggregateRepository.class);
    private static final Pattern QUALIFIED_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)?$");

    private final DocumentCommandMapper commandMapper;
    private final DocumentQueryMapper queryMapper;
    private final JdbcTemplate jdbcTemplate;

    public MybatisDocumentAggregateRepository(
            DocumentCommandMapper commandMapper,
            DocumentQueryMapper queryMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.commandMapper = commandMapper;
        this.queryMapper = queryMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<DocumentId> findDocIdByFileHash(FileHash fileHash) {
        Objects.requireNonNull(fileHash, "fileHash must not be null");
        return Optional.ofNullable(commandMapper.findDocIdByFileHash(fileHash.value()))
                .map(DocumentId::of);
    }

    @Override
    public void saveForUpload(LawDocument document, OffsetDateTime now) {
        Objects.requireNonNull(document, "document must not be null");
        DocumentFile primaryFile = Objects.requireNonNull(document.primaryFile(), "primaryFile must not be null");
        Long fileId = primaryFile.id();
        if (fileId == null || fileId <= 0) {
            throw new IllegalArgumentException("fileId must be positive");
        }

        commandMapper.insertSourceDocument(
                document.id().value(),
                document.lawName().value(),
                document.lawCode().value(),
                document.docType().value(),
                document.sourceFileName().value(),
                document.fileHash().value(),
                document.versionNo().value(),
                document.status().dbValue(),
                now
        );

        commandMapper.insertDocumentFile(
                fileId,
                document.id().value(),
                primaryFile.storagePath().value(),
                primaryFile.mimeType().value(),
                primaryFile.fileSizeBytes().value(),
                primaryFile.sha256().value(),
                primaryFile.uploadBatchNo() == null ? null : primaryFile.uploadBatchNo().value(),
                now
        );
    }

    @Override
    public Long createImportTask(Long id, DocumentId docId, String taskType, OffsetDateTime now) {
        commandMapper.insertImportTask(id, docId.value(), taskType, now);
        return id;
    }

    @Override
    public Optional<LawDocument> findById(DocumentId docId) {
        return Optional.ofNullable(queryMapper.findFileDetail(docId.value()))
                .map(this::toLawDocument);
    }

    @Override
    public void deleteVectorsByDocId(DocumentId docId) {
        List<String> storageTables = findVectorTables();
        if (storageTables.isEmpty()) {
            return;
        }
        for (String table : storageTables) {
            String safeTable = safeTable(table);
            String sql = """
                    delete from %s r
                    using ingest.law_chunk c
                     where c.doc_id = ?
                       and r.id = c.id::text
                    """.formatted(safeTable);
            try {
                jdbcTemplate.update(sql, docId.value());
            } catch (Exception ex) {
                log.warn("failed deleting vectors for docId={} table={}", docId.value(), safeTable, ex);
                throw ex;
            }
        }
    }

    @Override
    public int deleteSourceDocument(DocumentId docId) {
        return commandMapper.deleteSourceDocument(docId.value());
    }

    private LawDocument toLawDocument(FileDetailRow row) {
        DocumentFile primaryFile = toPrimaryFile(row);
        return LawDocument.rehydrate(
                DocumentId.of(row.docId()),
                LawName.of(row.lawName()),
                LawCode.of(row.lawCode()),
                DocType.of(row.docType()),
                VersionNo.of(row.versionNo()),
                DocumentStatus.from(row.status()),
                ParseStatus.from(row.parseStatus()),
                SourceFileName.of(row.sourceFileName()),
                FileHash.of(row.fileHash()),
                primaryFile
        );
    }

    private DocumentFile toPrimaryFile(FileDetailRow row) {
        if (!StringUtils.hasText(row.storagePath())) {
            return null;
        }

        if (!StringUtils.hasText(row.mimeType()) || row.fileSizeBytes() == null || !StringUtils.hasText(resolveSha256(row))) {
            return null;
        }

        return DocumentFile.rehydrate(
                row.fileId(),
                StoragePath.of(row.storagePath()),
                MimeType.of(row.mimeType()),
                FileSizeBytes.of(row.fileSizeBytes()),
                FileHash.of(resolveSha256(row)),
                UploadBatchNo.ofNullable(row.uploadBatchNo())
        );
    }

    private String resolveSha256(FileDetailRow row) {
        if (StringUtils.hasText(row.fileSha256())) {
            return row.fileSha256();
        }
        return row.fileHash();
    }

    private List<String> findVectorTables() {
        try {
            return jdbcTemplate.queryForList(
                    """
                    select distinct storage_table
                      from indexing.embedding_profile
                     where storage_table is not null
                    """,
                    String.class
            );
        } catch (Exception ex) {
            // fallback for legacy deployment before embedding_profile migration
            log.warn("embedding_profile lookup failed, fallback to legacy vector table", ex);
            return List.of("indexing.rag_law_chunk_store");
        }
    }

    private String safeTable(String storageTable) {
        if (storageTable == null || !QUALIFIED_IDENTIFIER.matcher(storageTable).matches()) {
            throw new IllegalArgumentException("invalid storage table identifier: " + storageTable);
        }
        return storageTable;
    }
}
