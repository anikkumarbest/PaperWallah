package com.paperwallah.service;
import com.paperwallah.model.Question;
import java.util.*;
/** Small in-memory TF-IDF index with cosine similarity. Fast enough for thousands of questions; no library needed. */
public final class TextIndex {
    private final Map<Integer, Map<String, Double>> vectors = new HashMap<>(); private final Map<String, Double> idf = new HashMap<>(); private final int n;
    public TextIndex(Collection<Question> qs) {
        n = qs.size(); Map<Integer, Map<String, Integer>> tf = new HashMap<>(); Map<String, Integer> df = new HashMap<>();
        for (Question q : qs) {
            Map<String, Integer> c = new HashMap<>(); for (String t : TextUtil.terms(q.getQuestionContent() + " " + q.getAnswerContent())) c.merge(t, 1, Integer::sum);
            tf.put(q.getQuestionId(), c); for (String t : c.keySet()) df.merge(t, 1, Integer::sum);
        }
        df.forEach((t, d) -> idf.put(t, Math.log((1.0 + n) / (1.0 + d)) + 1.0));
        tf.forEach((id, c) -> vectors.put(id, weigh(c)));
    }
    private Map<String, Double> weigh(Map<String, Integer> counts) {
        Map<String, Double> v = new HashMap<>(); double norm = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) { double w = (1 + Math.log(e.getValue())) * idf.getOrDefault(e.getKey(), Math.log(1.0 + n) + 1.0); v.put(e.getKey(), w); norm += w * w; }
        norm = Math.sqrt(norm); if (norm > 0) for (Map.Entry<String, Double> e : v.entrySet()) e.setValue(e.getValue() / norm);
        return v;
    }
    public Map<String, Double> vector(int id) { return vectors.getOrDefault(id, Map.of()); }
    public Map<String, Double> vectorOf(String text) { Map<String, Integer> c = new HashMap<>(); for (String t : TextUtil.terms(text)) c.merge(t, 1, Integer::sum); return weigh(c); }
    /** Vectors are already length-normalised, so cosine similarity is the dot product (0 to 1). */
    public static double cosine(Map<String, Double> a, Map<String, Double> b) {
        if (a.size() > b.size()) { Map<String, Double> t = a; a = b; b = t; }
        double d = 0; for (Map.Entry<String, Double> e : a.entrySet()) { Double o = b.get(e.getKey()); if (o != null) d += e.getValue() * o; }
        return Math.min(1.0, d);
    }
    public static List<String> topTerms(Map<String, Double> v, int k) {
        List<Map.Entry<String, Double>> es = new ArrayList<>(v.entrySet()); es.sort((x, y) -> { int c = Double.compare(y.getValue(), x.getValue()); return c != 0 ? c : x.getKey().compareTo(y.getKey()); });
        List<String> out = new ArrayList<>(); for (int i = 0; i < Math.min(k, es.size()); i++) out.add(es.get(i).getKey()); return out;
    }
}
