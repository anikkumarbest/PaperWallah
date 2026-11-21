package com.paperwallah.service;
import com.paperwallah.model.*;
import java.util.*;
/**
 * Deterministic, offline, word-boundary-aware matcher.
 * Rules are tried by priority (high first), then by number of words (longer phrase first), then phrase length.
 * Words used by a winning match cannot be reused, so "priority queue" does not also fire the "queue" rule.
 */
public final class TagMatcher {
    private TagMatcher() {}
    private static int words(TagRule r) { return r.normalized().split(" ").length; }
    public static List<TagMatch> match(String text, String where, List<TagRule> rules) {
        List<TextUtil.Token> tk = TextUtil.tokenize(text);
        if (tk.isEmpty() || rules.isEmpty()) return List.of();
        List<TagRule> rs = new ArrayList<>();
        for (TagRule r : rules) if (r.enabled() && !r.normalized().isEmpty()) rs.add(r);
        rs.sort((a, b) -> { int c = Integer.compare(b.priority(), a.priority()); if (c != 0) return c;
            c = Integer.compare(words(b), words(a)); if (c != 0) return c;
            c = Integer.compare(b.normalized().length(), a.normalized().length()); return c != 0 ? c : Integer.compare(a.id(), b.id()); });
        boolean[] used = new boolean[tk.size()]; List<TagMatch> out = new ArrayList<>();
        for (TagRule r : rs) {
            String[] p = r.normalized().split(" "); int k = p.length;
            for (int i = 0; i + k <= tk.size(); i++) {
                boolean ok = true;
                for (int j = 0; j < k; j++) {
                    String t = tk.get(i + j).text();
                    boolean hit = used[i + j] ? false : (r.mode() == MatchMode.PREFIX && j == k - 1) ? t.startsWith(p[j]) : t.equals(p[j]);
                    if (!hit) { ok = false; break; }
                }
                if (!ok) continue;
                for (int j = 0; j < k; j++) used[i + j] = true;
                int s = tk.get(i).start(), e = tk.get(i + k - 1).end();
                out.add(new TagMatch(r.tagId(), r.id(), text.substring(Math.min(s, text.length()), Math.min(e, text.length())), where, s, e,
                    r.mode() == MatchMode.PHRASE ? 1.0 : r.mode() == MatchMode.EXACT_WORD ? 0.9 : 0.7));
                i += k - 1;
            }
        }
        return out;
    }
}
