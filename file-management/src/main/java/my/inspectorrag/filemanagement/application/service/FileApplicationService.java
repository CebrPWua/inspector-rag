package my.inspectorrag.filemanagement.application.service;

import my.inspectorrag.filemanagement.application.command.UploadLawFileCommand;
import my.inspectorrag.filemanagement.domain.model.FileDetail;
import my.inspectorrag.filemanagement.domain.repository.DocumentRepository;
import my.inspectorrag.filemanagement.domain.service.FileHashService;
import my.inspectorrag.filemanagement.domain.service.ObjectStorageGateway;
import my.inspectorrag.filemanagement.interfaces.dto.FileDetailResponse;
import my.inspectorrag.filemanagement.interfaces.dto.UploadFileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class FileApplicationService {

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
            return new UploadFileResponse(duplicateDocId.get(), true, null);
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
                command.lawCode(),
                command.docType(),
                sourceFileName,
                fileHash,
                command.versionNo(),
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
        return new UploadFileResponse(docId, false, taskId);
    }

    @Transactional(readOnly = true)
    public FileDetailResponse getFile(Long docId) {
        FileDetail detail = documentRepository.findFileDetail(docId)
                .orElseThrow(() -> new IllegalArgumentException("document not found: " + docId));
        return new FileDetailResponse(
                detail.docId(),
                detail.lawName(),
                detail.lawCode(),
                detail.versionNo(),
                detail.status(),
                detail.parseStatus(),
                detail.sourceFileName(),
                detail.fileHash(),
                detail.storagePath(),
                detail.createdAt()
        );
    }

    private Long newId() {
        long ts = System.currentTimeMillis();
        int rand = ThreadLocalRandom.current().nextInt(1_000_000);
        return ts * 1_000_000L + rand;
    }
}
