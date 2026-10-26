# v0.3.5 source validation — CI passed; compatible installer pending

2026-10-06. The latest supplied v0.3.3 screenshot progresses past the earlier MQTT code 5: broker CONNECT completes, UDP reports cloud/WAN with code protection enabled, but application registration times out. HTTP port 80 remains unreachable and was not used by the probe. No usable monitoring, PIN validity, or successful Matrix coexistence has been established.

The local Gradle test/lint/APK build started, but the execution workspace stopped responding before final results could be retrieved. Source changes were reconstructed against the exact published v0.3.4 commit through GitHub and preserved there. GitHub CI subsequently passed on the preserved source commit `f3b29bf17d42bf5f185f5d3961a828ceabfcfe9b`: **129 JVM tests, zero failures/errors/skips; lint zero errors and 23 warnings; successful debug APK assembly; all 46 Gradle tasks executed**. [Run/logs](https://github.com/TheLastFrogrammer/elegoo-link/actions/runs/37402251862). The runner-generated development signature is not the existing app signing certificate. No compatible signed APK, packaged source ZIP, native acceptance or physical connection success is claimed. MainActivity version labels and selectable connection text were also updated in the preserved source.

Eight new JVM regression tests cover subscription grants (including rejected/incomplete/invalid/downgraded QoS), bounded/redacted facts, timeout text, no-PIN HTTP, missing/malformed/unmatched/retained/unexpected-topic replies, publication failure, no publication after denied subscription, terminal PIN failure and a valid reply following a wrong-client reply. Existing fake-transport sessions now assert QoS 1 subscriptions/registration and non-retained registration. These fixtures do not prove actual firmware support. Android CI runs testDebugUnitTest, lintDebug and assembleDebug with JDK 17/SDK 36; the successful run above covers the exact source commit. CI does not publish APKs or access the existing signing key. The last verified installer remains v0.3.4.

On-device acceptance after a compatible signed APK is available: leave cloud mode/Matrix enabled; make one explicit current-PIN attempt on home Wi-Fi; share the new connection failure facts or verify fresh status and simultaneous Matrix updates. No credential, binding, client identity or alternate-topic guessing was added. PINs remain memory-only, read-only and excluded from HTTP; failures do not start automatic reconnects.

# v0.3.4 validation

Validated 2026-10-06. No physical CC2, S24+ or emulator is attached. The embedded-image parser and material summary were exercised with generated fixtures and a pinned full public Elegoo sample. Native Android BitmapFactory rendering, UI/lifecycle, user files and firmware mapping semantics still need device testing. Matrix coexistence/authorization remains unverified.

- Full Gradle testDebugUnitTest, lintDebug and assembleDebug passed with build cache disabled: 46 tasks, 22 executed and 24 up-to-date. JDK 17, Gradle 8.13, AGP 8.11.1, SDK 36/build tools 35.0.0.
- **121 JVM tests, zero failures/errors**. Twenty new tests cover wrapped PNG/JPEG extraction, defensive image-byte copies, largest valid candidate, unsupported formats, incorrect count/missing end, corrupt PNG CRC/truncated JPEG/wrong format, dimension mismatch and size limits, invalid base64, nested blocks/overlong lines, candidate-count cap, incomplete blocks interrupted by G-code, material arrays/quoted values/zero usage, vector length mismatch, malformed/negative/nonfinite usage, color interpretation, ambiguous quotes, contradictory usage indicators, vector/row bounds, incomplete evidence, colon headers, image isolation and binary invalidation.
- **Lint: 0 errors, 21 warnings**, all SetTextI18n English UI text construction.
- Primary integration check: official sample at CentauriCarbon2 commit 5a2ea7fc03e707552701b1a69f463699cbd39230, full 388901 bytes, SHA-256 49675a3a34c5ff52c7acefe4153a607ae22dd8599c143ff6376957812c1c01b7. Production inspector extracted a 2701-byte 144×144 PNG from 3604 base64 characters, colon header layer count 100, PLA/#36A8E1, 1466.83 mm and 4.41 g. PNG fully decoded on host with matching dimensions. This is additional integration evidence, not an Android graphics or physical printer test; the older factory sample reports a Centauri Carbon model. No sample file/image is shipped.
- Final APK ZIP integrity, DEX markers for EmbeddedThumbnail/ThumbnailDecoder/SlicedMaterials/material dialog/version and retained PIN picker verified; final compiled classes postdate all final Java sources. Manifest **v0.3.4/code 9**, min 26, target/compile 36.
- Signature Scheme v2 verified, one unchanged signer: certificate SHA-256 **9384f581bc33bc47517c6e5177702493fd0bcaabe17f0257430b37212efde6b3**. Compatible normal update over v0.2.0–v0.3.3.
- APK **253311 bytes**, SHA-256 **f013f947c3b4fce3a3d0e3d76420bafb2c97a17e3ab457299b1d3d8b10c2f802**. Source checksum accompanies release; no credentials or signing keys are included.

Acceptance: import actual single/multi-material files, compare embedded previews/comments with the slicer, try missing/corrupt/unsupported and multiple-size image blocks, test both themes/rotation and file replacement/cache clearing, and verify material swatches and array positions without automatic tray mapping. Android decoding failure must retain the text workspace. Test imported and downloaded copies, document picker return and report sharing. Native memory/performance/layout/accessibility remain unverified. Existing HTTP/VPN transfer and PIN restrictions continue under prior validation records. JPEG structural checks do not fully validate entropy; Android native decode must still succeed.

See [THUMBNAILS.md](THUMBNAILS.md), [FILE_WORKSPACE.md](FILE_WORKSPACE.md) and [PROTOCOL.md](PROTOCOL.md). Earlier validation follows.

# v0.3.3 validation

Validated 2026-10-06. Offline file inspection, HTTP downloading/cancellation and session guards are implemented and covered with JVM fixtures. No physical CC2, S24+ or emulator is attached. The printer's earlier HTTP refusal remains unresolved; this implementation does not make that service available. Cloud-mode PIN authorization and Matrix coexistence remain unverified.

- Empty app/build directory and build cache disabled: testDebugUnitTest, lintDebug and assembleDebug passed in one full Gradle run, 46 tasks executed. JDK 17, Gradle 8.13, AGP 8.11.1, SDK 36/build tools 35.0.0.
- **101 tests, zero failures/errors**. Fifteen new tests cover inspector header/footer estimates, exact-byte SHA-256/line endings, final-line/BOM handling, commented/macro/special selectors, overlong lines, binary/control bytes, bounded metadata, empty/interrupted inspection; internal/USB encoded query and token handling; non-200/redirect/JSON/HTML/compressed responses; oversized/empty/truncated payloads; unknown-length progress; cancellation before/mid-transfer and unsafe paths; effective session credentials and no changing MQTT requests; session closure cancelling a blocked download and removing its partial file without replay.
- The existing production PIN guard test was extended to invoke download and verify refusal without any HTTP connection, alongside all changing-command/upload blocks.
- Lint: **0 errors, 20 warnings**, all SetTextI18n English UI text construction.
- Final APK manifest verified: **v0.3.3/code 8**, min 26, target/compile 36. ZIP integrity and DEX markers for GcodeInspector, phone workspace, download action, PIN download guard and retained coexistence picker verified.
- Signature Scheme v2 verified, one unchanged signer; certificate SHA-256 **9384f581bc33bc47517c6e5177702493fd0bcaabe17f0257430b37212efde6b3**. Compatible normal update over v0.2.0–v0.3.2.
- APK: **244923 bytes**, SHA-256 **40855d8710a422653da5dbc9cc4ac8b0bbc15e9aa228382aa337850a4b669138**. Source archive checksum accompanies the release. No account/printer credentials or signing keys are packaged with the source.

Hardware acceptance: import representative single/multi-material G-code on the phone while disconnected, compare supported comments/explicit selections with the file, and confirm no automatic tray/start/temperature change. Test large-file performance, unknown metadata, binary/overlong input, share chooser, cache clearing and document export. Cancel the document picker, rotate/change theme while it is open, and change the selected file: fingerprint mismatch must refuse a different copy. Process death/cache eviction must not revive a missing copy or repeat an upload/download. A failed document write may leave a partial destination file.

On a working LAN session with HTTP available, download listed internal/USB G-code, compare the saved exact-byte hash with a trusted copy if available, test cancellation/network loss/HTTP rejection/truncation and preservation of the previous completed phone copy. Try downloads during monitoring; uploads and downloads must not overlap. Test profile/default credential handling. On the Pi/VPN route, interrupt the VPN during download: close the old transfer and remove its partial cache copy without replay. No remote firmware test was performed here.

Unknown-length HTTP responses have no independent expected-size/checksum guarantee. Slicer metadata and T selections are observational; no simulation, complete tool count, mapping inference or print-compatibility certification is made. Existing local/remote routing, native theme/service/document-provider behavior and Matrix coexistence still require device acceptance. [FILE_WORKSPACE.md](FILE_WORKSPACE.md) documents scope and [MATRIX_COEXISTENCE.md](MATRIX_COEXISTENCE.md) retains the read-only Matrix test.

Earlier release validation records follow.

# v0.3.2 validation

Validated 2026-10-06. Physical cloud-mode PIN authorization, Matrix coexistence and Pi/VPN acceptance remain unverified. The latest supplied physical result is the earlier MQTT code 5. No account login, binding/unbinding or credential extraction was performed.

- Empty app/build directory, build cache disabled: testDebugUnitTest, lintDebug and assembleDebug passed in one complete Gradle run (46 tasks executed). JDK 17, Gradle 8.13, AGP 8.11.1, SDK 36/build tools 35.0.0.
- Full suite: **86 tests, zero failures/errors**. Thirteen added tests exercise exact PIN handling independently of LAN protection flags, missing/control/oversized PIN rejection, reported-mode mismatch, LAN intent remaining separate, protection-off LAN default, discovery/manual identity without HTTP, redacted summaries, production-session explicit PIN MQTT credentials, broker rejection without alternate/default credential, terminal connection loss, and registration-limit messaging.
- Production session tests invoke pause/resume/stop/start/delete/light/heater/fan/speed/refill/upload in read-only mode and verify no changing MQTT request or HTTP connection is made, while file read queries and readiness still work. No real printer effects are exercised.
- Lint: **0 errors, 22 warnings**, all SetTextI18n English UI text construction.
- Final APK ZIP integrity, DEX markers for PrinterAuthentication, explicit cloud-mode PIN picker and terminal probe text verified. Manifest: **v0.3.2/code 7**, min 26, target/compile 36.
- Signature Scheme v2 verified, one signer; certificate SHA-256 remains **9384f581bc33bc47517c6e5177702493fd0bcaabe17f0257430b37212efde6b3**. Compatible normal update over v0.2.0–v0.3.1.
- APK: **234491 bytes**, SHA-256 **46522dda110fd87b7660b46d52c0170d7928ddc773c67e902fc878e4508c1fed**. Source archive checksum is published alongside the release.

No emulator or physical device is attached. JVM transport fixtures validate our protocol/dispatcher behavior, not Android UI/lifecycle, ConnectivityManager/VPN callbacks, printer firmware support or Matrix coexistence. Read-only prevents changing printer commands; firmware registration could still consume/replace another client's slot. PINs are memory-only and excluded from authenticated HTTP, saved profiles/state, diagnostics and logs. UI masking/state suppression was reviewed in source; actual phone lifecycle remains a device check.

Acceptance: follow [MATRIX_COEXISTENCE.md](MATRIX_COEXISTENCE.md), preserving cloud mode and the existing Matrix binding. Test home Wi-Fi, explicit current displayed PIN, UDP or exact manual serial, one connection attempt, fresh status and simultaneous Matrix use. Confirm changing controls remain disabled, query errors do not imply capability, and a rejected/lost session does not retry. Rotate/change theme during an active probe; it may recover the PIN only from the live service. Disconnect/kill process and verify no saved PIN is restored. Test saved mode preference and old-profile LAN defaults. Check camera separately. Only then test the same working authentication over the configured Pi/VPN; route loss must stop the probe rather than restart it. No secrets need to be included in feedback.

Earlier release validation records follow.

# v0.3.1 validation

Validated 2026-10-06. Away-from-home operation has not been exercised on a physical Pi, CC2 or Galaxy S24+. The earlier MQTT authorization refusal remains the latest supplied printer connection result. This update adds VPN routing; it does not establish successful printer authentication or make the unavailable HTTP service work.

- Gradle full unit suite passed: 73 tests, zero failures/errors. Eight new tests cover all VPN-presence/network/process-binding combinations; refusing socket creation without a valid route; rechecking after creation for both timed/ordinary connect; guarded successful and denied paths for all four connected SocketFactory overloads; wrapped MQTT VPN-error redaction; discovery VPN loss blocking manual/HTTP fallback; and HTTP VPN loss retaining the route error.
- Final core route changes were rebuilt and the full 73-test reports verified again. The subsequent camera error wording was rebuilt with lintDebug/assembleDebug. Lint has zero errors and 22 warnings: 21 English UI translation warnings and one newer test-only JSON dependency notice. JDK 17, Gradle 8.13, AGP 8.11.1, SDK 36/build tools 35.0.0.
- Final APK assembled with build cache disabled from an empty app/build directory: 31 tasks executed. ZIP integrity and DEX markers for VpnRouteGuard, GuardedSocketFactory, remote route picker and final camera wording verified. Actual APK manifest: v0.3.1/code 6, min 26, target/compile 36.
- APK Signature Scheme v2 verified; certificate SHA-256 unchanged: 9384f581bc33bc47517c6e5177702493fd0bcaabe17f0257430b37212efde6b3. Compatible update over v0.2.0–v0.3.0.
- APK: 230979 bytes; SHA-256 e2b9a8ae7e719951135fd686fd068956b720f76af5ab81176921c97bd9babf7d.
- Pi-guide example policy parsed as valid JSON; single /32 destination and expected TCP/UDP ports checked. Official Android/Tailscale routing/security documentation reviewed; no gateway or tailnet policy was applied remotely.

The JVM tests exercise route-policy logic, loopback sockets and simulated printer transports. They do not exercise Android ConnectivityManager, actual VPN route injection, native UI, service callbacks, Pi forwarding/firewalls, Tailscale policy enforcement or firmware behavior. VPN presence is a precondition, not proof of provider/destination/access policy. Connection setup can race route changes; Android Always-on VPN / Block connections without VPN gives stronger system enforcement.

On-device acceptance: verify local MQTT registration first, then set up the Pi per [REMOTE_ACCESS.md](REMOTE_ACCESS.md). Enable the approved narrow route and phone-specific permissions; select Remote through home VPN, retain the printer home IP and check diagnostics over cellular with home Wi-Fi off. Compare monitoring and camera with the touchscreen. Disable/switch VPN during registration, monitoring, a test upload and camera playback: the old session/work must close; commands/uploads must not replay; a restored route must register anew. Test saved route preference and older-profile local defaults, rotation/theme changes, Wi-Fi/cellular handover, foreground/background and screen-off behavior. HTTP remains required for uploads. Test denied-route/denied-port and an excluded-app VPN configuration; these must fail clearly without suggesting an access-code change.

No emulator or physical device is attached. No signing keys, access codes or cloud credentials are included in the source archive. Earlier release validation records follow.

# v0.3.0 validation

Validated 2026-10-05. New controls have not been exercised on a physical CC2 or S24+. The user's earlier broker authorization refusal remains the latest supplied connection result; this feature release does not establish that authentication is resolved.

- Clean Gradle build, build cache disabled: testDebugUnitTest, lintDebug and assembleDebug passed. JDK 17, Gradle 8.13, AGP 8.11.1, platform 36/build tools 35.0.0.
- 64 tests passed in the full suite. Subsequent filename validation was tightened to reject multiple embedded control characters; nine feature tests passed again. A new mid-transfer cancellation test was added; the final focused run passed all 13 feature/upload tests. Together 65 distinct tests passed, zero failures/errors.
- New coverage: exact start/config fields and defensive mapping copies; unsafe file paths/settings; offset/limit and USB root pagination; deletion arrays; heater/fan/speed/light bounds; paused-state resume; query shapes/request context; reported tray mapping; same-host camera URLs; history ordering; observed-job completion/fault deduplication; bounded multipart JPEG extraction; query rejection/publish failure without loss of monitoring; correlated resume acknowledgement; state change blocking a queued start; stop priority over queued reads; upload cancellation after the first acknowledged chunk preventing any next chunk.
- Lint: 0 errors, 20 warnings, all English UI text/resource translation warnings. API-27 navigation-bar appearance attributes now live in values-v27; API-26 fallback uses a contrasting navigation bar.
- Final APK repackaged from an empty app/build directory with build cache disabled; all 31 assembly tasks executed. Actual APK manifest verified: v0.3.0/code 5, min 26, target/compile 36. DEX verified contains the final control-character validation and new print/appearance UI. Light/dark/launch resources, night/version variants and embedded licenses are packaged.
- APK Signature Scheme v2 verified, one signer. Certificate SHA-256 unchanged: 9384f581bc33bc47517c6e5177702493fd0bcaabe17f0257430b37212efde6b3. Compatible update over v0.2.0–v0.2.2.
- APK: 224543 bytes; SHA-256 2a92ee44eb3969c10f7e4de291961ef3dd93b822d7850fd8f04eba87c463466b.

No emulator/device is attached. Automated transport fixtures validate this implementation, not a real printer's responses. Native theme/layout and lifecycle behavior, discovery broadcast routing, credential migration/Keystore, notifications/battery management and camera decode/playback still need device testing. HTTP upload remains unavailable when port 80 is unreachable. Camera opens independently of MQTT but still depends on the printer's stream endpoint and local routing. Feature errors/timeouts do not establish firmware support; acknowledgements do not alone prove the physical result.

On-device acceptance: install normally over v0.2, check System/Light/Dark including dialogs and rotation, select/discover/save profiles, reconnect and compare status/trays against the touchscreen. Verify file pagination/metadata/history/storage and camera start/stop/snapshot. On suitable test files/jobs, verify explicit start settings/tool maps, pause/resume/stop, confirmed idle deletion, heater/fan/light/speed/refill state, cancellation and alerts. Change state while a start is queued; it must not dispatch. Disconnect/process death must never replay a changing command or upload. Completion during stale/disconnected gaps can be missed and is never inferred from idle.

Cloud/remote access, timelapse export, filament loading, thumbnails, axis movement and other models remain pending. No cloud credentials or signing keys are included in the source archive.

Earlier validation records follow.

# v0.2.2 validation

Validated on 2026-10-05. The user's v0.2.1 screenshot establishes that the HTTP prerequisite is bypassed and the MQTT broker responds with authorization refusal code 5. It does not establish that the access code itself is incorrect. Authentication/registration after this update remains physically unverified.

- Clean Gradle build with build cache disabled: testDebugUnitTest, lintDebug, assembleDebug passed. JDK 17, Gradle 8.13, AGP 8.11.1, SDK 36/build tools 35.0.0.
- 43 JVM tests passed with zero failures/errors. Eight added checks cover boolean/numeric authentication flags and invalid/absent unknown values; disabled protection selecting default credentials; enabled/unknown protection preserving the entered credential; authoritative selected-IP serial mismatch handling; production-session MQTT default credential selection; cloud mode stopping before MQTT; and code 5 described without claiming a wrong password. Existing tests cover discovery cancellation/other-IP rejection, registration/status/command correlation, HTTP upload and no command replay.
- MQTT session tests require explicit version 3.1.1. Manual-serial fallback still avoids HTTP when UDP is unavailable.
- Lint: 0 errors, 12 warnings (English string construction and newer test-only JSON dependency).
- Final APK manifest/DEX verified: v0.2.2/code 4, min SDK 26, target/compile 36; updated discovery authentication summary and UI present.
- APK signature verified; certificate SHA-256 unchanged: 9384f581bc33bc47517c6e5177702493fd0bcaabe17f0257430b37212efde6b3. Normal update over v0.2.0/v0.2.1 is supported.
- APK SHA-256: b4d72e52bcab6802cddf3e5ac2a27b0dd6a265cc8a6a8e6d29bc9a95c087a3fa.

No physical CC2/S24+ or emulator is attached. Next device check: install update, run Check connection, observe advertised mode/protection flags, then connect. Reported cloud/WAN mode requests LAN Only for the current supported path; protection off selects the upstream default; protection on/unknown retains the user's entered code. No password/client-format guessing or auth bypass is attempted. HTTP uploads remain unavailable when port 80 is unreachable. Android lifecycle/routing/Keystore behavior still needs device testing as described below.

Previous release validation follows.

# v0.2.1 validation

Validated on 2026-10-05. The user's v0.2.0 diagnostic confirms phone 192.168.1.69/24 can reach printer 192.168.1.84 on MQTT 1883 while HTTP 80 is refused/unreachable. Physical connection on v0.2.1 still requires verification.

- Clean Gradle build with build cache disabled: testDebugUnitTest, lintDebug, assembleDebug passed. JDK 17, Gradle 8.13, AGP 8.11.1, SDK platform 36/build tools 35.0.0.
- 35 JVM tests passed, zero failures/errors. Ten added tests cover upstream UDP identity formats, echoed/malformed/wrong-method/unsafe replies, actual loopback UDP request/reply, rejection of a different source IP, socket cancellation, manual identity without either transport, actionable missing identity, MQTT registration/status/control with HTTP unopened, retained MQTT credential rejection, and invalid identity blocking MQTT.
- Existing tests continue to cover HTTP upload chunks/checksum, delta merging, request correlation, CANVAS freshness, errors and reconnect/no-replay policy.
- Lint: 0 errors, 13 warnings (English text construction and test-only JSON dependency update).
- Final APK manifest and DEX inspected: version name 0.2.1, code 3, min SDK 26, target/compile 36; Cc2Discovery, PrinterIdentity and optional serial UI present.
- APK Signature Scheme v2 verified. Signing certificate SHA-256: 9384f581bc33bc47517c6e5177702493fd0bcaabe17f0257430b37212efde6b3, the same certificate as v0.2.0. Normal update installation is supported.
- APK SHA-256: c3f12883eeef05d6c2fc8fbe322f2654da215206ba611c12a4754021bc661656.

No emulator, physical Galaxy S24+ or physical CC2 is attached. Loopback UDP and fake MQTT transports verify the implementation but do not establish firmware compatibility. On-device next check: connect with blank serial; if UDP identity fails, enter the exact printer Settings → Device Serial Number; verify MQTT authentication, registration and live status. HTTP remains required by the upload implementation, and is unavailable on the observed IP at test time. No cloud protocol, new printer-changing command, automatic upload replay or signing-key rotation is introduced.

Previous release validation follows.

# v0.2.0 validation

Validated on 2026-10-05. APK behavior on a physical printer/phone remains unverified.

- Clean Gradle build and subsequent final incremental verification: testDebugUnitTest, lintDebug, assembleDebug passed. JDK 17, Gradle 8.13, AGP 8.11.1, SDK platform 36 / build tools 35.0.0.
- 25 JVM tests passed: 9 codec/address, 3 recording HTTP upload, 4 production-session fake HTTP/MQTT transport, 4 error/backoff/request-policy, 5 presentation/CANVAS baseline tests.
- Session tests establish subscription/registration, attributes/status/CANVAS callbacks, credential rejection before MQTT, request-ID correlation, explicit refill payload/acknowledgement and no pending command replay after connection loss.
- Regression: a separate CANVAS query updates the delta baseline so an unrelated temperature event cannot restore old tray data. Unrelated cached status does not refresh CANVAS freshness.
- Credential-bearing raw exception URLs are not displayed. Tests verify URL/secret exclusion, known authentication failures stop retries, and retry delays exhaust/reset correctly.
- Lint: 0 errors, 12 warnings (English UI text construction and a newer test-only JSON version available).
- Signature verified using APK Signature Scheme v2, one signer. Signing certificate SHA-256: 9384f581bc33bc47517c6e5177702493fd0bcaabe17f0257430b37212efde6b3.
- Manifest verified: io.github.thelastfrogrammer.elink; v0.2.0/code 2; min SDK 26; target/compile SDK 36; non-exported connectedDevice foreground service. Backups disabled, encrypted profile never placed in Activity saved state.
- APK SHA-256: 44fe4663782c508f75a4d8933ac2bbeda9a96bf6d82df7e57c183d6041b5d65b.

No emulator, physical Galaxy S24+ or physical CC2 was available. Service lifecycle/rotation, Android notification permission and foreground restrictions, Android Keystore behavior, Wi-Fi routing and router isolation require device testing. Synthetic transports do not establish compatibility with the user's firmware V02.01.00.00. The user reports an IP connection failure on v0.1.0; the new connection diagnostic and explicit local network routing are intended to isolate/address this, but successful connection has not yet been verified.

The v0.1.0 signing certificate differs from this build. Its scratch-only private key was lost; installing v0.2.0 over it requires uninstalling v0.1.0 first, which clears its saved IP. This is a debug-signed development APK rather than a production release channel. Keep the v0.2 signing key privately for future compatible builds.

Hardware checklist: Check connection report; HTTP/MQTT/registration; touchscreen status comparison; foreground notification + Disconnect; file picker and rotation during connection; switch apps while uploading; Wi-Fi loss/reconnect with fresh registration; no automatic command/upload replay; rejected code; save/restore/forget credentials; tray freshness and reported colors/materials; refill acknowledgement; nested fault appearance/clearing; pause/stop on a test print. Screen-off power management can suspend network/CPU activity; this version does not hold a wake lock. Process death stops the session without restarting it or replaying a printer command.
