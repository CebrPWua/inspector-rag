package my.inspectorrag.filemanagement.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import my.inspectorrag.filemanagement.application.command.UploadLawFileCommand;
import my.inspectorrag.filemanagement.application.service.FileApplicationService;
import my.inspectorrag.filemanagement.interfaces.dto.ApiResponse;
import my.inspectorrag.filemanagement.interfaces.dto.FileDetailResponse;
import my.inspectorrag.filemanagement.interfaces.dto.FileListItemResponse;
import my.inspectorrag.filemanagement.interfaces.dto.UploadFileResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileApplicationService fileApplicationService;

    public FileController(FileApplicationService fileApplicationService) {
        this.fileApplicationService = fileApplicationService;
    }

    @PostMapping("/upload")
    public ApiResponse<UploadFileResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("lawName") @NotBlank String lawName,
            @RequestParam("lawCode") @NotBlank String lawCode,
            @RequestParam("versionNo") @NotBlank String versionNo,
            @RequestParam(value = "docType", defaultValue = "standard") String docType,
            @RequestParam(value = "status", defaultValue = "active") String status
    ) {
        UploadLawFileCommand command = new UploadLawFileCommand(file, lawName, lawCode, versionNo, docType, status);
        return ApiResponse.ok(fileApplicationService.upload(command));
    }

    @GetMapping("/{docId}")
    public ApiResponse<FileDetailResponse> getDetail(@PathVariable("docId") Long docId) {
        return ApiResponse.ok(fileApplicationService.getFile(docId));
    }

    @GetMapping
    public ApiResponse<List<FileListItemResponse>> list(
            @RequestParam(value = "limit", defaultValue = "200") Integer limit
    ) {
        return ApiResponse.ok(fileApplicationService.listFiles(limit));
    }
}
