package com.paperwallah;
import com.paperwallah.config.DatabaseManager;
import com.paperwallah.model.*;
import com.paperwallah.service.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class TaggingTest {
    @TempDir Path tmp; DatabaseManager db; TaggingService ts; QuestionService qs; List<Question> bank = new ArrayList<>();
    @BeforeEach void open() throws Exception { db = new DatabaseManager(tmp.resolve("t.db")); db.open(); ts = new TaggingService(db); qs = new QuestionService(db); }
    @AfterEach void close() { db.close(); }
    private static TagRule rule(int id, String phrase, int tag, MatchMode m, boolean on, int pr) { return new TagRule(id, phrase, TextUtil.normalize(phrase), tag, m, on, pr); }
    private static Set<Integer> tagIds(String text, TagRule... rs) { return TagMatcher.match(text, "question", List.of(rs)).stream().map(TagMatch::tagId).collect(Collectors.toSet()); }
    private Question save(int id, String q, String a) throws Exception { Question x = new Question(id, q, a, 3, Difficulty.EASY, "T"); bank.add(x); qs.sync(bank); return x; }
    private Set<String> names(int qid, String source) throws Exception { return ts.tagsOf(qid).stream().filter(t -> t.source().equals(source)).map(QuestionTagInfo::name).collect(Collectors.toSet()); }

    @Test void wholeWordsOnly() {
        TagRule cls = rule(1, "class", 1, MatchMode.EXACT_WORD, true, 0), in = rule(2, "int", 2, MatchMode.EXACT_WORD, true, 0);
        assertTrue(tagIds("Classification and print statements", cls, in).isEmpty());
        assertEquals(Set.of(1, 2), tagIds("Define a class and an int.", cls, in));
    }
    @Test void phraseCaseAndPunctuation() {
        assertEquals(Set.of(1), tagIds("Explain METHOD-overriding, please!", rule(1, "method overriding", 1, MatchMode.PHRASE, true, 0)));
        assertTrue(tagIds("overriding method", rule(1, "method overriding", 1, MatchMode.PHRASE, true, 0)).isEmpty());
    }
    @Test void prefixMode() { assertEquals(Set.of(1), tagIds("It shows polymorphism.", rule(1, "polymorph", 1, MatchMode.PREFIX, true, 0))); }
    @Test void disabledRuleDoesNotMatch() { assertTrue(tagIds("queue", rule(1, "queue", 1, MatchMode.EXACT_WORD, false, 0)).isEmpty()); }
    @Test void longerPhraseThenPriorityResolveConflicts() {
        TagRule q = rule(1, "queue", 1, MatchMode.EXACT_WORD, true, 0), pq = rule(2, "priority queue", 2, MatchMode.PHRASE, true, 0);
        assertEquals(Set.of(2), tagIds("A priority queue is used.", q, pq));
        assertEquals(Set.of(1), tagIds("A priority queue is used.", rule(1, "queue", 1, MatchMode.EXACT_WORD, true, 10), pq));
    }
    @Test void questionAndAnswerAreBothAnalysed() throws Exception {
        int t = ts.createTag("Inheritance", "OOP"); ts.addRule(t, "extends", MatchMode.EXACT_WORD, 0);
        assertEquals(1, ts.analyze("Define it.", "A class extends another.").size());
        assertEquals("answer", ts.analyze("Define it.", "A class extends another.").get(t).where());
    }
    @Test void manualTagsAndExclusionsSurviveRetagging() throws Exception {
        int stack = ts.createTag("Stack", null), queue = ts.createTag("Queue", null);
        ts.addRule(stack, "stack", MatchMode.EXACT_WORD, 0); ts.addRule(queue, "queue", MatchMode.EXACT_WORD, 0);
        Question q = save(1, "Compare a stack and a queue", "LIFO vs FIFO");
        ts.retag(bank, null); assertEquals(Set.of("Stack", "Queue"), names(1, "AUTO"));
        ts.applyUserTags(q, Set.of("My Manual Tag"), Set.of("Queue"));
        assertEquals(Set.of("My Manual Tag"), names(1, "MANUAL")); assertEquals(Set.of("Stack"), names(1, "AUTO"));
        q.setQuestionContent("Compare a stack and a queue again"); ts.retag(bank, null); ts.retag(bank, null);
        assertEquals(Set.of("My Manual Tag"), names(1, "MANUAL")); assertEquals(Set.of("Stack"), names(1, "AUTO")); assertEquals(Set.of("Queue"), ts.excludedNames(1));
    }
    @Test void autoTagsFollowTextChanges() throws Exception {
        int stack = ts.createTag("Stack", null); ts.addRule(stack, "stack", MatchMode.EXACT_WORD, 0);
        Question q = save(1, "Explain a stack", "x"); ts.retag(bank, null); assertEquals(Set.of("Stack"), names(1, "AUTO"));
        q.setQuestionContent("Explain a heap"); ts.retag(bank, null); assertTrue(names(1, "AUTO").isEmpty());
    }
    @Test void ignoreListSkipsAmbiguousWords() throws Exception {
        int t = ts.createTag("Join", null); ts.addRule(t, "join", MatchMode.EXACT_WORD, 0);
        assertEquals(1, ts.analyze("join the tables", "").size()); ts.setIgnoreWords("join"); assertTrue(ts.analyze("join the tables", "").isEmpty());
    }
    @Test void mergeMovesRulesAndQuestionTags() throws Exception {
        int a = ts.createTag("Overriding", null), b = ts.createTag("Method Overriding", null); ts.addRule(a, "overriding", MatchMode.EXACT_WORD, 0);
        Question q = save(1, "Explain overriding", "x"); ts.retag(bank, null);
        ts.mergeTags(a, b);
        assertEquals(1, ts.rulesFor(b).size()); assertEquals(Set.of("Method Overriding"), names(1, "AUTO")); assertEquals(1, ts.allTags().size());
    }
    @Test void starterRulesSeedOnceAndTagAJavaQuestion() throws Exception {
        assertTrue(ts.seedStarterIfNeeded()); assertFalse(ts.seedStarterIfNeeded());
        Question q = save(1, "Explain inheritance and method overriding in Java.", "Inheritance allows a class to reuse another class. Method overriding provides runtime polymorphism.");
        ts.retag(bank, null);
        assertTrue(names(1, "AUTO").containsAll(Set.of("Java", "Inheritance", "Method Overriding", "Polymorphism")));
    }
    @Test void previewRuleListsMatchingQuestions() throws Exception {
        Question q = save(1, "What is a queue?", "FIFO"); save(2, "What is a stack?", "LIFO");
        assertEquals(1, ts.previewRule(rule(0, "queue", 0, MatchMode.EXACT_WORD, true, 0), bank).size());
    }
}
