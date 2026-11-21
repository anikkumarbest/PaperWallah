# PaperWallah – Smart Question Paper Generator

A desktop application (Java + JavaFX) that helps teachers keep a question bank, generate balanced question papers automatically, fine-tune them, and print the question paper, answer key and blueprint.

It works **fully offline**. There is no database server, no internet connection and no API. All data is stored in plain files on your computer.

---

## Features

| Area | What you can do |
|---|---|
| **Question Bank** | Add, edit, delete and search questions. Filter by marks, difficulty and topic. Questions show as cards, with a formatted detail panel (question, images, answer). |
| **Marks** | Each question is worth 1 to 6 marks. |
| **Topics** | Starts with 60 syllabus topics. Teachers can add or remove topics (a topic that still has questions cannot be removed). |
| **Images** | Attach images to questions with a live thumbnail preview. Every image is copied into the app's `images` folder under a unique name, so two files with the same name never clash. |
| **Generate – by total marks** | Enter a total (for example 50) and tick the allowed question marks. The app picks the most even split of question counts and chooses random, non-repeating questions. |
| **Generate – by blueprint** | Define sections as rows: name, number of questions, marks each, topics and difficulty. A live message shows whether the sections add up to your target total. |
| **Multiple topics** | Both generation modes allow one, several or all topics. |
| **Paper Editor** | Move questions up and down (animated), edit, replace from the bank, remove, add manual questions, edit the paper header, and rename or merge sections. |
| **Smart edit saving** | When you change a bank question inside a paper you choose: *correct the existing question*, *create a new question*, or *keep the change in this paper only*. |
| **Saved Papers** | Papers are saved as separate copies, so later changes to the bank never alter an old paper. |
| **Print Preview** | Question Paper, Answer Key and Blueprint, with a page border, centred header, images and page numbers. Print to any printer or to PDF. |

---

## Requirements

- **JDK 17 or newer** (JDK 21 recommended) – for example Temurin from adoptium.net
- **Maven** (included inside IntelliJ IDEA, or install it separately for the command line)
- Internet is needed **only once**, so Maven can download JavaFX and JUnit. After that the app runs offline.

---

## How to run

### Option A – IntelliJ IDEA (easiest)

1. Install JDK 21 and IntelliJ IDEA Community Edition.
2. Unzip the project. In IntelliJ choose **Open** and select the `pom.xml` file, then **Open as Project**.
3. Wait for the Maven download to finish (progress bar at the bottom).
4. If IntelliJ reports "No SDK", go to *File → Project Structure → Project* and pick your JDK.
5. Open the **Maven** panel (right side) and run:
   - **Lifecycle → test** to run the unit tests
   - **Plugins → javafx → javafx:run** to start the app

> Do not start `Main.java` with the green play button at first. It often fails with "JavaFX runtime components are missing". The Maven `javafx:run` route avoids this.

### Option B – Command line

```bash
mvn test            # run the unit tests
mvn javafx:run      # start the application
```

Run the commands from the folder that contains `pom.xml`.

---

## Automatic tags (Phase 2)

Every question gets **tags** automatically, using rules you can edit. Nothing needs the internet.

- **How matching works:** the question text and the answer text are both checked. Matching ignores upper/lower case and punctuation, works on whole words only (so `class` does not match `classification`, and `int` does not match `print`), supports multi-word phrases, and has three match types: whole word, phrase, and prefix (`polymorph` matches `polymorphism`).
- **Conflicts:** higher priority wins, then the longer phrase. Words used by one match are not reused, so "priority queue" does not also trigger a `queue` rule.
- **Auto vs manual:** automatic tags (dashed, teal) are rebuilt whenever a question is saved, imported, or the bank is re-tagged. Manual tags (purple) are never removed by re-tagging. If an automatic tag is wrong for one question, click **x** to exclude it for that question.
- **While you type** in the Add/Edit question window, suggested tags appear after a short pause, with a tooltip showing which phrase triggered each one. Nothing is saved until you save the question.
- **Tags tab:** create, rename, merge and delete tags (with an optional group such as "Data Structures"), add trigger phrases with a match type and priority, switch rules on or off, preview which questions a rule would match, keep an ignore list for ambiguous words, and **Re-tag question bank** (runs with a progress window).
- **Starter rules** for about 28 common computing tags are added on first run. They are only a starting point; edit or delete them, and add your own for any subject.

## Quick start guide

1. **Question Bank** – browse the starting questions, or press **Add question** to create your own.
2. **Manage topics** – add or remove topics if your syllabus differs.
3. **Generate** – choose a mode:
   - *By total marks*: set the total, tick the allowed marks, optionally pick topics, then **Generate paper**.
   - *By blueprint*: add one row per section, set the target total, then **Generate paper**.
4. **Paper Editor** – adjust the paper:
   - **Edit header** (college, exam title, subject, duration, instructions)
   - **Edit sections** (rename or merge)
   - ▲ Up / ▼ Down, **Edit**, **Replace**, **Remove** on each question
   - **Add manual question**
   - **Save paper**
