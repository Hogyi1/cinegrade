package hu.elte.ik.thesis.cinegrade.engine.renderer;

import hu.elte.ik.thesis.cinegrade.engine.GpuWorker;

public class ExportRenderer implements AutoCloseable {

    private final GpuWorker worker;

    public ExportRenderer(GpuWorker gpuWorker) {
        this.worker = gpuWorker;
    }

    @Override
    public void close() {

    }
}
