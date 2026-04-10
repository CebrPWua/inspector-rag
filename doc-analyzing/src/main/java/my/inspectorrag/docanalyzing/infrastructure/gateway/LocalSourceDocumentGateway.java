package my.inspectorrag.docanalyzing.infrastructure.gateway;

import my.inspectorrag.docanalyzing.domain.service.SourceDocumentGateway;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@ConditionalOnProperty(prefix = "inspector.storage", name = "mode", havingValue = "local", matchIfMissing = true)
public class LocalSourceDocumentGateway implements SourceDocumentGateway {

    @Override
    public byte[] read(String storagePath) {
        try {
            Path path;
            if (storagePath.startsWith("file://")) {
                path = Path.of(URI.create(storagePath));
            } else {
                path = Path.of(storagePath);
            }
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new IllegalArgumentException("failed to read source file: " + storagePath, ex);
        }
    }
}
