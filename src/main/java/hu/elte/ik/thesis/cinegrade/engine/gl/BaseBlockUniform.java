package hu.elte.ik.thesis.cinegrade.engine.gl;

import hu.elte.ik.thesis.cinegrade.domain.editing.EditState;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;

/**
 * The {@code BasicBlock} uniform block (std140) of the grade shader, filled from an {@link EditState}.
 * <p>
 * Binding: the shader's block is tied to {@link #BINDING} once after linking
 * ({@link ShaderProgram#bindUniformBlock}); before each draw {@link #bindBase()} attaches this buffer to the same
 * binding point. The number is the meeting point, the shader never sees the buffer id.
 */
public class BaseBlockUniform implements AutoCloseable {

    public static final String BLOCK_NAME = "BasicBlock";
    public static final int BINDING = 0;

    private static final int BYTE_BUFFER_SIZE = 48; // 3 rows * 16 bytes each
    private final ByteBuffer buffer;
    private final int uboID;

    public BaseBlockUniform() {
        uboID = glGenBuffers();
        // GL reads the floats in the machine's byte order; allocateDirect alone would be big-endian
        this.buffer = ByteBuffer.allocateDirect(BYTE_BUFFER_SIZE).order(ByteOrder.nativeOrder());

        glBindBuffer(GL_UNIFORM_BUFFER, uboID);
        glBufferData(GL_UNIFORM_BUFFER, BYTE_BUFFER_SIZE, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
    }

    /**
     * Attaches this buffer to {@link #BINDING} so the shader's {@code BasicBlock} reads from it.
     */
    public void bindBase() {
        glBindBufferBase(GL_UNIFORM_BUFFER, BINDING, uboID);
    }

    public void upload(EditState editState) {
        buffer.clear();
        convertToBuffer(editState, buffer);
        buffer.flip();

        glBindBuffer(GL_UNIFORM_BUFFER, uboID);
        glBufferSubData(GL_UNIFORM_BUFFER, 0, buffer);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
    }

    private void convertToBuffer(EditState editState, ByteBuffer buffer) {
        // 16 bytes
        buffer.putFloat(editState.getExposure());
        buffer.putFloat(editState.getTemperature());
        buffer.putFloat(editState.getTint());
        buffer.putFloat(editState.getContrast());

        // 16 bytes
        buffer.putFloat(editState.getHighlights());
        buffer.putFloat(editState.getShadows());
        buffer.putFloat(editState.getWhites());
        buffer.putFloat(editState.getBlacks());

        // 8 bytes - + padding
        buffer.putFloat(editState.getVibrance());
        buffer.putFloat(editState.getSaturation());
        buffer.putFloat(0.0f); // pad1
        buffer.putFloat(0.0f); // pad2
    }

    @Override
    public void close() {
        glDeleteBuffers(uboID);
    }
}
