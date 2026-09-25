import java.util.Map;

public final class PatientOnboardingService {
    private final InfraiClient client;

    public PatientOnboardingService(InfraiClient client) {
        this.client = client;
    }

    public OnboardingResult onboard(PatientSignup signup) {
        Map<String, Object> user = client.createUser(signup);
        String userId = String.valueOf(user.get("user_id"));

        if (!client.isEmailSuppressed(signup.email())) {
            return new OnboardingResult(userId, DeliveryChannel.EMAIL, client.sendWelcomeEmail(signup));
        }
        if (!client.isSmsSuppressed(signup.phone())) {
            return new OnboardingResult(userId, DeliveryChannel.SMS, client.sendWelcomeSms(signup));
        }
        return new OnboardingResult(userId, DeliveryChannel.MANUAL_REVIEW, "none");
    }
}

record PatientSignup(
        String signupId,
        String patientName,
        String email,
        String phone,
        String password,
        String appointmentId,
        String appointmentTime) {}

enum DeliveryChannel { EMAIL, SMS, MANUAL_REVIEW }

record OnboardingResult(String userId, DeliveryChannel channel, String messageId) {}
