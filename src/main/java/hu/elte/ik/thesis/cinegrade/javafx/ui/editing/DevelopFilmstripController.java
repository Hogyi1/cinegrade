package hu.elte.ik.thesis.cinegrade.javafx.ui.editing;

import hu.elte.ik.thesis.cinegrade.app.managers.photos.PhotoManager;
import hu.elte.ik.thesis.cinegrade.app.managers.photos.PhotoLibrary;
import hu.elte.ik.thesis.cinegrade.domain.objects.PhotoEntry;
import hu.elte.ik.thesis.cinegrade.domain.enums.ColorLabel;
import hu.elte.ik.thesis.cinegrade.domain.enums.Operation;
import hu.elte.ik.thesis.cinegrade.infra.services.storage.ThumbnailService;
import hu.elte.ik.thesis.cinegrade.javafx.ui.components.CommentEditor;
import hu.elte.ik.thesis.cinegrade.javafx.ui.components.ConfirmDialog;
import hu.elte.ik.thesis.cinegrade.javafx.ui.components.FilmstripCell;
import hu.elte.ik.thesis.cinegrade.javafx.ui.components.SearchBar;
import hu.elte.ik.thesis.cinegrade.javafx.ui.input.InputListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.skin.VirtualFlow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Objects;

// [AI-GENERATED]

/**
 * Filmstrip of the development page: filter bar and the photo strip.
 * <p>
 * Reads and binds to the {@link PhotoLibrary} (photos, filters, selection) and sends every change of a photo
 * through the {@link PhotoManager}. Clicking a photo only updates the library's selection; whoever shows the
 * photo listens to that.
 */
public class DevelopFilmstripController {

    private static final Logger logger = LogManager.getLogger(DevelopFilmstripController.class);

    // Horizontal padding of a cell (see .filmstrip-cell), the gap between two cards
    private static final double CELL_GAP = 8;
    // Height of the horizontal scroll bar plus a little air, so the cards never need vertical scrolling
    private static final double SCROLLBAR_ROOM = 14;

    private final PhotoLibrary library;
    private final PhotoManager photoManager;
    private final ThumbnailService thumbnailService;

    // Set while the library's selection is copied into the ListView, so that copy is not sent back
    private boolean syncingSelection;
    private InputListener inputListener;

    @FXML
    private VBox filmstripRoot;
    @FXML
    private ToggleButton allPhotosBtn;
    @FXML
    private ToggleButton lastImportBtn;
    @FXML
    private Label filmstripCountLabel;
    @FXML
    private Label selectedBadge;
    @FXML
    private Slider filmstripZoomSlider;
    @FXML
    private ToggleButton editedFilterBtn;
    @FXML
    private ToggleButton notEditedFilterBtn;
    @FXML
    private ToggleGroup editedGroup;
    @FXML
    private Button ratingOperationBtn;
    @FXML
    private Button star1Btn;
    @FXML
    private Button star2Btn;
    @FXML
    private Button star3Btn;
    @FXML
    private Button star4Btn;
    @FXML
    private Button star5Btn;
    @FXML
    private Button colorNoneBtn;
    @FXML
    private Button colorRedBtn;
    @FXML
    private Button colorYellowBtn;
    @FXML
    private Button colorGreenBtn;
    @FXML
    private Button colorBlueBtn;
    @FXML
    private ToggleButton likedFilterBtn;
    @FXML
    private ToggleButton commentedFilterBtn;
    @FXML
    private SearchBar filmstripSearchField;
    @FXML
    private ListView<PhotoEntry> filmstripList;

    public DevelopFilmstripController(PhotoLibrary library, PhotoManager photoManager, ThumbnailService thumbnailService) {
        this.library = library;
        this.photoManager = photoManager;
        this.thumbnailService = thumbnailService;
    }

    @FXML
    private void initialize() {
        setupList();
        setupSelectionSync();
        setupCountLabels();
        setupScopeSwitch();
        setupFilters();
        setupRatingFilter();
        setupColorLabels();
        setupInput();
    }

