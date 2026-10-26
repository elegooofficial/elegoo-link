# G-code inspection and phone copies

v0.3.4 extends a file workspace under Files. Offline inspection does not need the printer, LAN Only, a PIN or a VPN. Downloading printer files does need a working LAN-authenticated printer session and HTTP port 80. Your earlier HTTP refusal therefore remains a separate obstacle to downloads, even if MQTT monitoring succeeds.

## Inspect a sliced file on your phone

Choose G-code in Files and select a nonempty plain-text `.gcode` document with a simple filename (letters, numbers, spaces, dash, underscore or dot; up to 120 characters). The app imports a bounded cache copy, then scans it off the UI thread. Maximum size is 512 MiB. It does not open or slice STL/3MF models or execute any file commands.

The report displays exact size, line count and SHA-256. Where present, it reads supported comment fields: generator, normal-mode estimated time, per-filament lengths/masses, total filament mass, layer count/height, configured material/color, printer model and nozzle diameter. Missing comments stay absent; the app does not invent time, usage, printer compatibility or nozzle settings. Values are read from the file and can be stale, inaccurate or inconsistent with the actual print setup.

Explicit `T` selections are shown separately. A file that selects T0 and T2 reports those values; it does not silently become a two-tool mapping. Macros, firmware-specific commands and sentinel selectors such as T255/T1000 make simple command counts incomplete. Configured materials may include unused slots. The app does not use these observations to automatically assign CANVAS trays, change a heater target or prefill print-start settings.

SHA-256 identifies the exact bytes, including line endings. Matching filenames alone do not establish matching content. A fingerprint helps compare exported copies, but it is not a signature, authentication credential or assurance that the G-code is safe for the printer. No reference checksum is available from the download endpoint in this implementation.

Text analysis bounds each line at 16 KiB, skipping longer lines and reporting that limitation. This includes large embedded data lines. Detected binary/control bytes disable text metadata/tool inferences. Only up to 32 distinct tool values are shown. Supported embedded PNG/JPEG thumbnails are extracted and displayed with strict bounds; unsupported image formats are skipped. Full G-code interpretation and motion preview are not included. See [THUMBNAILS.md](THUMBNAILS.md).

Material details shows up to eight source-array positions with configured material/color and estimated length/mass. Only #RRGGBB colors are interpreted. Unsupported, invalid or mismatched values remain explicit uncertainty; these positions are not confirmed tool or tray IDs. The report also includes this evidence.

Use Share inspection report to open Android's share chooser. The app does not automatically send the report anywhere. The report includes filename and supported printer/material configuration comments, so review it before sharing. It includes no app connection credentials.

## Download a file from the printer

1. Establish a working **LAN access code** session. Resolve MQTT authentication and registration first; HTTP must also be available. The read-only cloud-mode PIN probe does not download because its PIN is not an HTTP token.
2. Refresh Files, select internal storage or USB, and tap a listed `.gcode` file. Choose **Download to phone workspace**. A recent matching file list is required. The app exposes listed root-level filenames, not arbitrary remote paths or directories.
3. Follow download progress. If the server does not report size, progress remains indeterminate until completion. Use Cancel download to stop; a partial phone cache copy is removed. Printer files are unchanged by download/cancellation.
4. The completed file becomes the workspace's selected phone copy and is inspected. The prior completed cache copy is replaced only after the new copy is ready. Failed downloads leave the previous copy available.
5. Use **Save phone copy…** and choose a destination with Android's document picker. The app verifies that the selected fingerprint still matches after the picker returns. If it changed or disappeared, choose Save again. Share the saved file using your preferred Android file app if needed.

Downloads use the effective session's LAN credential, including the discovery-reported default when code protection is off. They use the chosen local or VPN route. Downloads and uploads are serialized; inspection, import and document export also prevent replacing a copy while it is in use. Session/VPN loss cancels the transfer rather than automatically replaying it. MQTT read queries can continue during a download.

The HTTP response must be 200, without redirection or compressed encoding. JSON/HTML/error documents are rejected. Streams are capped at 512 MiB; empty data or a mismatch with reported Content-Length fails and removes the partial cache copy. Unknown-length streams cannot independently prove absence of server-side truncation. A successful HTTP transfer is not proof of printer/model/material compatibility.

The storage URLs come from the [official CC2 HTTP transfer implementation](https://github.com/elegooofficial/elegoo-link/blob/46c7b814e055cf9675d58482d79f43d0bd2280da/src/lan/adapters/elegoo_fdm_cc2/elegoo_fdm_cc2_http_transfer.cpp), inspected at the SDK baseline used throughout this port. The Android query builder encodes the token and filename independently and uses the correct `&` separator between parameters. No new endpoint is guessed. Firmware acceptance is still unverified on the user's CC2.

## Copy lifetime and practical limits

One completed copy is held in app cache, with no persistent job library. Clearing it affects neither the original Android document nor the printer file. Android may clear cache or kill the process; the service removes its cache copy when destroyed and cleans stale app-created temporary copies on creation. Use Save phone copy for a retained document. Saved destination files are under your document provider's control. If export fails, a partial destination document may remain and may need removing before another attempt.

Native document-picker rotation/theme restoration, actual phone storage providers, large-file performance, HTTP firmware behavior and VPN interruption still need device testing. Automated tests cover the inspector and real download/session implementations with simulated transports; they do not substitute for those device checks.

The next file feature should verify complete sliced-tool metadata and its firmware mapping semantics before reviewed mapping suggestions. Filament loading/unloading, calibration and axis motion need complete behavior/parameter evidence; commented SDK method numbers alone are insufficient to ship mechanical controls. Matrix preservation continues through the separate [coexistence test](MATRIX_COEXISTENCE.md).
