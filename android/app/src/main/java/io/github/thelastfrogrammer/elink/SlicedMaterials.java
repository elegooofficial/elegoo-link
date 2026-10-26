package io.github.thelastfrogrammer.elink;

import java.util.*;

/** Independent comment-vector positions. These are not proven tool IDs or CANVAS tray IDs. */
public final class SlicedMaterials {
    public static final String TYPES = "filament_type", COLORS = "filament_colour", LENGTHS = "filament used [mm]", MASSES = "filament used [g]";
    public static final class Entry {
        public final int index; public final String type, color; public final Double lengthMm, massG;
        private Entry(int index, String type, String color, Double lengthMm, Double massG) { this.index = index; this.type = type; this.color = color; this.lengthMm = lengthMm; this.massG = massG; }
        public String text() { return "Comment index " + index + ": " + (type == null ? "type not reported" : type) + (color == null ? "" : " · " + color)
            + (lengthMm == null ? "" : String.format(Locale.ROOT,"\nEstimated length: %.2f mm",lengthMm)) + (massG == null ? "" : String.format(Locale.ROOT," · %.2f g",massG)); }
    }
    public final List<Entry> entries; public final List<String> warnings;
    private SlicedMaterials(List<Entry> entries, List<String> warnings) { this.entries = Collections.unmodifiableList(entries); this.warnings = Collections.unmodifiableList(warnings); }
    public static SlicedMaterials from(Map<String,String> raw, boolean incomplete) {
        List<String> warnings = new ArrayList<>(); if (incomplete) warnings.add("Some source lines/values were skipped; material evidence may be incomplete.");
        String[] types = vector(raw.get(TYPES), ";", warnings), colors = vector(raw.get(COLORS), ";", warnings), lengths = vector(raw.get(LENGTHS), ",", warnings), masses = vector(raw.get(MASSES), ",", warnings);
        Set<Integer> sizes = new HashSet<>(); for (String[] values : new String[][] {types,colors,lengths,masses}) if (values.length > 0) sizes.add(values.length);
        if (sizes.size() > 1) warnings.add("Comment vectors have different lengths; their alignment is unverified.");
        int size = Math.min(8, Math.max(Math.max(types.length,colors.length), Math.max(lengths.length,masses.length))); List<Entry> entries = new ArrayList<>();
        if (sizes.stream().anyMatch(n -> n > 8)) warnings.add("Only the first eight comment positions are shown.");
        for (int i = 0; i < size; i++) {
            String type = at(types,i), color = at(colors,i); if (type != null && type.length() > 48) { type = null; add(warnings,"Invalid material text omitted."); }
            if (color != null && !color.matches("#[0-9A-Fa-f]{6}")) { color = null; add(warnings,"Unsupported color value omitted; only #RRGGBB is interpreted."); }
            Double length = number(at(lengths,i), warnings), mass = number(at(masses,i), warnings);
            if (length != null && mass != null && (length > 0) != (mass > 0)) add(warnings,"Length/mass usage indicators disagree; verify the sliced file.");
            entries.add(new Entry(i,type,color,length,mass));
        }
        return new SlicedMaterials(entries,warnings);
    }
    public String text() {
        StringBuilder text = new StringBuilder("Sliced material evidence");
        if (entries.isEmpty()) text.append("\nNo supported material vectors found.");
        for (Entry entry : entries) text.append("\n\n").append(entry.text());
        for (String warning : warnings) text.append("\n").append(warning);
        return text.append("\n\nComment indices are source-array positions, not confirmed tool or tray IDs. Values are slicer estimates/configuration; no CANVAS mapping is applied.").toString();
    }
    private static void add(List<String> warnings,String text) { if (!warnings.contains(text)) warnings.add(text); }
    private static String[] vector(String raw,String separator,List<String> warnings) {
        if (raw == null) return new String[0];
        if (raw.length() > 2048) { add(warnings,"Oversized material vector omitted."); return new String[0]; }
        String[] values = raw.split(separator,-1); if (values.length > 32) { add(warnings,"Material vector exceeds 32 positions and was omitted."); return new String[0]; }
        for (int i = 0; i < values.length; i++) { String value = values[i].trim(); if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) value = value.substring(1,value.length()-1); if (value.contains("\"")) { add(warnings,"Ambiguous quoted material vector omitted."); return new String[0]; } values[i] = value; }
        return values;
    }
    private static String at(String[] values,int index) { return index >= values.length || values[index].isEmpty() ? null : values[index]; }
    private static Double number(String value,List<String> warnings) {
        if (value == null) return null;
        try { double number = Double.parseDouble(value); if (Double.isFinite(number) && number >= 0 && number <= 1e12) return number; } catch (NumberFormatException ignored) { }
        add(warnings,"Invalid usage value omitted; it was not treated as zero."); return null;
    }
}
