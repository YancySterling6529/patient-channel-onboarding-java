import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PatientOnboardingServiceTest {
    public static void main(String[] args) {
        FakeInfraiClient client = new FakeInfraiClient();
        PatientOnboardingService service = new PatientOnboardingService(client);
        PatientSignup signup = new PatientSignup(
                "signup-2026-041", "Avery Chen", "avery@example.test", "+1555010199",
                "correct-horse-47", "appt-8042", "2026-10-02T09:30:00-07:00");

        OnboardingResult result = service.onboard(signup);

        check(result.userId().equals("patient-77"), "created user must reach the result");
        check(result.channel() == DeliveryChannel.SMS, "suppressed email must select SMS");
        check(result.messageId().equals("sms-900"), "SMS receipt must be returned");
        check(client.calls.equals(List.of("create", "email-check", "sms-check", "sms-send")),
                "the service must not send email after suppression");
        System.out.println("PASS: email suppression selected one SMS welcome for patient-77");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FakeInfraiClient extends InfraiClient {
        private final List<String> calls = new ArrayList<>();

        FakeInfraiClient() {
            super(new InfraiConfig(URI.create("https://example.test"), "test-key"));
        }

        @Override public Map<String, Object> createUser(PatientSignup signup) {
            calls.add("create");
            return Map.of("user_id", "patient-77");
        }

        @Override public boolean isEmailSuppressed(String email) {
            calls.add("email-check");
            return true;
        }

        @Override public String sendWelcomeEmail(PatientSignup signup) {
            throw new AssertionError("email send must not run");
        }

        @Override public boolean isSmsSuppressed(String phone) {
            calls.add("sms-check");
            return false;
        }

        @Override public String sendWelcomeSms(PatientSignup signup) {
            calls.add("sms-send");
            return "sms-900";
        }
    }
}
