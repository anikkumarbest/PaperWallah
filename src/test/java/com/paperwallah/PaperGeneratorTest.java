package com.paperwallah;
import com.paperwallah.model.*;
import com.paperwallah.service.QuestionEditService;
import com.paperwallah.strategy.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PaperGeneratorTest {
    private List<Question> bank(int perMark) {
        List<Question> b = new ArrayList<>(); int id = 1;
        for (int m : new int[]{1, 3, 5}) for (int i = 0; i < perMark; i++) b.add(new Question(id++, "q", "a", m, Difficulty.EASY, "T"));
        return b;
    }
    @Test void combinationsAddUp() {
        for (int[] c : MarksBasedStrategy.combinations(50, new int[]{1, 3, 5})) assertEquals(50, c[0] + 3 * c[1] + 5 * c[2]);
    }
    @Test void marksBasedTotalsAndNoDuplicates() throws Exception {
        List<PaperQuestion> r = new MarksBasedStrategy(50, new int[]{1, 3, 5}, null).generate(bank(10));
        assertEquals(50, r.stream().mapToInt(PaperQuestion::getMarks).sum());
        assertEquals(r.size(), r.stream().map(PaperQuestion::getQuestionContent).count());
    }
    @Test void noValidCombination() { assertThrows(GenerationException.class, () -> new MarksBasedStrategy(7, new int[]{4, 6}, null).generate(bank(10))); }
    @Test void insufficientQuestions() { assertThrows(GenerationException.class, () -> new MarksBasedStrategy(50, new int[]{1, 3, 5}, null).generate(bank(1))); }
    @Test void blueprintTotalMismatch() {
        var s = List.of(new BlueprintBasedStrategy.Section("A", 2, 1, Set.of(), null));
        assertThrows(GenerationException.class, () -> new BlueprintBasedStrategy(s, 5).generate(bank(5)));
    }
    @Test void correctKeepsIdCreateNewDoesNot() {
        List<Question> b = bank(2); QuestionEditService svc = new QuestionEditService(b);
        PaperQuestion pq = new PaperQuestion(b.get(0), "Section A"); pq.setQuestionContent("fixed");
        svc.apply(pq, QuestionEditService.Choice.CORRECT_EXISTING);
        assertEquals("fixed", b.get(0).getQuestionContent()); assertEquals(1, b.get(0).getQuestionId());
        PaperQuestion p2 = new PaperQuestion(b.get(1), "Section A"); p2.setQuestionContent("new version"); int n = b.size();
        svc.apply(p2, QuestionEditService.Choice.CREATE_NEW);
        assertEquals(n + 1, b.size()); assertEquals("q", b.get(1).getQuestionContent()); assertNotEquals(2, (int) p2.getOriginalQuestionId());
    }
    @Test void snapshotIndependentOfBank() {
        List<Question> b = bank(1); PaperQuestion pq = new PaperQuestion(b.get(0), "S"); b.get(0).setQuestionContent("changed");
        assertEquals("q", pq.getQuestionContent());
    }
}
