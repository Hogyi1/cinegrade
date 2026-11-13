package hu.elte.ik.thesis.cinegrade.domain.objects;

import hu.elte.ik.thesis.cinegrade.domain.enums.PhotoExtension;

import java.nio.file.Path;
import java.util.Locale;

public record PhotoExportOptions(Path exportDirectory,
                                 PhotoExtension extension,
                                 float compressionQuality,
                                 String namingPrefix,
                                 String namingPostfix,
                                 String fileName) {
    // [AI-GENERATED METHOD] The same name ExportBatchService writes: [prefix_]fileName[_postfix].ext (it adds the dot)
    public Path getExportPath() {
        String prefix = namingPrefix == null || namingPrefix.isBlank() ? "" : namingPrefix + "_";
        String postfix = namingPostfix == null || namingPostfix.isBlank() ? "" : "_" + namingPostfix;
        return exportDirectory.resolve(prefix + fileName + postfix + "." + extension.getExtension().toLowerCase(Locale.ROOT));
    }
}
