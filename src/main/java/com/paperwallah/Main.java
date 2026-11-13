package com.paperwallah;
import com.paperwallah.model.*;
import com.paperwallah.service.*;
import com.paperwallah.strategy.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import javafx.application.Application;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
    private final Label sQ = new Label(), sT = new Label(), sP = new Label();
    private final String css = Main.class.getResource("/style.css").toExternalForm();

    static class Draft { String q = "", a = "", topic = ""; int marks = 1; Difficulty d = Difficulty.MEDIUM; List<String> imgs = new ArrayList<>(); }

    @Override public void start(Stage s) {
        stage = s;
        tabs = new TabPane(tab("Dashboard", dashTab()), tab("Question Bank", bankTab()), tab("Generate", genTab()),
            tab("Paper Editor", editorTab()), tab("Saved Papers", savedTab()), tab("Print Preview", printTab()));
        tabs.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> refreshDash());
        refreshDash();
        Scene sc = new Scene(tabs, 1050, 700); sc.getStylesheets().add(css);
        sc.getRoot().lookupAll(".button").forEach(n -> { if (n instanceof Button bt && (bt.getText().equals("Delete") || bt.getText().equals("Remove"))) bt.getStyleClass().add("danger"); });
        s.setScene(sc); s.setTitle("PaperWallah"); s.show();
    }
    private Tab tab(String n, javafx.scene.Node c) { Tab t = new Tab(n, c); t.setClosable(false); return t; }
    private void style(Dialog<?> d) { d.getDialogPane().getStylesheets().add(css); }
    private void alert(String m) { Alert a = new Alert(Alert.AlertType.WARNING, m); style(a); a.showAndWait(); }
    private boolean confirm(String m) { Alert a = new Alert(Alert.AlertType.CONFIRMATION, m); style(a); return a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK; }
    private void saveBank() { try { st.saveQuestions(bank); } catch (IOException e) { alert("Could not save questions: " + e.getMessage()); } }
    private void savePapers() { try { st.savePapers(papers); } catch (IOException e) { alert("Could not save papers: " + e.getMessage()); } }
    private void refreshDash() {
        sQ.setText("" + bank.size()); sT.setText("" + bank.stream().map(Question::getTopic).distinct().count()); sP.setText("" + papers.size());
    }
    private Paper deepCopy(Paper p) {
        try (ByteArrayOutputStream b = new ByteArrayOutputStream(); ObjectOutputStream o = new ObjectOutputStream(b)) {
            o.writeObject(p); o.flush();
            try (ObjectInputStream i = new ObjectInputStream(new ByteArrayInputStream(b.toByteArray()))) { return (Paper) i.readObject(); }
        } catch (IOException | ClassNotFoundException e) { throw new IllegalStateException(e); }
    }
    private TextArea area(String text, int rows) { TextArea a = new TextArea(text); a.setPrefRowCount(rows); a.setFont(Font.font("Monospaced", 13)); return a; }
    private VBox page(javafx.scene.Node... n) {
        VBox v = new VBox(10, n); v.setPadding(new Insets(14));
        for (javafx.scene.Node x : n) if (x instanceof HBox hb) hb.setAlignment(Pos.CENTER);
        return v;
    }
    private VBox titled(String t, javafx.scene.Node... n) {
        Label h = new Label(t); h.getStyleClass().add("h1"); h.setMaxWidth(Double.MAX_VALUE); h.setAlignment(Pos.CENTER);
        VBox v = page(n); v.getChildren().add(0, h); return v;
    }

    /* ---------- Dashboard ---------- */
    private VBox statCard(Label num, String cap) { num.getStyleClass().add("stat-num"); VBox c = new VBox(2, num, new Label(cap)); c.getStyleClass().add("card"); c.setMinWidth(170); return c; }
    private javafx.scene.Node dashTab() {
        Label hero = new Label("PaperWallah"); hero.getStyleClass().add("hero");
        Label sub = new Label("Smart Question Paper Generator  -  fully offline"); sub.getStyleClass().add("sub");
        HBox stats = new HBox(20, statCard(sQ, "Questions"), statCard(sT, "Topics used (of 60)"), statCard(sP, "Saved papers")); stats.setAlignment(Pos.CENTER);
        Button go = new Button("Question Bank"), gen = new Button("Generate a paper");
        go.setOnAction(e -> tabs.getSelectionModel().select(1)); gen.setOnAction(e -> tabs.getSelectionModel().select(2));
        Button smp = new Button("Load sample questions"), imp = new Button("Import questions (CSV)"), rms = new Button("Remove sample questions");
        smp.setOnAction(e -> loadSamples()); imp.setOnAction(e -> importCsv()); rms.setOnAction(e -> removeSamples());
        HBox btns = new HBox(14, go, gen, smp, imp, rms); btns.setAlignment(Pos.CENTER);
        VBox v = new VBox(26, hero, sub, stats, btns); v.setAlignment(Pos.CENTER); return v;
    }
    /** 3 sample questions (1, 3 and 5 marks) for every one of the 60 topics = 180 questions, all marked [SAMPLE]. */
    private void loadSamples() {
        if (!confirm("Add 180 sample questions (3 per topic)? You can remove them later.")) return;
        Difficulty[] dl = Difficulty.values(); int k = 0;
        for (String topic : Topics.ALL) for (int m : new int[]{1, 3, 5}) {
            String q = m == 1 ? "[SAMPLE] Which statement is correct about: " + topic + "?\nA) Option one\nB) Option two\nC) Option three\nD) Option four"
                : "[SAMPLE] " + (m == 3 ? "Briefly explain" : "Explain in detail with an example") + ": " + topic;
            bank.add(new Question(editSvc.nextId(), q, m == 1 ? "B" : "[SAMPLE] Model answer for " + topic, m, dl[k++ % 3], topic));
        }
        saveBank(); refreshDash();
    }
    private void removeSamples() {
        if (confirm("Delete every question whose text starts with [SAMPLE]?")) { bank.removeIf(q -> q.getQuestionContent().startsWith("[SAMPLE]")); saveBank(); refreshDash(); }
    }
    /** CSV columns: marks,difficulty,topic (number 1-60 or name),question,answer. Use \n inside a cell for a new line. */
    private void importCsv() {
        FileChooser fc = new FileChooser(); fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv"));
        File f = fc.showOpenDialog(stage); if (f == null) return;
        List<String> lines; try { lines = Files.readAllLines(f.toPath()); } catch (IOException | RuntimeException ex) { alert("Could not read the file (save it as UTF-8 CSV): " + ex.getMessage()); return; }
        int added = 0; List<String> bad = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).replace("\uFEFF", ""); if (line.isBlank()) continue;
            List<String> c = csvLine(line);
            if (i == 0 && !c.get(0).trim().matches("\\d+")) continue; // header row
            try {
                if (c.size() < 5) throw new IllegalArgumentException("needs 5 columns");
                int marks = Integer.parseInt(c.get(0).trim()); if (marks <= 0) throw new IllegalArgumentException("marks must be positive");
                Difficulty d = Difficulty.valueOf(c.get(1).trim().toUpperCase());
                String topic = Topics.resolve(c.get(2).trim()); if (!Topics.ALL.contains(topic)) throw new IllegalArgumentException("unknown topic");
                String q = c.get(3).replace("\\n", "\n").trim(), a = c.get(4).replace("\\n", "\n").trim();
                if (q.isEmpty() || a.isEmpty()) throw new IllegalArgumentException("question/answer empty");
                bank.add(new Question(editSvc.nextId(), q, a, marks, d, topic)); added++;
            } catch (RuntimeException ex) { bad.add("line " + (i + 1) + ": " + ex.getMessage()); }
        }
        saveBank(); refreshDash();
        new Alert(Alert.AlertType.INFORMATION, "Imported " + added + " question(s)." + (bad.isEmpty() ? "" : "\nSkipped " + bad.size() + ":\n" + String.join("\n", bad.subList(0, Math.min(8, bad.size()))))).showAndWait();
    }
    private List<String> csvLine(String line) {
        List<String> out = new ArrayList<>(); StringBuilder sb = new StringBuilder(); boolean q = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (q) { if (c == '"') { if (i + 1 < line.length() && line.charAt(i + 1) == '"') { sb.append('"'); i++; } else q = false; } else sb.append(c); }
            else if (c == '"') q = true; else if (c == ',') { out.add(sb.toString()); sb.setLength(0); } else sb.append(c);
        }
        out.add(sb.toString()); return out;
    }
    private void editHeader() {
        if (current == null) { alert("Open or generate a paper first."); return; }
        Dialog<ButtonType> dlg = new Dialog<>(); dlg.setTitle("Edit paper header"); style(dlg);
        TextField col = new TextField(current.getCollege()), ti = new TextField(current.getTitle()), su = new TextField(current.getSubject()), du = new TextField(current.getDuration());
        du.setPromptText("e.g. 3 Hours"); TextArea in = area(current.getInstructions(), 4); in.setWrapText(true);
        GridPane g = new GridPane(); g.setHgap(10); g.setVgap(8); g.setPadding(new Insets(12));
        String[] names = {"College / institution", "Exam title", "Subject", "Duration", "Instructions"}; javafx.scene.Node[] f = {col, ti, su, du, in};
        for (int i = 0; i < f.length; i++) { g.add(new Label(names[i]), 0, i); g.add(f[i], 1, i); ((Region) f[i]).setPrefWidth(420); }
        dlg.getDialogPane().setContent(g); dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (dlg.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            current.setCollege(col.getText().trim()); current.setTitle(ti.getText().trim()); current.setSubject(su.getText().trim());
            current.setDuration(du.getText().trim()); current.setInstructions(in.getText().trim()); refreshEditor();
        }
    }

    /* ---------- shared question form ---------- */
    private boolean form(String title, Draft d) {
        Dialog<ButtonType> dlg = new Dialog<>(); dlg.setTitle(title); style(dlg);
        TextArea q = area(d.q, 6), a = area(d.a, 3); TextField m = new TextField("" + d.marks);
        ComboBox<String> t = new ComboBox<>(FXCollections.observableArrayList(Topics.ALL)); t.setPromptText("Select topic (60 syllabus topics)"); t.setMaxWidth(560);
        if (Topics.ALL.contains(d.topic)) t.setValue(d.topic);
        ComboBox<Difficulty> df = new ComboBox<>(FXCollections.observableArrayList(Difficulty.values())); df.setValue(d.d);
        List<String> added = new ArrayList<>();
        Label imgLbl = new Label(d.imgs.size() + " image(s) attached");
        Button att = new Button("Attach image"), rm = new Button("Remove last image");
        att.setOnAction(e -> {
            FileChooser fc = new FileChooser(); fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
            File f = fc.showOpenDialog(stage);
            if (f != null) try { String p = st.importImage(f); d.imgs.add(p); added.add(p); imgLbl.setText(d.imgs.size() + " image(s) attached"); }
            catch (IOException ex) { alert("Could not add image: " + ex.getMessage()); }
        });
        rm.setOnAction(e -> { if (!d.imgs.isEmpty()) { String last = d.imgs.remove(d.imgs.size() - 1); if (added.remove(last)) st.deleteImage(last); imgLbl.setText(d.imgs.size() + " image(s) attached"); } });
        dlg.getDialogPane().setContent(page(new Label("Question (code: indentation kept; MCQ: write A) B) C) D) lines)"), q, new Label("Answer / correct option"), a,
            new HBox(8, new Label("Marks"), m, new Label("Difficulty"), df), new HBox(8, new Label("Topic"), t), new HBox(8, att, rm, imgLbl)));
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, ev -> {
            String err = q.getText().isBlank() ? "Question text is required." : a.getText().isBlank() ? "Answer is required."
                : t.getValue() == null ? "Please select a topic." : null;
            try { if (Integer.parseInt(m.getText().trim()) <= 0) err = "Marks must be positive."; } catch (NumberFormatException ex) { err = "Marks must be a whole number."; }
            if (err != null) { alert(err); ev.consume(); }
        });
        if (dlg.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) { added.forEach(st::deleteImage); return false; }
        d.q = q.getText(); d.a = a.getText(); d.topic = t.getValue(); d.marks = Integer.parseInt(m.getText().trim()); d.d = df.getValue();
        return true;
    }

    /* ---------- Question Bank ---------- */
    private javafx.scene.Node bankTab() {
        FilteredList<Question> shown = new FilteredList<>(bank, x -> true);
        TextField search = new TextField(); search.setPromptText("Search text or topic");
        ComboBox<Integer> mf = new ComboBox<>(); mf.setPromptText("Marks"); ComboBox<Difficulty> df = new ComboBox<>(FXCollections.observableArrayList(Difficulty.values())); df.setPromptText("Difficulty");
        ComboBox<String> tf = new ComboBox<>(FXCollections.observableArrayList(Topics.ALL)); tf.setPromptText("Topic"); tf.setMaxWidth(240);
        Button clear = new Button("Clear filters");
        Runnable filt = () -> shown.setPredicate(x -> (search.getText().isBlank() || (x.getQuestionContent() + x.getTopic()).toLowerCase().contains(search.getText().toLowerCase()))
            && (mf.getValue() == null || x.getMarks() == mf.getValue()) && (df.getValue() == null || x.getDifficulty() == df.getValue()) && (tf.getValue() == null || tf.getValue().equals(x.getTopic())));
        search.textProperty().addListener((o, a, b) -> filt.run()); mf.setOnAction(e -> filt.run()); tf.setOnAction(e -> filt.run()); df.setOnAction(e -> filt.run());
        mf.setItems(FXCollections.observableArrayList(1, 2, 3, 4, 5, 6, 8, 10));
        clear.setOnAction(e -> { search.clear(); mf.setValue(null); df.setValue(null); tf.setValue(null); });
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
        return titled("Question Bank", new HBox(8, search, mf, df, tf, clear), table, new HBox(8, add, edit, del), detail, iv, warn);
    }

    /* ---------- Generate ---------- */
    private javafx.scene.Node genTab() {
        TextField title = new TextField("Examination"), subject = new TextField(), total = new TextField("50"), allowed = new TextField("1,3,5"), college = new TextField();
        college.setPromptText("College / institution");
        List<String> tl = new ArrayList<>(); tl.add("All topics"); tl.addAll(Topics.ALL);
        ComboBox<String> topic = new ComboBox<>(FXCollections.observableArrayList(tl)); topic.setValue("All topics"); topic.setMaxWidth(260);
        Button gA = new Button("Generate (total marks)");
        TextArea bp = area("Section A,10,1\nSection B,5,3\nSection C,5,5", 6); TextField declared = new TextField("50");
        Button gB = new Button("Generate (blueprint)");
        gA.setOnAction(e -> {
            try {
                int[] al = Arrays.stream(allowed.getText().split("[,\\s]+")).filter(s -> !s.isBlank()).mapToInt(Integer::parseInt).distinct().sorted().toArray();
                run(new MarksBasedStrategy(Integer.parseInt(total.getText().trim()), al, topic.getValue().equals("All topics") ? "" : topic.getValue()), title.getText(), subject.getText(), college.getText());
            } catch (NumberFormatException ex) { alert("Total and allowed marks must be whole numbers."); }
        });
        gB.setOnAction(e -> {
            try {
                List<BlueprintBasedStrategy.Section> secs = new ArrayList<>();
                for (String line : bp.getText().split("\n")) {
                    if (line.isBlank()) continue; String[] p = line.split(",", -1);
                    secs.add(new BlueprintBasedStrategy.Section(p[0].trim(), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()),
                        p.length > 3 ? Topics.resolve(p[3].trim()) : "", p.length > 4 && !p[4].isBlank() ? Difficulty.valueOf(p[4].trim().toUpperCase()) : null));
                }
                run(new BlueprintBasedStrategy(secs, Integer.parseInt(declared.getText().trim())), title.getText(), subject.getText(), college.getText());
            } catch (RuntimeException ex) { alert("Blueprint lines must look like: Section A,10,1[,topic[,EASY|MEDIUM|HARD]]"); }
        });
        return titled("Generate Paper", new HBox(8, new Label("College"), college, new Label("Title"), title, new Label("Subject"), subject),
            new Label("MODE A - total marks and allowed question marks"), new HBox(8, new Label("Total"), total, new Label("Allowed"), allowed, topic, gA),
            new Label("MODE B - blueprint (one per line: name,count,marksEach[,topic number 1-60[,EASY|MEDIUM|HARD]])"), bp, new HBox(8, new Label("Declared total"), declared, gB));
    }
    private void run(PaperGenerationStrategy s, String title, String subject, String college) {
        try {
            current = new Paper(0, title, subject, s.modeName(), s.generate(new ArrayList<>(bank))); current.setCollege(college);
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
            man = new Button("Add manual"), save = new Button("Save paper"), prt = new Button("Print preview"), hdr = new Button("Edit header");
        hdr.setOnAction(e -> editHeader());
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
        totalLbl.setMaxWidth(Double.MAX_VALUE); totalLbl.setAlignment(Pos.CENTER); totalLbl.getStyleClass().add("summary");
        return titled("Paper Editor", totalLbl, editorList, new HBox(8, hdr, up, down, edit, rep, rem, man, save, prt));
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
        return titled("Saved Papers", lv, new HBox(8, open, prt, del));
    }

    /* ---------- Print preview (text, code and images) ---------- */
    private javafx.scene.Node printTab() {
        printKind.setValue("Question Paper"); printKind.setOnAction(e -> updatePreview());
        pv.getStyleClass().add("paper"); pv.setMaxWidth(600); pv.setMinWidth(600);
        StackPane holder = new StackPane(pv); holder.setPadding(new Insets(10)); StackPane.setAlignment(pv, Pos.TOP_CENTER);
        ScrollPane sp = new ScrollPane(holder); sp.setFitToWidth(true); VBox.setVgrow(sp, Priority.ALWAYS);
        Button print = new Button("Print"); print.setOnAction(e -> doPrint());
        return titled("Print Preview", new HBox(8, printKind, print), sp);
    }
    private Text head(String s, double w, int size) {
        Text t = txt(s, w, true); t.setFont(Font.font("SansSerif", FontWeight.BOLD, size)); t.setTextAlignment(TextAlignment.CENTER); return t;
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
        if (!p.getCollege().isBlank()) out.add(head(p.getCollege(), w, 17));
        out.add(head(p.getTitle(), w, 14)); out.add(head(p.getSubject(), w, 13));
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
        if (key) out.add(head("ANSWER KEY", w, 13));
        else {
            Text tm = txt("Time: " + (p.getDuration().isBlank() ? "________" : p.getDuration()) + "        Total Marks: " + p.getTotalMarks(), w, true); tm.setTextAlignment(TextAlignment.CENTER); out.add(tm);
            if (!p.getInstructions().isBlank()) out.add(txt("Instructions: " + p.getInstructions(), w, false));
        }
        String sec = ""; int n = 0;
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
