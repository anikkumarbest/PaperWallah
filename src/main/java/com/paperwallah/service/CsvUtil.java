package com.paperwallah.service;
import java.util.*;
/** UTF-8 CSV helpers that handle commas, quotes and line breaks inside cells. */
public final class CsvUtil {
    private CsvUtil() {}
    public static String cell(String s) { return "\"" + (s == null ? "" : s).replace("\"", "\"\"") + "\""; }
    public static List<List<String>> parse(String t) {
        List<List<String>> rows = new ArrayList<>(); List<String> row = new ArrayList<>(); StringBuilder sb = new StringBuilder(); boolean q = false, any = false;
        if (t.startsWith("\uFEFF")) t = t.substring(1);
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (q) { if (c == '"') { if (i + 1 < t.length() && t.charAt(i + 1) == '"') { sb.append('"'); i++; } else q = false; } else sb.append(c); }
            else if (c == '"') { q = true; any = true; }
            else if (c == ',') { row.add(sb.toString()); sb.setLength(0); any = true; }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < t.length() && t.charAt(i + 1) == '\n') i++;
                if (any || sb.length() > 0) { row.add(sb.toString()); rows.add(row); }
                row = new ArrayList<>(); sb.setLength(0); any = false;
            } else { sb.append(c); any = true; }
        }
        if (any || sb.length() > 0) { row.add(sb.toString()); rows.add(row); }
        return rows;
    }
}
