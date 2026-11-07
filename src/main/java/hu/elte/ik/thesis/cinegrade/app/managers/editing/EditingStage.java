package hu.elte.ik.thesis.cinegrade.app.managers.editing;

import hu.elte.ik.thesis.cinegrade.domain.objects.EditState;
import hu.elte.ik.thesis.cinegrade.domain.objects.PhotoEntry;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.image.Image;

/**
 * What the develop page shows, in one place. {@link EditingManager} writes it, the controllers bind to it.
 * Only the before / after toggle can be set from outside this package. FX thread only.
 */
public class EditingStage {

    private final ObjectProperty<EditState> currentEditState = new SimpleObjectProperty<>();
    private final ObjectProperty<PhotoEntry> currentPhoto = new SimpleObjectProperty<>();
    private final ObjectProperty<Image> placeholderImage = new SimpleObjectProperty<>();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty showOriginal = new SimpleBooleanProperty(false);
    private final ReadOnlyObjectProperty<Image> viewportImage;

    public EditingStage(ReadOnlyObjectProperty<Image> viewportImage, ObjectProperty<PhotoEntry> selectedPhoto) {
        this.viewportImage = viewportImage;
        currentPhoto.bind(selectedPhoto);
    }

    public ReadOnlyObjectProperty<EditState> currentEditStateProperty() {
        return currentEditState;
    }

    public ReadOnlyObjectProperty<PhotoEntry> currentPhotoProperty() {
        return currentPhoto;
    }

    public ReadOnlyObjectProperty<Image> viewportImageProperty() {
        return viewportImage;
    }

    public ReadOnlyObjectProperty<Image> placeholderImageProperty() {
        return placeholderImage;
    }

    public ReadOnlyBooleanProperty loadingProperty() {
        return loading;
    }

    public BooleanProperty showOriginalProperty() {
        return showOriginal;
    }

    EditState getEditState() {
        return currentEditState.get();
    }

    void setEditState(EditState editState) {
        currentEditState.set(editState);
    }

    PhotoEntry getPhoto() {
        return currentPhoto.get();
    }

    void setPlaceholder(Image image) {
        placeholderImage.set(image);
    }

    void setLoading(boolean value) {
        loading.set(value);
    }

    boolean isShowOriginal() {
        return showOriginal.get();
    }

    void setShowOriginal(boolean value) {
        showOriginal.set(value);
    }
}
