package com.paperwallah.service;
import com.paperwallah.config.DatabaseManager;
import com.paperwallah.dao.*;
import com.paperwallah.model.*;
import java.sql.SQLException;
import java.util.*;
/**
 * Offline, explainable related-question scoring (default points out of 100):
 * shared tags 50 (Dice overlap) + same topic 20 + shared important keywords 15 + text similarity 10 (TF-IDF cosine) + same Bloom level 5.
 * The Bloom component is wired in but contributes 0 until Bloom metadata exists (Phase 4).
 * Penalties: very different marks, and questions the teacher marked unrelated. Duplicates and hidden questions are never recommended.
 */
public class RelatedQuestionService {
    private final DatabaseManager db; private final TaggingService tagging; private final QuestionDAO qd; private final QuestionTagDAO qtd; private final RelatedFeedbackDAO fd;
    private List<Question> cacheQs; private Map<Integer, Set<String>> cacheTags; private TextIndex cacheIdx;
    public RelatedQuestionService(DatabaseManager db, TaggingService tagging) { this.db = db; this.tagging = tagging; qd = new QuestionDAO(db); qtd = new QuestionTagDAO(db); fd = new RelatedFeedbackDAO(db); }
    /** Call whenever questions or tags change; the index is rebuilt on the next request. */
    public void invalidate() { cacheQs = null; cacheTags = null; cacheIdx = null; }
    private void ensure() throws SQLException { if (cacheQs == null) { cacheQs = qd.findAll(); cacheTags = qtd.allNames(); cacheIdx = new TextIndex(cacheQs); } }