    private void setupList() {
        FilmstripCell.Actions actions = new FilmstripCell.Actions(
                (photo, rating) -> photoManager.updateRating(targetsOf(photo), rating),
                photo -> toggleLike(targetsOf(photo)),
                this::editComment,
                photo -> confirmRemove(targetsOf(photo)),
                photo -> confirmDelete(targetsOf(photo)));

        // The zoom slider scales the cards; the list is exactly one card high, so it only scrolls sideways
        DoubleBinding cardSize = filmstripZoomSlider.valueProperty().multiply(FilmstripCell.BASE_SIZE);
        DoubleBinding listHeight = cardSize.add(SCROLLBAR_ROOM);
        filmstripList.fixedCellSizeProperty().bind(cardSize.add(CELL_GAP));
        filmstripList.minHeightProperty().bind(listHeight);
        filmstripList.prefHeightProperty().bind(listHeight);
        filmstripList.maxHeightProperty().bind(listHeight);

        filmstripList.setItems(library.getSortedEntries());
        filmstripList.setCellFactory(list -> new FilmstripCell(thumbnailService, library.getSelectedPhotoProperty(), cardSize, actions));
        filmstripList.setPlaceholder(new Label("No photos match the filters"));
        filmstripList.addEventFilter(ScrollEvent.SCROLL, this::scrollSideways);
    }

    /**
     * A mouse wheel only scrolls vertically; turn that into horizontal scrolling of the strip.
     */
    private void scrollSideways(ScrollEvent event) {
        if (event.getDeltaY() == 0 || Math.abs(event.getDeltaX()) > Math.abs(event.getDeltaY())) {
            return;
        }
        if (filmstripList.lookup(".virtual-flow") instanceof VirtualFlow<?> flow) {
            flow.scrollPixels(-event.getDeltaY());
        }
        event.consume();
    }

    private void setupSelectionSync() {
        MultipleSelectionModel<PhotoEntry> selection = filmstripList.getSelectionModel();
        selection.setSelectionMode(SelectionMode.MULTIPLE);

        // ListView -> library: the last clicked photo is the active one
        selection.getSelectedItems().addListener((ListChangeListener<PhotoEntry>) change -> pushSelection());
        selection.selectedItemProperty().addListener((obs, oldPhoto, newPhoto) -> pushSelection());

        // Library -> ListView: the active photo changed elsewhere (removed, opened from another panel)
        library.getSelectedPhotoProperty().addListener((obs, oldPhoto, newPhoto) -> {
            if (newPhoto == selection.getSelectedItem()) {
                return;
            }
            syncingSelection = true;
            try {
                int index = newPhoto == null ? -1 : filmstripList.getItems().indexOf(newPhoto);
                if (index < 0) {
                    selection.clearSelection();
                } else {
                    selection.clearAndSelect(index);
                    filmstripList.scrollTo(index);
                }
            } finally {
                syncingSelection = false;
            }
        });
    }

    private void pushSelection() {
        if (syncingSelection) {
            return;
        }
        MultipleSelectionModel<PhotoEntry> selection = filmstripList.getSelectionModel();
        // The list can hold nulls in the middle of a change
        List<PhotoEntry> selected = selection.getSelectedItems().stream().filter(Objects::nonNull).toList();
        library.select(selection.getSelectedItem(), selected);
    }

