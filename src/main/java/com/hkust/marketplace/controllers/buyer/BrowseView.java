package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.models.Category;
import com.hkust.marketplace.models.ItemCondition;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.services.BrowseQuery;
import com.hkust.marketplace.services.BrowseService;
import com.hkust.marketplace.services.ListingSort;
import com.hkust.marketplace.services.ServiceException;
import com.hkust.marketplace.ui.BrowseText;
import com.hkust.marketplace.ui.Choices;
import com.hkust.marketplace.ui.Dialogs;
import com.hkust.marketplace.ui.ListingCard;
import com.hkust.marketplace.ui.Refreshable;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * The Browse Marketplace screen (UC8 steps 1-5; Task 1.3): the live listings as cards, with a
 * keyword search, filters for category, listing type, condition and price range, and a sort
 * order. Choosing a listing opens it.
 *
 * <p>The screen keeps what the buyer typed and chose, so coming back from a listing shows the
 * same keyword, filters and sort order (UC8 step 8); {@link #refresh()} then reloads the
 * results. Which listings match is decided by the tested {@link BrowseService}.
 */
public class BrowseView extends VBox implements Refreshable {

    private final BrowseService service;
    private final Consumer<Listing> onOpen;

    private final TextField keyword = new TextField();
    private final ComboBox<String> category = Choices.withAll("All categories", Category.values());
    private final ComboBox<String> type = Choices.withAll("All types", ListingType.values());
    private final ComboBox<String> condition = Choices.withAll("All conditions", ItemCondition.values());
    private final TextField minPrice = new TextField();
    private final TextField maxPrice = new TextField();
    private final ComboBox<ListingSort> sort = new ComboBox<>(FXCollections.observableArrayList(ListingSort.values()));

    private final Label summary = new Label();
    private final FlowPane grid = ListingCard.grid();
    /** The last query that was accepted; {@link #refresh()} runs it again. */
    private BrowseQuery shown = BrowseQuery.ALL;
    /** True while the controls are being reset, so each change does not start a search of its own. */
    private boolean resetting;

    /** @param onOpen opens the chosen listing's detail screen (UC8 step 5) */
    public BrowseView(BrowseService service, Consumer<Listing> onOpen) {
        super(14);
        this.service = service;
        this.onOpen = onOpen;
        setPadding(new Insets(4));

        summary.getStyleClass().add("listing-card-line");
        ScrollPane results = new ScrollPane(new VBox(12, summary, grid));
        results.setFitToWidth(true);
        VBox.setVgrow(results, Priority.ALWAYS);

        getChildren().addAll(searchBar(), results);
        refresh();
    }

    /** Runs the last accepted search again, for example after the buyer comes back from a listing. */
    @Override
    public final void refresh() {
        show(shown);
    }

    // ---------- Refine (UC8 step 3) ----------

    private VBox searchBar() {
        keyword.setPromptText("Search by title or keyword");
        HBox.setHgrow(keyword, Priority.ALWAYS);
        Button search = new Button("Search");
        search.getStyleClass().add("btn-primary");
        search.setOnAction(e -> search());
        Button reset = new Button("Reset");
        reset.getStyleClass().add("btn-outline");
        reset.setOnAction(e -> reset());
        HBox top = new HBox(10, keyword, search, reset);
        top.setAlignment(Pos.CENTER_LEFT);

        minPrice.setPromptText("Min");
        minPrice.setPrefWidth(90);
        maxPrice.setPromptText("Max");
        maxPrice.setPrefWidth(90);
        sort.getSelectionModel().select(ListingSort.NEWEST);
        // The filters wrap onto a second line in a narrow window, so no choice is ever cut short
        FlowPane filters = new FlowPane(10, 8, category, type, condition,
                group(new Label("Price (HK$):"), minPrice, new Label("to"), maxPrice),
                group(new Label("Sort by:"), sort));
        filters.setAlignment(Pos.CENTER_LEFT);
        for (ComboBox<?> box : List.of(category, type, condition, sort)) {
            box.setMinWidth(Region.USE_PREF_SIZE);
        }

        // Enter in a text box and every choice start the search
        keyword.setOnAction(e -> search());
        minPrice.setOnAction(e -> search());
        maxPrice.setOnAction(e -> search());
        for (ComboBox<?> box : List.of(category, type, condition, sort)) {
            box.setOnAction(e -> search());
        }

        VBox bar = new VBox(10, top, filters);
        bar.getStyleClass().add("card");
        bar.setPadding(new Insets(14, 16, 14, 16));
        return bar;
    }

    private void search() {
        if (resetting) {
            return;
        }
        show(new BrowseQuery(keyword.getText(),
                Choices.chosen(category, Category.values()), Choices.chosen(type, ListingType.values()),
                Choices.chosen(condition, ItemCondition.values()),
                minPrice.getText(), maxPrice.getText(), sort.getValue()));
    }

    private void reset() {
        resetting = true;
        keyword.clear();
        minPrice.clear();
        maxPrice.clear();
        for (ComboBox<?> box : List.of(category, type, condition)) {
            box.getSelectionModel().selectFirst();
        }
        sort.getSelectionModel().select(ListingSort.NEWEST);
        resetting = false;
        show(BrowseQuery.ALL);
    }

    // ---------- Show Results (UC8 steps 2 and 4) ----------

    private void show(BrowseQuery query) {
        List<Listing> listings;
        try {
            listings = service.search(query);
        } catch (ServiceException e) {
            Dialogs.problem(e); // A2: the earlier results stay, and the buyer can correct the range
            return;
        }
        shown = query;
        summary.setText(BrowseText.resultSummary(listings.size())); // A1 when there are none
        grid.getChildren().clear();
        for (Listing listing : listings) {
            ListingCard card = new ListingCard(listing, UserRole.BUYER);
            Button view = new Button("View Details");
            view.getStyleClass().addAll("btn-primary", "btn-sm");
            view.setOnAction(e -> onOpen.accept(listing));
            grid.getChildren().add(card.addActions(view).setOnOpen(() -> onOpen.accept(listing)));
        }
    }

    // ---------- Layout ----------

    /** Controls that belong together, such as a label and its box: they wrap as one. */
    private static HBox group(Node... controls) {
        HBox group = new HBox(8, controls);
        group.setAlignment(Pos.CENTER_LEFT);
        for (Node control : controls) {
            if (control instanceof Label label) {
                label.setMinWidth(Region.USE_PREF_SIZE);
            }
        }
        return group;
    }
}
