package my.inspectorrag.filemanagement.infrastructure.gateway;

import my.inspectorrag.filemanagement.domain.service.ObjectStorageGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;

@Component
@ConditionalOnProperty(prefix = "inspector.storage", name = "mode", havingValue = "rustfs")
public class RustfsObjectStorageGateway implements ObjectStorageGateway {

    private final S3Client s3Client;
    private final String bucket;

    public RustfsObjectStorageGateway(
            @Value("${inspector.storage.rustfs.endpoint}") String endpoint,
            @Value("${inspector.storage.rustfs.access-key}") String accessKey,
            @Value("${inspector.storage.rustfs.secret-key}") String secretKey,
            @Value("${inspector.storage.rustfs.bucket}") String bucket
    ) {
        this.bucket = bucket;
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(normalizeHttpUri(endpoint)))
                .region(Region.US_EAST_1)
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        )
                )
                .forcePathStyle(true)
                .build();
    }

    @Override
    public String save(Long docId, String originalFilename, byte[] content) {
        String key = "laws/" + docId + "/" + originalFilename.replaceAll("\\s+", "_");
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType("application/octet-stream")
                        .build(),
                RequestBody.fromBytes(content)
        );
        return "s3://" + bucket + "/" + key;
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
}
