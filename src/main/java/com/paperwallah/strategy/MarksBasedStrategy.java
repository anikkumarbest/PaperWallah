package com.paperwallah.strategy;
import com.paperwallah.model.*;
import java.util.*;
import java.util.stream.Collectors;
public class MarksBasedStrategy implements PaperGenerationStrategy {
    private final int total; private final int[] allowed; private final Set<String> topics; // null or empty = all topics
    public MarksBasedStrategy(int total, int[] allowed, Set<String> topics) { this.total = total; this.allowed = allowed; this.topics = topics; }
    public String modeName() { return "Marks-based"; }

    /** All non-negative count combinations whose weighted sum equals total. */
    public static List<int[]> combinations(int total, int[] marks) {
        List<int[]> out = new ArrayList<>(); build(0, total, marks, new int[marks.length], out); return out;
    }
    private static void build(int i, int rem, int[] m, int[] c, List<int[]> out) {
        if (i == m.length) { if (rem == 0) out.add(c.clone()); return; }
        for (int k = 0; k * m[i] <= rem; k++) { c[i] = k; build(i + 1, rem - k * m[i], m, c, out); }
    }
    private static int spread(int[] c) { return Arrays.stream(c).max().getAsInt() - Arrays.stream(c).min().getAsInt(); }
    private static int sum(int[] c) { return Arrays.stream(c).sum(); }

    public List<PaperQuestion> generate(List<Question> bank) throws GenerationException {
        if (total <= 0 || allowed.length == 0) throw new GenerationException("Enter a positive total and at least one allowed mark value.");
        List<int[]> all = combinations(total, allowed);
        if (all.isEmpty()) throw new GenerationException("No exact combination of the allowed marks adds up to " + total + ".");
        all.sort(Comparator.comparingInt(MarksBasedStrategy::spread).thenComparingInt(MarksBasedStrategy::sum));
        List<Question> pool = bank.stream().filter(q -> topics == null || topics.isEmpty() || topics.contains(q.getTopic())).collect(Collectors.toList());
        int[] have = new int[allowed.length];
        for (int i = 0; i < allowed.length; i++) { final int m = allowed[i]; have[i] = (int) pool.stream().filter(q -> q.getMarks() == m).count(); }
        int[] pick = null;
        for (int[] c : all) { boolean ok = true; for (int i = 0; i < c.length; i++) if (c[i] > have[i]) ok = false; if (ok) { pick = c; break; } }
        if (pick == null) {
            int[] b = all.get(0); StringBuilder sb = new StringBuilder("Not enough questions. Best distribution needs: ");
            for (int i = 0; i < b.length; i++) sb.append(b[i]).append("x").append(allowed[i]).append("-mark (have ").append(have[i]).append(") ");
            throw new GenerationException(sb.toString().trim());
        }
        List<PaperQuestion> result = new ArrayList<>(); char sec = 'A';
        for (int i = 0; i < allowed.length; i++) {
            if (pick[i] == 0) continue; final int m = allowed[i];
            List<Question> p = pool.stream().filter(q -> q.getMarks() == m).collect(Collectors.toList());
            Collections.shuffle(p); String name = "Section " + (sec++);
            for (int k = 0; k < pick[i]; k++) result.add(new PaperQuestion(p.get(k), name));
        }
        return result;
    }
}
