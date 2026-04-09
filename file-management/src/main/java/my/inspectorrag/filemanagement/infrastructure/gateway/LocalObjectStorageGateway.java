package my.inspectorrag.filemanagement.infrastructure.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class LocalObjectStorageGateway {

    private final Path rootDir;

    public LocalObjectStorageGateway(@Value("${inspector.storage.root-dir}") String rootDir) {
        this.rootDir = Paths.get(rootDir);
    }

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
}
