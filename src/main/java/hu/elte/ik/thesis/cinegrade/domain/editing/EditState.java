// [AI-GENERATED]
package hu.elte.ik.thesis.cinegrade.domain.editing;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class EditState {
    private String photoId;
    private float exposure = 0.0f;
    private float contrast = 0.0f;
    private float highlights = 0.0f;
    private float shadows = 0.0f;
    private float whites = 0.0f;
    private float blacks = 0.0f;
    private float temperature = 5500.0f;
    private float tint = 0.0f;
    private float saturation = 0.0f;
    private float vibrance = 0.0f;
    private String lutPath;
    private String curvesJson;
    private Instant updatedAt = Instant.now();

    // [AI-GENERATED METHOD]
    public EditState() {
    }

    // [AI-GENERATED METHOD]
    public EditState(String photoId) {
        this.photoId = photoId;
    }

    // [AI-GENERATED METHOD]
    public EditState(String photoId, float exposure, float contrast, float highlights, float shadows, float whites, float blacks, float temperature, float tint, float saturation, float vibrance, String lutPath, String curvesJson, Instant updatedAt) {
        this.photoId = photoId;
        this.exposure = exposure;
        this.contrast = contrast;
        this.highlights = highlights;
        this.shadows = shadows;
        this.whites = whites;
        this.blacks = blacks;
        this.temperature = temperature;
        this.tint = tint;
        this.saturation = saturation;
        this.vibrance = vibrance;
        this.lutPath = lutPath;
        this.curvesJson = curvesJson;
        this.updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }
}
