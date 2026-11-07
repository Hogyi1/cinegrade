package hu.elte.ik.thesis.cinegrade.domain.objects;

import hu.elte.ik.thesis.cinegrade.domain.enums.WhiteBalanceType;
import hu.elte.ik.thesis.cinegrade.engine.GradeParam;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class Preset {
    private final Instant createdAt;
    private UUID id;
    private int formatVersion;
    private String name, author, description;
    // Category name; in the DB it is the category's id, in a .cgedit file the name (an imported preset brings its pack)
    private String category;
    private EditState editState;
    private Instant updatedAt;
    private Set<GradeParam> included;
    private boolean includeWb;
    private transient boolean builtIn;
    private transient StringProperty nameProperty;
    private transient StringProperty categoryProperty;

    public Preset(UUID id, int formatVersion, String name, String category, Instant createdAt) {
        this.id = id;
        this.formatVersion = formatVersion;
        this.name = name;
        this.category = category;
        this.createdAt = createdAt;
        included = new HashSet<>();
    }

    public StringProperty nameProperty() {
        if (nameProperty == null) nameProperty = new SimpleStringProperty(name);
        return nameProperty;
    }

    public void setName(String name) {
        this.name = name;
        nameProperty().set(name);
    }

    public StringProperty categoryProperty() {
        if (categoryProperty == null) categoryProperty = new SimpleStringProperty(category);
        return categoryProperty;
    }

    public void setCategory(String category) {
        this.category = category;
        categoryProperty().set(category);
    }

    public void applyTo(EditState target) {
        for (GradeParam p : included) p.write(target, p.read(editState));
        if (!includeWb) {
            return;
        }
        // CUSTOM carries its own values; As Shot, Daylight... are recomputed for the target photo
        if (editState.getWhiteBalance() == WhiteBalanceType.CUSTOM) {
            target.setTemperature(editState.getTemperature());
            target.setTemperatureOffset(editState.getTemperatureOffset());
            target.setTint(editState.getTint());
            target.setWhiteBalance(WhiteBalanceType.CUSTOM);
        } else {
            target.applyWhiteBalance(editState.getWhiteBalance());
        }
    }
}
