package com.paperwallah.model;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
/** A reusable bank question. Images are stored as relative file paths, never as binary. */
public class Question implements Serializable {
    private static final long serialVersionUID = 1L;
    private int questionId;
    private String questionContent, answerContent, topic;
    private int marks;
    private Difficulty difficulty;
    private List<String> imagePaths = new ArrayList<>();
    public Question(int id, String q, String a, int marks, Difficulty d, String topic) {
        this.questionId = id; this.questionContent = q; this.answerContent = a;
        this.marks = marks; this.difficulty = d; this.topic = topic;
    }
    public int getQuestionId() { return questionId; }
    public String getQuestionContent() { return questionContent; }
    public void setQuestionContent(String s) { questionContent = s; }
    public String getAnswerContent() { return answerContent; }
    public void setAnswerContent(String s) { answerContent = s; }
    public int getMarks() { return marks; }
    public void setMarks(int m) { marks = m; }
    public Difficulty getDifficulty() { return difficulty; }
    public void setDifficulty(Difficulty d) { difficulty = d; }
    public String getTopic() { return topic; }
    public void setTopic(String t) { topic = t; }
    public List<String> getImagePaths() { return imagePaths; }
}
