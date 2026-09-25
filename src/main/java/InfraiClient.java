import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public class InfraiClient {
    private final InfraiConfig config;
    private final HttpClient http;

    public InfraiClient(InfraiConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    InfraiClient(InfraiConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    public Map<String, Object> createUser(PatientSignup signup) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", signup.email());
        body.put("password", signup.password());
        body.put("name", signup.patientName());
        body.put("metadata", Map.of("appointment_id", signup.appointmentId()));
        body.put("mode", "patient-onboarding");
        body.put("idempotency_key", signup.signupId());
        return request("POST", "/v1/auth/user/create", body);
    }

    public boolean isEmailSuppressed(String email) {
        return suppressed(request("GET", "/v1/email/suppression/check/" + encode(email), null));
    }

    public String sendWelcomeEmail(PatientSignup signup) {
        Map<String, Object> data = request("POST", "/v1/email/send", Map.of(
                "to", signup.email(),
                "subject", "Your appointment is scheduled",
                "body", welcomeText(signup)));
        return String.valueOf(data.get("message_id"));
    }

    public boolean isSmsSuppressed(String phone) {
        return suppressed(request("POST", "/v1/sms/suppression/check", Map.of("phone", phone)));
    }

    public String sendWelcomeSms(PatientSignup signup) {
        Map<String, Object> data = request("POST", "/v1/sms/send", Map.of(
                "to", signup.phone(),
                "body", welcomeText(signup)));
        return String.valueOf(data.get("message_id"));
    }

    protected Map<String, Object> request(String method, String path, Map<String, Object> body) {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(config.baseUrl().resolve(path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Accept", "application/json");
            if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
            else builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(Json.write(body)));

            HttpResponse<String> response;
            try {
                response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                throw new InfraiException("TRANSPORT_ERROR", e.getMessage(), 0);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InfraiException("INTERRUPTED", "Request interrupted", 0);
            }

            Map<String, Object> envelope = Json.readObject(response.body());
            if (response.statusCode() == 429 && attempt < 3) {
                pause(response, attempt);
                continue;
            }
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                Map<String, Object> error = object(envelope.get("error"));
                throw new InfraiException(String.valueOf(error.get("code")),
                        String.valueOf(error.get("message")), response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new InfraiException("SERVER_RESPONSE", "Unexpected server response", response.statusCode());
            }
            return object(envelope.get("data"));
        }
        throw new IllegalStateException("Retry loop exhausted");
    }

    private static boolean suppressed(Map<String, Object> data) {
        return Boolean.TRUE.equals(data.get("suppressed"));
    }

    private static String welcomeText(PatientSignup signup) {
        return "Hello " + signup.patientName() + ", your appointment " + signup.appointmentId()
                + " is scheduled for " + signup.appointmentTime() + ".";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        return Map.of();
    }

    private static void pause(HttpResponse<?> response, int attempt) {
        long seconds = response.headers().firstValue("Retry-After")
                .flatMap(value -> {
                    try { return java.util.Optional.of(Long.parseLong(value)); }
                    catch (NumberFormatException ignored) { return java.util.Optional.empty(); }
                })
                .orElse(1L << attempt);
        try {
            Thread.sleep(Math.min(seconds, 30) * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InfraiException("INTERRUPTED", "Retry interrupted", 0);
        }
    }

    public static final class InfraiException extends RuntimeException {
        private final String code;
        private final int status;

        InfraiException(String code, String message, int status) {
            super(message);
            this.code = code;
            this.status = status;
        }

        public String code() { return code; }
        public int status() { return status; }
    }
}
