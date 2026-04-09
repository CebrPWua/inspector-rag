package my.inspectorrag.filemanagement.infrastructure.config;

import my.inspectorrag.filemanagement.domain.service.FileHashService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Configuration
public class HashConfig {

    @Bean
    public FileHashService fileHashService() {
        return bytes -> {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                return HexFormat.of().formatHex(digest.digest(bytes));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("SHA-256 algorithm not found", e);
            }
        };
    }
}