    private void setupCountLabels() {
        filmstripCountLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            int listed = library.getListedPhotosProperty().get();
            return "%d photo%s".formatted(listed, listed == 1 ? "" : "s");
        }, library.getListedPhotosProperty()));

        selectedBadge.textProperty().bind(library.getSelectedFilesProperty().asString("%d selected"));
        selectedBadge.visibleProperty().bind(library.getSelectedFilesProperty().greaterThan(0));
        selectedBadge.managedProperty().bind(selectedBadge.visibleProperty());
    }

    /**
     * "All photos | Last import": always exactly one of the two is on, whatever the user clicks.
     */
    private void setupScopeSwitch() {
        Runnable sync = () -> {
            boolean lastImported = library.getLastImportedProperty().get();
            allPhotosBtn.setSelected(!lastImported);
            lastImportBtn.setSelected(lastImported);
        };
        allPhotosBtn.setOnAction(e -> {
            library.getLastImportedProperty().set(false);
            sync.run();
        });
        lastImportBtn.setOnAction(e -> {
            library.getLastImportedProperty().set(true);
            sync.run();
        });
        library.getLastImportedProperty().addListener((obs, oldValue, newValue) -> sync.run());
        sync.run();
    }

    private void setupFilters() {
        // The toggle group keeps edited / not edited exclusive, both off shows every photo
        editedFilterBtn.selectedProperty().bindBidirectional(library.getEditedProperty());
        notEditedFilterBtn.selectedProperty().bindBidirectional(library.getNotEditedProperty());
        likedFilterBtn.selectedProperty().bindBidirectional(library.getLikedProperty());
        commentedFilterBtn.selectedProperty().bindBidirectional(library.getCommentedProperty());
        filmstripSearchField.textProperty().bindBidirectional(library.getNameProperty());
    }

    private void setupRatingFilter() {
        ratingOperationBtn.textProperty().bind(library.getRatingOperationProperty().asString());
        ratingOperationBtn.setOnAction(e -> {
            Operation[] operations = Operation.values();
            Operation current = library.getRatingOperationProperty().get();
            library.getRatingOperationProperty().set(operations[(current.ordinal() + 1) % operations.length]);
        });

        Button[] stars = {star1Btn, star2Btn, star3Btn, star4Btn, star5Btn};
        for (int i = 0; i < stars.length; i++) {
            int rating = i + 1;
            Region shape = new Region();
            shape.getStyleClass().add("filmstrip-star-shape");
            stars[i].setGraphic(shape);
            stars[i].setOnAction(e -> {
                // Clicking the active star again clears the rating filter
                int current = library.getRatingProperty().get();
                library.getRatingProperty().set(current == rating ? 0 : rating);
            });
        }

        library.getRatingProperty().addListener((obs, oldRating, newRating) -> paintStars(stars, newRating.intValue()));
        paintStars(stars, library.getRatingProperty().get());
    }

    private static void paintStars(Button[] stars, int rating) {
        for (int i = 0; i < stars.length; i++) {
            stars[i].getStyleClass().remove("filled");
            if (i < rating) {
                stars[i].getStyleClass().add("filled");
            }
        }
    }

    private void setupColorLabels() {
        colorNoneBtn.setOnAction(e -> applyColorLabel(null));
        colorRedBtn.setOnAction(e -> applyColorLabel(ColorLabel.RED));
        colorYellowBtn.setOnAction(e -> applyColorLabel(ColorLabel.YELLOW));
        colorGreenBtn.setOnAction(e -> applyColorLabel(ColorLabel.GREEN));
        colorBlueBtn.setOnAction(e -> applyColorLabel(ColorLabel.BLUE));
    }

    private void applyColorLabel(ColorLabel label) {
        photoManager.updateColorLabel(library.getSelection(), label != null ? label.name() : null);
    }

    /**
     * Shortcuts while the strip has the focus (and the search field does not):
     * 0-5 rate, Q W E R T color labels (red, yellow, green, blue, none), Ctrl+C comment, Ctrl+L like,
     * Delete removes from the catalog, Shift+Delete deletes from the disk,
     * Ctrl+Alt+L / C / E toggle the liked / commented / edited filters.
     */
    private void setupInput() {
        inputListener = new InputListener(filmstripRoot, InputListener.focusedNotTyping(filmstripRoot));

        KeyCode[] digits = {KeyCode.DIGIT0, KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4, KeyCode.DIGIT5};
        KeyCode[] numpad = {KeyCode.NUMPAD0, KeyCode.NUMPAD1, KeyCode.NUMPAD2, KeyCode.NUMPAD3, KeyCode.NUMPAD4, KeyCode.NUMPAD5};
        for (int rating = 0; rating < digits.length; rating++) {
            int value = rating;
            inputListener.bind(new KeyCodeCombination(digits[rating]), () -> photoManager.updateRating(library.getSelection(), value));
            inputListener.bind(new KeyCodeCombination(numpad[rating]), () -> photoManager.updateRating(library.getSelection(), value));
        }

        inputListener.bind(new KeyCodeCombination(KeyCode.Q), () -> applyColorLabel(ColorLabel.RED));
        inputListener.bind(new KeyCodeCombination(KeyCode.W), () -> applyColorLabel(ColorLabel.YELLOW));
        inputListener.bind(new KeyCodeCombination(KeyCode.E), () -> applyColorLabel(ColorLabel.GREEN));
        inputListener.bind(new KeyCodeCombination(KeyCode.R), () -> applyColorLabel(ColorLabel.BLUE));
        inputListener.bind(new KeyCodeCombination(KeyCode.T), () -> applyColorLabel(null));

        inputListener.bind(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN), () -> {
            PhotoEntry active = library.getSelectedPhotoProperty().get();
            if (active != null) {
                editComment(active);
            }
        });
        inputListener.bind(new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN), () -> toggleLike(library.getSelection()));
        inputListener.bind(new KeyCodeCombination(KeyCode.DELETE), () -> confirmRemove(library.getSelection()));
        inputListener.bind(new KeyCodeCombination(KeyCode.DELETE, KeyCombination.SHIFT_DOWN), () -> confirmDelete(library.getSelection()));

        inputListener.bind(new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN), () -> toggle(library.getLikedProperty()));
        inputListener.bind(new KeyCodeCombination(KeyCode.C, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN), () -> toggle(library.getCommentedProperty()));
        inputListener.bind(new KeyCodeCombination(KeyCode.E, KeyCombination.SHORTCUT_DOWN, KeyCombination.ALT_DOWN), () -> toggle(library.getEditedProperty()));
    }

    private static void toggle(BooleanProperty filter) {
        filter.set(!filter.get());
    }

    /**
     * A right-clicked photo that is part of the selection acts for the whole selection, otherwise only for itself.
     */
    private List<PhotoEntry> targetsOf(PhotoEntry photo) {
        List<PhotoEntry> selection = library.getSelection();
        return selection.contains(photo) ? selection : List.of(photo);
    }

    private void toggleLike(List<PhotoEntry> photos) {
        if (photos.isEmpty()) {
            return;
        }
        // Mixed selection: like all of them first, like Lightroom's flags
        boolean like = !photos.stream().allMatch(photo -> photo.getIsLiked().get());
        photoManager.updateLiked(photos, like);
    }

    private void editComment(PhotoEntry photo) {
        CommentEditor.show(cellOf(photo), photo.getFileName(), photo.getComment(),
                text -> photoManager.updateComment(photo, text));
    }

    /**
     * The visible cell showing the photo, or the whole strip if it is scrolled out of view.
     */
    private Node cellOf(PhotoEntry photo) {
        for (Node node : filmstripList.lookupAll(".filmstrip-cell")) {
            if (node instanceof ListCell<?> cell && cell.getItem() == photo && cell.isVisible()) {
                return cell;
            }
        }
        return filmstripList;
    }

    private void confirmRemove(List<PhotoEntry> photos) {
        if (photos.isEmpty()) {
            return;
        }
        boolean confirmed = ConfirmDialog.show(filmstripRoot.getScene().getWindow(),
                "Remove " + describe(photos) + " from the catalog?",
                "Edits and ratings are removed. The files stay on the disk and can be imported again.",
                "Remove", true);
        if (confirmed) {
            logger.info("Removing {} photo(s) from the catalog", photos.size());
            photoManager.removePhotos(photos);
        }
    }

    private void confirmDelete(List<PhotoEntry> photos) {
        if (photos.isEmpty()) {
            return;
        }
        boolean confirmed = ConfirmDialog.show(filmstripRoot.getScene().getWindow(),
                "Delete " + describe(photos) + " from the disk?",
                "The original files are deleted permanently, together with their edits. This can't be undone.",
                "Delete", true);
        if (confirmed) {
            logger.info("Deleting {} photo(s) from the disk", photos.size());
            photoManager.deletePhotos(photos);
        }
    }

    private static String describe(List<PhotoEntry> photos) {
        return photos.size() == 1 ? "“" + photos.getFirst().getFileName() + "”" : photos.size() + " photos";
    }
}
