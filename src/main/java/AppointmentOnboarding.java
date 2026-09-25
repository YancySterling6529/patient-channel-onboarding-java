public final class AppointmentOnboarding {
    private AppointmentOnboarding() {}

    public static void main(String[] args) {
        if (args.length != 7) {
            System.err.println("Usage: AppointmentOnboarding <signup-id> <name> <email> <phone> <password> <appointment-id> <appointment-time>");
            System.exit(2);
        }

        InfraiConfig config = InfraiConfig.fromEnvironment();
        PatientOnboardingService service = new PatientOnboardingService(new InfraiClient(config));
        PatientSignup signup = new PatientSignup(args[0], args[1], args[2], args[3], args[4], args[5], args[6]);

        try {
            OnboardingResult result = service.onboard(signup);
            System.out.println(Json.write(java.util.Map.of(
                    "user_id", result.userId(),
                    "delivery_channel", result.channel().name().toLowerCase(),
                    "message_id", result.messageId())));
        } catch (InfraiClient.InfraiException error) {
            System.err.println(Json.write(java.util.Map.of(
                    "error", error.code(),
                    "status", error.status(),
                    "message", error.getMessage())));
            System.exit(error.status() >= 400 && error.status() < 500 ? 2 : 1);
        }
    }
}
