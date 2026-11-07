package hu.elte.ik.thesis.cinegrade.javafx.ui.presenters;

import hu.elte.ik.thesis.cinegrade.app.managers.editing.EditingManager;
import javafx.application.Platform;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.BlendMode;
import javafx.scene.paint.Color;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadPoolExecutor;

public class HistogramService {

    private final Canvas canvas;
    private final GraphicsContext graphicsContext;
    private int[][] last;

    public HistogramService(Canvas canvas) {
        this.canvas = canvas;
        this.graphicsContext = canvas.getGraphicsContext2D();
        canvas.widthProperty().addListener((obs, oldVal, newVal) -> redraw());
        canvas.heightProperty().addListener((obs, oldVal, newVal) -> redraw());
    }

    public void drawHistogram(ByteBuffer bgra) {
        int[][] bins = new int[4][256];
        for (int i = 0; i + 3 < bgra.limit(); i += 4 * 4) {
            int b = bgra.get(i) & 0xFF;
            int g = bgra.get(i + 1) & 0xFF;
            int r = bgra.get(i + 2) & 0xFF;
            bins[0][r]++;
            bins[1][g]++;
            bins[2][b]++;
            bins[3][Math.round(0.2126f * r + 0.7152f * g + 0.0722f * b)]++;
        }
        Platform.runLater(() -> {
            last = bins;
            redraw();
        });
    }

    public void redraw() {
        graphicsContext.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        // Implementation for drawing histogram bars would go here
        if (last == null) return;
        int max = 0;
        for (int[] channel : last) {
            for (int i = 1; i < channel.length - 1; ++i) {
                max = Math.max(max, channel[i]);
            }
        }
        if (max == 0) return;

        graphicsContext.setGlobalBlendMode(BlendMode.SRC_OVER);
        drawColor(last[3], new Color(0.83, 0.83, 0.85, 0.35), max); // Luminance

        graphicsContext.setGlobalBlendMode(BlendMode.ADD);
        drawColor(last[0], new Color(1.00, 0.30, 0.30, 0.50), max); // Red
        drawColor(last[1], new Color(0.18, 0.80, 0.44, 0.45), max); // Green
        drawColor(last[2], new Color(0.23, 0.51, 0.96, 0.55), max); // Blue
        graphicsContext.setGlobalBlendMode(BlendMode.SRC_OVER);
    }

    private void drawColor(int[] bins, Color color, float maxVal) {
        // use sqrt of the maxval
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        graphicsContext.setFill(color);
        graphicsContext.beginPath();
        graphicsContext.moveTo(0, h);

        for (int i = 1; i < last[0].length; ++i) {
            double barHeight = Math.sqrt(bins[i] / maxVal) * h;
            graphicsContext.lineTo(w * i / bins.length, h - barHeight); // bottom right
        }
        graphicsContext.lineTo(w, h);
        graphicsContext.closePath();
        graphicsContext.fill();
    }

    public void clear() {
        last = null;
        redraw();
    }
}
