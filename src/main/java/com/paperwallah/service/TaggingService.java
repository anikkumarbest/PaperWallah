package com.paperwallah.service;
import com.paperwallah.config.DatabaseManager;
import com.paperwallah.dao.*;
import com.paperwallah.model.*;
import java.sql.SQLException;
import java.util.*;
import java.util.function.DoubleConsumer;
import java.util.stream.Collectors;
/** Rule-based keyword tagging, fully offline. Manual tags and exclusions are never touched by re-tagging. */
public class TaggingService {
    private final DatabaseManager db; private final TagDAO tags; private final TagRuleDAO rulesDao; private final QuestionTagDAO qt;
    private List<TagRule> rulesCache; private Map<Integer, String> namesCache;
    public TaggingService(DatabaseManager db) { this.db = db; tags = new TagDAO(db); rulesDao = new TagRuleDAO(db); qt = new QuestionTagDAO(db); }
    private void invalidate() { rulesCache = null; namesCache = null; }

    /* ---- tags and rules ---- */
    public List<Tag> allTags() throws SQLException { return tags.all(); }
    public Map<Integer, String> tagNames() throws SQLException { if (namesCache == null) { namesCache = new HashMap<>(); for (Tag t : tags.all()) namesCache.put(t.id(), t.name()); } return namesCache; }
    public int createTag(String name, String group) throws SQLException { invalidate(); Integer ex = tags.findId(name); return ex != null ? ex : tags.insert(name, group); }
    public void updateTag(int id, String name, String group) throws SQLException { invalidate(); tags.update(id, name, group); }
    public void deleteTag(int id) throws SQLException { invalidate(); tags.delete(id); }
    public void mergeTags(int from, int to) throws SQLException { invalidate(); db.tx(c -> { tags.merge(from, to); return null; }); }
    public List<TagRule> allRules() throws SQLException { return rulesDao.all(); }
    public List<TagRule> rulesFor(int tagId) throws SQLException { return rulesDao.all().stream().filter(r -> r.tagId() == tagId).collect(Collectors.toList()); }
    public int addRule(int tagId, String phrase, MatchMode mode, int priority) throws SQLException { invalidate(); return rulesDao.insert(tagId, phrase.trim(), TextUtil.normalize(phrase), mode, priority, true); }
    public void updateRule(TagRule r) throws SQLException { invalidate(); rulesDao.update(new TagRule(r.id(), r.phrase().trim(), TextUtil.normalize(r.phrase()), r.tagId(), r.mode(), r.enabled(), r.priority())); }
    public void setRuleEnabled(TagRule r, boolean on) throws SQLException { invalidate(); rulesDao.update(new TagRule(r.id(), r.phrase(), r.normalized(), r.tagId(), r.mode(), on, r.priority())); }
    public void deleteRule(int id) throws SQLException { invalidate(); rulesDao.delete(id); }
    public String ignoreWords() throws SQLException { return db.setting("tag_ignore_words", ""); }
    public void setIgnoreWords(String csv) throws SQLException { invalidate(); db.setSetting("tag_ignore_words", csv.toLowerCase().trim()); }
    private List<TagRule> rules() throws SQLException {
        if (rulesCache == null) {
            Set<String> ignore = new HashSet<>(); for (String w : ignoreWords().split("[,;\\n]")) if (!w.isBlank()) ignore.add(TextUtil.normalize(w));
            rulesCache = rulesDao.all().stream().filter(r -> !ignore.contains(r.normalized())).collect(Collectors.toList());
        }
        return rulesCache;
    }

    /* ---- analysis (no database writes) ---- */
    /** Question and answer are both analysed; the result has one match per tag (the first reason found). */
    public Map<Integer, TagMatch> analyze(String question, String answer) throws SQLException {
        List<TagRule> rs = rules(); Map<Integer, TagMatch> first = new LinkedHashMap<>(); Map<Integer, Integer> hits = new HashMap<>();
        List<TagMatch> all = new ArrayList<>(TagMatcher.match(question, "question", rs)); all.addAll(TagMatcher.match(answer, "answer", rs));
        for (TagMatch m : all) { first.putIfAbsent(m.tagId(), m); hits.merge(m.tagId(), 1, Integer::sum); }
        Map<Integer, TagMatch> out = new LinkedHashMap<>();
        first.forEach((t, m) -> out.put(t, new TagMatch(t, m.ruleId(), m.phrase(), m.where(), m.start(), m.end(), Math.min(1.0, m.confidence() + 0.05 * (hits.get(t) - 1)))));
        return out;
    }
    /** Which questions would this rule match? (for the "Preview matches" button) */
    public List<String> previewRule(TagRule rule, List<Question> qs) {
        TagRule r = new TagRule(0, rule.phrase(), TextUtil.normalize(rule.phrase()), 0, rule.mode(), true, rule.priority()); List<String> out = new ArrayList<>();
        for (Question q : qs) {
            List<TagMatch> m = TagMatcher.match(q.getQuestionContent(), "question", List.of(r)); if (m.isEmpty()) m = TagMatcher.match(q.getAnswerContent(), "answer", List.of(r));
            if (!m.isEmpty()) out.add("#" + q.getQuestionId() + " [" + q.getMarks() + "m] matched \"" + m.get(0).phrase() + "\" in the " + m.get(0).where() + ": " + q.getQuestionContent().replace("\n", " "));
        }
        return out;
    }

