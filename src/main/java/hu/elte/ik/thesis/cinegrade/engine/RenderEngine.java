package hu.elte.ik.thesis.cinegrade.engine;

import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import lombok.Getter;

import java.util.concurrent.CompletableFuture;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;

@Getter
public class RenderEngine implements AutoCloseable {

    private GpuWorker interactive;
    private GpuWorker background;

    long interactiveWindowID, backgroundWindowID;
    public RenderEngine() {
        if (!glfwInit())
            throw new CineGradeException(ErrorCode.OPENGL_INIT_FAILED);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        interactiveWindowID = glfwCreateWindow(1, 1, "GL", 0, 0);
        if (interactiveWindowID == 0)
            throw new CineGradeException(ErrorCode.OPENGL_WINDOW_CREATE_FAILED);

        glfwMakeContextCurrent(0L);
        interactive = new GpuWorker("interactive", interactiveWindowID);

        backgroundWindowID = glfwCreateWindow(1, 1, "GL", 0, 0);
        if (backgroundWindowID == 0)
            throw new CineGradeException(ErrorCode.OPENGL_WINDOW_CREATE_FAILED);
        glfwMakeContextCurrent(0L);
        background = new GpuWorker("background", backgroundWindowID);
    }

    /**
     * Starts the GPU threads.
     *
     * @return completes when every worker finished its GL init, or fails with the init error
     */
    public CompletableFuture<Void> start() {
        interactive.init();
        background.init();
        return CompletableFuture.allOf(interactive.getReady(), background.getReady());
    }

    @Override
    public void close() throws Exception {
        background.close();
        interactive.close();
        glfwDestroyWindow(interactiveWindowID);
        glfwDestroyWindow(backgroundWindowID);
        glfwTerminate();

    }
}
