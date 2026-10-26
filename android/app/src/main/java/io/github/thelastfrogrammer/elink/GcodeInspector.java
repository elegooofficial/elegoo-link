package io.github.thelastfrogrammer.elink;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/** Bounded, offline text inspection. Does not interpret motion or establish print compatibility. */
public final class GcodeInspector {
    public static final long MAX_BYTES = 512L * 1024 * 1024;
    private static final int MAX_LINE = 16384;
    private static final Pattern TOOL = Pattern.compile("^(?:N[0-9]+\\s*)?T([0-9]+)(?:\\s|\\*|$).*", Pattern.CASE_INSENSITIVE);
    private static final Map<String, String> FIELDS = new LinkedHashMap<>();
    static {
        FIELDS.put("estimated printing time (normal mode)", "Slicer estimated time");
        FIELDS.put("filament used [mm]", "Filament lengths [mm]");
        FIELDS.put("filament used [cm3]", "Filament volumes [cm³]");
        FIELDS.put("filament used [g]", "Filament masses [g]");
        FIELDS.put("total filament used [g]", "Total filament mass [g]");
        FIELDS.put("total layer number", "Reported layer count");
        FIELDS.put("layer_count", "Reported layer count");
        FIELDS.put("layer height", "Layer height [mm]");
        FIELDS.put("layer_height", "Layer height [mm]");
        FIELDS.put("filament_type", "Configured filament types");
        FIELDS.put("filament_colour", "Configured filament colors");
        FIELDS.put("printer_model", "Configured printer model");
        FIELDS.put("nozzle_diameter", "Configured nozzle diameter [mm]");
    }
    public static final class Report {
        public final long bytes, lines;
        public final String sha256;
        public final boolean binary, longLines;
        public final SortedSet<Integer> tools;
        public final Map<String, String> metadata;
        public final EmbeddedThumbnail thumbnail;
        public final String thumbnailNote;
        public final SlicedMaterials materials;
        private Report(long bytes, long lines, String hash, boolean binary, boolean longLines, SortedSet<Integer> tools, Map<String, String> metadata, EmbeddedThumbnail thumbnail, String thumbnailNote, SlicedMaterials materials) {
            this.bytes = bytes; this.lines = lines; sha256 = hash; this.binary = binary; this.longLines = longLines;
            this.tools = Collections.unmodifiableSortedSet(new TreeSet<>(tools));
            this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
            this.thumbnail = thumbnail; this.thumbnailNote = thumbnailNote; this.materials = materials;
        }
        public String text() {
            StringBuilder s = new StringBuilder("Offline G-code inspection\n" + FeatureData.size(bytes) + " · " + lines + " line(s)\nSHA-256: " + sha256);
            if (binary) s.append("\nBinary/non-text bytes detected. Text metadata and tool selections are unavailable; use plain-text G-code.");
            else {
                s.append("\n").append(thumbnailNote);
                for (Map.Entry<String, String> field : metadata.entrySet()) s.append('\n').append(field.getKey()).append(": ").append(field.getValue());
                s.append("\nExplicit T selections seen (up to 32 distinct): ").append(tools.isEmpty() ? "none" : tools.toString());
                if (metadata.isEmpty()) s.append("\nNo supported slicer metadata comments found.");
                if (longLines) s.append("\nOverlong lines were skipped during text analysis.");
                if (tools.stream().anyMatch(t -> t > 7)) s.append("\nSelections outside T0–T7 were observed; these may be firmware-specific or sentinel commands.");
            }
            if (!binary) s.append("\n\n").append(materials.text());
            return s.append("\n\nSlicer comments are estimates/configuration, not measured usage. T selections are observations, not an exhaustive tool count: macros or firmware-specific commands may select tools. No tray mappings or print settings are changed. This report does not simulate motion or certify printer/material compatibility.").toString();
        }
    }
    public static Report inspect(File file) throws Exception {
        if (file.length() == 0 || file.length() > MAX_BYTES) throw new IOException("Choose a nonempty file up to 512 MiB.");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        Map<String, String> metadata = new LinkedHashMap<>(), rawMaterials = new LinkedHashMap<>(); EmbeddedThumbnail.Parser thumbnails = new EmbeddedThumbnail.Parser(); SortedSet<Integer> tools = new TreeSet<>();
        byte[] buffer = new byte[65536]; ByteArrayOutputStream line = new ByteArrayOutputStream();
        long bytes = 0, lines = 0; boolean binary = false, longLines = false, skip = false, pending = false;
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Inspection cancelled");
                bytes += count; if (bytes > MAX_BYTES) throw new IOException("File exceeds inspection limit"); digest.update(buffer, 0, count);
                for (int i = 0; i < count; i++) {
                    int c = buffer[i] & 255;
                    if (c < 32 && c != 9 && c != 10 && c != 13) binary = true;
                    if (c == 10) {
                        lines++; if (!skip && !binary) accept(new String(line.toByteArray(), StandardCharsets.UTF_8), metadata, tools, rawMaterials, thumbnails);
                        line.reset(); skip = false; pending = false;
                    } else {
                        pending = true;
                        if (!skip) { if (line.size() >= MAX_LINE) { skip = true; longLines = true; thumbnails.overlongLine(); line.reset(); } else line.write(c); }
                    }
                }
            }
        }
        if (pending) { lines++; if (!skip && !binary) accept(new String(line.toByteArray(), StandardCharsets.UTF_8), metadata, tools, rawMaterials, thumbnails); }
        thumbnails.finish();
        if (binary) { metadata.clear(); tools.clear(); rawMaterials.clear(); }
        StringBuilder hash = new StringBuilder(); for (byte b : digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", b & 255));
        return new Report(bytes, lines, hash.toString(), binary, longLines, tools, metadata, binary ? null : thumbnails.best(), binary ? "Text analysis unavailable." : thumbnails.note(), SlicedMaterials.from(rawMaterials, longLines || binary));
    }
    private static void accept(String supplied, Map<String, String> metadata, SortedSet<Integer> tools, Map<String,String> rawMaterials, EmbeddedThumbnail.Parser thumbnails) {
        String line = supplied.trim(); if (line.startsWith("\uFEFF")) line = line.substring(1).trim();
        if (thumbnails.accept(line)) return;
        if (line.startsWith(";")) {
            String comment = line.substring(1).trim();
            if (comment.toLowerCase(Locale.ROOT).startsWith("generated by ")) metadata.put("Generator", clean(comment.substring(13)));
            int separator = comment.indexOf('='); if (separator < 0) separator = comment.indexOf(':');
            if (separator > 0) { String key = comment.substring(0, separator).trim().toLowerCase(Locale.ROOT), value = comment.substring(separator + 1).trim(); String label = FIELDS.get(key); if (label != null) metadata.put(label, clean(value));
                if (key.equals(SlicedMaterials.TYPES) || key.equals(SlicedMaterials.COLORS) || key.equals(SlicedMaterials.LENGTHS) || key.equals(SlicedMaterials.MASSES)) rawMaterials.put(key, value);
            }
            return;
        }
        int semicolon = line.indexOf(';'); if (semicolon >= 0) line = line.substring(0, semicolon).trim();
        // Ignore parenthetical text: a T command inside a comment is not an observed selection.
        if (line.indexOf('(') >= 0) line = line.replaceAll("\\([^)]*\\)", "").trim();
        if (line.isEmpty() || "TtNn".indexOf(line.charAt(0)) < 0) return;
        Matcher match = TOOL.matcher(line);
        if (match.matches()) try { int value = Integer.parseInt(match.group(1)); if (value <= 65535 && tools.size() < 32) tools.add(value); } catch (NumberFormatException ignored) { }
    }
    private static String clean(String value) { String s = value.trim().replaceAll("[\\p{Cntrl}]", " "); return s.length() > 240 ? s.substring(0, 240) + "…" : s; }
}
