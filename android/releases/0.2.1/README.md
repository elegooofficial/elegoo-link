# Link Workshop v0.2.1

Fixes an HTTP-first blocker observed on the CC2: MQTT 1883 reachable, HTTP 80 refused/unreachable. Identity now uses selected-IP UDP discovery or an optional exact serial number, with HTTP fallback. MQTT still requires authentication and registration; existing uploads still require HTTP.

Install the APK as an update over v0.2.0; the signing certificate is unchanged. Connect with serial blank for discovery. If identity lookup fails, enter the exact Serial Number from printer Settings → Device, then reconnect.

Source commit: c8f44a2ad370c41e9793e1472734ede4a37a75d5

35 JVM tests, clean build, lint (0 errors) and APK signature/manifest verification passed. Physical connection after the fix remains unverified. APK is a development build; no printer access code or private signing key is included.
