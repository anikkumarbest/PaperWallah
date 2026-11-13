package com.paperwallah.service;
import com.paperwallah.model.*;
import java.util.List;
/** The three ways to save an edit made inside a paper. */
public class QuestionEditService {
    private final List<Question> bank;
    public QuestionEditService(List<Question> bank) { this.bank = bank; }
    public int nextId() { return bank.stream().mapToInt(Question::getQuestionId).max().orElse(0) + 1; }
    public enum Choice { CORRECT_EXISTING, CREATE_NEW, PAPER_ONLY }
    public void apply(PaperQuestion pq, Choice c) {
        switch (c) {
            case CORRECT_EXISTING -> {
                Question q = pq.getOriginalQuestionId() == null ? null : bank.stream().filter(x -> x.getQuestionId() == pq.getOriginalQuestionId()).findFirst().orElse(null);
                if (q == null) { addAsNew(pq); return; }
                q.setQuestionContent(pq.getQuestionContent()); q.setAnswerContent(pq.getAnswerContent()); q.setMarks(pq.getMarks());
            }
            case CREATE_NEW -> addAsNew(pq);
            case PAPER_ONLY -> { }
        }
    }
    public Question addAsNew(PaperQuestion pq) {
        Question q = pq.toQuestion(nextId()); bank.add(q); pq.setOriginalQuestionId(q.getQuestionId()); return q;
    }
}
