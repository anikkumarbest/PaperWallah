package com.paperwallah.model;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
public class Paper implements Serializable {
    private static final long serialVersionUID = 1L;
    private int paperId;
    private String title, subject, college = "", generationMode, duration = "", instructions = "Answer all questions. Marks are shown beside each question.";
    private LocalDate creationDate = LocalDate.now();
    private List<PaperQuestion> questions = new ArrayList<>();
    public Paper(int id, String title, String subject, String mode, List<PaperQuestion> qs) {
        paperId = id; this.title = title; this.subject = subject; generationMode = mode; questions = new ArrayList<>(qs);
    }
    public int getPaperId() { return paperId; }
    public void setPaperId(int id) { paperId = id; }
    public String getTitle() { return title; }
    public String getSubject() { return subject; }
    public String getCollege() { return college == null ? "" : college; }
    public void setCollege(String s) { college = s; }
    public void setTitle(String s) { title = s; }
    public void setSubject(String s) { subject = s; }
    public String getDuration() { return duration == null ? "" : duration; }
    public void setDuration(String s) { duration = s; }
    public String getInstructions() { return instructions == null ? "" : instructions; }
    public void setInstructions(String s) { instructions = s; }
    public String getGenerationMode() { return generationMode; }
    public LocalDate getCreationDate() { return creationDate; }
    public void setCreationDate(LocalDate d) { if (d != null) creationDate = d; }
    public List<PaperQuestion> getQuestions() { return questions; }
    public int getTotalMarks() { return questions.stream().mapToInt(PaperQuestion::getMarks).sum(); }
}
