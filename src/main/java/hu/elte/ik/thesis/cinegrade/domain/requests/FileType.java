package hu.elte.ik.thesis.cinegrade.domain.requests;

public enum FileType {
    CGEDIT("CG Edit file containing the editors "),
    LUT("LUT"),
    IMAGE("Image");

    private FileType(String description) {
        this.description = description;
    }

    private final String description;
}
