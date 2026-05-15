#!/usr/bin/env bash
# Set up Apple code signing keychain and notarization credentials.
# Expected environment variables:
#   APPLE_CERTIFICATE_P12, APPLE_CERTIFICATE_PASSWORD, APPLE_SIGNING_IDENTITY,
#   APPLE_TEAM_ID, APPLE_NOTARY_KEY, APPLE_NOTARY_KEY_ID, APPLE_NOTARY_ISSUER_ID,
#   RUNNER_TEMP
set -euo pipefail

# Create a temporary keychain
KEYCHAIN_PASSWORD=$(openssl rand -base64 32)
KEYCHAIN_PATH="$RUNNER_TEMP/app-signing.keychain-db"
security create-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN_PATH"
security set-keychain-settings -lut 21600 "$KEYCHAIN_PATH"
security unlock-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN_PATH"

# Import the certificate
CERT_PATH="$RUNNER_TEMP/certificate.p12"
echo "$APPLE_CERTIFICATE_P12" | base64 --decode > "$CERT_PATH"
security import "$CERT_PATH" \
  -P "$APPLE_CERTIFICATE_PASSWORD" \
  -A -t cert -f pkcs12 -k "$KEYCHAIN_PATH"
rm -f "$CERT_PATH"

# Allow codesign to access the keychain without UI prompt
security set-key-partition-list \
  -S apple-tool:,apple:,codesign: \
  -s -k "$KEYCHAIN_PASSWORD" "$KEYCHAIN_PATH"
security list-keychains -d user -s "$KEYCHAIN_PATH" login.keychain

# Verify the signing identity is available
if ! security find-identity -v -p codesigning "$KEYCHAIN_PATH" \
  | grep -q "$APPLE_SIGNING_IDENTITY"; then
  echo "::error::Signing identity $APPLE_SIGNING_IDENTITY not found in keychain"
  exit 1
fi
echo "Signing identity verified"

# Store notarytool credentials in the profile Briefcase expects
API_KEY_PATH="$RUNNER_TEMP/AuthKey.p8"
echo "$APPLE_NOTARY_KEY" | base64 --decode > "$API_KEY_PATH"
xcrun notarytool store-credentials \
  "briefcase-macOS-$APPLE_TEAM_ID" \
  --key "$API_KEY_PATH" \
  --key-id "$APPLE_NOTARY_KEY_ID" \
  --issuer "$APPLE_NOTARY_ISSUER_ID"
rm -f "$API_KEY_PATH"
