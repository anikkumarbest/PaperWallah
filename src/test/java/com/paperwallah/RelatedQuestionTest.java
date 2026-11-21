package com.paperwallah;
import com.paperwallah.config.DatabaseManager;
import com.paperwallah.model.*;
import com.paperwallah.service.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class RelatedQuestionTest {
    @TempDir Path tmp; DatabaseManager db; TaggingService ts; RelatedQuestionService rs; List<Question> bank = new ArrayList<>(); QuestionService qs;
    @BeforeEach void open() throws Exception {
        db = new DatabaseManager(tmp.resolve("t.db")); db.open(); ts = new TaggingService(db); rs = new RelatedQuestionService(db, ts); qs = new QuestionService(db);
        int stack = ts.createTag("Stack", null), tree = ts.createTag("Trees", null);
        ts.addRule(stack, "stack", MatchMode.EXACT_WORD, 0); ts.addRule(stack, "lifo", MatchMode.EXACT_WORD, 0); ts.addRule(tree, "tree", MatchMode.EXACT_WORD, 0);
        add(1, "Explain how a stack works with push and pop operations", "LIFO order", "Data Structures", 3, Difficulty.MEDIUM);
        add(2, "Describe stack overflow and underflow conditions", "Pushing onto a full stack", "Data Structures", 3, Difficulty.HARD);
        add(3, "Why is a stack called a LIFO structure", "Last in first out", "Data Structures", 2, Difficulty.EASY);
        add(4, "What is a binary tree traversal", "Inorder preorder postorder", "Data Structures", 3, Difficulty.MEDIUM);
        add(5, "Explain thread synchronization in Java", "Monitors and locks", "Multithreading", 3, Difficulty.MEDIUM);
        qs.sync(bank); ts.retag(bank, null); rs.invalidate();
    }
    @AfterEach void close() { db.close(); }
    private void add(int id, String q, String a, String topic, int marks, Difficulty d) { bank.add(new Question(id, q, a, marks, d, topic)); }
    private RelatedResult suggest(int id, RelatedMode m) throws Exception { return rs.suggest(rs.forQuestion(bank.get(id - 1)), m, Set.of(), 10); }
    private List<Integer> ids(RelatedResult r) { return r.related().stream().map(s -> s.question().getQuestionId()).toList(); }

    @Test void defaultWeightsTotalOneHundredAndPersist() throws Exception {
        RelatedWeights d = RelatedWeights.defaults(); assertEquals(100, d.tags() + d.topic() + d.keywords() + d.text() + d.bloom());
        rs.setWeights(new RelatedWeights(40, 10, 10, 10, 0, 2, 20, 5)); assertEquals(40, rs.weights().tags()); assertEquals(5, rs.weights().minScore());
    }
    @Test void sharedTagsRankAboveUnrelatedAndSelfIsExcluded() throws Exception {
        List<Integer> r = ids(suggest(1, RelatedMode.SAME_CONCEPT));
        assertFalse(r.contains(1)); assertTrue(r.indexOf(2) < r.indexOf(4) || !r.contains(4)); assertFalse(r.contains(5));
    }
    @Test void scoreIsExplainedAndWithinZeroToHundred() throws Exception {
        RelatedSuggestion s = suggest(1, RelatedMode.SAME_CONCEPT).related().get(0);
        assertTrue(s.score() > 0 && s.score() <= 100); assertTrue(s.sharedTags().contains("Stack")); assertTrue(String.join(" ", s.reasons()).contains("Stack")); assertTrue(s.sameTopic());
    }
    @Test void weightsChangeTheScore() throws Exception {
        double before = suggest(1, RelatedMode.SAME_CONCEPT).related().stream().filter(s -> s.question().getQuestionId() == 2).findFirst().get().score();
        rs.setWeights(new RelatedWeights(0, 20, 15, 10, 5, 3, 30, 0));
        double after = suggest(1, RelatedMode.SAME_CONCEPT).related().stream().filter(s -> s.question().getQuestionId() == 2).findFirst().get().score();
        assertTrue(after < before);
    }
    @Test void topicBonusIsApplied() throws Exception {
        rs.setWeights(new RelatedWeights(0, 20, 0, 0, 0, 0, 0, 0));
        assertEquals(20.0, suggest(1, RelatedMode.SAME_CONCEPT).related().stream().filter(s -> s.question().getQuestionId() == 4).findFirst().get().score(), 0.001);
    }
    @Test void exactDuplicatesAreFlaggedNotRecommended() throws Exception {
        bank.add(new Question(6, "  EXPLAIN how a stack works with push and pop operations ", "x", 3, Difficulty.MEDIUM, "Data Structures")); qs.sync(bank); rs.invalidate();
        RelatedResult r = suggest(1, RelatedMode.SAME_CONCEPT);
        assertFalse(ids(r).contains(6)); assertEquals(List.of(6), r.duplicates().stream().map(s -> s.question().getQuestionId()).toList());
    }
    @Test void easierAndHarderModesFilterByDifficulty() throws Exception {
        assertEquals(List.of(3), ids(suggest(1, RelatedMode.EASIER))); assertEquals(List.of(2), ids(suggest(1, RelatedMode.HARDER)));
    }
    @Test void complementaryMeansSameTopicDifferentTags() throws Exception { assertEquals(List.of(4), ids(suggest(1, RelatedMode.COMPLEMENTARY))); }
    @Test void alreadyInPaperIsExcluded() throws Exception { assertFalse(ids(rs.suggest(rs.forQuestion(bank.get(0)), RelatedMode.SAME_CONCEPT, Set.of(2), 10)).contains(2)); }
    @Test void feedbackHidesOrLowersSuggestions() throws Exception {
        double base = suggest(1, RelatedMode.SAME_CONCEPT).related().stream().filter(s -> s.question().getQuestionId() == 2).findFirst().get().score();
        rs.feedback(1, 2, "UNRELATED");
        double lowered = suggest(1, RelatedMode.SAME_CONCEPT).related().stream().filter(s -> s.question().getQuestionId() == 2).findFirst().map(RelatedSuggestion::score).orElse(0.0);
        assertTrue(lowered < base);
        rs.feedback(1, 2, "EXCLUDE"); assertFalse(ids(suggest(1, RelatedMode.SAME_CONCEPT)).contains(2));
        assertFalse(ids(suggest(2, RelatedMode.SAME_CONCEPT)).contains(1)); // works in both directions
    }
    @Test void probeForUnsavedTextUsesAutomaticTags() throws Exception {
        RelatedProbe p = rs.forText(0, "What is a stack", "LIFO", "Data Structures", 2, Difficulty.EASY, Set.of("Manual"), Set.of());
        assertTrue(p.tags().containsAll(Set.of("Stack", "Manual"))); assertFalse(ids(rs.suggest(p, RelatedMode.SAME_CONCEPT, Set.of(), 5)).isEmpty());
    }
    @Test void textIndexCosineAndNormalisation() {
        TextIndex idx = new TextIndex(bank);
        assertEquals(1.0, TextIndex.cosine(idx.vectorOf("stack push pop"), idx.vectorOf("stack push pop")), 0.001);
        assertEquals(0.0, TextIndex.cosine(idx.vectorOf("zzz"), idx.vectorOf("stack")), 0.001);
        assertEquals(TextUtil.terms("queues"), TextUtil.terms("queue")); assertEquals(TextUtil.terms("classes"), TextUtil.terms("class"));
        assertTrue(TextUtil.terms("Explain the stack").equals(List.of("stack")));
    }
}
