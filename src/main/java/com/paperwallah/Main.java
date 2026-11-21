package com.paperwallah;
import com.paperwallah.config.DatabaseManager;
import com.paperwallah.model.*;
import com.paperwallah.service.*;
import com.paperwallah.strategy.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import javafx.animation.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.event.ActionEvent;
import javafx.geometry.*;
import javafx.print.PrinterJob;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.stage.*;
import javafx.util.Duration;

public class Main extends Application {
    private StorageService st;
    private DatabaseManager db;
    private QuestionService questionSvc; private TopicService topicSvc; private PaperService paperSvc;
    private String firstReport;
    private TextArea dbInfo;
    private TaggingService tagSvc;
    private RelatedQuestionService relatedSvc;
    private ListView<Tag> tagList; private TableView<TagRule> ruleTable; private Map<Integer, Integer> tagCounts = new HashMap<>();
    private final ObservableList<Question> bank = FXCollections.observableArrayList();
    private final ObservableList<String> topics = FXCollections.observableArrayList();
    private final ObservableList<Paper> papers = FXCollections.observableArrayList();
    private final QuestionEditService editSvc = new QuestionEditService(bank);
    private final String css = Main.class.getResource("/style.css").toExternalForm();
    private Paper current, printPaper;
    private Stage stage;
    private TabPane tabs;
    private boolean animating;
    private final VBox editorBox = new VBox(8), pv = new VBox(6), bpRows = new VBox(6);
    private final List<VBox> cards = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final Label totalLbl = new Label(), sQ = new Label(), sT = new Label(), sP = new Label(), bpStatus = new Label();
    private final TextField bpTarget = new TextField("50");
    private final ChoiceBox<String> printKind = new ChoiceBox<>(FXCollections.observableArrayList("Question Paper", "Answer Key", "Blueprint"));
    private static final Border BLACK = new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(1.5)));
    private static final ObservableList<Integer> MARKS = FXCollections.observableArrayList(1, 2, 3, 4, 5, 6);

    static class Draft { int id; boolean tagsEnabled; Set<String> manualTags = new LinkedHashSet<>(), excludedTags = new LinkedHashSet<>(); String q = "", a = "", topic = "", section = "Section A"; int marks = 1; Difficulty d = Difficulty.MEDIUM; List<String> imgs = new ArrayList<>(); }

    /** One editable blueprint section (Mode B). */
    private class Row {
        final TextField name = new TextField(); final Spinner<Integer> n = new Spinner<>(1, 60, 5);
        final ComboBox<Integer> m = new ComboBox<>(MARKS); final Set<String> tp = new LinkedHashSet<>(); final Button tb = new Button("All topics");
        final ComboBox<String> df = new ComboBox<>(FXCollections.observableArrayList("Any", "EASY", "MEDIUM", "HARD")); final HBox box;
        Row(String nm, int cnt, int mk) {
            name.setText(nm); name.setPrefWidth(150); n.getValueFactory().setValue(cnt); n.setEditable(true); n.setPrefWidth(85);
            m.setValue(mk); df.setValue("Any"); tb.setPrefWidth(190); tb.getStyleClass().add("ghost");
            Button rm = new Button("Remove"); rm.getStyleClass().add("danger");
            box = new HBox(8, name, n, m, tb, df, rm); box.setAlignment(Pos.CENTER);
            n.valueProperty().addListener((o, a, b) -> bpUpdate()); m.valueProperty().addListener((o, a, b) -> bpUpdate());
            tb.setOnAction(e -> { pickTopics(tp); tb.setText(topicsLabel(tp)); });
            rm.setOnAction(e -> { rows.remove(this); bpRows.getChildren().remove(box); bpUpdate(); });
        }
    }

    @Override public void start(Stage s) {
        stage = s;
        try { initData(); }
        catch (Exception e) {
            Alert a = new Alert(Alert.AlertType.ERROR, "PaperWallah could not open its database.\n\n" + e.getMessage() + "\n\nThe database was not changed or recreated. Location: " + DatabaseManager.defaultDataDir());
            a.showAndWait(); Platform.exit(); return;
        }
        tabs = new TabPane(tab("Dashboard", dashTab()), tab("Question Bank", bankTab()), tab("Generate", genTab()),
            tab("Paper Editor", editorTab()), tab("Saved Papers", savedTab()), tab("Print Preview", printTab()), tab("Tags", tagTab()), tab("Database", dbTab()));
        tabs.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> refreshDash());
        refreshDash();
        Scene sc = new Scene(tabs, 1120, 740); sc.getStylesheets().add(css);
        s.setScene(sc); s.setTitle("PaperWallah"); s.show();
        if (firstReport != null) { Alert a = new Alert(Alert.AlertType.INFORMATION); style(a); a.setTitle("Data migration"); a.setHeaderText("Your data was moved to the new SQLite database");
            TextArea ta = area(firstReport, 16); ta.setEditable(false); ta.setPrefWidth(640); a.getDialogPane().setContent(ta); a.showAndWait(); }
    }

    /* ---------- helpers ---------- */
    private Tab tab(String n, javafx.scene.Node c) { Tab t = new Tab(n, c); t.setClosable(false); return t; }
    private void style(Dialog<?> d) { d.getDialogPane().getStylesheets().add(css); }
    private void alert(String m) { Alert a = new Alert(Alert.AlertType.WARNING, m); style(a); a.showAndWait(); }
    private boolean confirm(String m) { Alert a = new Alert(Alert.AlertType.CONFIRMATION, m); style(a); return a.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK; }
    private void initData() throws Exception {
        Path dir = DatabaseManager.defaultDataDir();
        db = new DatabaseManager(dir.resolve("PaperWallah.db")); db.open();
        st = new StorageService(dir.resolve("images"));
        topicSvc = new TopicService(db); questionSvc = new QuestionService(db); paperSvc = new PaperService(db);
        firstReport = new LegacyMigrator(db, Paths.get("PaperWallah", "data"), dir).runIfNeeded();
        reloadAll();
        tagSvc = new TaggingService(db);
        if (tagSvc.seedStarterIfNeeded()) tagSvc.retag(new ArrayList<>(bank), null);
        relatedSvc = new RelatedQuestionService(db, tagSvc);
    }
    private void reloadAll() throws SQLException { topics.setAll(topicSvc.load()); bank.setAll(questionSvc.loadAll()); papers.setAll(paperSvc.loadAll()); }
    /** On a failed save the in-memory lists are reloaded from the database so screen and data never disagree. */
    private void failed(String what, SQLException e) { alert("Could not save " + what + ": " + e.getMessage()); try { reloadAll(); } catch (SQLException ex) { alert("Could not reload data: " + ex.getMessage()); } }
    private void invalidateRelated() { if (relatedSvc != null) relatedSvc.invalidate(); }
    private void saveBank() { invalidateRelated(); try { questionSvc.sync(bank); } catch (SQLException e) { failed("questions", e); } }
    private void saveTopics() { try { topicSvc.sync(topics); } catch (SQLException e) { failed("topics", e); } }
    private void savePapers() { try { paperSvc.sync(papers); } catch (SQLException e) { failed("papers", e); } }
    private void refreshDash() { sQ.setText("" + bank.size()); sT.setText("" + topics.size()); sP.setText("" + papers.size()); refreshDb(); }
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
    private Label chip(String t, String extra) { Label l = new Label(t); l.getStyleClass().add("chip"); if (extra != null) l.getStyleClass().add(extra); return l; }
    private Label diffChip(Difficulty d) { String n = d.name(); return chip(n.charAt(0) + n.substring(1).toLowerCase(), "chip-" + n.toLowerCase()); }
    private String shorten(String s, int n) { return s.length() > n ? s.substring(0, n) + "..." : s; }
    private String topicsLabel(Set<String> s) { return s.isEmpty() ? "All topics" : s.size() == 1 ? shorten(s.iterator().next(), 26) : s.size() + " topics selected"; }
    private javafx.scene.Node thumb(String path, double h) {
        if (!st.imageExists(path)) return new Label("[image missing]");
        Image im = new Image(new File(path).toURI().toString(), 0, h, true, true);
        if (im.isError()) return new Label("[image unreadable]");
        ImageView v = new ImageView(im); v.setFitHeight(h); v.setPreserveRatio(true); return v;
    }
    private javafx.scene.Node bigImg(String path, double w) {
        if (!st.imageExists(path)) return new Label("[image missing: " + path + "]");
        Image im = new Image(new File(path).toURI().toString(), w, 0, true, true);
        if (im.isError()) return new Label("[image unreadable: " + path + "]");
        return new ImageView(im);
    }

    /* ---------- Dashboard ---------- */
    private VBox statCard(Label num, String cap) { num.getStyleClass().add("stat-num"); VBox c = new VBox(2, num, new Label(cap)); c.getStyleClass().add("card"); c.setMinWidth(180); return c; }
    private VBox navCard(String title, String sub, int tabIndex) {
        Label t = new Label(title); t.getStyleClass().add("navtitle"); Label d = new Label(sub); d.setWrapText(true); d.setMaxWidth(190); d.setTextAlignment(TextAlignment.CENTER);
        VBox c = new VBox(6, t, d); c.getStyleClass().add("navcard"); c.setMinWidth(220); c.setOnMouseClicked(e -> tabs.getSelectionModel().select(tabIndex)); return c;
    }
    private javafx.scene.Node dashTab() {
        Label hero = new Label("PaperWallah"); hero.getStyleClass().add("hero");
        Label sub = new Label("Smart question paper generator  -  works fully offline"); sub.getStyleClass().add("sub");
        HBox stats = new HBox(20, statCard(sQ, "Questions"), statCard(sT, "Topics"), statCard(sP, "Saved papers")); stats.setAlignment(Pos.CENTER);
        HBox nav = new HBox(20, navCard("Question Bank", "Add, edit and organise questions", 1), navCard("Generate Paper", "By total marks or by blueprint", 2), navCard("Saved Papers", "Reopen and print earlier papers", 4));
        nav.setAlignment(Pos.CENTER);
        VBox v = new VBox(28, hero, sub, stats, nav); v.setAlignment(Pos.CENTER); return v;
    }

    /* ---------- topic dialogs ---------- */
    private void pickTopics(Set<String> sel) {
        Dialog<ButtonType> d = new Dialog<>(); d.setTitle("Choose topics"); style(d);
        Map<String, BooleanProperty> m = new LinkedHashMap<>(); topics.forEach(t -> m.put(t, new SimpleBooleanProperty(sel.contains(t))));
        ListView<String> lv = new ListView<>(topics); lv.setCellFactory(CheckBoxListCell.forListView(m::get)); lv.setPrefSize(660, 420);
        Button none = new Button("Select none (= all topics)"); none.getStyleClass().add("ghost"); none.setOnAction(e -> m.values().forEach(p -> p.set(false)));
        d.getDialogPane().setContent(new VBox(8, new Label("Tick one or more topics. Nothing ticked means every topic."), lv, none));
        d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (d.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) { sel.clear(); m.forEach((k, v) -> { if (v.get()) sel.add(k); }); }
    }
    private void manageTopics() {
        Dialog<ButtonType> d = new Dialog<>(); d.setTitle("Manage topics"); style(d);
        ListView<String> lv = new ListView<>(topics); lv.setPrefSize(660, 380);
        TextField nt = new TextField(); nt.setPromptText("New topic name"); nt.setPrefWidth(500);
        Button add = new Button("Add topic"), del = new Button("Remove selected"); del.getStyleClass().add("danger");
        add.setOnAction(e -> {
            String t = nt.getText().trim(); if (t.isEmpty()) return;
            if (topics.stream().anyMatch(x -> x.equalsIgnoreCase(t))) { alert("That topic already exists."); return; }
            topics.add(t); saveTopics(); nt.clear(); refreshDash(); lv.scrollTo(t);
        });
        del.setOnAction(e -> {
            String t = lv.getSelectionModel().getSelectedItem(); if (t == null) return;
            long n = bank.stream().filter(q -> q.getTopic().equals(t)).count();
            if (n > 0) { alert(n + " question(s) use this topic. Delete them or move them to another topic first."); return; }
            if (confirm("Remove topic \"" + t + "\"?")) { topics.remove(t); saveTopics(); refreshDash(); }
        });
        HBox r = new HBox(8, nt, add); r.setAlignment(Pos.CENTER);
        d.getDialogPane().setContent(new VBox(10, lv, r, del)); d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.showAndWait();
    }

    /* ---------- question form (with live image previews) ---------- */
    private boolean form(String title, Draft d, List<String> sections) {
        Dialog<ButtonType> dlg = new Dialog<>(); dlg.setTitle(title); style(dlg); dlg.setResizable(true);
        TextArea q = area(d.q, 6), a = area(d.a, 3);
        ComboBox<Integer> m = new ComboBox<>(MARKS); m.setValue(d.marks >= 1 && d.marks <= 6 ? d.marks : 1);
        ComboBox<Difficulty> df = new ComboBox<>(FXCollections.observableArrayList(Difficulty.values())); df.setValue(d.d);
        ComboBox<String> t = new ComboBox<>(topics); t.setPromptText("Select topic"); t.setMaxWidth(Double.MAX_VALUE); if (topics.contains(d.topic)) t.setValue(d.topic);
        ComboBox<String> sec = null;
        if (sections != null) { sec = new ComboBox<>(FXCollections.observableArrayList(sections)); sec.setEditable(true); sec.setValue(d.section); }
        final ComboBox<String> secF = sec;
        List<String> added = new ArrayList<>(); FlowPane thumbs = new FlowPane(10, 10); thumbs.setPrefWrapLength(600);
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            thumbs.getChildren().clear();
            for (String p : new ArrayList<>(d.imgs)) {
                Button x = new Button("Remove"); x.getStyleClass().add("danger");
                x.setOnAction(e -> { d.imgs.remove(p); if (added.remove(p)) st.deleteImage(p); refresh[0].run(); });
                VBox cell = new VBox(3, thumb(p, 110), x); cell.setAlignment(Pos.CENTER); thumbs.getChildren().add(cell);
            }
            if (d.imgs.isEmpty()) thumbs.getChildren().add(new Label("No images attached"));
        };
        refresh[0].run();
        Button att = new Button("Attach image");
        att.setOnAction(e -> {
            FileChooser fc = new FileChooser(); fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
            File f = fc.showOpenDialog(stage); if (f == null) return;
            try { String p = st.importImage(f); d.imgs.add(p); added.add(p); refresh[0].run(); } catch (IOException ex) { alert("Could not add image: " + ex.getMessage()); }
        });
        VBox box = new VBox(8, new Label("Question (code: indentation is kept; MCQ: write A) B) C) D) lines)"), q, new Label("Answer / correct option"), a,
            new HBox(10, new Label("Marks"), m, new Label("Difficulty"), df), new Label("Topic"), t);
        if (secF != null) box.getChildren().addAll(new Label("Section (pick one or type a new name)"), secF);
        if (d.tagsEnabled) {
            box.getChildren().add(tagPanel(d, q, a));
            Button rel = new Button("Show related questions"); rel.getStyleClass().add("ghost");
            rel.setOnAction(e -> {
                if (q.getText().isBlank()) { alert("Write the question first."); return; }
                try { showRelated("Related to this question", relatedSvc.forText(d.id, q.getText(), a.getText(), t.getValue(), m.getValue(), df.getValue(), d.manualTags, d.excludedTags), d.id == 0 ? Set.of() : Set.of(d.id), null, null); }
                catch (SQLException ex) { alert("Could not find related questions: " + ex.getMessage()); }
            });
            box.getChildren().add(rel);
        }
        box.getChildren().addAll(att, thumbs);
        ScrollPane sp = new ScrollPane(box); sp.setFitToWidth(true); sp.setPrefSize(680, 600); box.setPadding(new Insets(8));
        dlg.getDialogPane().setContent(sp); dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dlg.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, ev -> {
            String err = q.getText().isBlank() ? "Question text is required." : a.getText().isBlank() ? "Answer is required." : t.getValue() == null ? "Please select a topic." : null;
            if (err != null) { alert(err); ev.consume(); }
        });
        if (dlg.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) { added.forEach(st::deleteImage); return false; }
        d.q = q.getText(); d.a = a.getText(); d.topic = t.getValue(); d.marks = m.getValue(); d.d = df.getValue();
        if (secF != null) { String s = secF.getEditor().getText().trim(); d.section = s.isEmpty() ? "Section A" : s; }
        return true;
    }

    /* ---------- Question Bank ---------- */
    private javafx.scene.Node bankCard(Question q) {
        HBox chips = new HBox(6, chip("#" + q.getQuestionId(), null), chip(q.getMarks() + (q.getMarks() == 1 ? " mark" : " marks"), "chip-marks"), diffChip(q.getDifficulty()), chip(shorten(q.getTopic(), 34), null));
        Label body = new Label(shorten(q.getQuestionContent().strip(), 170)); body.setWrapText(true); body.setMaxWidth(470);
        VBox c = new VBox(8, chips, body); c.getStyleClass().add("qcard"); return c;
    }
    private void showDetail(VBox box, Question q) {
        box.getChildren().clear();
        if (q == null) { Label l = new Label("Select a question to see it here."); l.getStyleClass().add("sub"); box.getChildren().add(l); return; }
        Label qh = new Label("QUESTION"), ah = new Label("ANSWER"); qh.getStyleClass().add("summary"); ah.getStyleClass().add("summary");
        Text qt = new Text(q.getQuestionContent()), at = new Text(q.getAnswerContent());
        for (Text t : List.of(qt, at)) { t.setFont(Font.font("Monospaced", 13)); t.setWrappingWidth(420); }
        box.getChildren().addAll(new HBox(6, chip("#" + q.getQuestionId(), null), chip(q.getMarks() + " marks", "chip-marks"), diffChip(q.getDifficulty())), chip(q.getTopic(), null), qh, qt);
        for (String p : q.getImagePaths()) box.getChildren().add(bigImg(p, 420));
        box.getChildren().addAll(new Separator(), ah, at, tagRow(q), relatedPreview(q));
    }
    private javafx.scene.Node bankTab() {
        FilteredList<Question> shown = new FilteredList<>(bank, x -> true);
        TextField search = new TextField(); search.setPromptText("Search text or topic");
        ComboBox<Integer> mf = new ComboBox<>(MARKS); mf.setPromptText("Marks");
        ComboBox<Difficulty> df = new ComboBox<>(FXCollections.observableArrayList(Difficulty.values())); df.setPromptText("Difficulty");
        ComboBox<String> tf = new ComboBox<>(topics); tf.setPromptText("Topic"); tf.setMaxWidth(230);
        Button clear = new Button("Clear filters"), manage = new Button("Manage topics"); clear.getStyleClass().add("ghost");
        Runnable filt = () -> shown.setPredicate(x -> (search.getText().isBlank() || (x.getQuestionContent() + x.getTopic()).toLowerCase().contains(search.getText().toLowerCase()))
            && (mf.getValue() == null || x.getMarks() == mf.getValue()) && (df.getValue() == null || x.getDifficulty() == df.getValue()) && (tf.getValue() == null || tf.getValue().equals(x.getTopic())));
        search.textProperty().addListener((o, a, b) -> filt.run()); mf.setOnAction(e -> filt.run()); df.setOnAction(e -> filt.run()); tf.setOnAction(e -> filt.run());
        clear.setOnAction(e -> { search.clear(); mf.setValue(null); df.setValue(null); tf.setValue(null); });
        manage.setOnAction(e -> manageTopics());
        ListView<Question> lv = new ListView<>(shown); lv.getStyleClass().add("q-list"); lv.setPrefWidth(520);
        lv.setCellFactory(l -> new ListCell<>() { @Override protected void updateItem(Question q, boolean e) { super.updateItem(q, e); setText(null); setGraphic(e || q == null ? null : bankCard(q)); } });
        VBox detail = new VBox(10); detail.getStyleClass().add("detail"); detail.setPadding(new Insets(16)); showDetail(detail, null);
        ScrollPane ds = new ScrollPane(detail); ds.setFitToWidth(true); ds.setPrefWidth(480);
        lv.getSelectionModel().selectedItemProperty().addListener((o, a, q) -> showDetail(detail, q));
        Button add = new Button("Add question"), edit = new Button("Edit"), del = new Button("Delete"); del.getStyleClass().add("danger");
        add.setOnAction(e -> { Draft d = new Draft(); d.tagsEnabled = true; if (form("Add question", d, null)) { Question q = new Question(editSvc.nextId(), d.q, d.a, d.marks, d.d, d.topic); q.getImagePaths().addAll(d.imgs); bank.add(q); saveBank(); applyTags(q, d); refreshDash(); lv.getSelectionModel().select(q); lv.scrollTo(q); } });
        edit.setOnAction(e -> {
            Question q = lv.getSelectionModel().getSelectedItem(); if (q == null) { alert("Select a question first."); return; }
            Draft d = new Draft(); d.tagsEnabled = true; d.id = q.getQuestionId(); loadUserTags(q, d); d.q = q.getQuestionContent(); d.a = q.getAnswerContent(); d.topic = q.getTopic(); d.marks = q.getMarks(); d.d = q.getDifficulty(); d.imgs = new ArrayList<>(q.getImagePaths());
            if (form("Edit question #" + q.getQuestionId(), d, null)) {
                q.setQuestionContent(d.q); q.setAnswerContent(d.a); q.setTopic(d.topic); q.setMarks(d.marks); q.setDifficulty(d.d);
                q.getImagePaths().clear(); q.getImagePaths().addAll(d.imgs); saveBank(); applyTags(q, d); lv.refresh(); showDetail(detail, q);
            }
        });
        del.setOnAction(e -> { Question q = lv.getSelectionModel().getSelectedItem(); if (q != null && confirm("Delete question #" + q.getQuestionId() + "?")) { bank.remove(q); saveBank(); refreshDash(); } });
        HBox body = new HBox(14, lv, ds); HBox.setHgrow(lv, Priority.ALWAYS); VBox.setVgrow(body, Priority.ALWAYS);
        return titled("Question Bank", new HBox(8, search, mf, df, tf, clear, manage), body, new HBox(12, add, edit, del));
    }

    /* ---------- Generate ---------- */
    private void bpUpdate() {
        int sum = rows.stream().mapToInt(r -> r.n.getValue() * r.m.getValue()).sum(); String t = bpTarget.getText().trim();
        boolean ok = t.matches("\\d+") && Integer.parseInt(t) == sum;
        bpStatus.setText("Sections total: " + sum + " marks - " + (ok ? "matches the target" : "does not match the target (" + (t.isEmpty() ? "empty" : t) + ")"));
        bpStatus.setStyle("-fx-font-weight:bold;-fx-text-fill:" + (ok ? "#2e7d32" : "#c62828"));
    }
    private void addRow(String name, int n, int m) { Row r = new Row(name, n, m); rows.add(r); bpRows.getChildren().add(r.box); bpUpdate(); }
    private javafx.scene.Node genTab() {
        TextField college = new TextField(), title = new TextField("Examination"), subject = new TextField(); college.setPromptText("College / institution");
        HBox hdr = new HBox(8, new Label("College"), college, new Label("Title"), title, new Label("Subject"), subject);
        // Mode A
        TextField total = new TextField("50"); total.setPrefWidth(80);
        HBox mk = new HBox(14); mk.setAlignment(Pos.CENTER); List<CheckBox> cbs = new ArrayList<>();
        for (int m = 1; m <= 6; m++) { CheckBox c = new CheckBox(m + (m == 1 ? " mark" : " marks")); c.setSelected(m == 1 || m == 3 || m == 5); c.setUserData(m); cbs.add(c); mk.getChildren().add(c); }
        Set<String> aTopics = new LinkedHashSet<>(); Button tbA = new Button("All topics"); tbA.getStyleClass().add("ghost"); tbA.setPrefWidth(220);
        tbA.setOnAction(e -> { pickTopics(aTopics); tbA.setText(topicsLabel(aTopics)); });
        Button gA = new Button("Generate paper");
        gA.setOnAction(e -> {
            try {
                int[] al = cbs.stream().filter(CheckBox::isSelected).mapToInt(c -> (Integer) c.getUserData()).toArray();
                run(new MarksBasedStrategy(Integer.parseInt(total.getText().trim()), al, new LinkedHashSet<>(aTopics)), title.getText(), subject.getText(), college.getText());
            } catch (NumberFormatException ex) { alert("Total marks must be a whole number."); }
        });
        HBox r1 = new HBox(8, new Label("Total marks"), total), r2 = new HBox(8, new Label("Topics"), tbA);
        VBox a = new VBox(16, r1, new Label("Allowed question marks"), mk, r2, gA); a.setAlignment(Pos.CENTER); a.setPadding(new Insets(20));
        // Mode B
        if (rows.isEmpty()) { addRow("Section A", 10, 1); addRow("Section B", 5, 3); addRow("Section C", 5, 5); }
        HBox head = new HBox(8); head.setAlignment(Pos.CENTER);
        for (String[] c : new String[][]{{"Section name", "150"}, {"Questions", "85"}, {"Marks each", "95"}, {"Topics", "190"}, {"Difficulty", "110"}}) { Label l = new Label(c[0]); l.setPrefWidth(Double.parseDouble(c[1])); l.getStyleClass().add("summary"); head.getChildren().add(l); }
        head.getChildren().add(new Label("                "));
        Button addSec = new Button("+ Add section"); addSec.getStyleClass().add("ghost"); addSec.setOnAction(e -> addRow("Section " + (char) ('A' + rows.size() % 26), 2, 2));
        bpTarget.textProperty().addListener((o, x, y) -> bpUpdate()); bpUpdate();
        Button gB = new Button("Generate paper");
        gB.setOnAction(e -> {
            if (rows.isEmpty()) { alert("Add at least one section."); return; }
            try {
                rows.forEach(r -> r.n.increment(0));
                List<BlueprintBasedStrategy.Section> secs = rows.stream().map(r -> new BlueprintBasedStrategy.Section(r.name.getText().isBlank() ? "Section" : r.name.getText().trim(), r.n.getValue(), r.m.getValue(), new LinkedHashSet<>(r.tp), r.df.getValue().equals("Any") ? null : Difficulty.valueOf(r.df.getValue()))).collect(Collectors.toList());
                run(new BlueprintBasedStrategy(secs, Integer.parseInt(bpTarget.getText().trim())), title.getText(), subject.getText(), college.getText());
            } catch (NumberFormatException ex) { alert("Target total must be a whole number."); }
        });
        bpTarget.setPrefWidth(80); HBox tr = new HBox(10, addSec, new Label("Target total marks"), bpTarget, bpStatus);
        VBox b = new VBox(12, head, bpRows, tr, gB); b.setAlignment(Pos.TOP_CENTER); b.setPadding(new Insets(16));
        ScrollPane bs = new ScrollPane(b); bs.setFitToWidth(true); tr.setAlignment(Pos.CENTER);
        TabPane modes = new TabPane(tab("By total marks", a), tab("By blueprint", bs)); VBox.setVgrow(modes, Priority.ALWAYS);
        return titled("Generate Paper", hdr, modes);
    }
    private void run(PaperGenerationStrategy s, String title, String subject, String college) {
        try {
            current = new Paper(0, title, subject, s.modeName(), s.generate(new ArrayList<>(bank))); current.setCollege(college);
            refreshEditor(); tabs.getSelectionModel().select(3);
        } catch (GenerationException ex) { alert(ex.getMessage()); }
    }

    /* ---------- Paper editor (cards + move animation) ---------- */
    private javafx.scene.Node editorTab() {
        editorBox.setPadding(new Insets(6)); ScrollPane sp = new ScrollPane(editorBox); sp.setFitToWidth(true); VBox.setVgrow(sp, Priority.ALWAYS);
        Button hdr = new Button("Edit header"), secs = new Button("Edit sections"), man = new Button("Add manual question"), save = new Button("Save paper"), prt = new Button("Print preview");
        hdr.setOnAction(e -> editHeader()); secs.setOnAction(e -> editSections());
        man.setOnAction(e -> {
            if (current == null) return; Draft d = new Draft(); List<PaperQuestion> l = current.getQuestions(); if (!l.isEmpty()) d.section = l.get(l.size() - 1).getSectionName();
            if (form("Manual question", d, sectionNames())) {
                PaperQuestion pq = new PaperQuestion(d.q, d.a, d.marks, d.topic, d.d, d.section); pq.getImagePaths().addAll(d.imgs); l.add(pq);
                if (confirm("Also add this question to the question bank?")) { editSvc.addAsNew(pq); saveBank(); refreshDash(); retagById(pq.getOriginalQuestionId()); }
                refreshEditor();
            }
        });
        save.setOnAction(e -> {
            if (current == null) return;
            if (current.getPaperId() == 0) current.setPaperId(papers.stream().mapToInt(Paper::getPaperId).max().orElse(0) + 1);
            Paper snap = deepCopy(current); papers.removeIf(p -> p.getPaperId() == snap.getPaperId()); papers.add(snap); savePapers(); refreshDash();
            Alert a = new Alert(Alert.AlertType.INFORMATION, "Paper saved (" + snap.getTotalMarks() + " marks)."); style(a); a.showAndWait();
        });
        prt.setOnAction(e -> { printPaper = current; tabs.getSelectionModel().select(5); updatePreview(); });
        totalLbl.setMaxWidth(Double.MAX_VALUE); totalLbl.setAlignment(Pos.CENTER); totalLbl.getStyleClass().add("summary");
        refreshEditor();
        return titled("Paper Editor", totalLbl, sp, new HBox(10, hdr, secs, man, save, prt));
    }
    private List<String> sectionNames() { return current.getQuestions().stream().map(PaperQuestion::getSectionName).distinct().collect(Collectors.toList()); }
    private void refreshEditor() {
        editorBox.getChildren().clear(); cards.clear();
        if (current == null) { totalLbl.setText("No paper open. Generate one or open a saved paper."); return; }
        totalLbl.setText(current.getTitle() + "   |   Total marks: " + current.getTotalMarks() + "   |   Questions: " + current.getQuestions().size());
        String sec = null; List<PaperQuestion> qs = current.getQuestions();
        for (int i = 0; i < qs.size(); i++) {
            PaperQuestion q = qs.get(i);
            if (!q.getSectionName().equals(sec)) {
                sec = q.getSectionName(); final String sn = sec;
                Label h = new Label(sn + "   (" + qs.stream().filter(x -> x.getSectionName().equals(sn)).mapToInt(PaperQuestion::getMarks).sum() + " marks)");
                h.getStyleClass().add("sec-head"); h.setMaxWidth(Double.MAX_VALUE); editorBox.getChildren().add(h);
            }
            VBox c = qCard(i, q); cards.add(c); editorBox.getChildren().add(c);
        }
    }
    private VBox qCard(int i, PaperQuestion q) {
        HBox chips = new HBox(6, chip("Q" + (i + 1), null), chip(q.getMarks() + (q.getMarks() == 1 ? " mark" : " marks"), "chip-marks"), diffChip(q.getDifficulty()), chip(shorten(q.getTopic(), 40), null));
        Label body = new Label(shorten(q.getQuestionContent().strip(), 320)); body.setWrapText(true); body.setFont(Font.font("Monospaced", 13));
        HBox imgs = new HBox(6); for (String p : q.getImagePaths()) imgs.getChildren().add(thumb(p, 48));
        Button up = new Button("▲ Up"), dn = new Button("▼ Down"), ed = new Button("Edit"), rp = new Button("Replace"), rl = new Button("Related"), rm = new Button("Remove"); rm.getStyleClass().add("danger");
        up.setOnAction(e -> move(i, -1)); dn.setOnAction(e -> move(i, 1)); ed.setOnAction(e -> editQuestion(i)); rp.setOnAction(e -> replaceQuestion(i)); rl.setOnAction(e -> relatedForPaperQuestion(i));
        rm.setOnAction(e -> { if (confirm("Remove this question from the paper?")) { current.getQuestions().remove(i); refreshEditor(); } });
        HBox bt = new HBox(8, up, dn, ed, rp, rl, rm); bt.setAlignment(Pos.CENTER_RIGHT);
        VBox c = new VBox(8, chips, body); if (!imgs.getChildren().isEmpty()) c.getChildren().add(imgs); c.getChildren().add(bt); c.getStyleClass().add("qcard"); return c;
    }
    /** Slides the two neighbouring cards past each other, then commits the swap. */
    private void move(int i, int dir) {
        int j = i + dir; if (animating || current == null || j < 0 || j >= cards.size()) return;
        int lo = Math.min(i, j), hi = Math.max(i, j); VBox a = cards.get(lo), b = cards.get(hi);
        Bounds ba = a.getBoundsInParent(), bb = b.getBoundsInParent();
        TranslateTransition ta = new TranslateTransition(Duration.millis(320), a), tb = new TranslateTransition(Duration.millis(320), b);
        ta.setToY(bb.getMaxY() - ba.getMaxY()); tb.setToY(ba.getMinY() - bb.getMinY()); ta.setInterpolator(Interpolator.EASE_BOTH); tb.setInterpolator(Interpolator.EASE_BOTH);
        (dir < 0 ? b : a).toFront();
        ParallelTransition pt = new ParallelTransition(ta, tb); animating = true;
        pt.setOnFinished(e -> { Collections.swap(current.getQuestions(), lo, hi); animating = false; refreshEditor(); });
        pt.play();
    }
    private void editQuestion(int i) {
        PaperQuestion pq = current.getQuestions().get(i);
        Draft d = new Draft(); d.q = pq.getQuestionContent(); d.a = pq.getAnswerContent(); d.topic = pq.getTopic(); d.marks = pq.getMarks(); d.d = pq.getDifficulty(); d.section = pq.getSectionName(); d.imgs = new ArrayList<>(pq.getImagePaths());
        if (!form("Edit question " + (i + 1), d, sectionNames())) return;
        boolean changed = !d.q.equals(pq.getQuestionContent()) || !d.a.equals(pq.getAnswerContent());
        pq.setQuestionContent(d.q); pq.setAnswerContent(d.a); pq.setMarks(d.marks); pq.setSectionName(d.section);
        pq.getImagePaths().clear(); pq.getImagePaths().addAll(d.imgs);
        if (changed && pq.getOriginalQuestionId() != null) {
            List<String> opts = List.of("Correct the existing question", "Create a new question from this", "Keep changes only in this paper");
            ChoiceDialog<String> cd = new ChoiceDialog<>(opts.get(2), opts); style(cd); cd.setTitle("Save edit"); cd.setHeaderText("How should this edit be saved?");
            QuestionEditService.Choice c = QuestionEditService.Choice.values()[cd.showAndWait().map(opts::indexOf).orElse(2)];
            editSvc.apply(pq, c); if (c != QuestionEditService.Choice.PAPER_ONLY) { saveBank(); refreshDash(); retagById(pq.getOriginalQuestionId()); }
        } else if (changed && confirm("Also add this manual question to the question bank?")) { editSvc.addAsNew(pq); saveBank(); refreshDash(); retagById(pq.getOriginalQuestionId()); }
        refreshEditor();
    }
    private void replaceQuestion(int i) {
        PaperQuestion old = current.getQuestions().get(i);
        Set<Integer> used = current.getQuestions().stream().map(PaperQuestion::getOriginalQuestionId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<String> opts = bank.stream().filter(q -> q.getMarks() == old.getMarks() && !used.contains(q.getQuestionId())).map(q -> q.getQuestionId() + ": " + shorten(q.getQuestionContent().replace("\n", " "), 90)).collect(Collectors.toList());
        if (opts.isEmpty()) { alert("No unused " + old.getMarks() + "-mark questions in the bank."); return; }
        ChoiceDialog<String> cd = new ChoiceDialog<>(opts.get(0), opts); style(cd); cd.setTitle("Replace question"); cd.setHeaderText("Pick a replacement (" + old.getMarks() + " marks)");
        cd.showAndWait().ifPresent(s -> { int id = Integer.parseInt(s.substring(0, s.indexOf(':')));
            bank.stream().filter(q -> q.getQuestionId() == id).findFirst().ifPresent(q -> { current.getQuestions().set(i, new PaperQuestion(q, old.getSectionName())); refreshEditor(); }); });
    }
    private void editHeader() {
        if (current == null) { alert("Open or generate a paper first."); return; }
        Dialog<ButtonType> dlg = new Dialog<>(); dlg.setTitle("Edit paper header"); style(dlg);
        TextField col = new TextField(current.getCollege()), ti = new TextField(current.getTitle()), su = new TextField(current.getSubject()), du = new TextField(current.getDuration());
        du.setPromptText("e.g. 3 Hours"); TextArea in = area(current.getInstructions(), 4); in.setWrapText(true);
        GridPane g = new GridPane(); g.setHgap(10); g.setVgap(8); g.setPadding(new Insets(12));
        String[] names = {"College / institution", "Exam title", "Subject", "Duration", "Instructions"}; javafx.scene.Node[] f = {col, ti, su, du, in};
        for (int k = 0; k < f.length; k++) { g.add(new Label(names[k]), 0, k); g.add(f[k], 1, k); ((Region) f[k]).setPrefWidth(430); }
        dlg.getDialogPane().setContent(g); dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (dlg.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            current.setCollege(col.getText().trim()); current.setTitle(ti.getText().trim()); current.setSubject(su.getText().trim());
            current.setDuration(du.getText().trim()); current.setInstructions(in.getText().trim()); refreshEditor();
        }
    }
    private void editSections() {
        if (current == null || current.getQuestions().isEmpty()) { alert("Open a paper with questions first."); return; }
        List<String> olds = sectionNames(); Dialog<ButtonType> dlg = new Dialog<>(); dlg.setTitle("Edit sections"); style(dlg);
        GridPane g = new GridPane(); g.setHgap(10); g.setVgap(8); g.setPadding(new Insets(12)); List<TextField> fs = new ArrayList<>();
        for (int k = 0; k < olds.size(); k++) { TextField tf = new TextField(olds.get(k)); tf.setPrefWidth(320); fs.add(tf); g.add(new Label("Section " + (k + 1) + " is called"), 0, k); g.add(tf, 1, k); }
        dlg.getDialogPane().setContent(new VBox(10, g, new Label("Rename a section here. Give two sections the same name to merge them.\nTo move one question to another section, use Edit on that question.")));
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (dlg.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        for (PaperQuestion q : current.getQuestions()) { int k = olds.indexOf(q.getSectionName()); String nn = fs.get(k).getText().trim(); if (!nn.isEmpty()) q.setSectionName(nn); }
        refreshEditor();
    }

    /* ---------- Saved papers ---------- */
    private javafx.scene.Node savedTab() {
        ListView<Paper> lv = new ListView<>(papers); lv.getStyleClass().add("q-list");
        lv.setCellFactory(l -> new ListCell<>() { @Override protected void updateItem(Paper p, boolean e) { super.updateItem(p, e); setText(null);
            if (e || p == null) { setGraphic(null); return; }
            Label t = new Label(p.getTitle() + " - " + p.getSubject()); t.getStyleClass().add("summary");
            VBox c = new VBox(6, t, new HBox(6, chip("#" + p.getPaperId(), null), chip(p.getTotalMarks() + " marks", "chip-marks"), chip(p.getQuestions().size() + " questions", null), chip(p.getGenerationMode(), null), chip("" + p.getCreationDate(), null)));
            c.getStyleClass().add("qcard"); setGraphic(c); } });
        Button open = new Button("Open / edit"), prt = new Button("Print preview"), del = new Button("Delete"); del.getStyleClass().add("danger");
        open.setOnAction(e -> { Paper p = lv.getSelectionModel().getSelectedItem(); if (p != null) { current = deepCopy(p); refreshEditor(); tabs.getSelectionModel().select(3); } });
        prt.setOnAction(e -> { Paper p = lv.getSelectionModel().getSelectedItem(); if (p != null) { printPaper = p; tabs.getSelectionModel().select(5); updatePreview(); } });
        del.setOnAction(e -> { Paper p = lv.getSelectionModel().getSelectedItem(); if (p != null && confirm("Delete this saved paper?")) { papers.remove(p); savePapers(); refreshDash(); } });
        VBox.setVgrow(lv, Priority.ALWAYS);
        return titled("Saved Papers", lv, new HBox(12, open, prt, del));
    }

    /* ---------- Print preview (boxed page with text, code and images) ---------- */
    private javafx.scene.Node printTab() {
        printKind.setValue("Question Paper"); printKind.setOnAction(e -> updatePreview());
        pv.getStyleClass().add("paper"); pv.setBorder(BLACK); pv.setMinWidth(620); pv.setMaxWidth(620);
        StackPane holder = new StackPane(pv); holder.setPadding(new Insets(10)); StackPane.setAlignment(pv, Pos.TOP_CENTER);
        ScrollPane sp = new ScrollPane(holder); sp.setFitToWidth(true); VBox.setVgrow(sp, Priority.ALWAYS);
        Button print = new Button("Print"); print.setOnAction(e -> doPrint());
        return titled("Print Preview", new HBox(10, printKind, print), sp);
    }
    private Text txt(String s, double w, boolean bold) { Text t = new Text(s); t.setFont(Font.font("Monospaced", bold ? FontWeight.BOLD : FontWeight.NORMAL, 11)); t.setWrappingWidth(w); return t; }
    private Text head(String s, double w, int size) { Text t = txt(s, w, true); t.setFont(Font.font("SansSerif", FontWeight.BOLD, size)); t.setTextAlignment(TextAlignment.CENTER); return t; }
    private javafx.scene.Node img(String path, double w) {
        if (!st.imageExists(path)) return txt("[Image missing: " + path + "]", w, false);
        Image im = new Image(new File(path).toURI().toString());
        if (im.isError()) return txt("[Image unreadable: " + path + "]", w, false);
        ImageView v = new ImageView(im); v.setPreserveRatio(true); v.setFitWidth(Math.min(w, 360)); v.setFitHeight(200); return v;
    }
    private List<javafx.scene.Node> items(double w) {
        List<javafx.scene.Node> out = new ArrayList<>(); Paper p = printPaper; String kind = printKind.getValue();
        if (!p.getCollege().isBlank()) out.add(head(p.getCollege(), w, 17));
        out.add(head(p.getTitle(), w, 14)); out.add(head(p.getSubject(), w, 13));
        if (kind.equals("Blueprint")) {
            StringBuilder sb = new StringBuilder("\nBLUEPRINT\n\nSection | Topic | Difficulty | Questions | Marks each | Total\n");
            Map<String, List<PaperQuestion>> g = new LinkedHashMap<>();
            for (PaperQuestion q : p.getQuestions()) g.computeIfAbsent(q.getSectionName() + "|" + q.getMarks(), k -> new ArrayList<>()).add(q);
            g.values().forEach(l -> { PaperQuestion f = l.get(0);
                sb.append(f.getSectionName()).append(" | ").append(l.stream().map(PaperQuestion::getTopic).distinct().map(t -> shorten(t, 40)).collect(Collectors.joining(", "))).append(" | ")
                  .append(l.stream().map(x -> x.getDifficulty().name()).distinct().collect(Collectors.joining(", "))).append(" | ").append(l.size()).append(" | ").append(f.getMarks()).append(" | ").append(l.size() * f.getMarks()).append("\n"); });
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
        pv.getChildren().setAll(items(540));
    }
    private void doPrint() {
        if (printPaper == null) { alert("Open or generate a paper first."); return; }
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) { alert("No printer available."); return; }
        if (!job.showPrintDialog(stage)) return;
        double pw = job.getJobSettings().getPageLayout().getPrintableWidth(), ph = job.getJobSettings().getPageLayout().getPrintableHeight();
        double inner = pw - 36, budget = ph - 36 - 34;
        List<VBox> pages = new ArrayList<>(); VBox cur = new VBox(4); double used = 0;
        for (javafx.scene.Node n : items(inner)) {
            double nh = n.getLayoutBounds().getHeight() + 4;
            if (used + nh > budget && !cur.getChildren().isEmpty()) { pages.add(cur); cur = new VBox(4); used = 0; }
            cur.getChildren().add(n); used += nh;
        }
        pages.add(cur);
        for (int i = 0; i < pages.size(); i++) {
            VBox pg = pages.get(i); pg.getChildren().add(txt("\nPage " + (i + 1) + " of " + pages.size(), inner, false));
            pg.setPadding(new Insets(16)); pg.setBorder(BLACK); pg.setPrefSize(pw - 2, ph - 2); pg.setMinSize(pw - 2, ph - 2); pg.setMaxSize(pw - 2, ph - 2);
            if (!job.printPage(pg)) { alert("Printing failed."); break; }
        }
        job.endJob();
    }
    /* ---------- Related questions ---------- */
    private void addToCurrent(Question q) {
        if (current == null) { alert("Open or generate a paper first, then add the question."); return; }
        List<PaperQuestion> l = current.getQuestions(); l.add(new PaperQuestion(q, l.isEmpty() ? "Section A" : l.get(l.size() - 1).getSectionName())); refreshEditor();
    }
    private javafx.scene.Node relatedPreview(Question q) {
        Label h = new Label("RELATED QUESTIONS"); h.getStyleClass().add("summary"); VBox v = new VBox(6, h);
        try {
            RelatedProbe p = relatedSvc.forQuestion(q); RelatedResult r = relatedSvc.suggest(p, RelatedMode.SAME_CONCEPT, Set.of(q.getQuestionId()), 3);
            if (r.related().isEmpty()) v.getChildren().add(new Label("No related questions found yet."));
            for (RelatedSuggestion s : r.related()) { Label l = new Label("#" + s.question().getQuestionId() + "  " + shorten(s.question().getQuestionContent().replace("\n", " "), 55)); l.setWrapText(true); l.setMaxWidth(300); v.getChildren().add(new HBox(8, chip("Match " + Math.round(s.score()), "chip-marks"), l)); }
            if (!r.duplicates().isEmpty()) v.getChildren().add(new Label(r.duplicates().size() + " possible duplicate(s) found"));
            Button more = new Button("Show all related..."); more.getStyleClass().add("ghost"); more.setOnAction(e -> showRelated("Related to question #" + q.getQuestionId(), p, Set.of(q.getQuestionId()), this::addToCurrent, null)); v.getChildren().add(more);
        } catch (SQLException e) { v.getChildren().add(new Label("Related questions unavailable")); }
        return v;
    }
    private void relatedForPaperQuestion(int i) {
        PaperQuestion pq = current.getQuestions().get(i); Integer oid = pq.getOriginalQuestionId(); Set<String> manual = new LinkedHashSet<>(), excl = new LinkedHashSet<>();
        try {
            if (oid != null) { for (QuestionTagInfo t : tagSvc.tagsOf(oid)) if (t.source().equals("MANUAL")) manual.add(t.name()); excl.addAll(tagSvc.excludedNames(oid)); }
            RelatedProbe p = relatedSvc.forText(oid == null ? 0 : oid, pq.getQuestionContent(), pq.getAnswerContent(), pq.getTopic(), pq.getMarks(), pq.getDifficulty(), manual, excl);
            Set<Integer> inPaper = current.getQuestions().stream().map(PaperQuestion::getOriginalQuestionId).filter(Objects::nonNull).collect(Collectors.toSet());
            showRelated("Related to question " + (i + 1) + " of the paper", p, inPaper, this::addToCurrent, q -> { current.getQuestions().set(i, new PaperQuestion(q, pq.getSectionName())); refreshEditor(); });
        } catch (SQLException e) { alert("Could not find related questions: " + e.getMessage()); }
    }
    private void viewQuestion(Question q) {
        VBox box = new VBox(10); box.getStyleClass().add("detail"); box.setPadding(new Insets(16)); showDetail(box, q);
        Dialog<ButtonType> d = new Dialog<>(); d.setTitle("Question #" + q.getQuestionId()); style(d); ScrollPane sp = new ScrollPane(box); sp.setFitToWidth(true); sp.setPrefSize(520, 520);
        d.getDialogPane().setContent(sp); d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.showAndWait();
    }
    private void compareDialog(RelatedProbe p, Question o) {
        VBox left = compareColumn("This question", p.text(), p.answer(), p.marks() + " marks", p.difficulty().name(), p.topic(), p.tags());
        VBox right = compareColumn("Suggestion #" + o.getQuestionId(), o.getQuestionContent(), o.getAnswerContent(), o.getMarks() + " marks", o.getDifficulty().name(), o.getTopic(), Set.of());
        HBox h = new HBox(16, left, right); HBox.setHgrow(left, Priority.ALWAYS); HBox.setHgrow(right, Priority.ALWAYS);
        Dialog<ButtonType> d = new Dialog<>(); d.setTitle("Compare side by side"); style(d); d.setResizable(true); ScrollPane sp = new ScrollPane(h); sp.setFitToWidth(true); sp.setPrefSize(860, 520);
        d.getDialogPane().setContent(sp); d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.showAndWait();
    }
    private VBox compareColumn(String title, String q, String a, String marks, String diff, String topic, Set<String> tags) {
        Label t = new Label(title); t.getStyleClass().add("summary"); Text qt = new Text(q), at = new Text(a);
        for (Text x : List.of(qt, at)) { x.setFont(Font.font("Monospaced", 13)); x.setWrappingWidth(380); }
        HBox chips = new HBox(6, chip(marks, "chip-marks"), chip(diff, null), chip(shorten(topic, 28), null)); FlowPane tg = new FlowPane(6, 6); tags.forEach(x -> tg.getChildren().add(chip(x, "chip-auto")));
        VBox v = new VBox(8, t, chips, tg, new Label("QUESTION"), qt, new Separator(), new Label("ANSWER"), at); v.getStyleClass().add("detail"); v.setPadding(new Insets(14)); return v;
    }
    private void weightsDialog() {
        try {
            RelatedWeights w = relatedSvc.weights(); String[] names = {"Shared tags (max points)", "Same topic", "Shared keywords", "Text similarity", "Same Bloom level (Phase 4)", "Marks very different (penalty)", "Marked unrelated (penalty)", "Hide suggestions below"};
            double[] vals = {w.tags(), w.topic(), w.keywords(), w.text(), w.bloom(), w.marksPenalty(), w.unrelatedPenalty(), w.minScore()};
            Dialog<ButtonType> d = new Dialog<>(); d.setTitle("Match score settings"); style(d); GridPane g = new GridPane(); g.setHgap(10); g.setVgap(8); g.setPadding(new Insets(12)); List<Spinner<Double>> sp = new ArrayList<>();
            for (int k = 0; k < names.length; k++) { Spinner<Double> s2 = new Spinner<>(0.0, 100.0, vals[k], 1.0); s2.setEditable(true); sp.add(s2); g.add(new Label(names[k]), 0, k); g.add(s2, 1, k); }
            d.getDialogPane().setContent(new VBox(8, g, new Label("The Match score is a transparent weighted sum of these points (default total 100). It is not an AI probability."))); d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            if (d.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) { sp.forEach(x -> x.increment(0)); relatedSvc.setWeights(new RelatedWeights(sp.get(0).getValue(), sp.get(1).getValue(), sp.get(2).getValue(), sp.get(3).getValue(), sp.get(4).getValue(), sp.get(5).getValue(), sp.get(6).getValue(), sp.get(7).getValue())); }
        } catch (SQLException e) { alert("Could not read or save the settings: " + e.getMessage()); }
    }
    /** The Related Questions panel. onAdd / onReplace may be null when the action does not apply. */
    private void showRelated(String title, RelatedProbe probe, Set<Integer> exclude, java.util.function.Consumer<Question> onAdd, java.util.function.Consumer<Question> onReplace) {
        Set<Integer> ex = new HashSet<>(exclude); Dialog<ButtonType> d = new Dialog<>(); d.setTitle("Related questions"); style(d); d.setResizable(true);
        ChoiceBox<RelatedMode> mode = new ChoiceBox<>(FXCollections.observableArrayList(RelatedMode.values())); mode.setValue(RelatedMode.SAME_CONCEPT);
        Button gear = new Button("Score settings"); gear.getStyleClass().add("ghost");
        Label head = new Label(title); head.getStyleClass().add("summary"); Label note = new Label("Match score = transparent weighted points (tags, topic, keywords, wording). It is not an AI probability.");
        VBox list = new VBox(10); ScrollPane sp = new ScrollPane(list); sp.setFitToWidth(true); sp.setPrefSize(760, 480);
        Runnable[] load = new Runnable[1];
        load[0] = () -> {
            list.getChildren().clear();
            try {
                RelatedResult r = relatedSvc.suggest(probe, mode.getValue(), ex, 12);
                if (r.related().isEmpty()) list.getChildren().add(new Label("No related questions found for this mode."));
                for (RelatedSuggestion s : r.related()) {
                    Question q = s.question(); HBox chips = new HBox(6, chip("Match score " + Math.round(s.score()), "chip-marks"), chip(q.getMarks() + " marks", null), diffChip(q.getDifficulty()), chip(shorten(q.getTopic(), 30), null));
                    FlowPane tg = new FlowPane(6, 6); s.sharedTags().forEach(x -> tg.getChildren().add(chip(x, "chip-auto")));
                    Label pv2 = new Label(shorten(q.getQuestionContent().strip(), 240)); pv2.setWrapText(true); pv2.setMaxWidth(690);
                    Label why = new Label("Why: " + (s.reasons().isEmpty() ? "weak match" : String.join("; ", s.reasons()))); why.setWrapText(true); why.setMaxWidth(690); why.getStyleClass().add("sub");
                    Button view = new Button("View"), cmp = new Button("Compare"), nr = new Button("Not related"), hide = new Button("Hide"); view.getStyleClass().add("ghost"); cmp.getStyleClass().add("ghost"); nr.getStyleClass().add("ghost"); hide.getStyleClass().add("ghost");
                    view.setOnAction(e -> viewQuestion(q)); cmp.setOnAction(e -> compareDialog(probe, q));
                    nr.setDisable(probe.id() == 0); hide.setDisable(probe.id() == 0);
                    nr.setOnAction(e -> { try { relatedSvc.feedback(probe.id(), q.getQuestionId(), "UNRELATED"); load[0].run(); } catch (SQLException x) { alert(x.getMessage()); } });
                    hide.setOnAction(e -> { try { relatedSvc.feedback(probe.id(), q.getQuestionId(), "EXCLUDE"); load[0].run(); } catch (SQLException x) { alert(x.getMessage()); } });
                    HBox bt = new HBox(8, view, cmp);
                    if (onAdd != null) { Button add = new Button("Add to current paper"); add.setOnAction(e -> { onAdd.accept(q); ex.add(q.getQuestionId()); load[0].run(); }); bt.getChildren().add(add); }
                    if (onReplace != null) { Button rp = new Button("Replace selected"); rp.setOnAction(e -> { onReplace.accept(q); d.close(); }); bt.getChildren().add(rp); }
                    bt.getChildren().addAll(nr, hide);
                    VBox card = new VBox(6, chips, tg, pv2, why, bt); card.getStyleClass().add("qcard"); list.getChildren().add(card);
                }
                if (!r.duplicates().isEmpty()) {
                    Label h2 = new Label("Possible duplicates (flagged, not recommended as alternatives)"); h2.getStyleClass().add("summary"); list.getChildren().add(h2);
                    for (RelatedSuggestion s : r.duplicates()) { Button view = new Button("View"); view.getStyleClass().add("ghost"); view.setOnAction(e -> viewQuestion(s.question())); Label l = new Label("#" + s.question().getQuestionId() + "  " + shorten(s.question().getQuestionContent().replace("\n", " "), 70) + "   [" + s.reasons().get(0) + "]"); l.setWrapText(true); l.setMaxWidth(600); list.getChildren().add(new HBox(8, view, l)); }
                }
            } catch (SQLException e) { list.getChildren().add(new Label("Could not load suggestions: " + e.getMessage())); }
        };
        mode.setOnAction(e -> load[0].run()); gear.setOnAction(e -> { weightsDialog(); load[0].run(); }); load[0].run();
        HBox bar = new HBox(10, new Label("Show:"), mode, gear); bar.setAlignment(Pos.CENTER_LEFT);
        d.getDialogPane().setContent(new VBox(8, head, note, bar, sp)); d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.showAndWait();
    }

    /* ---------- Tags ---------- */
    private void retagQuietly(List<Question> qs) { invalidateRelated(); try { tagSvc.retag(qs, null); } catch (SQLException e) { alert("Could not update tags: " + e.getMessage()); } }
    private void retagById(Integer id) { if (id != null) bank.stream().filter(x -> x.getQuestionId() == id).findFirst().ifPresent(x -> retagQuietly(List.of(x))); }
    private void loadUserTags(Question q, Draft d) {
        try { for (QuestionTagInfo t : tagSvc.tagsOf(q.getQuestionId())) if (t.source().equals("MANUAL")) d.manualTags.add(t.name()); d.excludedTags.addAll(tagSvc.excludedNames(q.getQuestionId())); }
        catch (SQLException e) { alert("Could not load tags: " + e.getMessage()); }
    }
    private void applyTags(Question q, Draft d) { invalidateRelated(); try { tagSvc.applyUserTags(q, d.manualTags, d.excludedTags); } catch (SQLException e) { alert("Could not save tags: " + e.getMessage()); } }
    private javafx.scene.Node tagRow(Question q) {
        FlowPane fp = new FlowPane(6, 6); Label h = new Label("TAGS"); h.getStyleClass().add("summary");
        try {
            Map<Integer, TagMatch> why = tagSvc.analyze(q.getQuestionContent(), q.getAnswerContent());
            for (QuestionTagInfo t : tagSvc.tagsOf(q.getQuestionId())) {
                boolean auto = t.source().equals("AUTO"); Label c = chip(t.name() + (auto ? "  (auto)" : "  (manual)"), auto ? "chip-auto" : "chip-manual");
                TagMatch m = why.get(t.tagId()); if (auto && m != null) Tooltip.install(c, new Tooltip("Matched \"" + m.phrase() + "\" in the " + m.where() + " (confidence " + Math.round(m.confidence() * 100) + "%)"));
                fp.getChildren().add(c);
            }
            if (fp.getChildren().isEmpty()) fp.getChildren().add(new Label("No tags yet"));
        } catch (SQLException e) { fp.getChildren().add(new Label("Tags unavailable")); }
        return new VBox(6, h, fp);
    }
    /** Live tag suggestions while typing (debounced; nothing is written until the question is saved). */
    private javafx.scene.Node tagPanel(Draft d, TextArea q, TextArea a) {
        FlowPane auto = new FlowPane(6, 6), manual = new FlowPane(6, 6), excl = new FlowPane(6, 6);
        ComboBox<String> pick = new ComboBox<>(); pick.setEditable(true); pick.setPromptText("Type or choose a tag");
        try { pick.getItems().setAll(tagSvc.allTags().stream().map(Tag::name).collect(Collectors.toList())); } catch (SQLException e) { /* list stays empty */ }
        Button add = new Button("Add tag"); add.getStyleClass().add("ghost");
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            auto.getChildren().clear(); manual.getChildren().clear(); excl.getChildren().clear();
            try {
                Map<Integer, String> names = tagSvc.tagNames();
                for (TagMatch m : tagSvc.analyze(q.getText(), a.getText()).values()) {
                    String n = names.get(m.tagId()); if (n == null || d.manualTags.contains(n) || d.excludedTags.contains(n)) continue;
                    Label c = chip(n, "chip-auto"); Tooltip.install(c, new Tooltip("Matched \"" + m.phrase() + "\" in the " + m.where()));
                    Button x = new Button("x"); x.getStyleClass().add("danger"); x.setOnAction(e -> { d.excludedTags.add(n); refresh[0].run(); });
                    auto.getChildren().add(new HBox(3, c, x));
                }
            } catch (SQLException e) { auto.getChildren().add(new Label("Suggestions unavailable")); }
            if (auto.getChildren().isEmpty()) auto.getChildren().add(new Label("None yet - suggestions appear as you type"));
            for (String n : d.manualTags) { Button x = new Button("x"); x.getStyleClass().add("danger"); x.setOnAction(e -> { d.manualTags.remove(n); refresh[0].run(); }); manual.getChildren().add(new HBox(3, chip(n, "chip-manual"), x)); }
            for (String n : d.excludedTags) { Button r = new Button("restore"); r.getStyleClass().add("ghost"); r.setOnAction(e -> { d.excludedTags.remove(n); refresh[0].run(); }); excl.getChildren().add(new HBox(3, chip(n, null), r)); }
        };
        PauseTransition deb = new PauseTransition(Duration.millis(450)); deb.setOnFinished(e -> refresh[0].run());
        q.textProperty().addListener((o, x, y) -> deb.playFromStart()); a.textProperty().addListener((o, x, y) -> deb.playFromStart());
        add.setOnAction(e -> { String n = pick.getEditor().getText().trim(); if (!n.isEmpty()) { d.manualTags.add(n); d.excludedTags.remove(n); pick.getEditor().clear(); refresh[0].run(); } });
        refresh[0].run(); Label h = new Label("TAGS"); h.getStyleClass().add("summary");
        return new VBox(6, h, new Label("Suggested automatically (hover for the reason, x = not relevant)"), auto, new Label("Manual tags"), manual, new HBox(8, pick, add), new Label("Excluded for this question"), excl);
    }
    private void tagErr(SQLException e) { alert(e.getMessage() != null && e.getMessage().contains("UNIQUE") ? "That name or phrase already exists." : "Tag operation failed: " + e.getMessage()); }
    private Optional<String[]> textDialog(String title, String l1, String v1, String l2, String v2) {
        Dialog<ButtonType> d = new Dialog<>(); d.setTitle(title); style(d); TextField a = new TextField(v1), b = new TextField(v2); a.setPrefWidth(320);
        GridPane g = new GridPane(); g.setHgap(10); g.setVgap(8); g.setPadding(new Insets(12)); g.add(new Label(l1), 0, 0); g.add(a, 1, 0); g.add(new Label(l2), 0, 1); g.add(b, 1, 1);
        d.getDialogPane().setContent(g); d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, ev -> { if (a.getText().isBlank()) { alert("A name is required."); ev.consume(); } });
        return d.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK ? Optional.of(new String[]{a.getText().trim(), b.getText().trim()}) : Optional.empty();
    }
    private Optional<Object[]> ruleDialog(TagRule r) {
        Dialog<ButtonType> d = new Dialog<>(); d.setTitle(r == null ? "New rule" : "Edit rule"); style(d);
        TextField ph = new TextField(r == null ? "" : r.phrase()); ph.setPrefWidth(320);
        ChoiceBox<MatchMode> md = new ChoiceBox<>(FXCollections.observableArrayList(MatchMode.values())); md.setValue(r == null ? MatchMode.PHRASE : r.mode());
        Spinner<Integer> pr = new Spinner<>(-100, 1000, r == null ? 0 : r.priority()); pr.setEditable(true);
        CheckBox en = new CheckBox("Enabled"); en.setSelected(r == null || r.enabled());
        GridPane g = new GridPane(); g.setHgap(10); g.setVgap(8); g.setPadding(new Insets(12));
        g.add(new Label("Keyword or phrase"), 0, 0); g.add(ph, 1, 0); g.add(new Label("Match type"), 0, 1); g.add(md, 1, 1); g.add(new Label("Priority"), 0, 2); g.add(pr, 1, 2); g.add(en, 1, 3);
        Label help = new Label("EXACT_WORD: one whole word.  PHRASE: whole words in order.  PREFIX: last word may continue (polymorph matches polymorphism).\nHigher priority wins; then the longer phrase. Matching ignores case and punctuation and never matches inside other words.");
        help.setWrapText(true); help.setMaxWidth(430);
        d.getDialogPane().setContent(new VBox(8, g, help)); d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        d.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, ev -> { if (TextUtil.normalize(ph.getText()).isEmpty()) { alert("Enter at least one word."); ev.consume(); } });
        if (d.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return Optional.empty();
        pr.increment(0); return Optional.of(new Object[]{ph.getText().trim(), md.getValue(), pr.getValue(), en.isSelected()});
    }
    private void refreshTagScreen() {
        invalidateRelated(); if (tagList == null) return; Tag sel = tagList.getSelectionModel().getSelectedItem();
        try {
            tagCounts = tagSvc.questionCounts(); tagList.getItems().setAll(tagSvc.allTags());
            if (sel != null) tagList.getItems().stream().filter(t -> t.id() == sel.id()).findFirst().ifPresent(t -> tagList.getSelectionModel().select(t));
        } catch (SQLException e) { tagErr(e); }
        refreshRules(); tagList.refresh();
    }
    private void refreshRules() {
        Tag t = tagList.getSelectionModel().getSelectedItem();
        try { ruleTable.getItems().setAll(t == null ? List.<TagRule>of() : tagSvc.rulesFor(t.id())); } catch (SQLException e) { tagErr(e); }
    }
    private javafx.scene.Node tagTab() {
        tagList = new ListView<>(); tagList.getStyleClass().add("q-list"); tagList.setPrefWidth(330);
        tagList.setCellFactory(l -> new ListCell<>() { @Override protected void updateItem(Tag t, boolean e) { super.updateItem(t, e); setText(e || t == null ? null : t.name() + (t.group() == null ? "" : "   [" + t.group() + "]") + "   - " + tagCounts.getOrDefault(t.id(), 0) + " questions"); } });
        ruleTable = new TableView<>(); ruleTable.setPlaceholder(new Label("Select a tag to see its trigger phrases"));
        String[] heads = {"Trigger phrase", "Match type", "Priority", "Enabled"}; int[] widths = {260, 110, 80, 80};
        for (int i = 0; i < 4; i++) { final int k = i; TableColumn<TagRule, String> c = new TableColumn<>(heads[i]); c.setPrefWidth(widths[i]);
            c.setCellValueFactory(cd -> { TagRule r = cd.getValue(); return new javafx.beans.property.SimpleStringProperty(k == 0 ? r.phrase() : k == 1 ? r.mode().name() : k == 2 ? "" + r.priority() : r.enabled() ? "yes" : "no"); }); ruleTable.getColumns().add(c); }
        tagList.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> refreshRules());
        Button nt = new Button("New tag"), et = new Button("Edit tag"), mt = new Button("Merge into..."), dt = new Button("Delete tag"); dt.getStyleClass().add("danger");
        nt.setOnAction(e -> textDialog("New tag", "Tag name", "", "Group / subject (optional)", "").ifPresent(v -> { try { tagSvc.createTag(v[0], v[1]); refreshTagScreen(); } catch (SQLException ex) { tagErr(ex); } }));
        et.setOnAction(e -> { Tag t = tagList.getSelectionModel().getSelectedItem(); if (t == null) return;
            textDialog("Edit tag", "Tag name", t.name(), "Group / subject", t.group() == null ? "" : t.group()).ifPresent(v -> { try { tagSvc.updateTag(t.id(), v[0], v[1]); refreshTagScreen(); } catch (SQLException ex) { tagErr(ex); } }); });
        mt.setOnAction(e -> { Tag t = tagList.getSelectionModel().getSelectedItem(); if (t == null) return;
            List<String> others = tagList.getItems().stream().filter(x -> x.id() != t.id()).map(Tag::name).collect(Collectors.toList()); if (others.isEmpty()) return;
            ChoiceDialog<String> cd = new ChoiceDialog<>(others.get(0), others); style(cd); cd.setHeaderText("Merge \"" + t.name() + "\" into which tag?");
            cd.showAndWait().ifPresent(n -> tagList.getItems().stream().filter(x -> x.name().equals(n)).findFirst().ifPresent(target -> { if (confirm("Move all rules and questions of \"" + t.name() + "\" into \"" + n + "\" and delete \"" + t.name() + "\"?")) try { tagSvc.mergeTags(t.id(), target.id()); refreshTagScreen(); } catch (SQLException ex) { tagErr(ex); } })); });
        dt.setOnAction(e -> { Tag t = tagList.getSelectionModel().getSelectedItem(); if (t != null && confirm("Delete tag \"" + t.name() + "\", its rules and its links to questions?")) try { tagSvc.deleteTag(t.id()); refreshTagScreen(); } catch (SQLException ex) { tagErr(ex); } });
        Button ar = new Button("Add rule"), er = new Button("Edit rule"), tr = new Button("Enable / disable"), pr = new Button("Preview matches"), dr = new Button("Delete rule"); dr.getStyleClass().add("danger");
        ar.setOnAction(e -> { Tag t = tagList.getSelectionModel().getSelectedItem(); if (t == null) { alert("Select a tag first."); return; }
            ruleDialog(null).ifPresent(v -> { try { tagSvc.addRule(t.id(), (String) v[0], (MatchMode) v[1], (Integer) v[2]); refreshTagScreen(); } catch (SQLException ex) { tagErr(ex); } }); });
        er.setOnAction(e -> { TagRule r = ruleTable.getSelectionModel().getSelectedItem(); if (r == null) return;
            ruleDialog(r).ifPresent(v -> { try { tagSvc.updateRule(new TagRule(r.id(), (String) v[0], "", r.tagId(), (MatchMode) v[1], (Boolean) v[3], (Integer) v[2])); refreshTagScreen(); } catch (SQLException ex) { tagErr(ex); } }); });
        tr.setOnAction(e -> { TagRule r = ruleTable.getSelectionModel().getSelectedItem(); if (r != null) try { tagSvc.setRuleEnabled(r, !r.enabled()); refreshTagScreen(); } catch (SQLException ex) { tagErr(ex); } });
        dr.setOnAction(e -> { TagRule r = ruleTable.getSelectionModel().getSelectedItem(); if (r != null && confirm("Delete the rule \"" + r.phrase() + "\"?")) try { tagSvc.deleteRule(r.id()); refreshTagScreen(); } catch (SQLException ex) { tagErr(ex); } });
        pr.setOnAction(e -> { TagRule r = ruleTable.getSelectionModel().getSelectedItem(); if (r == null) { alert("Select a rule first."); return; }
            List<String> hits = tagSvc.previewRule(r, new ArrayList<>(bank)); Dialog<ButtonType> d = new Dialog<>(); d.setTitle("Rule preview"); style(d);
            TextArea ta = area(hits.size() + " question(s) match \"" + r.phrase() + "\":\n\n" + String.join("\n\n", hits), 18); ta.setEditable(false); ta.setWrapText(true); ta.setPrefWidth(680);
            d.getDialogPane().setContent(ta); d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); d.showAndWait(); });
        TextField ignore = new TextField(); ignore.setPrefWidth(360); ignore.setPromptText("words to ignore, separated by commas");
        try { ignore.setText(tagSvc.ignoreWords()); } catch (SQLException e) { /* leave blank */ }
        Button saveIgnore = new Button("Save ignore list"); saveIgnore.getStyleClass().add("ghost");
        saveIgnore.setOnAction(e -> { try { tagSvc.setIgnoreWords(ignore.getText()); alert("Saved. Re-tag the question bank to apply it."); } catch (SQLException ex) { tagErr(ex); } });
        Button retag = new Button("Re-tag question bank"); retag.setOnAction(e -> runRetag());
        VBox left = new VBox(8, new Label("TAGS"), tagList, new HBox(6, nt, et), new HBox(6, mt, dt)); VBox.setVgrow(tagList, Priority.ALWAYS);
        VBox right = new VBox(8, new Label("TRIGGER PHRASES FOR THE SELECTED TAG"), ruleTable, new HBox(6, ar, er, tr), new HBox(6, pr, dr)); VBox.setVgrow(ruleTable, Priority.ALWAYS); HBox.setHgrow(right, Priority.ALWAYS);
        for (Label l : List.of((Label) left.getChildren().get(0), (Label) right.getChildren().get(0))) l.getStyleClass().add("summary");
        HBox body = new HBox(14, left, right); VBox.setVgrow(body, Priority.ALWAYS);
        refreshTagScreen();
        return titled("Tag Manager", body, new HBox(10, new Label("Ignore words:"), ignore, saveIgnore, retag));
    }
    /** Re-tags the whole bank on a background thread with a progress window, so the screen stays responsive. */
    private void runRetag() {
        List<Question> snapshot = new ArrayList<>(bank);
        Task<Void> task = new Task<>() { @Override protected Void call() throws Exception { tagSvc.retag(snapshot, p -> updateProgress(p, 1.0)); return null; } };
        ProgressBar pb = new ProgressBar(0); pb.setPrefWidth(360); pb.progressProperty().bind(task.progressProperty());
        Stage ps = new Stage(); ps.initOwner(stage); ps.initModality(Modality.APPLICATION_MODAL); ps.setTitle("Re-tagging"); ps.setOnCloseRequest(ev -> ev.consume());
        VBox pbox = new VBox(12, new Label("Re-tagging " + snapshot.size() + " questions... manual tags are kept."), pb); pbox.setPadding(new Insets(20)); pbox.setAlignment(Pos.CENTER);
        Scene sc = new Scene(pbox); sc.getStylesheets().add(css); ps.setScene(sc);
        task.setOnSucceeded(ev -> { ps.close(); refreshTagScreen(); alert("Re-tagging finished for " + snapshot.size() + " questions."); });
        task.setOnFailed(ev -> { ps.close(); alert("Re-tagging failed and was rolled back: " + task.getException().getMessage()); });
        Thread t = new Thread(task, "retag"); t.setDaemon(true); t.start(); ps.show();
    }

    /* ---------- Database screen, CSV import / export, backup ---------- */
    private javafx.scene.Node dbTab() {
        dbInfo = area("", 16); dbInfo.setEditable(false); VBox.setVgrow(dbInfo, Priority.ALWAYS);
        Button imp = new Button("Import questions (CSV)"), exp = new Button("Export questions (CSV)"), expT = new Button("Export topics (TXT)"), bk = new Button("Back up database");
        imp.setOnAction(e -> importCsv());
        exp.setOnAction(e -> { File f = saveFile("questions.csv", "CSV files", "*.csv"); if (f != null) try { ImportExportService.exportQuestions(bank, f.toPath()); alert("Exported " + bank.size() + " questions."); } catch (IOException ex) { alert("Export failed: " + ex.getMessage()); } });
        expT.setOnAction(e -> { File f = saveFile("topics.txt", "Text files", "*.txt"); if (f != null) try { ImportExportService.exportTopics(topics, f.toPath()); alert("Exported " + topics.size() + " topics."); } catch (IOException ex) { alert("Export failed: " + ex.getMessage()); } });
        bk.setOnAction(e -> { File f = saveFile("PaperWallah-backup-" + java.time.LocalDate.now() + ".db", "Database backup", "*.db"); if (f == null) return;
            try { f.delete(); BackupService.backup(db, f.toPath()); alert("Backup saved to " + f); } catch (SQLException ex) { alert("Backup failed: " + ex.getMessage()); } });
        refreshDb();
        return titled("Database", new HBox(10, imp, exp, expT, bk), dbInfo);
    }
    private File saveFile(String name, String desc, String ext) { FileChooser fc = new FileChooser(); fc.setInitialFileName(name); fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(desc, ext)); return fc.showSaveDialog(stage); }
    private void refreshDb() {
        if (dbInfo == null || db == null) return;
        String ver; try { ver = "" + db.schemaVersion(); } catch (SQLException e) { ver = "unknown"; }
        dbInfo.setText("Database file: " + db.file() + "\nStatus: connected (SQLite, WAL mode, foreign keys on)\nSchema version: " + ver + "\nImages folder: " + db.file().getParent().resolve("images")
            + "\n\nRecords\n  Topics: " + topics.size() + "\n  Questions: " + bank.size() + "\n  Saved papers: " + papers.size() + "\n\n--- Last migration report ---\n" + lastReport());
    }
    private String lastReport() { try { return db.setting("migration_report", "(none)"); } catch (SQLException e) { return "(unavailable)"; } }
    private void importCsv() {
        FileChooser fc = new FileChooser(); fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files", "*.csv")); File f = fc.showOpenDialog(stage); if (f == null) return;
        List<ImportExportService.Row> rows;
        try { rows = ImportExportService.parse(f.toPath()); } catch (IOException | RuntimeException e) { alert("Cannot read this CSV: " + e.getMessage()); return; }
        Map<String, Question> byText = new HashMap<>(); for (Question q : bank) byText.putIfAbsent(ImportExportService.norm(q.getQuestionContent()), q);
        List<ImportExportService.Row> valid = rows.stream().filter(r -> r.error() == null).collect(Collectors.toList());
        long dups = valid.stream().filter(r -> byText.containsKey(ImportExportService.norm(r.question()))).count();
        StringBuilder pv2 = new StringBuilder("Rows read: " + rows.size() + "\nValid: " + valid.size() + "\nInvalid: " + (rows.size() - valid.size()) + "\nDuplicates of existing questions: " + dups + "\n");
        rows.stream().filter(r -> r.error() != null).limit(8).forEach(r -> pv2.append("\n  line ").append(r.line()).append(": ").append(r.error()));
        pv2.append("\n\nPreview:"); valid.stream().limit(6).forEach(r -> pv2.append("\n  [").append(r.marks()).append("] ").append(shorten(r.topic(), 30)).append(": ").append(shorten(r.question().replace("\n", " "), 60)));
        Dialog<ButtonType> d = new Dialog<>(); d.setTitle("Import preview"); style(d);
        ChoiceBox<String> policy = new ChoiceBox<>(FXCollections.observableArrayList("Skip duplicates", "Update existing question", "Import duplicates as new")); policy.setValue("Skip duplicates");
        TextArea ta = area(pv2.toString(), 14); ta.setEditable(false); ta.setPrefWidth(620);
        d.getDialogPane().setContent(new VBox(10, ta, new HBox(8, new Label("If a question already exists:"), policy))); d.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        if (valid.isEmpty() || d.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) { if (valid.isEmpty()) alert("No valid rows to import."); return; }
        int mode = policy.getItems().indexOf(policy.getValue()), added = 0, updated = 0, skipped = 0; List<Question> touched = new ArrayList<>();
        for (ImportExportService.Row r : valid) {
            Question ex = byText.get(ImportExportService.norm(r.question()));
            if (ex != null && mode == 0) { skipped++; continue; }
            if (!topics.contains(r.topic())) topics.add(r.topic());
            if (ex != null && mode == 1) { ex.setAnswerContent(r.answer()); ex.setMarks(r.marks()); ex.setDifficulty(r.difficulty()); ex.setTopic(r.topic()); ex.getImagePaths().clear(); ex.getImagePaths().addAll(r.images()); touched.add(ex); updated++; }
            else { Question q = new Question(editSvc.nextId(), r.question(), r.answer(), r.marks(), r.difficulty(), r.topic()); q.getImagePaths().addAll(r.images()); bank.add(q); byText.putIfAbsent(ImportExportService.norm(r.question()), q); touched.add(q); added++; }
        }
        saveBank(); retagQuietly(touched); refreshDash(); alert("Import finished: " + added + " added, " + updated + " updated, " + skipped + " skipped.");
    }
    public static void main(String[] a) { launch(a); }
}
