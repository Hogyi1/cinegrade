package hu.elte.ik.thesis.cinegrade.domain.editing;

import java.nio.file.Path;

public record LutExportOption(int lutSize, Path destination, boolean leaveOutExp) {
}
