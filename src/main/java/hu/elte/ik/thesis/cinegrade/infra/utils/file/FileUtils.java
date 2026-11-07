package hu.elte.ik.thesis.cinegrade.infra.utils.file;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class FileUtils {

    public static String getFileNameWithoutExtension(File file) {
        if (file == null) {
            return "";
        }
        String name = file.getName();
        int dotIndex = name.lastIndexOf('.');
        return (dotIndex > 0 ? name.substring(0, dotIndex) : name).toLowerCase(Locale.ROOT);
    }

    public static String getBaseKey(File file) {
        if (file == null) {
            return "";
        }
        String path = file.getAbsolutePath();
        int dotIndex = path.lastIndexOf('.');
        int sepIndex = Math.max(path.lastIndexOf(File.separatorChar), path.lastIndexOf('/'));
        return (dotIndex > sepIndex ? path.substring(0, dotIndex) : path).toLowerCase(Locale.ROOT);
    }

    /**
     * Picks a free file name in the import directory: name.ext, name_1.ext, name_2.ext, ...
     * Two cards can both hold IMG_0001.CR3, and the second must not overwrite the first.
     */
    public static Path uniqueTarget(Path directory, String fileName) {
        Path target = directory.resolve(fileName);
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String extension = dot > 0 ? fileName.substring(dot) : "";
        for (int n = 1; Files.exists(target); n++) {
            target = directory.resolve(base + "_" + n + extension);
        }
        return target;
    }

    public static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
