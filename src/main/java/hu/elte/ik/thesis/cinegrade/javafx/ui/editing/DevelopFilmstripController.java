package hu.elte.ik.thesis.cinegrade.javafx.ui.editing;

import hu.elte.ik.thesis.cinegrade.app.managers.photos.PhotoManager;
import hu.elte.ik.thesis.cinegrade.app.managers.photos.PhotoLibrary;
import hu.elte.ik.thesis.cinegrade.domain.editing.PhotoEntry;
import hu.elte.ik.thesis.cinegrade.domain.enums.ColorLabel;
import hu.elte.ik.thesis.cinegrade.domain.enums.Operation;
import hu.elte.ik.thesis.cinegrade.infra.services.storage.ThumbnailService;
import hu.elte.ik.thesis.cinegrade.javafx.ui.components.FilmstripCell;
import hu.elte.ik.thesis.cinegrade.javafx.ui.components.SearchBar;
import hu.elte.ik.thesis.cinegrade.javafx.ui.components.ThumbnailImageCache;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MultipleSelectionModel;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Filmstrip of the development page: filter bar and the photo strip.
 * <p>
 * Reads and binds to the {@link PhotoLibrary} (photos, filters, selection) and sends every change of a photo
 * through the {@link PhotoManager}. Clicking a photo only updates the library's selection; whoever shows the
 * photo listens to that.
 */
public class DevelopFilmstripController {

    private static final Logger logger = LogManager.getLogger(DevelopFilmstripController.class);

    // Cell plus the dark gap between two cells
    private static final double CELL_STEP = FilmstripCell.CELL_SIZE + 3;
    private static final int THUMBNAIL_DECODE_HEIGHT = 200;
    private static final int MAX_DECODED_THUMBNAILS = 300;

    private final PhotoLibrary library;
    private final PhotoManager photoManager;
    private final ThumbnailImageCache thumbnails;

    // Set while the library's selection is copied into the ListView, so that copy is not sent back
    private boolean syncingSelection;

    @FXML
    private VBox filmstripRoot;
    @FXML
    private Label filmstripCountLabel;
    @FXML
    private ToggleButton lastImportToggle;
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

    public DevelopFilmstripController(PhotoLibrary library, PhotoManager photoManager,
                                      ThumbnailService thumbnailService, Executor decodePool) {
        this.library = library;
        this.photoManager = photoManager;
        this.thumbnails = new ThumbnailImageCache(thumbnailService, decodePool, THUMBNAIL_DECODE_HEIGHT, MAX_DECODED_THUMBNAILS);
    }

    @FXML
    private void initialize() {
        setupList();
        setupSelectionSync();
        setupCountLabel();
        setupFilters();
        setupRatingFilter();
        setupColorLabels();
    }

    private void setupList() {
        FilmstripCell.Actions actions = new FilmstripCell.Actions(
                photoManager::updateRating,
                photo -> photoManager.updateLiked(List.of(photo), !photo.getIsLiked().get()),
                this::editComment,
                this::confirmRemove);

        filmstripList.setItems(library.getSortedEntries());
        filmstripList.setFixedCellSize(CELL_STEP);
        filmstripList.setCellFactory(list -> new FilmstripCell(thumbnails, library.getSelectedPhotoProperty(), actions));
        filmstripList.setPlaceholder(new Label("No photos match the filters"));
        filmstripList.addEventHandler(KeyEvent.KEY_PRESSED, this::onListKey);
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

    private void setupCountLabel() {
        filmstripCountLabel.textProperty().bind(Bindings.createStringBinding(() -> {
            int listed = library.getListedPhotosProperty().get();
            int selected = library.getSelectedFilesProperty().get();
            PhotoEntry active = library.getSelectedPhotoProperty().get();
            String text = "%d photo%s / %d selected".formatted(listed, listed == 1 ? "" : "s", selected);
            return active != null ? text + " / " + active.getFileName() : text;
        }, library.getListedPhotosProperty(), library.getSelectedFilesProperty(), library.getSelectedPhotoProperty()));
    }

    private void setupFilters() {
        lastImportToggle.selectedProperty().bindBidirectional(library.getLastImportedProperty());
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
        Image starImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/icons/Star.png")));
        for (int i = 0; i < stars.length; i++) {
            int rating = i + 1;
            ImageView icon = new ImageView(starImage);
            icon.setFitWidth(15);
            icon.setFitHeight(15);
            icon.setPreserveRatio(true);
            stars[i].setGraphic(icon);
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
     * 0-5 rate the selected photos while the strip has the focus.
     */
    private void onListKey(KeyEvent event) {
        String key = event.getText();
        if (key.length() == 1 && key.charAt(0) >= '0' && key.charAt(0) <= '5') {
            photoManager.updateRating(library.getSelection(), key.charAt(0) - '0');
            event.consume();
        }
    }

    private void editComment(PhotoEntry photo) {
        TextInputDialog dialog = new TextInputDialog(photo.getComment() != null ? photo.getComment() : "");
        dialog.initOwner(filmstripRoot.getScene().getWindow());
        dialog.setTitle("Comment");
        dialog.setHeaderText(photo.getFileName());
        dialog.setContentText("Comment:");
        dialog.showAndWait().ifPresent(text -> photoManager.updateComment(photo, text));
    }

    private void confirmRemove(PhotoEntry photo) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Remove " + photo.getFileName() + " from the catalog? The file on disk is kept.",
                ButtonType.OK, ButtonType.CANCEL);
        alert.initOwner(filmstripRoot.getScene().getWindow());
        alert.setHeaderText(null);
        alert.showAndWait()
                .filter(ButtonType.OK::equals)
                .ifPresent(ok -> {
                    logger.info("Removing photo {} from the catalog", photo.getId());
                    photoManager.removePhoto(photo);
                });
    }
}
