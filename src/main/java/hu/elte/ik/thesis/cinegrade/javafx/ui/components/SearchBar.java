package hu.elte.ik.thesis.cinegrade.javafx.ui.components;

import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.StringProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.Objects;

/**
 * Text field with a search icon on the right. The icon is swapped for a clear (X) button while there is text;
 * clicking it or pressing Escape empties the field.
 * <p>
 * Usable straight from FXML: {@code <SearchBar fx:id="searchField" promptText="Search..."/>}. Styles come from
 * {@code /css/components.css}, added to every scene by {@code ThemeManager}; a panel stylesheet can restyle it
 * through {@code .search-bar} and {@code .search-bar-field}.
 */
public class SearchBar extends HBox {

    private static final String SEARCH_ICON = "/images/icons/Search.png";
    private static final PseudoClass FOCUSED_FIELD = PseudoClass.getPseudoClass("focused-field");

    private final TextField textField = new TextField();
    private final ImageView searchIcon = new ImageView();
    private final Button clearButton = new Button();

    public SearchBar() {
        getStyleClass().add("search-bar");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(8);

        textField.getStyleClass().add("search-bar-field");
        HBox.setHgrow(textField, Priority.ALWAYS);
        textField.setMaxWidth(Double.MAX_VALUE);

        searchIcon.setImage(new Image(Objects.requireNonNull(SearchBar.class.getResourceAsStream(SEARCH_ICON))));
        searchIcon.setFitWidth(16);
        searchIcon.setFitHeight(16);
        searchIcon.setPreserveRatio(true);
        searchIcon.setMouseTransparent(true);
        searchIcon.getStyleClass().add("search-bar-icon");

        Region cross = new Region();
        cross.getStyleClass().add("search-bar-clear-mark");
        clearButton.setGraphic(cross);
        clearButton.getStyleClass().add("search-bar-clear");
        clearButton.setFocusTraversable(false);
        clearButton.setOnAction(e -> {
            textField.clear();
            textField.requestFocus();
        });

        BooleanBinding empty = textField.textProperty().isEmpty();
        searchIcon.visibleProperty().bind(empty);
        clearButton.visibleProperty().bind(empty.not());

        StackPane trailing = new StackPane(searchIcon, clearButton);
        trailing.setMinWidth(18);
        trailing.setPrefWidth(18);

        textField.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE && !textField.getText().isEmpty()) {
                textField.clear();
                e.consume();
            }
        });

        setOnMouseClicked(e -> textField.requestFocus());
        textField.focusedProperty().addListener((obs, o, focused) -> pseudoClassStateChanged(FOCUSED_FIELD, focused));

        getChildren().addAll(textField, trailing);
    }

    public StringProperty textProperty() {
        return textField.textProperty();
    }

    public String getText() {
        return textField.getText();
    }

    public void setText(String text) {
        textField.setText(text);
    }

    public StringProperty promptTextProperty() {
        return textField.promptTextProperty();
    }

    public String getPromptText() {
        return textField.getPromptText();
    }

    public void setPromptText(String promptText) {
        textField.setPromptText(promptText);
    }

    public void clear() {
        textField.clear();
    }
}
