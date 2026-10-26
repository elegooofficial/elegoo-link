# Android client progress

v0.3.5 source provides the CC2 client with explicit local and user-managed home-VPN routes plus a read-only cloud-mode local PIN probe, offline G-code inspection, embedded PNG/JPEG previews, structured material evidence and internal/USB file downloads/export. Implemented means present in code and covered where feasible by automated checks; physical CC2/S24+ validation remains pending.

| Area | Implemented | Remaining |
| --- | --- | --- |
| Interface | Monitor/Files/Camera/Settings; System/Light/Dark | On-device layout, accessibility and landscape review |
| Connection | UDP picker/manual identity; per-IP encrypted profiles; foreground monitoring; bounded reconnect; layered diagnosis including registration QoS 1, SUBACK/PUBACK and redacted reply counters | Successful physical authorization/registration on the user's CC2; device lifecycle/Keystore migration validation |
| Matrix preservation | Explicit current-PIN experiment, memory-only secret, no HTTP token use, no writes/uploads/retries, no account-binding changes, coexistence test guide | Hardware proof of simultaneous Matrix use; legitimate cloud account bootstrap and server-issued identity/collision handling if local PIN path fails |
| Files/printing | Internal/USB pagination, metadata, upload/cancel, download/cancel/export, offline comment/T-selection/SHA-256 inspection, embedded previews and material/color/usage evidence, delete, start with checks/plate/timelapse/tool mappings, pause/resume/stop | Verified complete sliced-tool metadata for mapping, file export/recovery transport when HTTP is unavailable |
| Printer settings | Light, bounded idle heater targets, fan channels, printing speed modes, automatic refill | Filament loading/unloading, calibration, homing/movement only after complete behavior verification |
| CANVAS | Materials/colors/active tray, fresh reported tray mapping | Editing filament profiles and persistent usage tracking |
| Camera | Local MJPEG, larger view, document-picker snapshots, independent lifecycle | Timelapse export, stream capability variations, background playback policy |
| Awareness | Live service notification, deduplicated completion/new fault alerts, history, storage usage | Reconciliation of missed completion while disconnected; device battery/notification behavior |
| Remote/model expansion | CC2 local Wi-Fi/Ethernet; explicit VPN route, route-loss checks, remote diagnostics and Pi setup guide | Physical Pi/VPN/S24+ acceptance; official cloud auth; other model adapters; simultaneous printer dashboard |

No automatic replay of any changing command or upload is planned. An acknowledgement and a fresh resulting printer state remain distinct. Printer start/delete/temperature require fresh appropriate state; explicit tray mappings require fresh CANVAS data and an existing connected tray with material. Axis movement remains blocked.

Phone-side slicing is a separate project and outside the present management-client scope.

Matrix preservation now takes priority over assuming LAN Only is required for every route. The local PIN branch is a source-backed candidate, not firmware proof; full cloud login/live transport is a separate implementation. See [MATRIX_COEXISTENCE.md](MATRIX_COEXISTENCE.md) for pinned source findings and the acceptance gate. No controls will be enabled for the experimental PIN route until coexistence and authorization semantics are established on hardware.

Next file-workspace priority: verify complete sliced tool-use metadata and index/firmware mapping semantics before offering a reviewed mapping suggestion. Offline inspection is observational and does not automatically infer a complete count from T commands or config vectors. Mechanical filament/calibration/movement features remain behind a complete protocol/behavior audit; commented SDK method IDs alone are insufficient. [FILE_WORKSPACE.md](FILE_WORKSPACE.md) records the current scope and limits.
