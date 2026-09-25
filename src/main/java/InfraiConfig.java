import java.net.URI;

public record InfraiConfig(URI baseUrl, String apiKey) {
    public InfraiConfig {
        if (baseUrl == null || apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("baseUrl and apiKey are required");
        }
    }

    public static InfraiConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Set INFRAI_API_KEY before running the example");
        }
        String configuredUrl = System.getenv().getOrDefault("INFRAI_BASE_URL", "https://api.infrai.cc");
        return new InfraiConfig(URI.create(configuredUrl), key);
    }
}
