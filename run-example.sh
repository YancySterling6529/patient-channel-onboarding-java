#!/bin/sh
set -eu

classes="${TMPDIR:-/tmp}/patient-onboarding-classes"
mkdir -p "$classes"
javac -d "$classes" src/main/java/*.java
java -cp "$classes" AppointmentOnboarding \
  "signup-2026-041" "Avery Chen" "chenhua@changba.com" "+1555010199" \
  "replace-with-a-strong-password" "appt-8042" "2026-10-02T09:30:00-07:00"
