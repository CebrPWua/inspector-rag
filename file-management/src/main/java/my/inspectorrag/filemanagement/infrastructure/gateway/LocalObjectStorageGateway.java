package my.inspectorrag.filemanagement.infrastructure.gateway;

import my.inspectorrag.filemanagement.domain.service.ObjectStorageGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
@ConditionalOnProperty(prefix = "inspector.storage", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalObjectStorageGateway implements ObjectStorageGateway {

    private final Path rootDir;

    public LocalObjectStorageGateway(@Value("${inspector.storage.root-dir}") String rootDir) {
        this.rootDir = Paths.get(rootDir);
    }

    @Override
    public String save(Long docId, String originalFilename, byte[] content) {
        try {
            Files.createDirectories(rootDir);
            Path target = rootDir.resolve(docId + "-" + originalFilename.replaceAll("\\s+", "_"));
            Files.write(target, content);
            return target.toString();
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to store file to local storage", e);
        }
    }

    @Override
    public void delete(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(storagePath));
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to delete file from local storage", e);
        }
    }
}
