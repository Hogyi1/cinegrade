package hu.elte.ik.thesis.cinegrade.javafx.ui.components;

import hu.elte.ik.thesis.cinegrade.domain.editing.PhotoEntry;
import hu.elte.ik.thesis.cinegrade.domain.enums.ColorLabel;
import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.value.ObservableValue;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * One photo of the filmstrip: the thumbnail inside a frame colored by the color label, with clickable rating
 * stars, a like heart and comment/edited badges.
 * <p>
 * The cell only shows the photo and reports clicks through {@link Actions}; it never writes to the photo or the
 * database itself. Listeners on the shown photo are moved along when the ListView reuses the cell.
 */
public class FilmstripCell extends ListCell<PhotoEntry> {

    private static final Logger logger = LogManager.getLogger(FilmstripCell.class);
    private static final String FXML_PATH = "/fxmls/filmstripCell.fxml";

    public static final double CELL_SIZE = 125.0;
    private static final double THUMB_SIZE = 100.0;
    private static final double STAR_SIZE = 11.0;

    private static final PseudoClass ACTIVE = PseudoClass.getPseudoClass("active");
    private static final PseudoClass RATED = PseudoClass.getPseudoClass("rated");

    private static final Image STAR = icon("/images/icons/Star.png");
    private static final Image LOVE = icon("/images/icons/Love.png");
    private static final Image COMMENT = icon("/images/icons/Comments.png");
    private static final Image EDITED = icon("/images/icons/Edited.png");

    /**
     * What the cell asks for when the user clicks; the controller forwards them to the PhotoManager.
     *
     * @param rate        set this rating on the photo (0 clears it)
     * @param toggleLike  flip the liked state of the photo
     * @param editComment open the comment editor for the photo
     * @param remove      remove the photo from the catalog
     */
    public record Actions(BiConsumer<PhotoEntry, Integer> rate,
                          Consumer<PhotoEntry> toggleLike,
                          Consumer<PhotoEntry> editComment,
                          Consumer<PhotoEntry> remove) {
    }

    @FXML
    private StackPane cellRoot;
    @FXML
    private StackPane thumbBox;
    @FXML
    private ImageView thumbnailView;
    @FXML
    private Label missingBadge;
    @FXML
    private HBox badgeBox;
    @FXML
    private ImageView editedIcon;
    @FXML
    private ImageView commentIcon;
    @FXML
    private ImageView likedIcon;
    @FXML
    private HBox starBox;

    private final ThumbnailImageCache thumbnails;
    private final ObservableValue<PhotoEntry> activePhoto;
    private final Actions actions;
    private final ImageView[] stars = new ImageView[5];
    private final Tooltip commentTooltip = new Tooltip();
    private final MenuItem likeItem = new MenuItem();
    private final ContextMenu contextMenu = new ContextMenu();

    // One listener for every badge property, one for a new thumbnail
    private final InvalidationListener badgeListener = obs -> refreshBadges();
    private final InvalidationListener thumbnailListener = obs -> loadThumbnail();
    private final InvalidationListener activeListener = obs -> refreshActive();

    private PhotoEntry currentPhoto;

    /**
     * @param thumbnails  decoded thumbnail cache shared by every cell
     * @param activePhoto the photo open in the viewport, highlighted on its cell
     * @param actions     click handlers
     */
    public FilmstripCell(ThumbnailImageCache thumbnails, ObservableValue<PhotoEntry> activePhoto, Actions actions) {
        this.thumbnails = thumbnails;
        this.activePhoto = activePhoto;
        this.actions = actions;

        FXMLLoader loader = new FXMLLoader(getClass().getResource(FXML_PATH));
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException exception) {
            logger.error("Failed to load {}", FXML_PATH, exception);
            throw new CineGradeException(ErrorCode.REQ_RESOURCE_MISSING, FXML_PATH);
        }

        getStyleClass().add("filmstrip-cell");
        setText(null);
        setupLayout();
        setupStars();
        setupBadges();
        setupContextMenu();

