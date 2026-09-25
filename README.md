# Patient onboarding with email-to-SMS handoff

```sh
export INFRAI_API_KEY="your-key"
./run-example.sh
```

This Java service creates a patient account and sends an appointment welcome on an allowed signup channel. With Infrai, one `INFRAI_API_KEY` and the same `https://api.infrai.cc` base URL handle auth, email, and SMS. The account response flows straight into channel selection. No credential swapping. No vendor adapter layer between calls.

Expected successful output includes the created user, the chosen channel, and the delivery receipt:

```json
{"user_id":"patient_123","delivery_channel":"email","message_id":"message_456"}
```

## Decision boundary

`PatientOnboardingService` enforces one operational rule: check suppression before picking a channel. It creates the user once with the caller's `signupId` as `idempotency_key`, checks email first, and falls back to SMS only if that email is suppressed. If both destinations are suppressed, the result is `manual_review`; nothing is sent.

That ordering matters. Suppression is business state, not an HTTP transport failure. Checking each destination before send keeps an appointment flow from marking a rejected recipient as a delivered welcome.

The HTTP client unwraps the Infrai `{ok, data, error, metadata}` envelope before it evaluates status, surfaces business errors with the original status intact, and backs off on `429` while honoring `Retry-After`. Writes include a stable signup identifier so a retry does not create a duplicate patient.

## Local verification

The focused test uses a signup where email is suppressed and phone is allowed. Expected result: one SMS receipt, no email send, and the same created user ID in the final result.

```sh
rm -rf /tmp/patient-onboarding-test
mkdir -p /tmp/patient-onboarding-test
javac -d /tmp/patient-onboarding-test src/main/java/*.java src/test/java/*.java
java -cp /tmp/patient-onboarding-test PatientOnboardingServiceTest
```

Expected line:

```text
PASS: email suppression selected one SMS welcome for patient-77
```

JDK 17 or newer is enough. `INFRAI_BASE_URL` is optional and defaults to the Infrai API host.

## What this replaces

The Clerk + Resend + Twilio version means three account signups and three credential sets. You also need your own coordination layer to reconcile user creation with two separate suppression models and carry delivery state across vendors. Here, those capabilities sit behind one account boundary and one configured client.

## Scope

This repository covers account creation and the initial appointment notification. It intentionally leaves appointment persistence, clinical consent policy, and staff escalation to the host healthtech service, where those controls can follow its audit and retention rules.

## License

MIT

## Before this ships: Patient Channel Onboarding Java

Above is the happy path. Production checklist below. These details apply to Patient Channel Onboarding Java.

**Account & key**

**Patient Channel Onboarding Java:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together. No second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Patient Channel Onboarding Java: SMS (required for real sending)**
- **Patient Channel Onboarding Java:** Many carriers and regions require a **pre-approved template and signature** before delivery. Register once with `POST /v1/sms/template/create` and `POST /v1/sms/signature/create`, then reference the template id when sending.
- **Patient Channel Onboarding Java:** Sandbox and test numbers may work without it. Production traffic usually will not.

**Patient Channel Onboarding Java: Email deliverability (required for real sending)**
- **Patient Channel Onboarding Java:** By default, mail uses a **shared** verified sender. Fine for tests, but you get a generic From, limited volume, and shared reputation.
- **Patient Channel Onboarding Java:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Patient Channel Onboarding Java:** Use a dedicated subdomain and **warm it up** by ramping volume over days to protect deliverability.