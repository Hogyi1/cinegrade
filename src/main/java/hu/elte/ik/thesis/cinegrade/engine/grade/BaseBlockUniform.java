package hu.elte.ik.thesis.cinegrade.engine.grade;

import hu.elte.ik.thesis.cinegrade.domain.editing.EditState;
import hu.elte.ik.thesis.cinegrade.engine.gl.ShaderProgram;
import hu.elte.ik.thesis.cinegrade.engine.gl.UniformBuffer;

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
public class BaseBlockUniform extends UniformBuffer<EditState> {

    public static final String BLOCK_NAME = "BasicBlock";
    public static final int BINDING = 0;
    // 48 bytes of basic params + 8 vec4 for the color mixer
    private static final int BYTE_BUFFER_SIZE = 48 + EditState.HSL_COLORS * 16;

    public BaseBlockUniform() {
        super(BINDING, BLOCK_NAME, BYTE_BUFFER_SIZE);
    }

    @Override
    protected void convertTo(EditState editState, ByteBuffer buffer) {
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

        // 8 x 16 bytes: hsl_red .. hsl_magenta, each vec4(hue, sat, lum, pad); the array already has this layout
        for (float value : editState.getHsl()) {
            buffer.putFloat(value);
        }
    }
}
