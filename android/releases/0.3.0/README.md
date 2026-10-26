# Link Workshop v0.3.0 development release

[Download Android APK](Link-Workshop-v0.3.0.apk?raw=true) · [Download Android source](Link-Workshop-v0.3.0-source.zip?raw=true)

System/Light/Dark under Settings → App preferences → Appearance. Monitor, Files, Camera and Settings tabs; printer discovery/profiles; internal/USB files and confirmed deletion; print setup with checks, plate, timelapse and CANVAS mappings; resume; heater/fan/light/speed controls; local MJPEG/snapshots; print history/storage; completion/new fault alerts; upload cancellation.

Install normally over v0.2.0–v0.2.2: same development certificate and application ID. v0.3.0/code 5, Android 8+; target Android 16. Debug-signed, independently developed; not an official Elegoo app.

65 distinct automated tests passed across the full suite and final focused checks. Lint: 0 errors (20 English text/resource warnings). APK version, packaged theme/DEX, signature and archive integrity verified. See ../../VALIDATION.md for exact evidence and device checks.

Physical CC2/S24+ authentication and new-feature behavior remain unverified. The earlier MQTT authorization refusal is not established as resolved. HTTP 80 is still required for upload, while MQTT monitoring and local camera have separate paths. Cloud/remote access, timelapse export, filament loading, thumbnails, axis movement and other models remain pending. No commands/uploads are automatically replayed.

Source commit: e8d7282263ffb110a4f93393d39ae9427c87695f
Signing certificate SHA-256: 9384f581bc33bc47517c6e5177702493fd0bcaabe17f0257430b37212efde6b3

SHA256SUMS provides checksums. Source ZIP contains the Android project, tests, build wrapper and licenses; no access codes/signing keys are included.
