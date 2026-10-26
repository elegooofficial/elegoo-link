# Keeping Matrix while extending the CC2

Inspected 2026-10-06. Target hardware: Centauri Carbon 2, firmware V02.01.00.00. **Coexistence on that printer is unverified.** v0.3.2 provides an explicit read-only test of a candidate local PIN path; it is not an Elegoo account/cloud client.

## What the SDK establishes

The official SDK baseline is `46c7b814e055cf9675d58482d79f43d0bd2280da`. These findings come from reading its implementation, rather than assuming every exposed code path works on every firmware:

| Finding | Primary source | Consequence |
| --- | --- | --- |
| Discovery interprets `lan_status: 1` as LAN Only and `0` as cloud/WAN; cloud discovery assigns `pinCode` authentication | [CC2 discovery](https://github.com/elegooofficial/elegoo-link/blob/46c7b814e055cf9675d58482d79f43d0bd2280da/src/lan/adapters/elegoo_fdm_cc2/elegoo_fdm_cc2_discovery_strategy.cpp) | A LAN access code and a cloud pairing PIN are different credential intents |
| Local CC2 MQTT has an explicit `pinCode` branch, with username `elegoo` and the supplied PIN as password | [CC2 protocol](https://github.com/elegooofficial/elegoo-link/blob/46c7b814e055cf9675d58482d79f43d0bd2280da/src/lan/adapters/elegoo_fdm_cc2/elegoo_fdm_cc2_protocol.cpp) | There is a source-backed candidate worth testing while cloud mode stays enabled |
| The top-level facade routes explicit CLOUD connections to CloudService, and explicit LAN connections to LanService | [Service selection](https://github.com/elegooofficial/elegoo-link/blob/46c7b814e055cf9675d58482d79f43d0bd2280da/src/elegoo_link.cpp) | The local PIN branch does not prove the normal official cloud connection uses local MQTT, or that firmware permits coexistence |
| Local MQTT still requires serial-based topics and successful application registration; the SDK handles a `too many clients` registration error | [CC2 protocol](https://github.com/elegooofficial/elegoo-link/blob/46c7b814e055cf9675d58482d79f43d0bd2280da/src/lan/adapters/elegoo_fdm_cc2/elegoo_fdm_cc2_protocol.cpp) | TCP reachability, broker authorization, registration, fresh status and Matrix coexistence are separate acceptance stages; the exact client limit is not established |
| HTTP identity/upload uses a LAN token; no matching local HTTP PIN handler was found | [CC2 protocol](https://github.com/elegooofficial/elegoo-link/blob/46c7b814e055cf9675d58482d79f43d0bd2280da/src/lan/adapters/elegoo_fdm_cc2/elegoo_fdm_cc2_protocol.cpp) and [HTTP transfer](https://github.com/elegooofficial/elegoo-link/blob/46c7b814e055cf9675d58482d79f43d0bd2280da/src/lan/adapters/elegoo_fdm_cc2/elegoo_fdm_cc2_http_transfer.cpp) | The probe never sends a PIN as an HTTP token; identity needs UDP discovery or an explicit serial, and upload stays disabled |

The upstream local PIN branch substitutes `123456` for an empty PIN. This probe deliberately requires an explicitly entered current PIN and has no default, fallback or credential guessing. LAN authentication retains its existing protection-off default only when discovery explicitly reports that setting.

## Test without changing the account binding

1. Keep the printer in normal cloud mode with **LAN Only off**. Confirm Matrix already shows current printer status and its camera. Keep the existing account binding; do not unbind, re-pair or regenerate credentials merely to make this experiment pass.
2. Install v0.3.2 or newer as a normal update. Use home Wi-Fi first. In Settings choose **Local Wi-Fi / Ethernet**, then **Cloud-mode PIN probe (read-only)** under Printer authentication. Enter the printer IP. If the printer currently displays a pairing PIN, enter that exact PIN in the separate masked PIN field. If no current PIN is available, stop here; the LAN access code is not a substitute.
3. Enter Serial Number from Settings → Device if UDP discovery cannot identify the printer. A supplied serial does not bypass authorization. Discovery's returned serial takes priority if it differs from the manually entered value.
4. Tap **Check connection**. This tests ports and UDP identity/mode, without authenticating MQTT or making authenticated HTTP requests in probe mode. A reachable MQTT port is only a routing result.
5. Tap **Connect** once. The app uses the normal local MQTT client format and explicitly entered PIN, then performs application registration. It does not sign into Elegoo, copy Matrix's cloud identities, bind/unbind devices or request other clients to disconnect. A failure or connection loss stops this probe without automatic retries.
6. If registered, wait for fresh status, compare values with the touchscreen, and check Matrix still updates. Switch between the two apps and verify both remain usable, including Matrix camera/notifications where practical. **Registration alone is not proof of coexistence.** If Matrix disconnects, disconnect the probe and restore Matrix's ordinary connection; do not leave clients fighting through repeated reconnects.
7. Try optional files/history/storage/CANVAS reads and the camera separately. Unsupported read queries may time out while monitoring stays connected. The local MJPEG camera is independent of MQTT authentication and may be unavailable in cloud mode. A working camera does not prove MQTT PIN authorization.
8. Only after local coexistence is demonstrated should the same authentication be tested through the Pi/home VPN using [REMOTE_ACCESS.md](REMOTE_ACCESS.md). The gateway extends a working route; it cannot unlock firmware-disabled local access.

The probe blocks pause/resume/stop/start, deletion, light, heater, fan, speed, refill changes and upload in the UI **and** the session dispatcher. Read-only refers to printer-changing commands; registration itself could still affect another connected client because firmware controls connection slots. The app cannot guarantee Matrix will remain connected before testing.

PINs are held in memory only. They do not enter profiles, Keystore, Activity saved state, diagnostics, HTTP requests or logs. Profiles retain route and authentication choice only. An active monitoring service keeps its in-memory credential through UI recreation; process death requires entering the PIN again. Disconnect ends the session. No secret needs to be sent back for diagnosis.

| Observed result | What it establishes / next action |
| --- | --- |
| TCP 1883 unreachable | Local route, port or firmware availability problem; authentication has not been tested |
| UDP reports LAN Only during PIN probe | Selected credential intent does not match the reported mode; use LAN authentication for that setting, or normal cloud mode for this experiment |
| No identity and no manual serial | Supply the exact serial; this probe does not fall back to HTTP identity |
| MQTT code 4/5 | The explicit local PIN path was not authorized; this does not prove a mistyped PIN. Verify the current displayed PIN and stop if refused; cloud transport may be required |
| Registration timeout | CONNECT completed, but no usable application registration reply arrived. PIN validity, topic permissions and cloud-mode firmware support remain unresolved; keep Matrix/cloud mode enabled |
| Registration rejected / connection limit | Broker authorization is insufficient. Stop the probe and leave Matrix available; do not interpret this as permission to evict or close other clients |
| Registered but status stale/missing | Session registration succeeded but usable monitoring did not; do not enable controls |
| Fresh app status, Matrix remains live | Evidence for local coexistence on this firmware/setup, pending longer lifecycle and camera tests |
| Fresh app status, Matrix drops | Coexistence failed; disconnect probe and investigate transport/session limits |

For feedback, share the secret-free diagnostic text, exact failure stage, whether status actually refreshed, and whether Matrix stayed connected. Do not share PINs, LAN codes or account tokens. The latest supplied v0.3.3 screenshot progresses past the earlier MQTT code 5: broker CONNECT completes, UDP reports cloud/WAN with code protection enabled, but application registration times out. HTTP port 80 remains unreachable and was not used by the probe. No usable monitoring, PIN validity, or successful Matrix coexistence has been established.

## v0.3.5 registration evidence

The pinned SDK requests QoS 1 subscriptions and sends registration at QoS 1 with a broker acknowledgement wait ([MQTT transport](https://github.com/elegooofficial/elegoo-link/blob/46c7b814e055cf9675d58482d79f43d0bd2280da/src/lan/protocols/mqtt_protocol.cpp)). v0.3.3/0.3.4 used QoS 0. v0.3.5 source aligns registration with the SDK, retaining the exact serial topics, client/request ID format and envelope. Other query/control dispatch is unchanged. Rejected/incomplete SUBACK stops before publication. PUBACK acknowledges broker receipt, not topic authorization or printer processing. Paho may retransmit registration within the same MQTT session for QoS 1 delivery; this is distinct from starting another PIN connection attempt.

Failure reports show CONNECT, SUBACK, PUBACK, matching reply outcome and bounded counters for malformed/wrong-client/retained replies and other pre-registration events. No credentials, serials, topics, payloads or raw exceptions enter these facts. Unexpected topics and unrelated/retained replies cannot establish readiness. Correctly correlated rejection remains terminal. Timeout remains eight seconds after PUBACK; PIN failures do not reconnect or try other credentials. Acknowledgements cannot prove Matrix coexistence. The APK is pending build/signing verification; do not expect these diagnostics in the published v0.3.4 APK.

## If local PIN access is unavailable

The SDK's cloud implementation is a larger adapter project. It separates account HTTPS APIs, live Agora RTM/RTC, cloud MQTT and cloud file transfers:

| Cloud layer | What was found | Android work still required |
| --- | --- | --- |
| Account HTTPS | Token refresh, account information/logout and account-bound device listing | Legitimate user-facing Android sign-in/bootstrap, region selection, protected token lifecycle and logout. The inspected example starts with an existing access token; initial Elegoo login is not implemented there |
| Device/file HTTPS | PIN lookup, binding/unbinding, file lists/details/thumbnails, history, exceptions, status reporting and multipart uploads | Start with authorized account-bound read operations. Ordinary connection must retain existing binding; listing a device is separate from pairing it. These APIs do not by themselves prove complete live monitoring |
| Agora RTM/RTC | Server-provided RTM/RTC identities and tokens; a public Agora app ID; same-UID-login handling | Use issued identities without inventing replacements. On collision, stop and explain the session conflict rather than reconnect indefinitely. RTC camera needs a separate Android integration |
| Cloud MQTT | Credentials plus `elegooslicer_<platform>_<account userId>` client identity | Resolve account/platform identity compatibility before connecting; copying an identity can collide with an existing session. A different arbitrary ID is not proven accepted |
| Cloud processing | Model/file processing and remote delivery in the official ecosystem | Reproducing local printer commands does not reproduce server conversion/slicing or background push infrastructure |

Primary implementation: [HTTP service](https://github.com/elegooofficial/elegoo-link/blob/46c7b814e055cf9675d58482d79f43d0bd2280da/src/cloud/services/http_service.cpp), [CloudService](https://github.com/elegooofficial/elegoo-link/blob/46c7b814e055cf9675d58482d79f43d0bd2280da/src/cloud/cloud_service.cpp), [RTM service](https://github.com/elegooofficial/elegoo-link/blob/46c7b814e055cf9675d58482d79f43d0bd2280da/src/cloud/services/rtm_service.cpp), [RTC service](https://github.com/elegooofficial/elegoo-link/blob/46c7b814e055cf9675d58482d79f43d0bd2280da/src/cloud/services/rtc_service.cpp), [cloud example](https://github.com/elegooofficial/elegoo-link/blob/46c7b814e055cf9675d58482d79f43d0bd2280da/examples/cloud_service_test.cpp).

The SDK already exposes substantial cloud implementation. The gap is supported account bootstrap plus Android transport/session behavior, rather than a claim that all app keys or cloud protocol code are missing. No official account login, Agora session or cloud MQTT session is attempted by v0.3.2.

Matrix's [privacy agreement](https://www.elegoo.com/pages/privacy-agreement) describes WAN discovery/relay, server processing and background notifications. Its [one-click remote printing announcement](https://www.elegoo.com/blogs/news/print-smarter-with-one-tap-elegoo-matrix-app-introduces-one-click-remote-printing) describes the Nexprint workflow. Keeping Matrix available preserves access to that ecosystem while we extend local management. Some history is also printer-reported and already readable locally; history as a whole should not be labeled cloud-exclusive.

## Development direction

Keep connection route (local/VPN), credential intent (LAN/PIN/cloud account), and individual firmware capabilities separate. LAN Only remains an optional full-control route. This experimental PIN route stays read-only until real coexistence is established. If it fails, implement legitimate cloud account sign-in and isolated read-only device/file access first, then evaluate live transport and camera with explicit collision handling. Do not extract tokens from Matrix, silently reinterpret a LAN code as a PIN, copy a live client's identity, or unbind the printer as a connection shortcut.
