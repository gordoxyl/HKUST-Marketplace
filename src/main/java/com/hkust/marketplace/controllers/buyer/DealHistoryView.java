package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.services.DealHistoryService;
import com.hkust.marketplace.services.HistoryEntry;
import com.hkust.marketplace.services.HistoryQuery;
import com.hkust.marketplace.services.InvalidDateRangeException;
import com.hkust.marketplace.services.ServiceException;
import com.hkust.marketplace.ui.Choices;
import com.hkust.marketplace.ui.Dialogs;
import com.hkust.marketplace.ui.Formats;
import com.hkust.marketplace.ui.HistoryText;
import com.hkust.marketplace.ui.OfferText;
import com.hkust.marketplace.ui.Refreshable;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Function;

/**
 * The Deal History screen (UC11 View Deal History; Task 1.9): every completed deal of the
 * buyer, newest deal date first, with its item title, seller, deal date, deal type and outcome
 * (step 2), and a date range, a category and a seller's name to narrow it down (step 3).
 *
 * <p>This is the use case's boundary (Lecture 15). Which deals match, in which order, and
 * whether a date range is valid are decided by {@link DealHistoryService}; the wording is in the
 * tested {@link HistoryText}, {@link OfferText} and {@link Formats}. The screen keeps what the
 * buyer chose, and {@link #refresh()} runs it again: the dashboard calls it when the borrowing
 * timer has returned an item, so the history updates by itself (Special Requirement 1; the
 * Observer pattern of {@code LiveUpdates}, Lecture 17).
 *
 * <p>The dates are picked from a calendar and cannot be typed, so an unreadable date cannot be
 * entered (Lecture 11: make the error impossible); Reset clears them. A range that starts after
 * it ends (A2) is reported in red under the dates, not in a dialog: every change searches at
 * once, so a dialog would interrupt the buyer while they are still moving the range. The message
 * goes as soon as the range is valid, and the earlier results stay meanwhile ({Refine}). Any
 * other problem is shown in a dialog, as on every screen. The columns cannot be re-sorted by a
 * click, so the rows stay newest deal date first (step 2).
 */
public class DealHistoryView extends VBox implements Refreshable {

    private final DealHistoryService service;

    private final DatePicker from = datePicker("From");
    private final DatePicker to = datePicker("To");
    private final ComboBox<String> category = Choices.withAll("All categories", Category.values());
    private final TextField seller = new TextField();
    /** UC11 A2, under the dates; hidden while the range is valid. */
    private final Label rangeProblem = new Label();

    private final Label summary = new Label();
    private final TableView<HistoryEntry> table = new TableView<>();
    /** The last query that was accepted; {@link #refresh()} runs it again. */
    private HistoryQuery shown = HistoryQuery.ALL;
    /** True while the controls are being reset, so each change does not start a search of its own. */
    private boolean resetting;

    public DealHistoryView(DealHistoryService service) {
        super(14);
        this.service = service;
        setPadding(new Insets(4));

        summary.getStyleClass().add("listing-card-line");
        buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);

        getChildren().addAll(filterBar(), summary, table);
        refresh();
    }

    /**
     * Shows the last accepted query again: when the screen opens (step 2), and when the timer
     * has returned an item while the screen is open (Special Requirement 1). If the buyer is
     * still correcting a range (A2), its message stays.
     */
    @Override
    public final void refresh() {
        String problem = rangeProblem.isVisible() ? rangeProblem.getText() : null;
        show(shown);
        if (problem != null) {
            showRangeProblem(problem);
        }
    }

    // ---------- Refine (UC11 step 3) ----------

    private VBox filterBar() {
        seller.setPromptText("Seller's name");
        HBox.setHgrow(seller, Priority.ALWAYS);
        Button search = new Button("Search");
        search.getStyleClass().add("btn-primary");
        search.setOnAction(e -> search());
        Button reset = new Button("Reset");
        reset.getStyleClass().add("btn-outline");
        reset.setOnAction(e -> reset());
        HBox top = new HBox(10, seller, search, reset);
        top.setAlignment(Pos.CENTER_LEFT);

        HBox filters = new HBox(10, new Label("Deal date:"), from, new Label("to"), to, category);
        filters.setAlignment(Pos.CENTER_LEFT);
        rangeProblem.getStyleClass().add("field-error");
        rangeProblem.setWrapText(true);
        showRangeProblem(null);

        // Step 5: a change of any filter, and Enter in the seller box, shows the results again
        seller.setOnAction(e -> search());
        from.setOnAction(e -> search());
        to.setOnAction(e -> search());
        category.setOnAction(e -> search());

        VBox bar = new VBox(10, top, filters, rangeProblem);
        bar.getStyleClass().add("card");
        bar.setPadding(new Insets(14, 16, 14, 16));
        return bar;
    }

    /**
     * A date picker that takes a date from its calendar only (see the class comment), and shows
     * it as the table shows deal dates: "21 Nov 2026".
     */
    private static DatePicker datePicker(String prompt) {
        DatePicker picker = new DatePicker();
        picker.setPromptText(prompt);
        picker.setConverter(Formats.datePickerConverter());
        picker.setEditable(false);
        picker.setPrefWidth(150);
        return picker;
    }

    private void search() {
        if (resetting) {
            return;
        }
        show(new HistoryQuery(from.getValue(), to.getValue(),
                Choices.chosen(category, Category.values()), seller.getText()));
    }

    private void reset() {
        resetting = true;
        from.setValue(null);
        to.setValue(null);
        category.getSelectionModel().selectFirst();
        seller.clear();
        resetting = false;
        show(HistoryQuery.ALL);
    }

    // ---------- Show History and Show Results (UC11 steps 2 and 4) ----------

    private void show(HistoryQuery query) {
        List<HistoryEntry> entries;
        try {
            entries = service.find(query);
        } catch (InvalidDateRangeException e) {
            showRangeProblem(e.getMessage()); // A2: the earlier results stay, and the buyer corrects the range ({Refine})
            return;
        } catch (ServiceException e) {
            Dialogs.problem(e);
            return;
        }
        showRangeProblem(null);
        shown = query;
        summary.setText(HistoryText.resultSummary(entries.size())); // A1 when there are none
        table.getItems().setAll(entries);
    }

    /** Shows A2's message under the dates, or hides the line (and its space) when there is none. */
    private void showRangeProblem(String message) {
        rangeProblem.setText(message == null ? "" : message);
        rangeProblem.setVisible(message != null);
        rangeProblem.setManaged(message != null);
    }

    // ---------- The table: the columns of Task 1.9 ----------

    private void buildTable() {
        table.getColumns().add(column("Item Title", entry -> entry.listing().getTitle()));
        table.getColumns().add(column("Seller", HistoryEntry::sellerName));
        table.getColumns().add(column("Deal Date", entry -> Formats.date(entry.dealDate())));
        table.getColumns().add(column("Deal Type", entry -> HistoryText.dealType(entry.deal())));
        table.getColumns().add(column("Outcome", entry -> OfferText.status(entry.deal())));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label()); // the summary above already says when nothing matches (A1)
    }

    /** A column of text that cannot be re-sorted by a click, so the rows keep the order of step 2. */
    private static TableColumn<HistoryEntry, String> column(String title, Function<HistoryEntry, String> text) {
        TableColumn<HistoryEntry, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(text.apply(cell.getValue())));
        column.setSortable(false);
        return column;
    }
}
