package my.inspectorrag.filemanagement.interfaces.rest;

import my.inspectorrag.filemanagement.interfaces.dto.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleFileTooLargeShouldReturnPayloadTooLarge() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleFileTooLarge(new MaxUploadSizeExceededException(1_073_741_824L));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("FILE_TOO_LARGE", response.getBody().code());
        assertEquals("上传文件过大，单文件最大 1GB", response.getBody().message());
    }

    @Test
    void handleMultipartExceptionShouldMapMaxUploadToPayloadTooLarge() {
        MultipartException ex = new MultipartException("multipart failed",
                new MaxUploadSizeExceededException(1_073_741_824L));

        ResponseEntity<ApiResponse<Void>> response = handler.handleMultipartException(ex);

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("FILE_TOO_LARGE", response.getBody().code());
        assertEquals("上传文件过大，单文件最大 1GB", response.getBody().message());
    }

    @Test
    void handleMultipartExceptionShouldKeepInternalErrorForOtherMultipartErrors() {
        MultipartException ex = new MultipartException("multipart parse failed");

        ResponseEntity<ApiResponse<Void>> response = handler.handleMultipartException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INTERNAL_ERROR", response.getBody().code());
        assertEquals("multipart parse failed", response.getBody().message());
    }
}
