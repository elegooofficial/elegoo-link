# Link Workshop v0.3.3 — phone file workspace

Install Link-Workshop-v0.3.3.apk as a normal update over v0.2.0–v0.3.2 (same development signing certificate). v0.1.0 used an older, lost certificate.

New: offline G-code inspection of supported slicer comments, explicit T selections, exact size and SHA-256; report sharing; phone cache-copy clearing and document-picker export. Select a sliced file while disconnected to use it. The report does not simulate motion, certify compatibility, infer a complete tool count or assign trays.

Printer files gain internal/USB downloads into that workspace, cancellation, bounded streaming, effective LAN session credential handling and partial-file cleanup. Download requires a recent listed filename, LAN authentication and HTTP port 80; it respects local/VPN routing. The earlier HTTP refusal is not solved by adding this feature. Upload/download are serialized; session closure cancels a transfer without replay. Unknown-length streams have no independent expected-size/checksum guarantee. Save completed copies through the document picker to retain them; cache is transient.

All previous System/Light/Dark, monitor/files/controls/camera and remote-VPN functionality remains included. The v0.3.2 cloud-mode local PIN probe is retained, read-only with no HTTP token/download/upload use, credential fallback, account binding changes or automatic retries. Matrix coexistence and successful PIN authorization still require physical testing; follow [MATRIX_COEXISTENCE.md](../../MATRIX_COEXISTENCE.md).

Validation: 101 JVM tests passed, zero failures/errors; lint zero errors and 20 English UI text warnings. Final APK manifest v0.3.3/code 8, ZIP integrity, final-source DEX and unchanged v2 signer verified. No physical printer/phone/emulator is attached; native picker/UI/lifecycle, firmware HTTP endpoints and VPN interruption remain acceptance work. See [VALIDATION.md](../../VALIDATION.md), [FILE_WORKSPACE.md](../../FILE_WORKSPACE.md) and [REMOTE_ACCESS.md](../../REMOTE_ACCESS.md).

Source commit: d343551e6b5ec3569711932664f7cddbff89856b. Source archive includes build scripts/wrapper, code, tests, guides, licenses and SOURCE.txt. No account/printer credentials or signing keys are included. SHA256SUMS covers both downloads.
