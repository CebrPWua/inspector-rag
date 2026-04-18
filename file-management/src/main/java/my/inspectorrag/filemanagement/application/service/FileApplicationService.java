package my.inspectorrag.filemanagement.application.service;

import my.inspectorrag.filemanagement.application.command.UploadLawFileCommand;
import my.inspectorrag.filemanagement.application.query.model.FileDetailView;
import my.inspectorrag.filemanagement.application.query.model.FileListItemView;
import my.inspectorrag.filemanagement.application.query.repository.FileQueryRepository;
import my.inspectorrag.filemanagement.domain.model.DocumentFile;
import my.inspectorrag.filemanagement.domain.model.LawDocument;
import my.inspectorrag.filemanagement.domain.model.value.DocType;
import my.inspectorrag.filemanagement.domain.model.value.DocumentId;
import my.inspectorrag.filemanagement.domain.model.value.DocumentStatus;
import my.inspectorrag.filemanagement.domain.model.value.FileHash;
import my.inspectorrag.filemanagement.domain.model.value.FileSizeBytes;
import my.inspectorrag.filemanagement.domain.model.value.LawName;
import my.inspectorrag.filemanagement.domain.model.value.MimeType;
import my.inspectorrag.filemanagement.domain.model.value.SourceFileName;
import my.inspectorrag.filemanagement.domain.model.value.StoragePath;
import my.inspectorrag.filemanagement.domain.model.value.UploadBatchNo;
import my.inspectorrag.filemanagement.domain.repository.DocumentAggregateRepository;
import my.inspectorrag.filemanagement.domain.service.FileHashService;
import my.inspectorrag.filemanagement.domain.service.ObjectStorageGateway;
import my.inspectorrag.filemanagement.interfaces.dto.FileDetailResponse;
import my.inspectorrag.filemanagement.interfaces.dto.FileListItemResponse;
import my.inspectorrag.filemanagement.interfaces.dto.UploadFileResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class FileApplicationService {

    private static final Logger log = LoggerFactory.getLogger(FileApplicationService.class);

    private final DocumentAggregateRepository documentAggregateRepository;
    private final FileQueryRepository fileQueryRepository;
    private final ObjectStorageGateway objectStorageGateway;
    private final FileHashService fileHashService;

    public FileApplicationService(
            DocumentAggregateRepository documentAggregateRepository,
            FileQueryRepository fileQueryRepository,
            ObjectStorageGateway objectStorageGateway,
            FileHashService fileHashService
    ) {
        this.documentAggregateRepository = documentAggregateRepository;
        this.fileQueryRepository = fileQueryRepository;
        this.objectStorageGateway = objectStorageGateway;
        this.fileHashService = fileHashService;
    }

    @Transactional
    public UploadFileResponse upload(UploadLawFileCommand command) {
        if (command.file() == null || command.file().isEmpty()) {
            throw new IllegalArgumentException("file must not be empty");
        }

        byte[] bytes;
        try {
            bytes = command.file().getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to read upload file");
        }

        FileHash fileHash = FileHash.of(fileHashService.sha256(bytes));
        Optional<DocumentId> duplicateDocId = documentAggregateRepository.findDocIdByFileHash(fileHash);
        if (duplicateDocId.isPresent()) {
            return new UploadFileResponse(toIdString(duplicateDocId.get().value()), true, null);
        }

        DocumentId docId = DocumentId.of(newId());
        SourceFileName sourceFileName = SourceFileName.of(
                StringUtils.hasText(command.file().getOriginalFilename()) ? command.file().getOriginalFilename() : "upload.bin"
        );
        String storagePath = objectStorageGateway.save(docId.value(), sourceFileName.value(), bytes);

        DocumentFile primaryFile = DocumentFile.createPrimaryForUpload(
                newId(),
                StoragePath.of(storagePath),
                MimeType.of(command.file().getContentType() == null ? "application/octet-stream" : command.file().getContentType()),
                FileSizeBytes.of(bytes.length),
                fileHash,
                UploadBatchNo.ofNullable(UUID.randomUUID().toString().replace("-", ""))
        );

        LawDocument lawDocument = LawDocument.createForUpload(
                docId,
                LawName.of(command.lawName()),
                command.lawCode(),
                DocType.of(command.docType()),
                sourceFileName,
                fileHash,
                command.versionNo(),
                DocumentStatus.from(command.status()),
                primaryFile
        );

        OffsetDateTime now = OffsetDateTime.now();
        documentAggregateRepository.saveForUpload(lawDocument, now);
        Long taskId = documentAggregateRepository.createImportTask(newId(), docId, "parse", now);
        return new UploadFileResponse(toIdString(docId.value()), false, toIdString(taskId));
    }

    @Transactional(readOnly = true)
    public FileDetailResponse getFile(Long docId) {
        FileDetailView detail = fileQueryRepository.findFileDetail(docId)
                .orElseThrow(() -> new IllegalArgumentException("document not found: " + docId));
        return new FileDetailResponse(
                toIdString(detail.docId()),
                detail.lawName(),
                detail.lawCode(),
                detail.docType(),
                detail.versionNo(),
                detail.status(),
                detail.parseStatus(),
                detail.sourceFileName(),
                detail.fileHash(),
                detail.storagePath(),
                detail.createdAt()
        );
    }

    @Transactional(readOnly = true)
    public List<FileListItemResponse> listFiles(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<FileListItemView> items = fileQueryRepository.listFiles(safeLimit);
        return items.stream()
                .map(item -> new FileListItemResponse(
                        toIdString(item.docId()),
                        item.lawName(),
                        item.lawCode(),
                        item.docType(),
                        item.versionNo(),
                        item.status(),
                        item.parseStatus(),
                        item.createdAt()
                ))
                .toList();
    }

    @Transactional
    public void deleteFile(Long docId) {
        DocumentId documentId = DocumentId.of(docId);
        LawDocument document = documentAggregateRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("document not found: " + docId));

        document.ensureDeletable();

        documentAggregateRepository.deleteVectorsByDocId(documentId);
        int affected = documentAggregateRepository.deleteSourceDocument(documentId);
        if (affected <= 0) {
            throw new IllegalArgumentException("document not found: " + docId);
        }

        String storagePath = document.primaryFileOptional()
                .map(DocumentFile::storagePath)
                .map(StoragePath::value)
                .orElse(null);
        try {
            objectStorageGateway.delete(storagePath);
        } catch (Exception ex) {
            // Best effort cleanup for object storage; DB deletion should remain successful.
            log.warn("failed to delete storage object for docId={}, storagePath={}", docId, storagePath, ex);
        }
    }

    private String toIdString(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    private Long newId() {
        long ts = System.currentTimeMillis();
        int rand = ThreadLocalRandom.current().nextInt(1_000_000);
        return ts * 1_000_000L + rand;
    }
}
