package com.paperwallah.model;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
/** A snapshot copy of a question inside a paper; independent of the bank. */
public class PaperQuestion implements Serializable {
    private static final long serialVersionUID = 1L;
    private Integer originalQuestionId; // null for manually written questions
    private String questionContent, answerContent, topic, sectionName;
    private int marks;
    private Difficulty difficulty;
    private List<String> imagePaths = new ArrayList<>();
    public PaperQuestion(Question q, String section) {
        originalQuestionId = q.getQuestionId(); questionContent = q.getQuestionContent();
        answerContent = q.getAnswerContent(); marks = q.getMarks(); topic = q.getTopic();
        difficulty = q.getDifficulty(); sectionName = section; imagePaths = new ArrayList<>(q.getImagePaths());
    }
    public PaperQuestion(String q, String a, int marks, String topic, Difficulty d, String section) {
        questionContent = q; answerContent = a; this.marks = marks; this.topic = topic; difficulty = d; sectionName = section;
    }
    public Integer getOriginalQuestionId() { return originalQuestionId; }
    public void setOriginalQuestionId(Integer id) { originalQuestionId = id; }
    public String getQuestionContent() { return questionContent; }
    public void setQuestionContent(String s) { questionContent = s; }
    public String getAnswerContent() { return answerContent; }
    public void setAnswerContent(String s) { answerContent = s; }
    public int getMarks() { return marks; }
    public void setMarks(int m) { marks = m; }
    public String getTopic() { return topic; }
    public Difficulty getDifficulty() { return difficulty; }
    public String getSectionName() { return sectionName; }
    public List<String> getImagePaths() { return imagePaths; }
    public Question toQuestion(int newId) {
        Question q = new Question(newId, questionContent, answerContent, marks, difficulty, topic);
        q.getImagePaths().addAll(imagePaths); return q;
    }
}
