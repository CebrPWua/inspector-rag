package my.inspectorrag.docanalyzing.infrastructure.gateway;

import my.inspectorrag.docanalyzing.domain.service.SourceDocumentGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.net.URI;
import java.util.Objects;

@Component
@ConditionalOnProperty(prefix = "inspector.storage", name = "mode", havingValue = "rustfs")
public class RustfsSourceDocumentGateway implements SourceDocumentGateway {

    private final S3Client s3Client;
    private final String defaultBucket;

    public RustfsSourceDocumentGateway(
            @Value("${inspector.storage.rustfs.endpoint}") String endpoint,
            @Value("${inspector.storage.rustfs.access-key}") String accessKey,
            @Value("${inspector.storage.rustfs.secret-key}") String secretKey,
            @Value("${inspector.storage.rustfs.bucket}") String defaultBucket
    ) {
        this.defaultBucket = defaultBucket;
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(normalizeHttpUri(endpoint)))
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    @Override
    public byte[] read(String storagePath) {
        S3Location location = parseS3Location(storagePath);
        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(location.bucket())
                            .key(location.key())
                            .build()
            );
            return response.asByteArray();
        } catch (Exception ex) {
            throw new IllegalArgumentException("failed to read source file: " + storagePath, ex);
        }
    }

    private S3Location parseS3Location(String storagePath) {
        if (!storagePath.startsWith("s3://")) {
            throw new IllegalArgumentException("unsupported storage path for rustfs mode: " + storagePath);
        }
        URI uri = URI.create(storagePath);
        String bucket = uri.getHost();
        String key = uri.getPath() == null ? "" : uri.getPath();
        if (key.startsWith("/")) {
            key = key.substring(1);
        }
        if ((bucket == null || bucket.isBlank()) && storagePath.length() > 5) {
            String rest = storagePath.substring(5);
            int slash = rest.indexOf('/');
            if (slash > 0) {
                bucket = rest.substring(0, slash);
                key = rest.substring(slash + 1);
            }
        }
        if (bucket == null || bucket.isBlank()) {
            bucket = defaultBucket;
        }
        if (bucket == null || bucket.isBlank() || key.isBlank()) {
            throw new IllegalArgumentException("invalid s3 storage path: " + storagePath);
        }
        return new S3Location(bucket, key);
    }

    private String normalizeHttpUri(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("rustfs endpoint must not be blank");
        }
        if (endpoint.startsWith("http://") || endpoint.startsWith("https://")) {
            return endpoint;
        }
        return "http://" + endpoint;
    }

    private record S3Location(String bucket, String key) {
        private S3Location {
            Objects.requireNonNull(bucket, "bucket");
            Objects.requireNonNull(key, "key");
        }
    }
}