    /* ---- persistence per question ---- */
    public List<QuestionTagInfo> tagsOf(int qid) throws SQLException { return qt.tagsOf(qid); }
    public Set<String> excludedNames(int qid) throws SQLException { Map<Integer, String> n = tagNames(); return qt.excludedIds(qid).stream().map(n::get).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new)); }
    public Map<Integer, Integer> questionCounts() throws SQLException { return qt.questionCounts(); }
    private void retagInTx(Question q) throws SQLException {
        int id = q.getQuestionId(); Set<Integer> excl = qt.excludedIds(id), manual = qt.manualIds(id); Map<Integer, Double> want = new LinkedHashMap<>();
        for (Map.Entry<Integer, TagMatch> e : analyze(q.getQuestionContent(), q.getAnswerContent()).entrySet()) if (!excl.contains(e.getKey()) && !manual.contains(e.getKey())) want.put(e.getKey(), e.getValue().confidence());
        qt.replaceAuto(id, want);
    }
    /** Rebuilds AUTO tags for the given saved questions in one transaction. */
    public void retag(List<Question> qs, DoubleConsumer progress) throws SQLException {
        db.tx(c -> { int n = 0; for (Question q : qs) { retagInTx(q); if (progress != null && ++n % 10 == 0) progress.accept((double) n / qs.size()); } if (progress != null) progress.accept(1.0); return null; });
    }
    /** Saves the teacher's manual tags and exclusions for a saved question, then refreshes its automatic tags. */
    public void applyUserTags(Question q, Set<String> manual, Set<String> excluded) throws SQLException {
        invalidate();
        db.tx(c -> {
            Set<Integer> m = new HashSet<>(), x = new HashSet<>();
            for (String n : manual) m.add(createTagKeepCache(n)); for (String n : excluded) x.add(createTagKeepCache(n));
            qt.setManual(q.getQuestionId(), m); qt.setExcluded(q.getQuestionId(), x); retagInTx(q); return null;
        });
    }
    private int createTagKeepCache(String name) throws SQLException { Integer ex = tags.findId(name); return ex != null ? ex : tags.insert(name, null); }

    /* ---- starter rule set (editable; any subject can add its own) ---- */
    private static final String[] STARTER = {
        "Java|Java|java;jvm;jdk;jre;javac", "OOP|OOP|oop;object oriented;class diagram;objects", "Inheritance|OOP|inheritance;extends;superclass;subclass;multilevel inheritance;multiple inheritance",
        "Polymorphism|OOP|polymorph*;runtime polymorphism;dynamic dispatch;dynamic method dispatch", "Method Overriding|OOP|method overriding;overriding;@override", "Method Overloading|OOP|method overloading;overloading",
        "Encapsulation|OOP|encapsulation;data hiding;getter;setter", "Abstraction|OOP|abstraction;abstract class;abstract method", "Exception Handling|Java|exception;exceptions;try catch;throws;finally;custom exception",
        "Collections|Java|collection framework;arraylist;hashmap;hashset;treemap;iterator", "Multithreading|Java|thread;threads;multithreading;synchronized;runnable;executorservice;deadlock", "Design Patterns|Java|design pattern;singleton;factory;observer;strategy pattern;decorator",
        "Arrays|Data Structures|array;arrays", "Searching|Data Structures|binary search;linear search;searching", "Sorting|Data Structures|sorting;bubble sort;merge sort;quick sort;selection sort;insertion sort",
        "Linked Lists|Data Structures|linked list;linkedlist;singly linked;doubly linked", "Stack|Data Structures|stack;push;pop;lifo", "Queue|Data Structures|queue;enqueue;dequeue;fifo",
        "Trees|Data Structures|tree;binary tree;bst;avl", "Graphs|Data Structures|graph;bfs;dfs;dijkstra", "Recursion|Data Structures|recursion;recursive;base case",
        "Probability|Maths|probability;bayes theorem;conditional probability", "Random Variables|Maths|random variable;expectation;variance", "Markov Chains|Maths|markov chain;transition matrix;stationary distribution",
        "Operating Systems|Computing|operating system;process scheduling;semaphore;paging", "DBMS|Computing|dbms;sql;normalization;primary key;join;jdbc", "Computer Networks|Computing|tcp;ip address;routing;osi model;socket"};
    /** Adds the starter tags and rules once. Returns true if it ran (so the caller can tag the existing bank). */
    public boolean seedStarterIfNeeded() throws SQLException {
        if (db.setting("starter_rules_v1", null) != null) return false;
        db.tx(c -> {
            for (String line : STARTER) {
                String[] p = line.split("\\|"); int tid = createTagKeepCache(p[0]); if (tags.findId(p[0]) != null) { /* group set on first insert */ }
                for (String ph : p[2].split(";")) {
                    boolean prefix = ph.endsWith("*"); String text = prefix ? ph.substring(0, ph.length() - 1) : ph; String norm = TextUtil.normalize(text);
                    if (norm.isEmpty()) continue;
                    MatchMode mode = prefix ? MatchMode.PREFIX : norm.contains(" ") ? MatchMode.PHRASE : MatchMode.EXACT_WORD;
                    try (java.sql.PreparedStatement s = c.prepareStatement("INSERT OR IGNORE INTO tag_rules(keyword_or_phrase,normalized_phrase,tag_id,match_mode,is_enabled,priority) VALUES(?,?,?,?,1,0)")) { s.setString(1, text); s.setString(2, norm); s.setInt(3, tid); s.setString(4, mode.name()); s.executeUpdate(); }
                }
                try (java.sql.PreparedStatement g = c.prepareStatement("UPDATE tags SET tag_group=? WHERE id=? AND tag_group IS NULL")) { g.setString(1, p[1]); g.setInt(2, tid); g.executeUpdate(); }
            }
            db.setSetting("starter_rules_v1", "done"); return null;
        });
        invalidate(); return true;
    }
}
