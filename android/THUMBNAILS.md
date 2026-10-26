# Embedded previews and material evidence

v0.3.4 extends the Files workspace. Select a sliced plain-text `.gcode` file on your phone, or download a printer file using the existing LAN/HTTP path. The preview and material analysis run offline on that completed phone copy, including while disconnected or using the read-only PIN probe. No new printer request or image URL is used.

## Embedded preview

Supported slicer comment blocks use `thumbnail`, `thumbnail_PNG` or `thumbnail_JPG`, a width×height declaration, base64 character count, wrapped base64 comment lines and matching end marker. The declaration counts encoded characters rather than decoded image bytes. PNG and JPEG are supported; QOI, proprietary printer formats, binary G-code and 3MF previews are not included.

The parser evaluates at most 16 candidate blocks and retains the largest valid bounded image. Each candidate is limited to 256 KiB of compressed data, 1024 pixels per axis and 1,048,576 pixels. Encoded count must match exactly. The PNG validator checks chunk structure and CRCs; the JPEG validator checks image markers and dimensions. Both require agreement with the slicer's declared dimensions. Android checks native decoded dimensions again on the file worker before allocating display pixels, sampling the longest side to at most 512 pixels.

An invalid, corrupt, overlong, unsupported or incomplete block is skipped. Missing previews do not block text inspection, upload or document export. If a structurally accepted JPEG/PNG still cannot be decoded by Android, the app shows that failure and retains the report. The embedded image is a slicer-provided picture; it is not a reconstructed toolpath, a live camera image or proof of print compatibility.

The source audit and pinned Elegoo/Orca references are in [PROTOCOL.md](PROTOCOL.md). Verification used Elegoo's full published sample without shipping that file or its image. Tests generate their own small PNG/JPEG fixtures. The factory sample was generated with an older slicer and reports a Centauri Carbon model, so passing it establishes format support rather than support for all CC2 slicing presets.

## Material details

The Material details dialog shows configured material text, interpreted color swatches and reported length/mass for up to eight comment-array positions. The shareable report includes the same evidence. Index 0 means the first position in the source vector; it does not automatically mean T0 or a particular CANVAS tray.

Material types and colors use semicolon-separated config vectors. Usage mm/g uses comma-separated numerical vectors. Only six-digit #RRGGBB colors are interpreted. Simple quoted values are accepted; ambiguous quoting, unsupported colors and oversized vectors are omitted with a notice. Raw vectors are captured separately from the short metadata presentation, so a shortened display string cannot silently become a partial parsed vector.

Absent or invalid usage remains unknown. Zero is preserved as a reported zero estimate. Negative, NaN, infinite, malformed or excessive values are not reinterpreted as zero. Different vector lengths are flagged because alignment is unverified. Contradictory zero/nonzero indicators between length and mass are flagged for review. Configured materials can include unused positions, while macros and firmware-specific selectors can evade explicit `T` observations.

There are no automatic CANVAS assignments, complete tool-count guesses or print-setting changes in this release. The existing manual print setup remains separate. Before offering mapping suggestions, we still need evidence linking the sliced array indices, actual used tools and CC2 firmware's mapping parameters for representative multi-material files.

## Acceptance on the phone

- Import a file with a supported thumbnail and compare the image with the slicer. Try no image, multiple sizes, a corrupt block and a file using an unsupported format; the text workspace should remain usable.
- Check both light and dark themes, rotation, opening a document picker and returning from another app. Clearing/replacing the cache copy should remove the previous image and material evidence.
- Compare single- and multi-material rows with the file's comments. Check empty positions, zero usage, unequal vector lengths and special `T` values. Nothing should be assigned to a tray automatically.
- Native BitmapFactory decoding, layout/accessibility and larger user files still need device testing. The host sample and JVM tests do not exercise an Android graphics pipeline.

The previous download, route, authentication and Matrix coexistence limits remain in [FILE_WORKSPACE.md](FILE_WORKSPACE.md), [REMOTE_ACCESS.md](REMOTE_ACCESS.md) and [MATRIX_COEXISTENCE.md](MATRIX_COEXISTENCE.md).