        // Not removed: the property belongs to the catalog scope, which outlives no cell
        activePhoto.addListener(activeListener);
    }

    private void setupLayout() {
        cellRoot.setMinSize(CELL_SIZE, CELL_SIZE);
        cellRoot.setPrefSize(CELL_SIZE, CELL_SIZE);
        cellRoot.setMaxSize(CELL_SIZE, CELL_SIZE);
        thumbBox.setMinSize(THUMB_SIZE, THUMB_SIZE);
        thumbBox.setPrefSize(THUMB_SIZE, THUMB_SIZE);
        thumbBox.setMaxSize(THUMB_SIZE, THUMB_SIZE);
        thumbnailView.setFitWidth(THUMB_SIZE);
        thumbnailView.setFitHeight(THUMB_SIZE);
    }

    private void setupStars() {
        for (int i = 0; i < stars.length; i++) {
            int rating = i + 1;
            ImageView star = new ImageView(STAR);
            star.setFitWidth(STAR_SIZE);
            star.setFitHeight(STAR_SIZE);
            star.setPreserveRatio(true);
            star.setPickOnBounds(true);
            star.getStyleClass().add("filmstrip-cell-star");
            // Pressed is what the ListView selects on: consume it so rating a photo does not select it
            star.addEventHandler(MouseEvent.MOUSE_PRESSED, MouseEvent::consume);
            star.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && currentPhoto != null) {
                    // Clicking the current rating again clears it
                    actions.rate().accept(currentPhoto, currentPhoto.getRating() == rating ? 0 : rating);
                }
                e.consume();
            });
            stars[i] = star;
        }
        starBox.getChildren().setAll(stars);
    }

    private void setupBadges() {
        likedIcon.setImage(LOVE);
        commentIcon.setImage(COMMENT);
        editedIcon.setImage(EDITED);
        Tooltip.install(commentIcon, commentTooltip);
        Tooltip.install(editedIcon, new Tooltip("Edited"));

        likedIcon.addEventHandler(MouseEvent.MOUSE_PRESSED, MouseEvent::consume);
        likedIcon.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && currentPhoto != null) {
                actions.toggleLike().accept(currentPhoto);
            }
            e.consume();
        });
    }

    private void setupContextMenu() {
        MenuItem commentItem = new MenuItem("Edit comment…");
        commentItem.setOnAction(e -> {
            if (currentPhoto != null) {
                actions.editComment().accept(currentPhoto);
            }
        });
        likeItem.setOnAction(e -> {
            if (currentPhoto != null) {
                actions.toggleLike().accept(currentPhoto);
            }
        });
        MenuItem removeItem = new MenuItem("Remove from catalog");
        removeItem.getStyleClass().add("menu-item-danger");
        removeItem.setOnAction(e -> {
            if (currentPhoto != null) {
                actions.remove().accept(currentPhoto);
            }
        });
        contextMenu.getItems().setAll(likeItem, commentItem, new SeparatorMenuItem(), removeItem);
        contextMenu.setOnShowing(e -> likeItem.setText(
                currentPhoto != null && currentPhoto.getIsLiked().get() ? "Unlike" : "Like"));
    }

    @Override
    protected void updateItem(PhotoEntry photo, boolean empty) {
        super.updateItem(photo, empty);

        PhotoEntry next = empty ? null : photo;
        if (next != currentPhoto) {
            unbind();
            currentPhoto = next;
            bind();
        }

        if (currentPhoto == null) {
            setGraphic(null);
            setContextMenu(null);
            return;
        }
        setGraphic(cellRoot);
        setContextMenu(contextMenu);
    }

    private void bind() {
        if (currentPhoto == null) {
            return;
        }
        for (Observable property : badgeProperties(currentPhoto)) {
            property.addListener(badgeListener);
        }
        currentPhoto.getThumbnailVersion().addListener(thumbnailListener);

        thumbnailView.setImage(null);
        loadThumbnail();
        refreshBadges();
        refreshActive();
    }

    private void unbind() {
        if (currentPhoto == null) {
            return;
        }
        for (Observable property : badgeProperties(currentPhoto)) {
            property.removeListener(badgeListener);
        }
        currentPhoto.getThumbnailVersion().removeListener(thumbnailListener);
    }

    private static Observable[] badgeProperties(PhotoEntry photo) {
        return new Observable[]{
                photo.getRatingProperty(),
                photo.getColorLabelProperty(),
                photo.getIsLiked(),
                photo.getIsEdited(),
                photo.getCommentProperty(),
                photo.missingPhotoProperty()
        };
    }

    private void loadThumbnail() {
        PhotoEntry requested = currentPhoto;
        if (requested == null) {
            return;
        }
        thumbnails.load(requested, image -> {
            // The cell may show another photo by the time the image arrives
            if (requested == currentPhoto) {
                thumbnailView.setImage(image);
            }
        });
    }

    private void refreshBadges() {
        PhotoEntry photo = currentPhoto;
        if (photo == null) {
            return;
        }

        int rating = photo.getRating();
        for (int i = 0; i < stars.length; i++) {
            stars[i].getStyleClass().remove("filled");
            if (i < rating) {
                stars[i].getStyleClass().add("filled");
            }
        }
        cellRoot.pseudoClassStateChanged(RATED, rating > 0);

        boolean liked = photo.getIsLiked().get();
        likedIcon.getStyleClass().remove("liked");
        if (liked) {
            likedIcon.getStyleClass().add("liked");
        }

        boolean commented = photo.hasComment();
        commentIcon.setVisible(commented);
        commentIcon.setManaged(commented);
        commentTooltip.setText(commented ? photo.getComment() : "");

        boolean edited = photo.getIsEdited().get();
        editedIcon.setVisible(edited);
        editedIcon.setManaged(edited);

        missingBadge.setVisible(photo.isMissingPhoto());
        if (photo.isMissingPhoto()) {
            missingBadge.setTooltip(new Tooltip("Original file is missing:\n" + photo.getFilePath()));
        }

        for (ColorLabel label : ColorLabel.values()) {
            cellRoot.getStyleClass().remove(label.styleClass());
        }
        ColorLabel label = ColorLabel.fromStored(photo.getColorLabel());
        if (label != null) {
            cellRoot.getStyleClass().add(label.styleClass());
        }

        setTooltip(new Tooltip(photo.getFileName()));
    }

    private void refreshActive() {
        cellRoot.pseudoClassStateChanged(ACTIVE, currentPhoto != null && currentPhoto == activePhoto.getValue());
    }

    private static Image icon(String path) {
        return new Image(Objects.requireNonNull(FilmstripCell.class.getResourceAsStream(path), path));
    }
}