5. **Print Preview** – switch between Question Paper, Answer Key and Blueprint, then **Print**.

---

## Where your data is stored

PaperWallah uses a local **SQLite** database. There is no database server and no internet connection is needed.

| What | Where |
|---|---|
| Database | `PaperWallah.db` inside your user data folder (Windows `%APPDATA%\PaperWallah`, macOS `~/Library/Application Support/PaperWallah`, Linux `~/.local/share/PaperWallah`) |
| Images | `images/` next to the database |
| Migration report | `migration_report.txt` and the **Database** tab |
| Backups of old files | `legacy_backup_<time>/` next to the database |

**Upgrading from the CSV version:** on first start, PaperWallah looks for the old `PaperWallah/data` folder (`questions.csv`, `topics.txt`, `papers.dat`) in the folder you start it from, copies those files to a backup, imports everything into the database in one transaction and shows a report. The old files are never changed or deleted. Start the app from the same project folder you used before. Images you attached earlier keep their old paths, so leave the old `PaperWallah/images` folder where it was.

**CSV is now for exchange.** The **Database** tab can import questions from CSV (with a preview, validation and a choice to skip, update or import duplicates as new), export questions and topics, and make a safe backup copy of the database. Import files need the header columns `marks,difficulty,topic,question,answer`; `id` and `images` are optional.

## Project structure

```
PaperWallah/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/paperwallah/
    │   │   ├── Main.java                       JavaFX user interface
    │   │   ├── model/
    │   │   │   ├── Question.java               bank question
    │   │   │   ├── PaperQuestion.java          copy of a question inside a paper
    │   │   │   ├── Paper.java                  a generated or saved paper
    │   │   │   ├── Difficulty.java             EASY / MEDIUM / HARD
    │   │   │   └── Topics.java                 the 60 default topics
    │   │   ├── strategy/
    │   │   │   ├── PaperGenerationStrategy.java
    │   │   │   ├── MarksBasedStrategy.java     generate by total marks
    │   │   │   ├── BlueprintBasedStrategy.java generate by sections
    │   │   │   └── GenerationException.java
    │   │   └── service/
    │   │       ├── StorageService.java         CSV, topics, papers and image files
    │   │       └── QuestionEditService.java    correct / create new / paper-only edits
    │   └── resources/
    │       ├── style.css                       look and feel
    │       └── initial_questions.csv           starting question set
    └── test/java/com/paperwallah/
        └── PaperGeneratorTest.java             JUnit 5 tests
```

---

## Design notes

- **Strategy pattern** – `MarksBasedStrategy` and `BlueprintBasedStrategy` implement one `PaperGenerationStrategy` interface, so the UI can run either mode the same way.
- **Even distribution** – for a total such as 50 with marks 1, 3 and 5, the marks-based mode enumerates every exact combination of question counts, ranks them by how even the counts are (smallest max-minus-min, then fewest questions) and uses the best one the bank can supply. If none can be supplied, it reports exactly which counts are missing.
- **Snapshots** – a paper stores its own copy of every question (`PaperQuestion`). Editing or deleting a bank question later does not change papers that were already saved.
- **Separation of concerns** – all file reading and writing is in `StorageService`; the strategies and models have no UI or file code, which is what makes them testable.
- **Images** – only relative file paths are stored in the data, never the image bytes. A missing image shows a clear warning instead of crashing.

## Unit tests

`mvn test` runs `PaperGeneratorTest`, which covers:

- combinations always add up to the requested total
- generated papers have the correct total and no repeated questions
- no valid combination (for example 7 marks using only 4- and 6-mark questions)
- not enough questions in the bank
- blueprint total mismatch
- tagging: whole-word matching, phrases, prefix, priority and longest-phrase conflicts, question and answer both analysed, manual tags and exclusions surviving re-tagging, disabled rules, merge, starter rules
- "correct existing" keeps the question ID, while "create new" leaves the original unchanged
- a paper's copy stays unchanged when the bank question is edited later

---

## Troubleshooting

| Problem | Fix |
|---|---|
| "JavaFX runtime components are missing" | Start with `mvn javafx:run`, not the play button on `Main.java`. |
| Maven cannot download dependencies | Connect to the internet once so JavaFX and JUnit can download, then rerun. |
| Wrong Java version / "No SDK" | Install JDK 17 or newer and select it in *Project Structure*. |
| "Could not save questions.csv" | Close the file in Excel or any other program, then try again. |
| "Not enough questions" when generating | Add more questions of the marks you need, widen the topic selection, or change the allowed marks or blueprint. |
| Image shows "[image missing]" | The file was moved or deleted. Keep the `PaperWallah/images` folder with the data. |
| No printer available | Install a PDF printer (for example *Microsoft Print to PDF* on Windows) and print to it. |

## Known limitations

- Answers are text only; answer images are not supported.
- The Answer Key prints answer text only, without images.
- Papers are stored in Java's binary format, so they can only be read by this application.
- One user at a time: the CSV file is not locked against two copies of the app running together.
