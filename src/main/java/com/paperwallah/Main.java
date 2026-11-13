package com.paperwallah;
import com.paperwallah.model.*;
import com.paperwallah.service.*;
import com.paperwallah.strategy.*;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import javafx.application.Application;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.print.PrinterJob;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.text.*;
import javafx.stage.*;

public class Main extends Application {
    private final StorageService st = new StorageService();
    private final ObservableList<Question> bank = FXCollections.observableArrayList(st.loadQuestions());
    private final ObservableList<Paper> papers = FXCollections.observableArrayList(st.loadPapers());
    private final QuestionEditService editSvc = new QuestionEditService(bank);
    private Paper current, printPaper;
    private Stage stage;
    private TabPane tabs;
    private final ListView<PaperQuestion> editorList = new ListView<>();
    private final Label totalLbl = new Label();
    private final VBox pv = new VBox(4);
    private final ChoiceBox<String> printKind = new ChoiceBox<>(FXCollections.observableArrayList("Question Paper", "Answer Key", "Blueprint"));
    private final Label dash = new Label();

    static class Draft { String q = "", a = "", topic = ""; int marks = 1; Difficulty d = Difficulty.MEDIUM; List<String> imgs = new ArrayList<>(); }

    @Override public void start(Stage s) {
        stage = s;
        tabs = new TabPane(tab("Dashboard", dashTab()), tab("Question Bank", bankTab()), tab("Generate", genTab()),
            tab("Paper Editor", editorTab()), tab("Saved Papers", savedTab()), tab("Print Preview", printTab()));
        tabs.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> refreshDash());
        refreshDash();
        s.setScene(new Scene(tabs, 1000, 680)); s.setTitle("PaperWallah"); s.show();
    }
    private Tab tab(String n, javafx.scene.Node c) { Tab t = new Tab(n, c); t.setClosable(false); return t; }
    private void alert(String m) { new Alert(Alert.AlertType.WARNING, m).showAndWait(); }
    private boolean confirm(String m) { return new Alert(Alert.AlertType.CONFIRMATION, m).showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK; }
    private void saveBank() { try { st.saveQuestions(bank); } catch (IOException e) { alert("Could not save questions: " + e.getMessage()); } }
    private void savePapers() { try { st.savePapers(papers); } catch (IOException e) { alert("Could not save papers: " + e.getMessage()); } }
    private void refreshDash() {
        long topics = bank.stream().map(Question::getTopic).distinct().count();
        dash.setText("Questions: " + bank.size() + "\nTopics: " + topics + "\nSaved papers: " + papers.size());
    }
    private Paper deepCopy(Paper p) {
        try (ByteArrayOutputStream b = new ByteArrayOutputStream(); ObjectOutputStream o = new ObjectOutputStream(b)) {
            o.writeObject(p); o.flush();
            try (ObjectInputStream i = new ObjectInputStream(new ByteArrayInputStream(b.toByteArray()))) { return (Paper) i.readObject(); }
        } catch (IOException | ClassNotFoundException e) { throw new IllegalStateException(e); }
    }
    private TextArea area(String text, int rows) { TextArea a = new TextArea(text); a.setPrefRowCount(rows); a.setFont(Font.font("Monospaced", 13)); return a; }
    private VBox page(javafx.scene.Node... n) { VBox v = new VBox(8, n); v.setPadding(new Insets(12)); return v; }

    /* ---------- Dashboard ---------- */
    private javafx.scene.Node dashTab() {
        dash.setFont(Font.font(22));
        Button go = new Button("Go to Question Bank"); go.setOnAction(e -> tabs.getSelectionModel().select(1));
        Button gen = new Button("Generate a paper"); gen.setOnAction(e -> tabs.getSelectionModel().select(2));
        return page(new Label("PaperWallah - Smart Question Paper Generator"), dash, new HBox(8, go, gen));
    }

    /* ---------- shared question form ---------- */
    private boolean form(String title, Draft d) {
        Dialog<ButtonType> dlg = new Dialog<>(); dlg.setTitle(title);
        TextArea q = area(d.q, 6), a = area(d.a, 3); TextField m = new TextField("" + d.marks), t = new TextField(d.topic);
        ComboBox<Difficulty> df = new ComboBox<>(FXCollections.observableArrayList(Difficulty.values())); df.setValue(d.d);
        Label imgLbl = new Label(d.imgs.size() + " image(s) attached");
        Button att = new Button("Attach image");
        att.setOnAction(e -> {
            File f = new FileChooser().showOpenDialog(stage);
            if (f != null) try { d.imgs.add(st.importImage(f)); imgLbl.setText(d.imgs.size() + " image(s) attached"); } catch (IOException ex) { alert("Could not copy image: " + ex.getMessage()); }
        });
        dlg.getDialogPane().setContent(page(new Label("Question (code: indentation kept; MCQ: write A) B) C) D) lines)"), q, new Label("Answer / correct option"), a,
            new HBox(8, new Label("Marks"), m, new Label("Topic"), t, df), new HBox(8, att, imgLbl)));
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, ev -> {
            String err = q.getText().isBlank() ? "Question text is required." : a.getText().isBlank() ? "Answer is required."
                : t.getText().isBlank() ? "Topic is required." : null;
            try { if (Integer.parseInt(m.getText().trim()) <= 0) err = "Marks must be positive."; } catch (NumberFormatException ex) { err = "Marks must be a whole number."; }
            if (err != null) { alert(err); ev.consume(); }
        });
        if (dlg.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
        d.q = q.getText(); d.a = a.getText(); d.topic = t.getText().trim(); d.marks = Integer.parseInt(m.getText().trim()); d.d = df.getValue();
        return true;
    }

    /* ---------- Question Bank ---------- */
    private javafx.scene.Node bankTab() {
        FilteredList<Question> shown = new FilteredList<>(bank, x -> true);
        TextField search = new TextField(); search.setPromptText("Search text or topic");
        ComboBox<Integer> mf = new ComboBox<>(); mf.setPromptText("Marks"); ComboBox<Difficulty> df = new ComboBox<>(FXCollections.observableArrayList(Difficulty.values())); df.setPromptText("Difficulty");
        Button clear = new Button("Clear filters");
        Runnable filt = () -> shown.setPredicate(x -> (search.getText().isBlank() || (x.getQuestionContent() + x.getTopic()).toLowerCase().contains(search.getText().toLowerCase()))
            && (mf.getValue() == null || x.getMarks() == mf.getValue()) && (df.getValue() == null || x.getDifficulty() == df.getValue()));
        search.textProperty().addListener((o, a, b) -> filt.run()); mf.setOnAction(e -> filt.run()); df.setOnAction(e -> filt.run());
        mf.setItems(FXCollections.observableArrayList(1, 2, 3, 4, 5, 6, 8, 10));
        clear.setOnAction(e -> { search.clear(); mf.setValue(null); df.setValue(null); });
        TableView<Question> table = new TableView<>(shown);
        String[][] cols = {{"ID", "questionId"}, {"Marks", "marks"}, {"Level", "difficulty"}, {"Topic", "topic"}, {"Question", "questionContent"}};
        for (String[] c : cols) { TableColumn<Question, Object> col = new TableColumn<>(c[0]); col.setCellValueFactory(new PropertyValueFactory<>(c[1])); table.getColumns().add(col); }
        table.getColumns().get(4).setPrefWidth(450);
        TextArea detail = area("", 6); detail.setEditable(false); ImageView iv = new ImageView(); iv.setFitHeight(160); iv.setPreserveRatio(true); Label warn = new Label();
        table.getSelectionModel().selectedItemProperty().addListener((o, a, q) -> {
            iv.setImage(null); warn.setText(""); if (q == null) return;
            detail.setText(q.getQuestionContent() + "\n\n--- Answer ---\n" + q.getAnswerContent());
            for (String p : q.getImagePaths()) { if (st.imageExists(p)) iv.setImage(new Image(new File(p).toURI().toString())); else warn.setText("Image missing: " + p); }
        });
        Button add = new Button("Add"), edit = new Button("Edit"), del = new Button("Delete");
        add.setOnAction(e -> { Draft d = new Draft(); if (form("Add question", d)) { Question q = new Question(editSvc.nextId(), d.q, d.a, d.marks, d.d, d.topic); q.getImagePaths().addAll(d.imgs); bank.add(q); saveBank(); } });
        edit.setOnAction(e -> {
            Question q = table.getSelectionModel().getSelectedItem(); if (q == null) return;
            Draft d = new Draft(); d.q = q.getQuestionContent(); d.a = q.getAnswerContent(); d.topic = q.getTopic(); d.marks = q.getMarks(); d.d = q.getDifficulty(); d.imgs = new ArrayList<>(q.getImagePaths());
            if (form("Edit question #" + q.getQuestionId(), d)) {
                q.setQuestionContent(d.q); q.setAnswerContent(d.a); q.setTopic(d.topic); q.setMarks(d.marks); q.setDifficulty(d.d);
                q.getImagePaths().clear(); q.getImagePaths().addAll(d.imgs); saveBank(); table.refresh();
            }
        });
        del.setOnAction(e -> { Question q = table.getSelectionModel().getSelectedItem(); if (q != null && confirm("Delete question #" + q.getQuestionId() + "?")) { bank.remove(q); saveBank(); } });
        VBox.setVgrow(table, Priority.ALWAYS);
        return page(new HBox(8, search, mf, df, clear), table, new HBox(8, add, edit, del), detail, iv, warn);
    }

    /* ---------- Generate ---------- */
    private javafx.scene.Node genTab() {
        TextField title = new TextField("Examination"), subject = new TextField(), total = new TextField("50"), allowed = new TextField("1,3,5"), topic = new TextField();
        topic.setPromptText("optional topic filter");
        Button gA = new Button("Generate (total marks)");
        TextArea bp = area("Section A,10,1\nSection B,5,3\nSection C,5,5", 6); TextField declared = new TextField("50");
        Button gB = new Button("Generate (blueprint)");
        gA.setOnAction(e -> {
            try {
                int[] al = Arrays.stream(allowed.getText().split("[,\\s]+")).filter(s -> !s.isBlank()).mapToInt(Integer::parseInt).distinct().sorted().toArray();
                run(new MarksBasedStrategy(Integer.parseInt(total.getText().trim()), al, topic.getText().trim()), title.getText(), subject.getText());
            } catch (NumberFormatException ex) { alert("Total and allowed marks must be whole numbers."); }
        });
        gB.setOnAction(e -> {
            try {
                List<BlueprintBasedStrategy.Section> secs = new ArrayList<>();
                for (String line : bp.getText().split("\n")) {
                    if (line.isBlank()) continue; String[] p = line.split(",", -1);
                    secs.add(new BlueprintBasedStrategy.Section(p[0].trim(), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()),
                        p.length > 3 ? p[3].trim() : "", p.length > 4 && !p[4].isBlank() ? Difficulty.valueOf(p[4].trim().toUpperCase()) : null));
                }
                run(new BlueprintBasedStrategy(secs, Integer.parseInt(declared.getText().trim())), title.getText(), subject.getText());
            } catch (RuntimeException ex) { alert("Blueprint lines must look like: Section A,10,1[,topic[,EASY|MEDIUM|HARD]]"); }
        });
        return page(new HBox(8, new Label("Title"), title, new Label("Subject"), subject),
            new Label("MODE A - total marks and allowed question marks"), new HBox(8, new Label("Total"), total, new Label("Allowed"), allowed, topic, gA),
            new Label("MODE B - blueprint (one section per line: name,count,marksEach[,topic[,difficulty]])"), bp, new HBox(8, new Label("Declared total"), declared, gB));
    }
    private void run(PaperGenerationStrategy s, String title, String subject) {
        try {
            current = new Paper(0, title, subject, s.modeName(), s.generate(new ArrayList<>(bank)));
            refreshEditor(); tabs.getSelectionModel().select(3);
        } catch (GenerationException ex) { alert(ex.getMessage()); }
    }

    /* ---------- Paper editor ---------- */
    private javafx.scene.Node editorTab() {
        editorList.setCellFactory(l -> new ListCell<>() {
            @Override protected void updateItem(PaperQuestion q, boolean empty) {
                super.updateItem(q, empty);
                setText(empty || q == null ? null : (getIndex() + 1) + ". [" + q.getMarks() + "] " + q.getSectionName() + " - " + q.getQuestionContent().replace("\n", " "));
            }
        });
        Button up = new Button("Up"), down = new Button("Down"), rem = new Button("Remove"), edit = new Button("Edit"), rep = new Button("Replace"),
            man = new Button("Add manual"), save = new Button("Save paper"), prt = new Button("Print preview");
        up.setOnAction(e -> move(-1)); down.setOnAction(e -> move(1));
        rem.setOnAction(e -> { int i = editorList.getSelectionModel().getSelectedIndex(); if (current != null && i >= 0) { current.getQuestions().remove(i); refreshEditor(); } });
        edit.setOnAction(e -> editSelected());
        rep.setOnAction(e -> replaceSelected());
        man.setOnAction(e -> {
            if (current == null) return; Draft d = new Draft();
            if (form("Manual question", d)) {
                PaperQuestion pq = new PaperQuestion(d.q, d.a, d.marks, d.topic, d.d, current.getQuestions().isEmpty() ? "Section A" : current.getQuestions().get(current.getQuestions().size() - 1).getSectionName());
                pq.getImagePaths().addAll(d.imgs); current.getQuestions().add(pq);
                if (confirm("Also add this question to the question bank?")) { editSvc.addAsNew(pq); saveBank(); }
                refreshEditor();
            }
        });
        save.setOnAction(e -> {
            if (current == null) return;
            if (current.getPaperId() == 0) current.setPaperId(papers.stream().mapToInt(Paper::getPaperId).max().orElse(0) + 1);
            Paper snap = deepCopy(current);
            papers.removeIf(p -> p.getPaperId() == snap.getPaperId()); papers.add(snap); savePapers();
            new Alert(Alert.AlertType.INFORMATION, "Paper saved (" + snap.getTotalMarks() + " marks).").showAndWait();
        });
        prt.setOnAction(e -> { printPaper = current; tabs.getSelectionModel().select(5); updatePreview(); });
        VBox.setVgrow(editorList, Priority.ALWAYS);
        return page(totalLbl, editorList, new HBox(8, up, down, edit, rep, rem, man, save, prt));
    }
    private void refreshEditor() {
        if (current == null) { totalLbl.setText("No paper open."); editorList.getItems().clear(); return; }
        editorList.setItems(FXCollections.observableArrayList(current.getQuestions()));
        totalLbl.setText(current.getTitle() + " | Total marks: " + current.getTotalMarks() + " | Questions: " + current.getQuestions().size());
    }
    private void move(int dir) {
        int i = editorList.getSelectionModel().getSelectedIndex(), j = i + dir;
        if (current == null || i < 0 || j < 0 || j >= current.getQuestions().size()) return;
        Collections.swap(current.getQuestions(), i, j); refreshEditor(); editorList.getSelectionModel().select(j);
    }
    private void editSelected() {
        PaperQuestion pq = editorList.getSelectionModel().getSelectedItem(); if (pq == null) return;
        Draft d = new Draft(); d.q = pq.getQuestionContent(); d.a = pq.getAnswerContent(); d.topic = pq.getTopic(); d.marks = pq.getMarks(); d.d = pq.getDifficulty(); d.imgs = new ArrayList<>(pq.getImagePaths());
        if (!form("Edit question", d)) return;
        boolean contentChanged = !d.q.equals(pq.getQuestionContent()) || !d.a.equals(pq.getAnswerContent());
        pq.setQuestionContent(d.q); pq.setAnswerContent(d.a); pq.setMarks(d.marks);
        pq.getImagePaths().clear(); pq.getImagePaths().addAll(d.imgs);
        if (contentChanged && pq.getOriginalQuestionId() != null) {
            List<String> opts = List.of("Correct the existing question", "Create a new question from this", "Keep changes only in this paper");
            ChoiceDialog<String> cd = new ChoiceDialog<>(opts.get(2), opts); cd.setTitle("Save edit"); cd.setHeaderText("How should this edit be saved?");
            Optional<String> r = cd.showAndWait();
            QuestionEditService.Choice c = QuestionEditService.Choice.values()[r.map(opts::indexOf).orElse(2)];
            editSvc.apply(pq, c); if (c != QuestionEditService.Choice.PAPER_ONLY) saveBank();
        } else if (contentChanged && confirm("Also add this manual question to the question bank?")) { editSvc.addAsNew(pq); saveBank(); }
        refreshEditor();
    }
    private void replaceSelected() {
        int i = editorList.getSelectionModel().getSelectedIndex(); if (current == null || i < 0) return;
        PaperQuestion old = current.getQuestions().get(i);
        Set<Integer> used = current.getQuestions().stream().map(PaperQuestion::getOriginalQuestionId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<String> opts = bank.stream().filter(q -> q.getMarks() == old.getMarks() && !used.contains(q.getQuestionId()))
            .map(q -> q.getQuestionId() + ": " + q.getQuestionContent().replace("\n", " ")).collect(Collectors.toList());
        if (opts.isEmpty()) { alert("No unused " + old.getMarks() + "-mark questions in the bank."); return; }
        new ChoiceDialog<>(opts.get(0), opts).showAndWait().ifPresent(s -> {
            int id = Integer.parseInt(s.substring(0, s.indexOf(':')));
            bank.stream().filter(q -> q.getQuestionId() == id).findFirst().ifPresent(q -> { current.getQuestions().set(i, new PaperQuestion(q, old.getSectionName())); refreshEditor(); });
        });
    }

    /* ---------- Saved papers ---------- */
    private javafx.scene.Node savedTab() {
        ListView<Paper> lv = new ListView<>(papers);
        lv.setCellFactory(l -> new ListCell<>() { @Override protected void updateItem(Paper p, boolean e) { super.updateItem(p, e);
            setText(e || p == null ? null : "#" + p.getPaperId() + " " + p.getTitle() + " - " + p.getSubject() + " | " + p.getTotalMarks() + " marks | " + p.getCreationDate()); } });
        Button open = new Button("Open / edit"), prt = new Button("Print preview"), del = new Button("Delete");
        open.setOnAction(e -> { Paper p = lv.getSelectionModel().getSelectedItem(); if (p != null) { current = deepCopy(p); refreshEditor(); tabs.getSelectionModel().select(3); } });
        prt.setOnAction(e -> { Paper p = lv.getSelectionModel().getSelectedItem(); if (p != null) { printPaper = p; tabs.getSelectionModel().select(5); updatePreview(); } });
        del.setOnAction(e -> { Paper p = lv.getSelectionModel().getSelectedItem(); if (p != null && confirm("Delete this saved paper?")) { papers.remove(p); savePapers(); } });
        VBox.setVgrow(lv, Priority.ALWAYS);
        return page(lv, new HBox(8, open, prt, del));
    }

    /* ---------- Print preview (text, code and images) ---------- */
    private javafx.scene.Node printTab() {
        printKind.setValue("Question Paper"); printKind.setOnAction(e -> updatePreview());
        pv.setPadding(new Insets(16)); pv.setStyle("-fx-background-color: white;");
        ScrollPane sp = new ScrollPane(pv); sp.setFitToWidth(true); VBox.setVgrow(sp, Priority.ALWAYS);
        Button print = new Button("Print"); print.setOnAction(e -> doPrint());
        return page(new HBox(8, printKind, print), sp);
    }
    private Text txt(String s, double w, boolean bold) {
        Text t = new Text(s); t.setFont(Font.font("Monospaced", bold ? FontWeight.BOLD : FontWeight.NORMAL, 11)); t.setWrappingWidth(w); return t;
    }
    /** Image shown below the text; a visible warning replaces it if the file is missing or unreadable. */
    private javafx.scene.Node img(String path, double w) {
        if (!st.imageExists(path)) return txt("[Image missing: " + path + "]", w, false);
        Image im = new Image(new File(path).toURI().toString());
        if (im.isError()) return txt("[Image unreadable: " + path + "]", w, false);
        ImageView v = new ImageView(im); v.setPreserveRatio(true); v.setFitWidth(Math.min(w, 360)); v.setFitHeight(200); return v;
    }
    /** Builds the printable content as separate nodes so the preview and the printout are identical. */
    private List<javafx.scene.Node> items(double w) {
        List<javafx.scene.Node> out = new ArrayList<>(); Paper p = printPaper; String kind = printKind.getValue();
        if (!p.getCollege().isBlank()) out.add(txt(p.getCollege(), w, true));
        out.add(txt(p.getTitle() + "\n" + p.getSubject(), w, true));
        if (kind.equals("Blueprint")) {
            StringBuilder sb = new StringBuilder("BLUEPRINT\n\nSection | Topic | Difficulty | Questions | Marks each | Total\n");
            Map<String, List<PaperQuestion>> g = new LinkedHashMap<>();
            for (PaperQuestion q : p.getQuestions()) g.computeIfAbsent(q.getSectionName() + "|" + q.getMarks(), k -> new ArrayList<>()).add(q);
            g.values().forEach(l -> { PaperQuestion f = l.get(0);
                sb.append(f.getSectionName()).append(" | ").append(l.stream().map(PaperQuestion::getTopic).distinct().collect(Collectors.joining(", "))).append(" | ")
                  .append(l.stream().map(x -> x.getDifficulty().name()).distinct().collect(Collectors.joining(", "))).append(" | ").append(l.size()).append(" | ")
                  .append(f.getMarks()).append(" | ").append(l.size() * f.getMarks()).append("\n"); });
            sb.append("\nOverall: ").append(p.getQuestions().size()).append(" questions, ").append(p.getTotalMarks()).append(" marks");
            out.add(txt(sb.toString(), w, false)); return out;
        }
        boolean key = kind.equals("Answer Key");
        out.add(txt(key ? "ANSWER KEY" : "Total Marks: " + p.getTotalMarks(), w, true)); String sec = ""; int n = 0;
        for (PaperQuestion q : p.getQuestions()) {
            if (!q.getSectionName().equals(sec)) { sec = q.getSectionName(); out.add(txt("\n== " + sec + " ==", w, true)); }
            out.add(txt(++n + ". " + (key ? q.getAnswerContent() : q.getQuestionContent()) + "  [" + q.getMarks() + "]", w, false));
            if (!key) for (String path : q.getImagePaths()) out.add(img(path, w));
        }
        return out;
    }
    private void updatePreview() {
        if (printPaper == null) { pv.getChildren().setAll(new Label("Open or generate a paper first.")); return; }
        pv.getChildren().setAll(items(520));
    }
    private void doPrint() {
        if (printPaper == null) { alert("Open or generate a paper first."); return; }
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) { alert("No printer available."); return; }
        if (!job.showPrintDialog(stage)) return;
        double w = job.getJobSettings().getPageLayout().getPrintableWidth(), h = job.getJobSettings().getPageLayout().getPrintableHeight() - 30;
        List<VBox> pages = new ArrayList<>(); VBox cur = new VBox(4); double used = 0;
        for (javafx.scene.Node n : items(w)) {
            double nh = n.getLayoutBounds().getHeight() + 4;
            if (used + nh > h && !cur.getChildren().isEmpty()) { pages.add(cur); cur = new VBox(4); used = 0; }
            cur.getChildren().add(n); used += nh;
        }
        pages.add(cur);
        for (int i = 0; i < pages.size(); i++) {
            pages.get(i).getChildren().add(txt("\nPage " + (i + 1) + " of " + pages.size(), w, false));
            if (!job.printPage(pages.get(i))) { alert("Printing failed."); break; }
        }
        job.endJob();
    }
    public static void main(String[] a) { launch(a); }
}
