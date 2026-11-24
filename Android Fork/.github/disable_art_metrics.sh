#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later

set -euo pipefail

# Only use for shell commands safe to repeat, such as reading or setting a value.
retry_repeatable_adb_shell_cmd() {
  bash "$(dirname "${BASH_SOURCE[0]}")/adb_retry.sh" shell "$@"
}

# Both CI images use BE2A.250530.026.F3. Revisit this workaround when that changes.
image_build=$(retry_repeatable_adb_shell_cmd getprop ro.build.version.incremental)
if [[ "$image_build" != "13894323" ]]; then
  echo "::warning::⚠️ Emulator system image changed from build 13894323 to '$image_build'. Reassess the ART and DeviceConfig bugs independently; continuing with the existing workaround."
  cat <<'EOF' | tee -a "${GITHUB_STEP_SUMMARY:-/dev/null}"
### ⚠️ ART metrics workaround: emulator image changed

- **ART/WebView crash (Issue 21883):** Does the 16 KB emulator still crash with ART
  metrics enabled? If fixed, remove this helper and its workflow/README references.
- **DeviceConfig override crash:** Does `device_config override` still crash
  system_server? If fixed, verify it keeps the native property false with server
  sync enabled, then use it to simplify this helper if metrics still need disabling.
EOF
fi

# On the 16 KB emulator, ART metrics reporting probes userfaultfd(), which kills
# the WebView renderer because its sandbox forbids that syscall.
# Apply on every run: DeviceConfig sync can overwrite a raw setprop, including
# during snapshot warmup. ART reloads these flags after fork.
# Avoid `device_config override`: it crashes SettingsToPropertiesMapper on the
# Android 16 emulator image. Disable server sync before writing the setting.
# See https://github.com/ankidroid/Anki-Android/issues/21883
retry_repeatable_adb_shell_cmd su root "device_config set_sync_disabled_for_tests persistent"
retry_repeatable_adb_shell_cmd su root "device_config put runtime_native metrics.write-to-statsd false"

# DeviceConfig allows Google Play images to update the native property asynchronously.
# Check the sync mode and both values before starting tests, so failed setup cannot leave metrics on.
for ((attempt = 0; attempt < 30; attempt++)); do
  sync_mode=$(retry_repeatable_adb_shell_cmd device_config get_sync_disabled_for_tests)
  config_value=$(retry_repeatable_adb_shell_cmd device_config get runtime_native metrics.write-to-statsd)
  native_value=$(retry_repeatable_adb_shell_cmd getprop persist.device_config.runtime_native.metrics.write-to-statsd)
  printf 'ART metrics: sync disabled=%s, DeviceConfig=%s, native property=%s\n' "$sync_mode" "$config_value" "$native_value"
  if [[ "$sync_mode" == "persistent" && "$config_value" == "false" && "$native_value" == "false" ]]; then
    exit 0
  fi
  sleep 1
done

echo "Failed to disable ART statsd metrics reporting after 30 checks" >&2
exit 1
