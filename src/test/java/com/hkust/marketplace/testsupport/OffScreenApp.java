package com.hkust.marketplace.testsupport;

import com.hkust.marketplace.data.DataStore;
import com.hkust.marketplace.ui.LiveUpdates;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Labeled;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.junit.jupiter.api.Assumptions;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The app with its real screens, in a window nobody sees, so that a test can press buttons and
 * read the screens as a user would: system tests through the user interface (Lecture 14). It
 * starts at the welcome screen.
 * <pre>{@code
 * OffScreenApp app = OffScreenApp.start();
 * app.logIn("Enter as Buyer", "alice", SeedData.DEMO_PASSWORD);   // UC2, on the login screen
 * app.press("My Deals");
 * assertEquals("My Deals", app.pageTitle());
 * app.reset();                                                   // after each test
 * }</pre>
 *
 * <p>The screens use the app's store, which holds the demo data in the test run's own folder
 * ({@link TestDataFolder}); all tests of a run share it, and a test adds data of its own with
 * {@link ScreenData}. Each dialog is answered as soon as it opens: as the test said with
 * {@link #answer}, or else with its safe choice, Cancel, or its only button when it has no
 * Cancel (an OK message). Every dialog is listed in {@link #dialogs()}.
 *
 * <p>Where JavaFX cannot start, such as on a computer without a display, the tests that use
 * this class are skipped, not failed.
 */
public final class OffScreenApp {

    /** How long one step may take before the test fails. */
    private static final int TIMEOUT_SECONDS = 30;
    /** How many times a step lets the screen catch up while dialogs keep opening, before the test fails. */
    private static final int MAX_ROUNDS = 1000;
    /** Far outside every screen; the windows are also fully transparent. */
    private static final double OFF_SCREEN = -10_000;

    private static OffScreenApp app;
    /** Why JavaFX could not start in this run, or null if it started. */
    private static String noJavaFx;

    private final Stage window;
    private final List<String> dialogs = new CopyOnWriteArrayList<>();
    private final List<Throwable> failures = new CopyOnWriteArrayList<>();
    /** The answers the test gave for dialogs still to open, by dialog title, in the order given. */
    private final Map<String, Deque<Consumer<Form>>> answers = new ConcurrentHashMap<>();

    private OffScreenApp(Stage window) {
        this.window = window;
    }

    /**
     * The app on its welcome screen, started the first time a test asks for it. Call it in
     * {@code @BeforeAll}; the test class is skipped if JavaFX cannot start here.
     * @throws IllegalStateException if the run has no data folder of its own, which would make
     *                               the screens use the data saved in the project folder
     */
    public static synchronized OffScreenApp start() {
        if (System.getProperty(DataStore.DATA_FOLDER_PROPERTY) == null) {
            throw new IllegalStateException("The screen tests need the test run's own data folder: run them "
                    + "with JUnit, which then starts TestDataFolder (see its comment)");
        }
        if (app == null) {
            startJavaFx();
            app = call("Opening the welcome screen", OffScreenApp::openWelcomeScreen);
        }
        return app;
    }

    /** Starts JavaFX once per run, or skips the calling test class if it cannot start here. */
    private static void startJavaFx() {
        if (noJavaFx == null) {
            try {
                Platform.startup(() -> { });
            } catch (IllegalStateException alreadyRunning) {
                // Started by other code of this run
            } catch (RuntimeException e) {
                noJavaFx = String.valueOf(e.getMessage());
            }
        }
        Assumptions.assumeTrue(noJavaFx == null, () -> "JavaFX cannot start here (" + noJavaFx + "), so the screen tests are skipped");
    }

    /** Opens the welcome screen in a new window nobody sees. Runs on the JavaFX thread. */
    private static OffScreenApp openWelcomeScreen() {
        OffScreenApp opened = new OffScreenApp(new Stage());
        Platform.setImplicitExit(false); // closing a dialog must not end JavaFX
        Thread.currentThread().setUncaughtExceptionHandler((thread, failure) -> opened.failures.add(failure));
        Window.getWindows().addListener(opened::answerNewDialogs);
        hide(opened.window);
        SceneManager.init(opened.window); // as MarketplaceApp does at start-up
        SceneManager.showWelcome();
        opened.window.show();
        return opened;
    }

    private static void hide(Window window) {
        window.setOpacity(0);
        window.setX(OFF_SCREEN);
        window.setY(OFF_SCREEN);
    }

    /** The app's store, which the screens use: for a test to add data of its own ({@link ScreenData}). */
    public DataStore store() {
        return DataStore.getInstance();
    }

    // ========== What a user does ==========

    /** Presses the button or link with this text; the first one if there are several, such as "View Details". */
    public void press(String text) {
        onScreen("Pressing '" + text + "'", () -> button(root(), text).fire());
    }

    /** Presses the button with this text on the card (a listing, a deal, a chat) that shows cardText. */
    public void press(String text, String cardText) {
        onScreen("Pressing '" + text + "' on the card of '" + cardText + "'", () -> button(card(cardText), text).fire());
    }

    /** Types into the field with this id, such as "#usernameField", or with this prompt text; the first if there are several. */
    public void type(String field, String text) {
        onScreen("Typing into '" + field + "'", () -> field(root(), field, 0).setText(text));
    }

    /** Chooses the item with this text in the first drop-down list that offers it, such as "Electronics". */
    public void choose(String item) {
        onScreen("Choosing '" + item + "'", () -> choose(root(), item));
    }

    /** Picks the date in the date picker with this prompt text, as a user picks it from the calendar. */
    public void pick(String prompt, LocalDate date) {
        onScreen("Picking " + date + " in '" + prompt + "'", () -> find(root(), DatePicker.class,
                picker -> prompt.equals(picker.getPromptText())).setValue(date));
    }

    /** Clicks, with the main mouse button, the first text on the window that contains this text. */
    public void click(String text) {
        click(text, MouseButton.PRIMARY);
    }

    /** Clicks, with the given mouse button, the first text on the window that contains this text. */
    public void click(String text, MouseButton button) {
        onScreen("Clicking '" + text + "'", () -> click(root(), text, button));
    }

    /** Presses a key in the field with this id or prompt text, with or without Shift. */
    public void pressKey(String field, KeyCode key, boolean shiftDown) {
        onScreen("Pressing " + key + " in '" + field + "'", () -> field(root(), field, 0)
                .fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", key, shiftDown, false, false, false)));
    }

    /** Logs in as a user would (UC2): the portal's button on the welcome screen, then the login form. */
    public void logIn(String portalButton, String username, String password) {
        press(portalButton);
        type("#usernameField", username);
        type("#passwordField", password);
        onScreen("Logging in", () -> find(root(), ButtonBase.class, button -> textOf(button).startsWith("Login")).fire());
    }

    /**
     * Opens every page of the sidebar in turn, except Logout.
     * @return each sidebar button with the title of the page it opened, in the sidebar's order
     */
    public Map<String, String> visitEveryPage() {
        Map<String, String> titles = new LinkedHashMap<>();
        forEveryPage(page -> titles.put(page, pageTitle()));
        return titles;
    }

    /**
     * Opens every page of the sidebar in turn, except Logout, and looks for text cut short for
     * lack of room at the app's window size: on labels and buttons ({@link #cutTexts()}), and the
     * hints of empty text fields ({@link #cutPrompts()}).
     * @return the pages that cut some text, each with those texts; empty when no page does
     */
    public Map<String, List<String>> cutTextsOnEveryPage() {
        Map<String, List<String>> cut = new LinkedHashMap<>();
        forEveryPage(page -> {
            List<String> texts = new ArrayList<>(cutTexts());
            texts.addAll(cutPrompts());
            if (!texts.isEmpty()) {
                cut.put(page, texts);
            }
        });
        return cut;
    }

    /** Opens every page of the sidebar in turn, except Logout, and runs {@code onPage} while each is shown. */
    private void forEveryPage(Consumer<String> onPage) {
        List<String> pages = read(() -> all(root()).stream()
                .filter(node -> node.getStyleClass().contains("sidebar-btn"))
                .map(OffScreenApp::textOf)
                .filter(text -> !text.equals("Logout"))
                .toList());
        for (String page : pages) {
            onScreen("Opening the page '" + page + "'", () -> find(root(), ButtonBase.class,
                    button -> button.getStyleClass().contains("sidebar-btn") && textOf(button).equals(page)).fire());
            onPage.accept(page);
        }
    }

    /**
     * How to answer the next dialog with this title, instead of with its safe choice. Answers for
     * the same title are used in the order given, and the answer must close the dialog:
     * <pre>{@code app.answer("Make Offer", form -> form.type("such as 150 or 99.50", "150").press("Submit"));}</pre>
     * {@link #reset()} fails the test if an answer was not used.
     */
    public void answer(String title, Consumer<Form> answer) {
        answers.computeIfAbsent(title, key -> new ConcurrentLinkedDeque<>()).add(answer);
    }

    // ========== What happens without the user ==========

    /**
     * The borrowing-deadline timer has changed the data: the open dashboard is told, as
     * MarketplaceApp's timer tells it (UC12 step 4).
     */
    public void dataChanged() {
        onScreen("The timer telling the screen that the data changed", LiveUpdates::dataChanged);
    }

    /** The timer has changed the data, and the user presses Logout before the screen has reacted. */
    public void dataChangedAsUserLogsOut() {
        onScreen("The timer telling the screen, as Logout is pressed", () -> {
            LiveUpdates.dataChanged();
            button(root(), "Logout").fire();
        });
    }

    // ========== What the screens show ==========

    /** The title of the page now shown. */
    public String pageTitle() {
        return text("#pageTitle");
    }

    /** The text of the label with this id, such as "#errorLabel". */
    public String text(String id) {
        return read(() -> ((Labeled) root().lookup(id)).getText());
    }

    /** True if a text on the window contains this text. */
    public boolean shows(String text) {
        return read(() -> all(root()).stream().filter(OffScreenApp::isShown).anyMatch(node -> textOf(node).contains(text)));
    }

    /** What the field with this id or prompt text holds now; the first if there are several. */
    public String valueOf(String field) {
        return read(() -> field(root(), field, 0).getText());
    }

    /**
     * True if the label with this text and the field with this id or prompt text are on the same
     * line of the window, as a label beside the field it names: the middle of the label is level
     * with the field.
     */
    public boolean onOneLine(String label, String field) {
        return read(() -> {
            Node name = find(root(), Labeled.class, candidate -> textOf(candidate).equals(label));
            Node box = field(root(), field, 0);
            Bounds nameOnWindow = name.localToScene(name.getLayoutBounds());
            Bounds boxOnWindow = box.localToScene(box.getLayoutBounds());
            return nameOnWindow.getCenterY() >= boxOnWindow.getMinY() && nameOnWindow.getCenterY() <= boxOnWindow.getMaxY();
        });
    }

    /**
     * True if the user can type into the field with this id or prompt text, or the first field
     * after the label with this text: false while it, or the form around it, is disabled, and
     * for a read-only field.
     */
    public boolean canType(String field) {
        return read(() -> {
            TextInputControl input = field(root(), field, 0);
            return !input.isDisabled() && input.isEditable();
        });
    }

    /** The titles of the cards shown (listings, deals, chats), in the order the window shows them. */
    public List<String> cardTitles() {
        return read(() -> all(root()).stream()
                .filter(node -> node.getStyleClass().contains("listing-card-title") && isShown(node))
                .map(OffScreenApp::textOf)
                .toList());
    }

    /**
     * How far, in pixels, the middle of the first shown node with this style class is from the
     * middle of the area it is shown in, across: the visible part of the scroll pane around it (left
     * of its scroll bar), or else the window. 0 when the node is centred, as the card of a login
     * screen ("auth-pane") is.
     */
    public double offCentre(String styleClass) {
        return read(() -> {
            Parent root = root();
            root.applyCss();
            root.layout();
            Node node = all(root).stream()
                    .filter(shown -> shown.getStyleClass().contains(styleClass) && isShown(shown))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Nothing on the screen has the style class " + styleClass));
            Bounds onWindow = node.localToScene(node.getLayoutBounds());
            return Math.abs(onWindow.getCenterX() - areaShowing(node).getCenterX());
        });
    }

    /** In window coordinates: the visible part of the scroll pane the node is in, or else the whole window. */
    private Bounds areaShowing(Node node) {
        for (Node around = node.getParent(); around != null; around = around.getParent()) {
            if (around instanceof ScrollPane scrollPane) {
                Node viewport = scrollPane.lookup(".viewport");
                return viewport.localToScene(viewport.getLayoutBounds());
            }
        }
        return new BoundingBox(0, 0, window.getScene().getWidth(), window.getScene().getHeight());
    }

    /**
     * The texts of the buttons and labels shown that are cut short for lack of room, such as a
     * "Send" button showing only "…". A screen laid out well at the app's window size has none.
     */
    public List<String> cutTexts() {
        return read(() -> all(root()).stream()
                .filter(node -> node instanceof Labeled && isShown(node))
                .map(node -> (Labeled) node)
                .filter(OffScreenApp::isCut)
                .map(Labeled::getText)
                .toList());
    }

    /** True if the label or button draws less of its text than it has: JavaFX then ends it with "…". */
    private static boolean isCut(Labeled labeled) {
        String text = labeled.getText();
        return text != null && !text.isBlank() && labeled.getChildrenUnmodifiable().stream()
                .anyMatch(drawn -> drawn instanceof Text shown && !text.equals(shown.getText()));
    }

    /**
     * The hints (prompt texts) of the empty text fields shown that are wider than their field, such
     * as a hint that shows only "Leave empty to keep 1 da". A field cuts its hint off at its edge,
     * without "…", so {@link #cutTexts()} cannot see it.
     */
    public List<String> cutPrompts() {
        return read(() -> cutPrompts(root()));
    }

    private static List<String> cutPrompts(Node from) {
        return all(from).stream()
                .filter(node -> node instanceof TextField && isShown(node))
                .map(node -> (TextField) node)
                .filter(OffScreenApp::isPromptCut)
                .map(TextField::getPromptText)
                .toList();
    }

    /** True if the field is empty and its hint, in the field's font, is wider than the room inside the field. */
    private static boolean isPromptCut(TextField field) {
        String prompt = field.getPromptText();
        String text = field.getText(); // null shows the hint too
        if (prompt == null || prompt.isBlank() || (text != null && !text.isEmpty())) {
            return false;
        }
        Text hint = new Text(prompt);
        hint.setFont(field.getFont());
        double room = field.getWidth() - field.getInsets().getLeft() - field.getInsets().getRight();
        return hint.getLayoutBounds().getWidth() > room;
    }

    /** How many photo frames on the window show their photo. Photos load in the background. */
    public int photosShown() {
        return read(() -> (int) photoFrames().stream().filter(OffScreenApp::showsItsPhoto).count());
    }

    /**
     * Waits until a photo frame on the window shows its photo: every frame is watched, and the
     * wait ends as soon as a photo loaded in the background is put into one.
     */
    public void waitForAPhoto() {
        CompletableFuture<Void> shown = new CompletableFuture<>();
        call("Watching the photo frames", () -> {
            for (Parent frame : photoFrames()) {
                frame.getChildrenUnmodifiable().addListener((InvalidationListener) children -> {
                    if (showsItsPhoto(frame)) {
                        shown.complete(null);
                    }
                });
                if (showsItsPhoto(frame)) {
                    shown.complete(null);
                }
            }
            return null;
        });
        await("Waiting for a photo", shown);
    }

    private List<Parent> photoFrames() {
        return all(root()).stream()
                .filter(node -> node.getStyleClass().contains("listing-card-thumbnail") && isShown(node))
                .filter(Parent.class::isInstance)
                .map(Parent.class::cast)
                .toList();
    }

    private static boolean showsItsPhoto(Parent frame) {
        return frame.getChildrenUnmodifiable().stream().anyMatch(node -> node instanceof ImageView image && image.getImage() != null);
    }

    /** The dialogs shown since the last {@link #reset()}, in order, as "title: message". */
    public List<String> dialogs() {
        return List.copyOf(dialogs);
    }

    /**
     * Back to the welcome screen, logged out, with no dialogs listed: call it after each test.
     * Fails the test if the screens failed since its last step, or if an answer was not used.
     */
    public void reset() {
        List<String> unused = answers.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .toList();
        answers.clear();
        onScreen("Going back to the welcome screen", () -> {
            SessionManager.logout();
            LiveUpdates.clear();
            SceneManager.showWelcome();
        });
        dialogs.clear();
        if (!unused.isEmpty()) {
            throw new AssertionError("The test answered dialogs that never opened: " + unused);
        }
    }

    // ========== Running steps on the JavaFX thread ==========

    /**
     * Runs a step on the JavaFX thread, then waits until the screen has finished what the step
     * started: the work it queued, and every dialog it opened, one after another.
     */
    private void onScreen(String step, Runnable action) {
        call(step, () -> {
            action.run();
            return null;
        });
        int rounds = 0;
        do {
            call(step, () -> null); // the work the step queued, such as a dialog's answer, runs before this
            if (++rounds > MAX_ROUNDS) {
                throw new AssertionError(step + " left a dialog open");
            }
        } while (rounds < 3 || call(step, this::dialogOpen));
        call(step, () -> {
            root().applyCss(); // builds what is new on the screen, such as the rows of a table
            root().layout();
            return null;
        });
        if (!failures.isEmpty()) {
            Throwable failure = failures.remove(0);
            throw new AssertionError(step + " failed: " + failure, failure);
        }
    }

    private boolean dialogOpen() {
        return Window.getWindows().stream().anyMatch(open -> open != window && open instanceof Stage && open.isShowing());
    }

    private <T> T read(Supplier<T> query) {
        return call("Reading the screen", query);
    }

    private static <T> T call(String step, Supplier<T> action) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(action.get());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        return await(step, result);
    }

    /** The result, once there is one; the test fails if the step failed or took too long. */
    private static <T> T await(String step, CompletableFuture<T> result) {
        try {
            return result.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new AssertionError(step + " failed: " + e.getCause(), e.getCause());
        } catch (TimeoutException e) {
            throw new AssertionError(step + " did not finish within " + TIMEOUT_SECONDS + " seconds");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(step + " was interrupted", e);
        }
    }

    // ========== Dialogs ==========

    /** Hides each dialog as it opens, then answers it. */
    private void answerNewDialogs(ListChangeListener.Change<? extends Window> change) {
        while (change.next()) {
            for (Window opened : change.getAddedSubList()) {
                if (opened != window && opened instanceof Stage dialog) {
                    hide(dialog);
                    Platform.runLater(() -> answer(dialog));
                }
            }
        }
    }

    /**
     * Lists the dialog, then answers it as the test said, or else presses its Cancel button, or its
     * only button if it has no Cancel. A failed answer fails the test's step, and the dialog is closed.
     */
    private void answer(Stage dialog) {
        if (dialog.getScene() == null || !(dialog.getScene().getRoot() instanceof DialogPane pane)) {
            dialogs.add(dialog.getTitle());
            dialog.close();
            return;
        }
        dialogs.add(dialog.getTitle() + ": " + message(pane));
        Deque<Consumer<Form>> given = answers.get(dialog.getTitle());
        Consumer<Form> answer = given == null ? null : given.poll();
        pane.applyCss(); // builds the dialog's insides before it is first drawn, such as its buttons
        pane.layout();
        try {
            if (answer != null) {
                answer.accept(new Form(pane));
            } else {
                pressSafeChoice(pane);
            }
        } catch (RuntimeException | AssertionError failure) {
            failures.add(failure);
        }
        if (dialog.isShowing()) {
            failures.add(new AssertionError("The dialog '" + dialog.getTitle() + "' was left open"));
            dialog.close();
        }
    }

    private static void pressSafeChoice(DialogPane pane) {
        if (pane.getButtonTypes().isEmpty()) {
            return;
        }
        ButtonType choice = pane.getButtonTypes().stream()
                .filter(type -> type.getButtonData() == ButtonBar.ButtonData.CANCEL_CLOSE)
                .findFirst()
                .orElse(pane.getButtonTypes().get(0));
        ((ButtonBase) pane.lookupButton(choice)).fire();
    }

    private static String message(DialogPane pane) {
        String header = pane.getHeaderText();
        return header != null && !header.isBlank() ? header : String.valueOf(pane.getContentText());
    }

    /**
     * A dialog, for a test's answer to fill in and close as a user would ({@link #answer}). The
     * answer runs while the dialog is open, so it can also check what the form shows.
     */
    public static final class Form {

        private final DialogPane pane;

        private Form(DialogPane pane) {
            this.pane = pane;
        }

        /** Types into the field with this prompt text; the first if there are several. */
        public Form type(String prompt, String text) {
            return type(prompt, 0, text);
        }

        /** Types into a field with this prompt text, counting the fields with the same prompt from 0. */
        public Form type(String prompt, int index, String text) {
            field(pane, prompt, index).setText(text);
            return this;
        }

        /** Chooses the item with this text in the first drop-down list that offers it. */
        public Form choose(String item) {
            OffScreenApp.choose(pane, item);
            return this;
        }

        /** Presses the button, link or option with this text, such as "Submit", "Cancel" or "4 ★". */
        public Form press(String text) {
            button(pane, text).fire();
            return this;
        }

        /**
         * Clicks the empty part of the row that shows this text, beside the text, as a user who
         * clicks a row of a list. A click on a button or option itself stays with it.
         */
        public Form clickBeside(String text) {
            find(pane, Node.class, node -> textOf(node).contains(text)).getParent().fireEvent(mouseClick(MouseButton.PRIMARY));
            return this;
        }

        /** What the field with this prompt text holds; the first if there are several. */
        public String valueOf(String prompt) {
            return field(pane, prompt, 0).getText();
        }

        /** True if a text in the dialog contains this text. */
        public boolean shows(String text) {
            return all(pane).stream().filter(OffScreenApp::isShown).anyMatch(node -> textOf(node).contains(text));
        }

        /** The hints of the dialog's empty text fields that are wider than their field, as {@link OffScreenApp#cutPrompts()}. */
        public List<String> cutPrompts() {
            return OffScreenApp.cutPrompts(pane);
        }
    }

    // ========== Finding things on the window ==========

    private Parent root() {
        return window.getScene().getRoot();
    }

    /** The node and everything inside it, in the order they are shown. */
    private static List<Node> all(Node from) {
        List<Node> nodes = new ArrayList<>();
        nodes.add(from);
        if (from instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                nodes.addAll(all(child));
            }
        }
        return nodes;
    }

    /** The first node of this type that is shown and matches. */
    private static <T extends Node> T find(Node from, Class<T> type, Predicate<T> matches) {
        return all(from).stream()
                .filter(type::isInstance)
                .map(type::cast)
                .filter(node -> isShown(node) && matches.test(node))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Not on the screen: a " + type.getSimpleName()
                        + " as asked; the screen shows " + texts(from)));
    }

    private static ButtonBase button(Node from, String text) {
        ButtonBase button = find(from, ButtonBase.class, candidate -> textOf(candidate).equals(text));
        if (button.isDisabled()) {
            throw new AssertionError("The button '" + text + "' is disabled");
        }
        return button;
    }

    /** The innermost card (a listing, a deal, a chat) that shows the text. */
    private Node card(String cardText) {
        List<Node> cards = all(root()).stream()
                .filter(node -> node.getStyleClass().contains("card") || node.getStyleClass().contains("listing-card"))
                .filter(node -> isShown(node) && all(node).stream().anyMatch(inner -> textOf(inner).contains(cardText)))
                .toList();
        return cards.stream()
                .filter(card -> cards.stream().noneMatch(other -> other != card && isInside(other, card)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No card shows '" + cardText + "'; the screen shows " + texts(root())));
    }

    /**
     * A text field with this id or prompt text, counting the fields with the same prompt from 0;
     * or else the first field after the label with this text, such as "Full name". A date picker
     * counts as the field it is typed into.
     */
    private static TextInputControl field(Node from, String idOrPrompt, int index) {
        if (idOrPrompt.startsWith("#")) {
            return (TextInputControl) from.lookup(idOrPrompt);
        }
        List<TextInputControl> fields = new ArrayList<>();
        for (Node node : all(from)) {
            if (node instanceof DatePicker picker && idOrPrompt.equals(picker.getPromptText()) && isShown(picker)) {
                fields.add(picker.getEditor());
            } else if (node instanceof TextInputControl input && idOrPrompt.equals(input.getPromptText())
                    && isShown(input) && !isInsideDatePicker(input)) {
                fields.add(input);
            }
        }
        if (fields.isEmpty() && index == 0) {
            return fieldAfterLabel(from, idOrPrompt);
        }
        if (index >= fields.size()) {
            throw new AssertionError("No field number " + (index + 1) + " with '" + idOrPrompt + "'; the screen shows " + texts(from));
        }
        return fields.get(index);
    }

    /** The first shown text field after the label with exactly this text, in the order the window shows them. */
    private static TextInputControl fieldAfterLabel(Node from, String label) {
        List<Node> nodes = all(from);
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i) instanceof Labeled labeled && label.equals(textOf(labeled)) && isShown(labeled)) {
                for (Node next : nodes.subList(i + 1, nodes.size())) {
                    if (next instanceof TextInputControl input && isShown(input)) {
                        return input;
                    }
                }
            }
        }
        throw new AssertionError("No field '" + label + "'; the screen shows " + texts(from));
    }

    private static boolean isInsideDatePicker(Node node) {
        for (Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof DatePicker) {
                return true;
            }
        }
        return false;
    }

    private static void choose(Node from, String item) {
        for (Node node : all(from)) {
            if (node instanceof ComboBox<?> box && isShown(box) && !box.isDisabled() && select(box, item)) {
                return;
            }
        }
        throw new AssertionError("No drop-down list offers '" + item + "'");
    }

    /** Selects the item shown as this text, as a user picks it from the list; false if the list has none. */
    private static <T> boolean select(ComboBox<T> box, String item) {
        for (T value : box.getItems()) {
            if (item.equals(box.getConverter().toString(value))) {
                box.getSelectionModel().select(value);
                return true;
            }
        }
        return false;
    }

    /** A click on the first text that contains this text. Buttons keep their clicks, as in JavaFX. */
    private static void click(Node from, String text, MouseButton button) {
        find(from, Node.class, node -> textOf(node).contains(text)).fireEvent(mouseClick(button));
    }

    private static MouseEvent mouseClick(MouseButton button) {
        return new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, button, 1,
                false, false, false, false, button == MouseButton.PRIMARY, false,
                button == MouseButton.SECONDARY, false, false, true, null);
    }

    private static boolean isInside(Node node, Node container) {
        for (Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (parent == container) {
                return true;
            }
        }
        return false;
    }

    /** True if the node and everything around it is visible. */
    private static boolean isShown(Node node) {
        for (Node shown = node; shown != null; shown = shown.getParent()) {
            if (!shown.isVisible()) {
                return false;
            }
        }
        return true;
    }

    /** The text a label or text shows; empty for anything else, and for what was typed into a field. */
    private static String textOf(Node node) {
        String text = node instanceof Labeled labeled ? labeled.getText()
                : node instanceof Text plain && !isTyped(plain) ? plain.getText()
                : null;
        return text == null ? "" : text.trim();
    }

    /** True for the text inside a text field: what the user typed, not a text the screen shows. */
    private static boolean isTyped(Node node) {
        for (Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof TextInputControl) {
                return true;
            }
        }
        return false;
    }

    /** The texts shown, for the message of a failed test. */
    private static List<String> texts(Node from) {
        return all(from).stream()
                .filter(node -> node instanceof Labeled && isShown(node))
                .map(OffScreenApp::textOf)
                .filter(text -> !text.isEmpty())
                .toList();
    }
}
