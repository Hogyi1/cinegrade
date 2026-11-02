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

        glDetachShader(programID, vertexID);
        glDetachShader(programID, fragmentID);
        glDeleteShader(vertexID);
        glDeleteShader(fragmentID);

        if (glGetProgrami(programID, GL_LINK_STATUS) == GL_FALSE) {
            throw new CineGradeException(ErrorCode.SHADER_LINK_FAILED, glGetProgramInfoLog(programID));
        }
    }

    // [AI-GENERATED METHOD] DEV: shader hot reload, see Documentation/REMOVE_BEFORE_RELEASE.md
    /**
     * Dev hot reload: builds a new program from these sources and swaps it in. If anything fails to compile or
     * link, the error is logged and the current program stays. GL thread only; bind the uniform blocks again after.
     *
     * @return true if the new program is in use
     */
    public boolean reload(String vertexSource, String fragmentSource) {
        int vertexID = compileSource(GL_VERTEX_SHADER, vertexSource, vertexFile);
        int fragmentID = vertexID == 0 ? 0 : compileSource(GL_FRAGMENT_SHADER, fragmentSource, fragmentFile);
        if (fragmentID == 0) {
            if (vertexID != 0) {
                glDeleteShader(vertexID);
            }
            return false;
        }

        int newProgram = glCreateProgram();
        glAttachShader(newProgram, vertexID);
        glAttachShader(newProgram, fragmentID);
        glLinkProgram(newProgram);
        glDetachShader(newProgram, vertexID);
        glDetachShader(newProgram, fragmentID);
        glDeleteShader(vertexID);
        glDeleteShader(fragmentID);

        if (glGetProgrami(newProgram, GL_LINK_STATUS) == GL_FALSE) {
            logger.error("Shader reload: link failed, keeping the previous program:\n{}", glGetProgramInfoLog(newProgram));
            glDeleteProgram(newProgram);
            return false;
        }

        glDeleteProgram(programID);
        programID = newProgram;
        // locations belong to the old program
        uniformLocations.clear();
        return true;
    }

    // [AI-GENERATED METHOD] DEV: shader hot reload, see Documentation/REMOVE_BEFORE_RELEASE.md
    /**
     * Compiles one stage for {@link #reload}; 0 (and the log) instead of an exception on a compile error.
     */
    private int compileSource(int type, String source, String name) {
        int shaderID = glCreateShader(type);
        glShaderSource(shaderID, source);
        glCompileShader(shaderID);
        if (glGetShaderi(shaderID, GL_COMPILE_STATUS) == GL_FALSE) {
            logger.error("Shader reload: {} failed to compile, keeping the previous program:\n{}", name, glGetShaderInfoLog(shaderID));
            glDeleteShader(shaderID);
            return 0;
        }
        return shaderID;
    }

    /**
     * Ties a uniform block of this program to a binding point. Call once after {@link #create()}.
     * The UBO attached to the same binding point (glBindBufferBase) is what the block reads.
     */
    public void bindUniformBlock(String blockName, int binding) {
        int index = glGetUniformBlockIndex(programID, blockName);
        if (index == GL_INVALID_INDEX) {
            logger.warn("Uniform block '{}' not found (unused in the shader?)", blockName);
            return;
        }
        glUniformBlockBinding(programID, index, binding);
    }

    public void bindIntUniform(String name, int value) {
        int location = uniform(name);
        if (location != -1) {
            glUniform1i(location, value);
        }
    }

    public void bindFloatUniform(String name, float value) {
        int location = uniform(name);
        if (location != -1) {
            glUniform1f(location, value);
        }
    }

    public void bindVec2Uniform(String name, Vector2 value) {
        int location = uniform(name);
        if (location != -1) {
            glUniform2f(location, value.x(), value.y());
        }
    }

    public void bindVec3Uniform(String name, Vector3 value) {
        int location = uniform(name);
        if (location != -1) {
            glUniform3f(location, value.x(), value.y(), value.z());
        }
    }

    public void bindVec4Uniform(String name, Vector4 value) {
        int location = uniform(name);
        if (location != -1) {
            glUniform4f(location, value.x(), value.y(), value.z(), value.w());
        }
    }

    /**
     * Location of a plain uniform, looked up once. -1 if the shader does not use it (setting -1 is a no-op).
     */
    private int uniform(String name) {
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

    public record Vector2(float x, float y) {
        public static Vector2 of(float v) { return new Vector2(v, v); }
    }

    public record Vector3(float x, float y, float z) {
        public static Vector3 of(float v) { return new Vector3(v, v, v); }
    }

    public record Vector4(float x, float y, float z, float w) {
        public static Vector4 of(float v) { return new Vector4(v, v, v, v); }
    }
}