    public RelatedWeights weights() throws SQLException {
        RelatedWeights d = RelatedWeights.defaults(); String s = db.setting("related_weights", "");
        try { String[] p = s.split(","); if (p.length == 8) return new RelatedWeights(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]), Double.parseDouble(p[4]), Double.parseDouble(p[5]), Double.parseDouble(p[6]), Double.parseDouble(p[7])); }
        catch (NumberFormatException e) { /* fall back to defaults */ }
        return d;
    }
    public void setWeights(RelatedWeights w) throws SQLException {
        db.setSetting("related_weights", String.join(",", String.valueOf(w.tags()), String.valueOf(w.topic()), String.valueOf(w.keywords()), String.valueOf(w.text()), String.valueOf(w.bloom()), String.valueOf(w.marksPenalty()), String.valueOf(w.unrelatedPenalty()), String.valueOf(w.minScore())));
    }
    public RelatedProbe forQuestion(Question q) throws SQLException {
        ensure(); return new RelatedProbe(q.getQuestionId(), q.getQuestionContent(), q.getAnswerContent(), q.getTopic(), q.getMarks(), q.getDifficulty(), cacheTags.getOrDefault(q.getQuestionId(), Set.of()));
    }
    /** For text that is not saved yet (editor) or lives in a paper: tags = manual tags + automatic matches minus exclusions. */
    public RelatedProbe forText(int id, String text, String answer, String topic, int marks, Difficulty d, Set<String> manual, Set<String> excluded) throws SQLException {
        Set<String> names = new LinkedHashSet<>(manual); Map<Integer, String> nm = tagging.tagNames();
        for (TagMatch m : tagging.analyze(text, answer == null ? "" : answer).values()) { String n = nm.get(m.tagId()); if (n != null && !excluded.contains(n)) names.add(n); }
        return new RelatedProbe(id, text, answer == null ? "" : answer, topic == null ? "" : topic, marks, d == null ? Difficulty.MEDIUM : d, names);
    }
    public void feedback(int qid, int other, String kind) throws SQLException { fd.set(qid, other, kind); }
    public void clearFeedback(int qid) throws SQLException { fd.clear(qid); }

    public RelatedResult suggest(RelatedProbe p, RelatedMode mode, Collection<Integer> exclude, int limit) throws SQLException {
        ensure(); RelatedWeights w = weights(); Map<Integer, String> fb = p.id() > 0 ? fd.forQuestion(p.id()) : Map.of();
        Map<String, Double> pv = cacheIdx.vectorOf(p.text() + " " + p.answer()); List<String> top = TextIndex.topTerms(pv, 8); String pn = ImportExportService.norm(p.text());
        List<RelatedSuggestion> out = new ArrayList<>(), dups = new ArrayList<>();
        for (Question c : cacheQs) {
            int id = c.getQuestionId(); if (id == p.id() || exclude.contains(id) || "EXCLUDE".equals(fb.get(id))) continue;
            Map<String, Double> cv = cacheIdx.vector(id); double sim = TextIndex.cosine(pv, cv); List<String> reasons = new ArrayList<>();
            if (ImportExportService.norm(c.getQuestionContent()).equals(pn) || sim >= 0.97) { dups.add(new RelatedSuggestion(c, 0, List.of(), false, sim, List.of(), List.of("Same or almost identical wording (" + Math.round(sim * 100) + "% similar)"))); continue; }
            Set<String> ct = cacheTags.getOrDefault(id, Set.of()); List<String> shared = new ArrayList<>(); for (String t : p.tags()) if (ct.contains(t)) shared.add(t); Collections.sort(shared);
            double tagPts = p.tags().isEmpty() && ct.isEmpty() ? 0 : w.tags() * 2.0 * shared.size() / (p.tags().size() + ct.size());
            boolean sameTopic = !p.topic().isBlank() && p.topic().equals(c.getTopic()); double topicPts = sameTopic ? w.topic() : 0;
            List<String> kw = new ArrayList<>(); for (String t : top) if (cv.containsKey(t)) kw.add(t);
            double kwPts = top.isEmpty() ? 0 : w.keywords() * kw.size() / top.size(), textPts = w.text() * sim, bloomPts = 0; // Bloom: Phase 4
            double pen = Math.abs(p.marks() - c.getMarks()) >= 3 ? w.marksPenalty() : 0; if ("UNRELATED".equals(fb.get(id))) pen += w.unrelatedPenalty();
            double score = Math.max(0, Math.min(100, tagPts + topicPts + kwPts + textPts + bloomPts - pen));
            if (!shared.isEmpty()) reasons.add("shares tags " + String.join(", ", shared) + " (+" + Math.round(tagPts) + ")");
            if (sameTopic) reasons.add("same topic (+" + Math.round(topicPts) + ")");
            if (!kw.isEmpty()) reasons.add("shared keywords " + String.join(", ", kw.subList(0, Math.min(4, kw.size()))) + " (+" + Math.round(kwPts) + ")");
            if (sim > 0.05) reasons.add("similar wording " + Math.round(sim * 100) + "% (+" + Math.round(textPts) + ")");
            if (pen > 0) reasons.add("penalty -" + Math.round(pen) + (fb.containsKey(id) ? " (marked unrelated)" : " (marks differ a lot)"));
            RelatedSuggestion s = new RelatedSuggestion(c, score, shared, sameTopic, sim, kw, reasons);
            boolean hasTags = p.tags().isEmpty() ? sameTopic : !shared.isEmpty(), linked = !shared.isEmpty() || sameTopic;
            boolean ok = switch (mode) {
                case SAME_CONCEPT -> linked;
                case ALTERNATIVE -> linked && sim < 0.6;
                case EASIER -> hasTags && c.getDifficulty().ordinal() < p.difficulty().ordinal();
                case HARDER -> hasTags && c.getDifficulty().ordinal() > p.difficulty().ordinal();
                case COMPLEMENTARY -> sameTopic && shared.isEmpty();
            };
            if (ok && score >= w.minScore()) out.add(s);
        }
        out.sort((a, b) -> { int c = Double.compare(b.score(), a.score()); return c != 0 ? c : Integer.compare(a.question().getQuestionId(), b.question().getQuestionId()); });
        return new RelatedResult(out.subList(0, Math.min(limit, out.size())), dups);
    }
}
