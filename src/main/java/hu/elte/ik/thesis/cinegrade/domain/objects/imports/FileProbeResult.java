package hu.elte.ik.thesis.cinegrade.domain.objects.imports;

import com.google.gson.annotations.SerializedName;
import hu.elte.ik.thesis.cinegrade.domain.enums.PhotoExtension;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;

public record FileProbeResult(
        @SerializedName("SourceFile") String sourceFile,
        @SerializedName("MIMEType") String mimeType,
        @SerializedName("FileType") String fileType,
        @SerializedName("FileSize") String fileSize,
        @SerializedName("ImageSize") String imageSize,
        @SerializedName("Orientation") String orientation
) {

    public boolean isImage() {
        return mimeType != null && mimeType.toLowerCase().startsWith("image/");
    }

    public boolean isRaw() {
        return fileType != null && Arrays.stream(PhotoExtension.values()).
                filter(PhotoExtension::isRaw)
                .map(PhotoExtension::getExtension)
                .toList()
                .contains(fileType.toUpperCase());
    }

    public Path toPath() {
        return Path.of(sourceFile);
    }
    public File toFile() {
        return Path.of(sourceFile).toFile();
    }

    public int getRotation() {
        if (orientation == null) return 0;
        return switch (orientation.trim()) {
            case "Rotate 90 CW", "Mirror horizontal and rotate 90 CW" -> 90;
            case "Rotate 180", "Mirror vertical" -> 180;
            case "Rotate 270 CW", "Mirror horizontal and rotate 270 CW" -> 270;
            default -> 0;
        };
    }

    public boolean isMirrored() {
        if (orientation == null) return false;
        return switch (orientation.trim()) {
            case "Mirror horizontal",
                 "Mirror vertical",
                 "Mirror horizontal and rotate 270 CW",
                 "Mirror horizontal and rotate 90 CW" -> true;
            default -> false;
        };
    }
}