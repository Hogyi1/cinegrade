package hu.elte.ik.thesis.cinegrade.javafx.ui.components;

import hu.elte.ik.thesis.cinegrade.domain.catalog.Catalog;
import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import hu.elte.ik.thesis.cinegrade.infra.utils.format.FormatUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.util.function.Consumer;

public class CatalogViewItem extends ListCell<Catalog> {

    @FXML
    private HBox rootPane;

    @FXML
    private Label nameLabel;

    @FXML
    private Label sizeLabel;

    @FXML
    private Label recentLabel;

    @FXML
    private Label createdAtLabel;

    @FXML
    private Button optionsButton;

    private ContextMenu optionsMenu;
    private Consumer<Catalog> onRemoveFromRecents;
    private Consumer<Catalog> onDelete;

    public CatalogViewItem() {
        this(null, null);
    }

    public CatalogViewItem(Consumer<Catalog> onRemoveFromRecents, Consumer<Catalog> onDelete) {
        this.onRemoveFromRecents = onRemoveFromRecents;
        this.onDelete = onDelete;

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxmls/catalogFileViewItem.fxml"));
        loader.setRoot(this);
        loader.setController(this);

        try {
            loader.load();
        } catch (IOException exception) {
            throw new CineGradeException(ErrorCode.REQ_RESOURCE_MISSING, "/fxmls/catalogFileViewItem.fxml");
        }

        setupOptionsMenu();
        setGraphic(rootPane);
    }

    private void setupOptionsMenu() {
        optionsMenu = new ContextMenu();

        MenuItem removeFromRecentsItem = new MenuItem("Remove from Recents");
        removeFromRecentsItem.setOnAction(e -> {
            Catalog current = getItem();
            if (current != null && onRemoveFromRecents != null) {
                onRemoveFromRecents.accept(current);
            }
            optionsMenu.hide();
        });

        MenuItem deleteCatalogItem = new MenuItem("Delete Catalog");
        deleteCatalogItem.getStyleClass().add("menu-item-danger");
        deleteCatalogItem.setOnAction(e -> {
            createConfirmationModal();
            e.consume();
            optionsMenu.hide();
        });

        optionsMenu.getItems().addAll(removeFromRecentsItem, deleteCatalogItem);

        optionsButton.setOnAction(e -> {
            if (optionsMenu.isShowing()) {
                optionsMenu.hide();
            } else {
                optionsMenu.show(optionsButton, Side.BOTTOM, -120, 6);
            }
        });
    }

    @Override
    protected void updateItem(Catalog item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setGraphic(null);
            setText(null);
            if (optionsMenu != null && optionsMenu.isShowing()) {
                optionsMenu.hide();
            }
            return;
        }

        if (getGraphic() != rootPane) {
            setGraphic(rootPane);
        }

        nameLabel.setText(item.getCatalogName() != null ? item.getCatalogName() : "");
        sizeLabel.setText(item.getSize() > 0 ? FormatUtils.formatBytes(item.getSize()) : "Empty");
        recentLabel.setText(FormatUtils.formatRelativeTime(item.getLastOpenedAt()));
        createdAtLabel.setText(FormatUtils.formatDate(item.getCreatedAt()));
    }

    private void createConfirmationModal() {
        Catalog current = getItem();
        if (current == null) {
            return;
        }
        String name = current.getCatalogName() != null ? current.getCatalogName() : "this catalog";
        boolean confirmed = ConfirmDialog.show(getScene().getWindow(),
                "Delete “" + name + "”?",
                "The catalog and its edits are deleted. This can't be undone.",
                "Delete catalog", true);
        if (confirmed && onDelete != null) {
            onDelete.accept(current);
        }
    }
}
