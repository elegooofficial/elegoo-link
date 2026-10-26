# Link Workshop v0.3.2 — Matrix coexistence probe

Install Link-Workshop-v0.3.2.apk as a normal update over v0.2.0–v0.3.1 (same development signing certificate). v0.1.0 used an older, lost certificate.

New: explicit experimental **Cloud-mode PIN probe (read-only)**, independent of local/VPN route; separate masked memory-only PIN; UDP/manual identity with no authenticated HTTP bootstrap; session and UI changing-command/upload blocks; no automatic retries or credential fallback. Profiles retain mode only, never the PIN. Existing full-control LAN mode, Pi/VPN route, System/Light/Dark, files, controls, history and camera remain included.

The official SDK contains a local PIN authentication branch, but normally routes CLOUD to CloudService. Local support alongside Matrix is a candidate requiring hardware validation, not a firmware guarantee. Follow [MATRIX_COEXISTENCE.md](../../MATRIX_COEXISTENCE.md): keep LAN Only off and Matrix working, use home Wi-Fi first and the current printer-displayed pairing PIN, keep the existing account binding, and compare fresh status in both apps. Do not substitute a LAN code, re-pair or unbind. The app does not request other clients to disconnect, but firmware registration could still affect a client slot. If Matrix drops, disconnect the probe.

No Elegoo account login, Agora session or account cloud MQTT is implemented. PINs never become HTTP tokens; upload remains disabled in the probe. Camera and optional read queries depend on firmware. A Pi cannot unlock rejected local authorization; use the VPN route only after authentication works at home. See [REMOTE_ACCESS.md](../../REMOTE_ACCESS.md).

Validation: 86 JVM tests passed, zero failures/errors; lint zero errors and 22 English UI text warnings. Final APK manifest v0.3.2/code 7, ZIP integrity, DEX and unchanged v2 signing certificate verified. No physical printer/phone/emulator is attached; successful PIN authorization and Matrix coexistence remain unverified. See [VALIDATION.md](../../VALIDATION.md).

Source commit: af71e6488dcfe3c06752c27ed8b30602fbafcc23. The source ZIP includes build scripts/wrapper, code, tests, guides, licenses and SOURCE.txt. No account/printer credentials or signing keys are included. SHA256SUMS covers both downloads.
