package my.inspectorrag.filemanagement.application.command;

import org.springframework.web.multipart.MultipartFile;

public record UploadLawFileCommand(
        MultipartFile file,
        String lawName,
        String lawCode,
        String versionNo,
        String docType,
        String status
) {
}
