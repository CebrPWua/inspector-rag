package my.inspectorrag.filemanagement.application.service;

import my.inspectorrag.filemanagement.application.command.UploadLawFileCommand;
import my.inspectorrag.filemanagement.domain.model.FileDetail;
import my.inspectorrag.filemanagement.domain.model.FileListItem;
import my.inspectorrag.filemanagement.domain.repository.DocumentRepository;
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

    private final DocumentRepository documentRepository;
    private final ObjectStorageGateway objectStorageGateway;
    private final FileHashService fileHashService;

    public FileApplicationService(
            DocumentRepository documentRepository,
            ObjectStorageGateway objectStorageGateway,
            FileHashService fileHashService
    ) {
        this.documentRepository = documentRepository;
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

        String fileHash = fileHashService.sha256(bytes);
        Optional<Long> duplicateDocId = documentRepository.findDocIdByFileHash(fileHash);
        if (duplicateDocId.isPresent()) {
            return new UploadFileResponse(toIdString(duplicateDocId.get()), true, null);
        }
        String effectiveLawCode = normalizeOptional(command.lawCode());
        if (effectiveLawCode == null) {
            effectiveLawCode = fileHash;
        }
        String effectiveVersionNo = normalizeOptional(command.versionNo());
        if (effectiveVersionNo == null) {
            effectiveVersionNo = "v1";
        }

        OffsetDateTime now = OffsetDateTime.now();
        Long docId = newId();
        String sourceFileName = StringUtils.hasText(command.file().getOriginalFilename())
                ? command.file().getOriginalFilename()
                : "upload.bin";
        String storagePath = objectStorageGateway.save(docId, sourceFileName, bytes);

        documentRepository.insertSourceDocument(
                docId,
                command.lawName(),
                effectiveLawCode,
                command.docType(),
                sourceFileName,
                fileHash,
                effectiveVersionNo,
                command.status(),
                now
        );

        documentRepository.insertDocumentFile(
                newId(),
                docId,
                storagePath,
                command.file().getContentType() == null ? "application/octet-stream" : command.file().getContentType(),
                bytes.length,
                fileHash,
                UUID.randomUUID().toString().replace("-", ""),
                now
        );

        Long taskId = documentRepository.createImportTask(newId(), docId, "parse", now);
        return new UploadFileResponse(toIdString(docId), false, toIdString(taskId));
    }

    @Transactional(readOnly = true)
    public FileDetailResponse getFile(Long docId) {
        FileDetail detail = documentRepository.findFileDetail(docId)
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
        List<FileListItem> items = documentRepository.listFiles(safeLimit);
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
        FileDetail detail = documentRepository.findFileDetail(docId)
                .orElseThrow(() -> new IllegalArgumentException("document not found: " + docId));

        if ("pending".equals(detail.parseStatus()) || "processing".equals(detail.parseStatus())) {
            throw new IllegalArgumentException("document is being parsed and cannot be deleted: " + docId);
        }

        documentRepository.deleteVectorsByDocId(docId);
        int affected = documentRepository.deleteSourceDocument(docId);
        if (affected <= 0) {
            throw new IllegalArgumentException("document not found: " + docId);
        }

        try {
            objectStorageGateway.delete(detail.storagePath());
        } catch (Exception ex) {
            // Best effort cleanup for object storage; DB deletion should remain successful.
            log.warn("failed to delete storage object for docId={}, storagePath={}", docId, detail.storagePath(), ex);
        }
    }

    private String toIdString(Long id) {
        return id == null ? null : String.valueOf(id);
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Long newId() {
        long ts = System.currentTimeMillis();
        int rand = ThreadLocalRandom.current().nextInt(1_000_000);
        return ts * 1_000_000L + rand;
    }
}
