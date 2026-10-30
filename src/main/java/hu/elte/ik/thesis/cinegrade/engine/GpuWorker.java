package hu.elte.ik.thesis.cinegrade.engine;

import hu.elte.ik.thesis.cinegrade.app.scope.ResourceStack;
import hu.elte.ik.thesis.cinegrade.engine.grade.BaseBlockUniform;
import hu.elte.ik.thesis.cinegrade.engine.gl.FullscreenQuad;
import hu.elte.ik.thesis.cinegrade.engine.gl.ShaderProgram;
import lombok.Getter;
import org.lwjgl.opengl.GL;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

import static org.lwjgl.glfw.GLFW.*;

@Getter
public class GpuWorker implements AutoCloseable {

    private final Thread thread;
    private final long windowID;
    private final String name;
    private final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<>();
    @Getter
    private ShaderProgram shader;
    @Getter
    private FullscreenQuad fullscreenQuad;
    private final ResourceStack resourceStack = new ResourceStack();
    private volatile boolean running = true;

    private final CompletableFuture<Void> ready = new CompletableFuture<>();

    public GpuWorker(String name, long windowID) {
        this.name = name;
        this.windowID = windowID;
        thread = new Thread(this::loop, "cinegrade-gpu-" + name);
        thread.setDaemon(true);
    }

    public <T> CompletableFuture<T> submit(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        taskQueue.offer(() -> {
            try {
                T result = task.call();
                future.complete(result);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public void init() {
        running = true;
        thread.start();
    }

    // Belongs to the thread
    private void loop() {
        try {
            glfwMakeContextCurrent(windowID);
            GL.createCapabilities();

            this.shader = resourceStack.push(new ShaderProgram());
            this.fullscreenQuad = resourceStack.push(new FullscreenQuad());
            shader.create();
            shader.bindUniformBlock(BaseBlockUniform.BLOCK_NAME, BaseBlockUniform.BINDING);
        } catch (Throwable t) {
            // Without this a failed init leaves ready pending and the splash waits forever
            ready.completeExceptionally(t);
            return;
        }

        ready.complete(null);
        while (running) {
            try {
                taskQueue.take().run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Override
    public void close() {
        taskQueue.offer(() -> {
            resourceStack.close();
            glfwMakeContextCurrent(0L);
            running = false;
        });
        try {
            thread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
