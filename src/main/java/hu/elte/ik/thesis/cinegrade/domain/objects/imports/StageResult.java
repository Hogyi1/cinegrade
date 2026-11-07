package hu.elte.ik.thesis.cinegrade.domain.objects.imports;

import java.util.List;

public record StageResult(List<StageEntry> entries, List<Runnable> fxUpdates) {}

