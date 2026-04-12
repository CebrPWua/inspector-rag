package my.inspectorrag.searchandreturn.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class AiProviderValidator {

    private static final Set<String> ALLOWED_PROVIDERS = Set.of("mock", "springai");

    private final String provider;

    public AiProviderValidator(@Value("${inspector.ai.provider}") String provider) {
        this.provider = provider;
    }

    @PostConstruct
    void validate() {
        String normalized = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_PROVIDERS.contains(normalized)) {
            throw new IllegalStateException(
                    "unsupported inspector.ai.provider: " + provider + ", allowed values: mock,springai"
            );
        }
    }
}
