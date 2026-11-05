package hu.elte.ik.thesis.cinegrade.domain.editing;

import hu.elte.ik.thesis.cinegrade.domain.enums.PhotoExtension;

import java.nio.file.Path;

public record PhotoExportOptions(Path exportDirectory,
                                 PhotoExtension extension,
                                 float compressionQuality,
                                 String namingPrefix,
                                 String namingPostfix,
                                 String fileName) {
}
