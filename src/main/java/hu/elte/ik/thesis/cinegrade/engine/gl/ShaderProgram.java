package hu.elte.ik.thesis.cinegrade.engine.gl;

import hu.elte.ik.thesis.cinegrade.domain.enums.ErrorCode;
import hu.elte.ik.thesis.cinegrade.domain.exceptions.CineGradeException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL31.GL_INVALID_INDEX;
import static org.lwjgl.opengl.GL31.glGetUniformBlockIndex;
import static org.lwjgl.opengl.GL31.glUniformBlockBinding;

public class ShaderProgram implements AutoCloseable {
    private static final Logger logger = LogManager.getLogger(ShaderProgram.class);
    private String vertexFile = "/shaders/vertex.vert";
    private String fragmentFile = "/shaders/fragment.frag";
    private int programID;
    private final Map<String, Integer> uniformLocations = new HashMap<>();

    public ShaderProgram() {
    }

    public ShaderProgram(String vertexShader, String fragmentShader) {
        this.fragmentFile = fragmentShader;
        this.vertexFile = vertexShader;
    }

    private String readFile(String fileName) {
        try (InputStream inputStream = this.getClass().getResourceAsStream(fileName)) {
            if (inputStream == null) {
                throw new CineGradeException(ErrorCode.REQ_RESOURCE_MISSING, fileName);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new CineGradeException(ErrorCode.REQ_RESOURCE_MISSING, fileName);
        }
    }

    private int loadShader(int type, String fileName) {
        int shaderID = glCreateShader(type);
        glShaderSource(shaderID, readFile(fileName));
        glCompileShader(shaderID);

        if (glGetShaderi(shaderID, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shaderID);
            glDeleteShader(shaderID);
            throw new CineGradeException(ErrorCode.SHADER_COMPILATION_FAILED, fileName + ": " + log);
        }

        return shaderID;
    }

    public void create() {
        programID = glCreateProgram();
        int vertexID = loadShader(GL_VERTEX_SHADER, vertexFile);
        int fragmentID = loadShader(GL_FRAGMENT_SHADER, fragmentFile);

        glAttachShader(programID, vertexID);
        glAttachShader(programID, fragmentID);
        glLinkProgram(programID);

        // The linked program keeps the compiled code; the shader objects are not needed anymore
        glDetachShader(programID, vertexID);
        glDetachShader(programID, fragmentID);
        glDeleteShader(vertexID);
        glDeleteShader(fragmentID);

        if (glGetProgrami(programID, GL_LINK_STATUS) == GL_FALSE) {
            throw new CineGradeException(ErrorCode.SHADER_LINK_FAILED, glGetProgramInfoLog(programID));
        }
    }

    /**
     * Ties a uniform block of this program to a binding point. Call once after {@link #create()}.
     * The UBO attached to the same binding point (glBindBufferBase) is what the block reads.
     */
    public void bindUniformBlock(String blockName, int binding) {
        int index = glGetUniformBlockIndex(programID, blockName);
        if (index == GL_INVALID_INDEX) {
            // The compiler removes blocks the shader never reads
            logger.warn("Uniform block '{}' not found (unused in the shader?)", blockName);
            return;
        }
        glUniformBlockBinding(programID, index, binding);
    }

    /**
     * Location of a plain uniform, looked up once. -1 if the shader does not use it (setting -1 is a no-op).
     */
    public int uniform(String name) {
        return uniformLocations.computeIfAbsent(name, n -> glGetUniformLocation(programID, n));
    }

    public void use() {
        glUseProgram(programID);
    }

    @Override
    public void close() {
        glUseProgram(0);
        glDeleteProgram(programID);
    }
}
