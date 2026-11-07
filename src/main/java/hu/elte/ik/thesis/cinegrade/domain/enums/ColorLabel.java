package hu.elte.ik.thesis.cinegrade.domain.enums;

import lombok.Getter;

/**
 * Color labels of a photo. Visual only (the frame of the filmstrip cell), not a filter.
 * Stored in {@code photos.color_label} by {@link #name()}; no label is stored as {@code null}.
 */
@Getter
public enum ColorLabel {
    RED("#E02424"),
    YELLOW("#F59E0B"),
    GREEN("#10B981"),
    BLUE("#1C7DF0");

    private final String hex;

    ColorLabel(String hex) {
        this.hex = hex;
    }

    /**
     * @param stored the stored value, may be {@code null}
     * @return the label, or {@code null} for no label or an unknown value
     */
    public static ColorLabel fromStored(String stored) {
        if (stored == null) {
            return null;
        }
        for (ColorLabel label : values()) {
            if (label.name().equalsIgnoreCase(stored)) {
                return label;
            }
        }
        return null;
    }

    /**
     * The style class the filmstrip cell frame gets for this label, e.g. {@code label-red}.
     */
    public String styleClass() {
        return "label-" + name().toLowerCase();
    }
}
