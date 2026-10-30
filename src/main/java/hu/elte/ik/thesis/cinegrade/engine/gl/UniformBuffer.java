package hu.elte.ik.thesis.cinegrade.engine.gl;

import hu.elte.ik.thesis.cinegrade.domain.editing.EditState;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL15.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL30.glBindBufferBase;
import static org.lwjgl.opengl.GL31.GL_UNIFORM_BUFFER;

public abstract class UniformBuffer<T> implements AutoCloseable {

    protected final String BLOCK_NAME;
    protected final int BINDING; // Same binding

    private final int BYTE_BUFFER_SIZE; // 3 rows * 16 bytes each calc by hand
    private final ByteBuffer buffer;
    private final int uboID;

    public UniformBuffer(int BINDING, String BLOCK_NAME, int BYTE_BUFFER_SIZE) {
        this.BINDING = BINDING;
        this.BLOCK_NAME = BLOCK_NAME;
        this.BYTE_BUFFER_SIZE = BYTE_BUFFER_SIZE;

        uboID = glGenBuffers();

        // float 1f ~ 0x3F800000 -> GPU: [00] [00] [80] [3F] ~ little endian JVM default is big endian [3F] [80] [00] [00]
        this.buffer = ByteBuffer.allocateDirect(BYTE_BUFFER_SIZE).order(ByteOrder.nativeOrder());

        glBindBuffer(GL_UNIFORM_BUFFER, uboID);
        glBufferData(GL_UNIFORM_BUFFER, BYTE_BUFFER_SIZE, GL_DYNAMIC_DRAW);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
        bindBase();
    }

    public void bindBase() {
        glBindBufferBase(GL_UNIFORM_BUFFER, BINDING, uboID);
    }

    public void upload(T editState) {
        buffer.clear();
        convertTo(editState, buffer);
        buffer.flip();

        glBindBuffer(GL_UNIFORM_BUFFER, uboID);
        glBufferSubData(GL_UNIFORM_BUFFER, 0, buffer);
        glBindBuffer(GL_UNIFORM_BUFFER, 0);
    }

    protected abstract void convertTo(T type, ByteBuffer buffer);

    @Override
    public void close() {
        glDeleteBuffers(uboID);
    }
}
