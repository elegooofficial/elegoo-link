# v0.3.5 registration acknowledgements (source)

Inspected 2026-10-06. The pinned SDK [MQTT transport](https://github.com/elegooofficial/elegoo-link/blob/46c7b814e055cf9675d58482d79f43d0bd2280da/src/lan/protocols/mqtt_protocol.cpp) subscribes at QoS 1 and publishes api_register at QoS 1, waiting for acknowledgement. Android previously used QoS 0. Registration now requests QoS 1 for all three exact topics, validates all three SUBACK grants (0/1 accepted, 128 rejected), then uses synchronous QoS 1 non-retained publication. The existing eight-second application-reply deadline starts after PUBACK. Ordinary query/control/heartbeat publication is unchanged.

Registration requires JSON with actual string client_id/error fields, matching client ID, exact serial/request topic and a non-retained reply. Malformed, wrong-client, unexpected-topic and retained replies cannot establish readiness or suppress a following correct reply. A matching error other than ok is rejected without exposing raw errors. Synchronized diagnostics store fixed outcomes and bounded counters only; no payloads, topics, identities or credentials. Reports separate broker acknowledgement from application registration. PIN mode remains one explicit connection attempt, read-only, memory-only, without HTTP or fallback credentials. QoS 1 may retransmit registration within that session. See [MATRIX_COEXISTENCE.md](MATRIX_COEXISTENCE.md) for the new physical timeout observation.

# v0.3.4 embedded preview and material evidence

Inspected 2026-10-06. Primary sample: [Elegoo CC2 factory G-code](https://raw.githubusercontent.com/elegooofficial/CentauriCarbon2/5a2ea7fc03e707552701b1a69f463699cbd39230/gcodefile/EEB001_PLA_xyz_10m15s.gcode) at `5a2ea7fc03e707552701b1a69f463699cbd39230`. The sample's `thumbnail begin 144x144 3604` declares base64 character count (3604), decoding to a 2701-byte PNG. Its layer header uses a colon. [Orca thumbnail exporter](https://github.com/OrcaSlicer/OrcaSlicer/blob/1d577ea4e219d3fcd56ed52648c5d0b1cb8dce8e/src/libslic3r/GCode/Thumbnails.hpp) emits wrapped base64 comments with matching begin/end tags and encoded-size declaration; [format tags](https://github.com/OrcaSlicer/OrcaSlicer/blob/1d577ea4e219d3fcd56ed52648c5d0b1cb8dce8e/src/libslic3r/GCode/Thumbnails.cpp) distinguish PNG/default thumbnail and thumbnail_JPG. Formats were independently implemented; no upstream image or slicer implementation is shipped. The factory sample's configured printer model says Centauri Carbon, so format validation does not certify CC2/model compatibility.

Recognized thumbnail/thumbnail_PNG/thumbnail_JPG blocks require matching terminator, exact encoded character count, strict base64, compressed size at most 256 KiB, dimensions at most 1024 per axis and 1,048,576 pixels. Only the first 16 candidate blocks are evaluated; the largest valid image is retained. PNG chunk lengths, CRCs, IHDR/IDAT/IEND structure and declared/actual dimensions are validated; JPEG SOF/SOS/EOI headers/dimensions are checked. Actual native decoding performs a second dimension check before pixel allocation on the file worker and samples the longest side to at most 512 pixels. JPEG header validation alone does not prove entropy/pixel validity; decoder failure leaves the text report available. Malformed, unsupported, unterminated, overlong or oversized blocks are skipped without breaking file import. Binary/control bytes suppress previews and text inferences. No image URL is followed.

Header/comment parsing now accepts colon as well as equals delimiters for recognized fields. Independent raw material vectors are captured before presentation truncation: filament_type/filament_colour separated by semicolons, usage mm/g separated by commas. Capture is bounded by the existing line limit; vectors over 2048 characters or 32 positions are omitted, with at most eight displayed positions. Simple quotes are supported; ambiguous quoted delimiters are omitted. Only #RRGGBB colors become swatches. Numeric usage must be finite, nonnegative and at most 1e12; missing/invalid usage remains unknown, never zero. Different vector lengths and inconsistent zero/nonzero length/mass indicators produce warnings. Source-array positions are not asserted to be tool IDs or CANVAS tray IDs. No automatic tool count, mapping, heater or print-start change is made.

The parser was exercised against the pinned full 388901-byte Elegoo sample; it returned layer count 100, 144×144 PNG, PLA/#36A8E1, 1466.83 mm and 4.41 g. Extracted PNG decoded fully on the host. Native Android BitmapFactory/rendering, actual user files and firmware mapping semantics remain device acceptance. See [THUMBNAILS.md](THUMBNAILS.md). Earlier records follow.

# v0.3.3 file workspace and download contract

Inspected 2026-10-06. The official SDK baseline CC2 HTTP transfer exposes `/download`, `/download/sdcard` and `/download/udisk`, selecting a LAN access token/default. This app exposes only its existing internal `local` and USB `u-disk` storage options. USB maps to `/download/udisk`; internal maps to `/download`. GET query values are encoded separately as `?X-Token=<token>&file_name=<filename>`; `X-Token` is also a header. The SDK's URL builder already adds a token query and its download caller appends another question mark; the Android implementation deliberately uses a single query separator and ampersand between parameters. See [primary implementation](https://github.com/elegooofficial/elegoo-link/blob/46c7b814e055cf9675d58482d79f43d0bd2280da/src/lan/adapters/elegoo_fdm_cc2/elegoo_fdm_cc2_http_transfer.cpp). Real firmware acceptance is pending.

Session downloads use the effective MQTT session token, including discovery-reported LAN protection-off default. PIN probes are refused before opening HTTP. Service initiation requires a current listed filename and recent file list; filenames cannot contain a path or control characters. Selected local/VPN connection factories are retained. Downloads and uploads cannot run together. Route/session closure cancels the active HTTP connection; transfer work is not automatically replayed. Registration is not needed for offline phone-file inspection.

Downloads stream to a temporary cache file with a 512 MiB cap, 5-second connection and 30-second read timeout. Redirects, non-200 status, JSON/HTML content types, leading error-document markers and non-identity content encoding are rejected. Reported Content-Length must match the received bytes; unknown-length responses remain bounded but have no independent expected-size/checksum guarantee. Progress is -1 when size is unknown, at most 99 until completion, then 100. Failed/cancelled/incomplete transfers remove the partial cache file. A completed copy is handed to the service for offline inspection; callbacks from obsolete sessions discard it.

The pure Java inspector scans raw file bytes for SHA-256 with bounded per-line buffers (16 KiB) and total size (512 MiB). Oversized lines are skipped for text analysis. Supported key/value slicer comments are presented as estimates/configuration; explicit T selections are observations only, limited to 32 distinct values. Binary/control bytes disable text inferences. Comments, unrelated G-code fields and macro selectors are not interpreted as tray assignments. No motion simulation, runtime/material compatibility certification, estimated-time recalculation or automatic print-setting changes are performed.

The service serializes import/inspection/document export off the UI thread and holds one completed phone cache copy. User-selected source documents and printer files are not deleted by clearing that copy. Exports use Android's document picker; the chosen copy's fingerprint is checked again after the picker returns to avoid silently exporting a different selection. Fingerprint/URI pending state survives Activity recreation; missing copy after process death refuses export. A failed document write can leave a partial destination document. Cache copies are transient; save a document to retain one. App-owned stale temporary G-code files are cleaned at service creation.

See [FILE_WORKSPACE.md](FILE_WORKSPACE.md) for usage and limitations. Earlier release protocol records follow.

# v0.3.2 authentication and read-only probe

Inspected/validated 2026-10-06. The CC2 local MQTT SDK has an explicit `pinCode` branch and discovery marks cloud mode with PIN intent. The top-level SDK normally routes CLOUD to CloudService, so this branch alone does not establish local PIN support alongside Matrix. [MATRIX_COEXISTENCE.md](MATRIX_COEXISTENCE.md) records the pinned primary sources and hardware acceptance stages.

Route and credential intent are independent. LAN authentication retains discovery-based code/default selection and refuses reported cloud mode. Explicit PIN probe requires a supplied nonempty current PIN, refuses reported LAN Only and never uses a default, protection-off override or alternate credential. Unknown discovery mode permits the explicit experiment, with an uncertainty notice. Identity comes from UDP or manually supplied serial; no HTTP fallback is allowed in PIN mode. MQTT keeps the existing local username, client format, serial topics, registration and request spacing. It does not connect account cloud MQTT/Agora or bind/unbind devices.

The probe never supplies the PIN to PrinterHttp, including connection checks. Read-only is enforced before enqueue and again at dispatch for all changing method IDs; upload is refused before creating its HTTP client/task. Read queries remain available, subject to firmware support. Every probe failure, authorization rejection or route loss is terminal for that attempt, including transport errors that would trigger bounded LAN retries. Raw registration errors are not displayed; known connection-limit rejection gets fixed secret-free wording. Firmware may still evict another client during registration; no other-client-disconnect request is sent. Registration is distinct from fresh status and Matrix coexistence.

Profiles persist route and PIN-probe selection, never the PIN. The masked PIN field disables Activity state saving and autofill; only an active session/service retains its memory credential. No account login or official cloud implementation is included in this release.

Earlier release protocol records follow.

# v0.3.1 routing additions

Inspected/built 2026-10-06. No CC2 wire methods or credentials change. Local mode retains Network-bound Wi-Fi/Ethernet sockets. Remote mode captures the app's active VPN network, refuses a process-bound network, and uses unbound/default sockets and direct HTTP connections so Android applies its VPN routing. TCP creation/connect, HTTP factory opening and UDP creation/send check the captured VPN. Selected-IP discovery uses unicast only; routed VPNs do not carry the local broadcast picker.

The service watches default network loss/replacement/capability changes, checks route availability during its freshness timer and disables readiness when the captured VPN is no longer active. It closes the old session and cancels work before bounded fresh-registration retries. Camera has an independent route watcher and stops on invalidation. Pending commands/uploads are not replayed. Diagnostics add TCP 8080 in remote mode, and profiles retain local/remote choice (older profiles default local).

These checks establish VPN presence for the app, not its provider, home gateway, approved destination route, access policy or encryption. Callback delivery and connection setup can race network changes; use Android Always-on VPN / Block connections without VPN for stronger system enforcement. Phone-to-Pi encryption depends on the configured VPN; Pi-to-printer still uses plaintext LAN MQTT/HTTP. Successful VPN routing does not correct CC2 authorization failures or a missing HTTP service. See [REMOTE_ACCESS.md](REMOTE_ACCESS.md) for narrow /32 route and phone-specific access-rule setup.

Primary routing sources: [Android VPN](https://developer.android.com/develop/connectivity/vpn), [Android network state](https://developer.android.com/develop/connectivity/network-ops/reading-network-state), [Tailscale subnet routers](https://tailscale.com/docs/features/subnet-routers). Android/Pi/CC2 physical acceptance remains pending.

Earlier release protocol records follow.

# v0.3.0 protocol additions

Inspected 2026-10-05. Start/config and paused/completed state mappings come from the official SDK baseline `46c7b814e055cf9675d58482d79f43d0bd2280da`. Previously commented-out methods are now backed by the primary author's packet captures and firmware probes in [bjan/pycentauri PROTOCOL.md](https://github.com/bjan/pycentauri/blob/2f6d9ed53922ea1d733394706ce2bafd875ae5b4/docs/PROTOCOL.md), pinned at `2f6d9ed53922ea1d733394706ce2bafd875ae5b4`. That source reports file/camera/deletion support on 02.01.00.00; it also documents older-firmware nonresponse. Wire-format facts were used for an independent implementation; no pycentauri source code is copied. This evidence is not a test of the user's printer.

| Method | Parameters / result | App policy |
| --- | --- | --- |
| 1020 start | `filename`, `storage_media`; `config` contains `printer_check`, `bedlevel_force`, `delay_video`, `print_layout` A/B, `slot_map` entries `{t,canvas_id,tray_id}` | Listed .gcode file, fresh idle/fault-free status, final confirmation. App checks explicit mappings against fresh connected reported trays. No guessed tool count or silent partial mapping |
| 1023 resume | Empty params | Machine 2, paused substate 2502/2505 only; confirmation |
| 1028 heaters | `extruder`, `heater_bed` integer °C | Idle/fault-free status; conservative app bounds 0–300 / 0–100, zero off |
| 1029 light | `power` 0/1; state `led.status` | Fresh status; uses power, not guessed status parameter |
| 1030 fans | `fan`, `aux_fan` or `box_fan` 0–255 | User percentage rounded to byte range; fresh status |
| 1031 speed | `mode` 0 silent, 1 balanced, 2 sport, 3 ludicrous | Printing substate 2075; confirmation |
| 1036 history | Empty params; `history_task_list` oldest-first; task_status 1 complete / 2 cancelled | Present newest 50 rows, unknown states explicit |
| 1042 camera | Empty params; `url` | Validate HTTP(S) with same selected private IPv4, no embedded credentials/fragment/redirects |
| 1044 file list | `storage_media` local/u-disk, `offset`, `limit` 50; USB adds `dir` `/`; result `file_list`, `offset`, `total` | Sequential pages; listed filename and metadata; no invented thumbnail endpoint |
| 1047 delete | `storage_media`, `file_path` array | One selected .gcode filename; fresh idle/fault-free status and list; confirmation; no replay |
| 1048 storage | Empty params; `total_bytes`, `used_bytes` | Read-only display |

Read queries 1036/1042/1044/1048 accept omitted error_code only with the exact expected result shape; error codes still take precedence. Changing commands require explicit error_code 0 and request ID/method correlation. Acknowledgement is separate from status evidence. Request publication is spaced at least 2 seconds; stop takes the next slot ahead of queued reads, without bypassing spacing or a pending changing command. Guards are repeated at dispatch. Query timeouts start at actual publication and report feature unavailability without disconnecting the monitor; failed query publication releases its busy state. Connection loss never replays changes.

The camera's default independent endpoint is `http://<printer IP>:8080/?action=stream`. Playback uses bounded JPEG marker frames (2 MiB max), checks decoded dimensions, samples large frames, throttles to 5 fps and applies UI backpressure. It stops on tab change/background, closes its socket on cancellation and never follows redirects. Camera access still depends on firmware and local routing, even when independent of MQTT auth.

Read-only discovery uses the existing UDP 52700/method 7000 parser, collecting up to 20 private IPv4 printers over four seconds. Per-IP credential encryption uses the unchanged Keystore alias and IP AAD, migrating existing ciphertext/IV without decryption/re-encryption. No access codes enter saved instance state or discovery traffic.

Alerts require an observed active job plus explicit completed substate 2077, match its reported UUID/filename when present and deduplicate completion. A completed event that clears the filename uses the observed name. Idle/cancelled/stale/disconnected states never imply completion; reconnection can therefore miss an event. Newly appearing fault codes alert once until cleared. No separate cloud notification backend runs after process death.

Homing/movement and filament-loading parameters are not exposed. Official cloud/Agora, timelapse export and other model protocols remain outside this release.

The following sections describe earlier releases and the original audit; their stated gaps are historical.

# CC2 source audit and port contract

Baseline: `elegooofficial/elegoo-link` commit `46c7b814e055cf9675d58482d79f43d0bd2280da`, inspected 2026-10-05. Claims here refer to that code, not untested firmware guarantees.

| Area | Source | Android behavior |
| --- | --- | --- |
| System information | `src/lan/adapters/elegoo_fdm_cc2/elegoo_fdm_cc2_protocol.cpp` | HTTP port 80 `/system/info`, `X-Token` header/query; serial from `system_info.sn` |
| MQTT connection | Same file | TCP port 1883, user `elegoo`, access code/default `123456`, client identity `1_PC_<four digits>` |
| Registration | Same file | Subscribe to `elegoo/<sn>/<client>_req/register_response`; publish `{client_id,request_id}` to `elegoo/<sn>/api_register`; require matching client and `error: ok`, 3-second timeout |
| Request and status topics | Same file | Requests at `elegoo/<sn>/<client>/api_request`; replies at corresponding `/api_response`; events at `elegoo/<sn>/api_status` |
| Application heartbeat | Same file | `{"type":"PING"}` every 10 seconds on request topic; MQTT keepalive also enabled. Android additionally polls full status every 15 seconds and disables controls after 20 seconds without valid status |
| Request envelope | `src/lan/adapters/elegoo_fdm_cc2/elegoo_fdm_cc2_message_adapter.cpp` | Numeric `id`, numeric `method`, object `params`; require request-ID/method correlation and `result.error_code == 0` |
| Implemented method table | Same file, `COMMAND_MAPPING_TABLE` | Attributes 1001, status 1002, pause 1021, stop 1022; status deltas 6000 |
| Delta cache | Same file, `handlePrinterStatus` and `mergeStatusUpdateJson` | Require full snapshot before deltas, recursively merge objects, replace arrays/scalars, replace `exception_code` atomically. Five consecutive sequence gaps invalidate the baseline and request refresh; zero sequence permits restart |
| Status display | Same file | `machine_status.status/sub_status/progress`, `print_status.filename/current_layer/total_layer/remaining_time_sec`, `extruder` and `heater_bed` temperature/target, `ztemperature_sensor.temperature` |
| File upload | `src/lan/adapters/elegoo_fdm_cc2/elegoo_fdm_cc2_http_transfer.cpp` | PUT `/upload`, chunks no larger than 1 MiB, `Content-Range`, `X-File-Name`, whole-file `X-File-MD5`, `X-Token`; each chunk must acknowledge `error_code: 0` |

## Explicit gaps

The CC2 command mapping table comments out resume (1023), homing (1026), movement (1027), temperature (1028), light (1029), fan (1030), print speed (1031), task history (1036–1038), camera (1042), file list (1044), file detail (1046), file delete (1047), disk information (1048), and filament loading/unloading (1024/1025). These numbers are clues for subsequent firmware inspection, **not permission to expose working controls**. Their request parameters, error semantics and firmware support need verification.

Start-print (1020), CANVAS status (2005), auto refill (2004) and printer downloads (1057/1058) are active upstream. Start-print includes bed checks, plate type, timelapse configuration and tray mappings; the first app deliberately leaves starting a job to the printer screen until these options can be represented and tested properly.

Discovery is also implemented upstream: CC2 UDP port 52700 and method 7000, with LAN/cloud and token-status fields. v0.2.1 implements identity lookup for the manually selected IP, with Android-bound UDP unicast and subnet broadcast. A full discovery picker remains pending.

Cloud code uses HTTP/MQTT plus Agora RTM and a generated private config. `thirdparty/agora/` contains desktop platform dependencies; the repository has no Android cloud build or APK app. Camera capability flags alone do not specify a working stream URL or playback format. The CC2 discovery source explicitly says it has no specific built-in web interface, so a WebView shortcut cannot be assumed to replace this app.

## v0.2.0 additions

The baseline command mapping remains the same. This release additionally implements CANVAS query 2005 and confirmed auto-refill 2004 (`params.auto_refill`: boolean), parses 1001 attributes, and displays nested `exception.exception_code` without guessing fault meanings. Unknown tray states remain numeric; camera/start/resume are still absent.

MQTT registration timeout is now 8 seconds. An Android connectedDevice foreground service owns each session independently of Activity lifetime. Local HTTP and MQTT socket factories use a Wi-Fi/Ethernet Network instead of relying on the default cellular route. A new session and fresh full status are required after each reconnect; no printer-changing commands or uploads are automatically replayed. Retry policy: five delays 1/2/4/8/16 seconds, reset on registration success, stop on known authentication/rejected-registration errors.

Connection diagnosis performs TCP-only probes on 80/1883 and an authenticated `/system/info` read. A reachable TCP broker does not establish MQTT credentials or registration. Raw exception URLs and access-code-bearing payloads are not displayed. Credential storage is optional Android Keystore AES-GCM with IP-bound AAD; backups exclude preferences. The local code does not require an official cloud account.

## v0.2.1 identity correction

The v0.2.0 phone diagnostic shows 192.168.1.69/24 reaching printer 192.168.1.84 on MQTT 1883 while HTTP 80 is refused/unreachable. The old app always read `/system/info` before opening MQTT; the upstream adapter requires that HTTP read only when a serial was not already supplied.

Identity resolution now uses the optional exact manual serial first, otherwise read-only UDP method 7000 on port 52700. UDP goes to the selected IP and the local IPv4 subnet broadcast, retransmits once per second, and times out after four seconds. Only replies from the selected IP with id 0, a result object and a bounded topic-safe serial are accepted; echoed requests, malformed data and replies from other IPs are ignored. Disconnect closes the discovery socket. Discovery does not transmit the access code.

If UDP fails, authenticated HTTP identity remains a compatibility fallback. If both identity paths fail, the app requests a manual serial and stops retries. The serial is never guessed from an account ID or hardcoded from a screenshot. MQTT still authenticates and registers normally before status or controls become ready. File uploads retain their existing HTTP dependency and are never automatically replayed. No cloud authentication or broader control methods are added.

## v0.2.2 authentication-state correction

The user's v0.2.1 manual-serial attempt reaches MQTT but receives CONNACK code 5. MQTT 3.1.1 defines 4 as bad username/password and 5 as not authorized; the broker does not provide the failed policy rule. Code 5 must not be presented as proof that the user mistyped the password.

The inspected upstream discovery adapter selects accessCode authentication only for token_status 1; token_status 0 selects the default password. A reported lan_status 0 is cloud mode and selects a separate pinCode path upstream. The Android client now retains these fields (boolean or numeric 0/1); invalid/absent values remain unknown. It selects 123456 only when code protection is explicitly disabled, otherwise retains the entered code. A known cloud/WAN mode is rejected locally with an explanation before sending LAN credentials. MQTT 3.1.1 is requested explicitly. Uploads use the same selected credential as the MQTT session.

Discovery metadata is queried even with a manual serial; if a valid response from the selected IP provides a different serial, it takes priority and the mismatch is reported. If UDP is blocked, the manual serial still permits connection without HTTP. Connection checks report mode/protection and serial mismatch without printing the serial or access code. MQTT authorization failures include the known mode/protection summary and are terminal; the app does not guess passwords or rotate client formats to bypass an authorization refusal.
