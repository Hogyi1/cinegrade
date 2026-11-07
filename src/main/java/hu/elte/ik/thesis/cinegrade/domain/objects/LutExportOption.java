package hu.elte.ik.thesis.cinegrade.domain.objects;

import java.nio.file.Path;

public record LutExportOption(int lutSize, Path destination, boolean leaveOutExp) {
}
