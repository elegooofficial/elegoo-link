# Link Workshop v0.3.4 — embedded previews and material evidence

Install Link-Workshop-v0.3.4.apk as a normal update over v0.2.0–v0.3.3 with the unchanged development signing certificate. v0.1.0 used an older lost certificate.

New in the offline phone file workspace: bounded embedded PNG/JPEG thumbnail extraction, native decoding on the file worker, colon-style header fields, and Material details with configured color swatches and source-index length/mass evidence. Imported and downloaded phone copies use the same inspector. No network image endpoint is guessed or opened.

Preview checks include encoded count, base64, byte/pixel/dimension/candidate limits, PNG CRC/chunk structure, JPEG headers, matching declared/native dimensions and sampled display size. Bad/unsupported images leave text inspection available. Material vectors are captured before presentation truncation; missing/invalid values remain unknown. Vector length mismatches and conflicting usage indicators are flagged. No complete tool-count inference, CANVAS mapping or print-setting change is applied. See [THUMBNAILS.md](../../THUMBNAILS.md) and [FILE_WORKSPACE.md](../../FILE_WORKSPACE.md).

All prior downloads/export, controls, System/Light/Dark, camera and local/VPN routing remain included. The read-only Matrix coexistence PIN probe is retained with no HTTP token use, changing commands, binding changes or automatic retries. Physical PIN/Matrix coexistence and HTTP firmware acceptance remain unverified.

Validation: 121 JVM tests passed, zero failures/errors; lint zero errors and 21 English UI text warnings. Production inspector passed a pinned full official Elegoo sample, including layer/material fields and a 144×144 PNG that fully decoded on the host. No official sample or image is shipped. APK manifest v0.3.4/code 9, ZIP, final-source classes/DEX and unchanged v2 signer verified. Native Android decoding/rendering, phone layout/lifecycle and actual user files still require device acceptance. See [VALIDATION.md](../../VALIDATION.md).

Source commit: 7cf0ce969affaf11a16d54b2f882dde22affd782. Source includes build scripts/wrapper, implementation, tests, guides, licenses and SOURCE.txt. No credentials or signing keys are included. SHA256SUMS covers both downloads.
