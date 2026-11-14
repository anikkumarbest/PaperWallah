package com.paperwallah.strategy;
import com.paperwallah.model.*;
import java.util.*;
import java.util.stream.Collectors;
public class BlueprintBasedStrategy implements PaperGenerationStrategy {
    /** One blueprint row. topics empty = any topic; difficulty null = any. */
    public record Section(String name, int count, int marks, Set<String> topics, Difficulty difficulty) {}
    private final List<Section> sections; private final int declaredTotal;
    public BlueprintBasedStrategy(List<Section> s, int declaredTotal) { sections = s; this.declaredTotal = declaredTotal; }
    public String modeName() { return "Blueprint"; }
    public List<PaperQuestion> generate(List<Question> bank) throws GenerationException {
        int sum = sections.stream().mapToInt(s -> s.count() * s.marks()).sum();
        if (sum != declaredTotal) throw new GenerationException("Sections add up to " + sum + " marks but the declared total is " + declaredTotal + ".");
        Set<Integer> used = new HashSet<>(); List<PaperQuestion> out = new ArrayList<>(); List<String> missing = new ArrayList<>();
        for (Section s : sections) {
            List<Question> p = bank.stream().filter(q -> q.getMarks() == s.marks() && !used.contains(q.getQuestionId())
                && (s.topics() == null || s.topics().isEmpty() || s.topics().contains(q.getTopic()))
                && (s.difficulty() == null || q.getDifficulty() == s.difficulty())).collect(Collectors.toList());
            if (p.size() < s.count()) { missing.add(s.name() + ": need " + s.count() + ", found " + p.size() + " matching (" + s.marks() + " marks)"); continue; }
            Collections.shuffle(p);
            for (int k = 0; k < s.count(); k++) { used.add(p.get(k).getQuestionId()); out.add(new PaperQuestion(p.get(k), s.name())); }
        }
        if (!missing.isEmpty()) throw new GenerationException("Not enough questions - " + String.join("; ", missing));
        return out;
    }
}
