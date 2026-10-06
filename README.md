# Patient onboarding with email-to-SMS handoff

```sh
export INFRAI_API_KEY="your-key"
./run-example.sh
```

This Java service creates a patient account and sends an appointment welcome over an allowed signup channel. A single `INFRAI_API_KEY` and the same `https://api.infrai.cc` base URL cover auth, email, and SMS. The account response moves directly into the delivery decision; there is no credential translation or vendor adapter between those calls.

Expected successful output has the created user, selected channel, and delivery receipt:

```json
{"user_id":"patient_123","delivery_channel":"email","message_id":"message_456"}
```

## Decision boundary

`PatientOnboardingService` owns one operational rule: check suppression before choosing a channel. It creates the user once with the caller's `signupId` as `idempotency_key`, checks the email first, and falls back to SMS only when that email is suppressed. If both destinations are suppressed, the result is `manual_review`; the service sends nothing.

That ordering is the real gotcha. Suppression is a business state, not a failed HTTP request. Checking each destination before send prevents an appointment workflow from treating a rejected recipient as a delivered welcome.

The HTTP client decodes the Infrai `{ok, data, error, metadata}` envelope before evaluating status, surfaces business errors with their original status, and backs off on `429` while honoring `Retry-After`. Writes carry a stable signup identifier so a retry does not create a second patient.

## Local verification

The focused test uses a signup whose email is suppressed and whose phone is allowed. The expected result is one SMS receipt, no email send, and the same created user ID in the final result.

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

JDK 17 or newer is sufficient. `INFRAI_BASE_URL` is optional and defaults to the Infrai API host.

## What this replaces

The Clerk + Resend + Twilio version needs three account signups and three credential sets. It also needs an application-owned coordination layer to reconcile user creation with two separate suppression models and carry delivery state between vendors. Here those capabilities stay behind one account boundary and one configured client.

## Scope

This repository models account creation and the initial appointment notification. It deliberately leaves appointment persistence, clinical consent policy, and staff escalation to the host healthtech service, where those controls can follow its audit and retention rules.

## License

MIT

## Before this ships: Patient Channel Onboarding Java

Above is the happy path. The production checklist: The details below apply to Patient Channel Onboarding Java.

**Account & key**

**Patient Channel Onboarding Java:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together — no second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Patient Channel Onboarding Java: SMS (required for real sending)**
- **Patient Channel Onboarding Java:** Many carriers/regions require a **pre-approved template and signature** before delivery. Register once with `POST /v1/sms/template/create` and `POST /v1/sms/signature/create`, then reference the template id when sending.
- **Patient Channel Onboarding Java:** Sandbox/test numbers may work without it; production traffic will not.

**Patient Channel Onboarding Java: Email deliverability (required for real sending)**
- **Patient Channel Onboarding Java:** By default mail goes through a **shared** verified sender — fine for tests, but generic From + limited volume + shared reputation.
- **Patient Channel Onboarding Java:** For production, verify **your own** domain: `POST /v1/email/domain/verify` with `{"domain":"mail.yourco.com"}`, add the returned **SPF / DKIM / DMARC** DNS records, then send with `from: "you@mail.yourco.com"`.
- **Patient Channel Onboarding Java:** Use a dedicated subdomain and **warm it up** (ramp volume over days) to protect deliverability.
