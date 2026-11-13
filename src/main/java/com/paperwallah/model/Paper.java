package com.paperwallah.model;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
public class Paper implements Serializable {
    private static final long serialVersionUID = 1L;
    private int paperId;
    private String title, subject, college = "", generationMode;
    private LocalDate creationDate = LocalDate.now();
    private List<PaperQuestion> questions = new ArrayList<>();
    public Paper(int id, String title, String subject, String mode, List<PaperQuestion> qs) {
        paperId = id; this.title = title; this.subject = subject; generationMode = mode; questions = new ArrayList<>(qs);
    }
    public int getPaperId() { return paperId; }
    public void setPaperId(int id) { paperId = id; }
    public String getTitle() { return title; }
    public String getSubject() { return subject; }
    public String getCollege() { return college; }
    public String getGenerationMode() { return generationMode; }
    public LocalDate getCreationDate() { return creationDate; }
    public List<PaperQuestion> getQuestions() { return questions; }
    public int getTotalMarks() { return questions.stream().mapToInt(PaperQuestion::getMarks).sum(); }
}
