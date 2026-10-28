package hu.elte.ik.thesis.cinegrade.engine.gl;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

import static org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL11.glReadPixels;
import static org.lwjgl.opengl.GL12.GL_BGRA;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER;
import static org.lwjgl.opengl.GL30.GL_MAP_READ_BIT;
import static org.lwjgl.opengl.GL30.glMapBufferRange;

/**
 * Reads the bound framebuffer back to the CPU through two PBOs.
 * <p>
 * Normal read: start the copy of this frame into one PBO and hand out the other one (the previous frame), so the
 * CPU never waits for the GPU. Flush read: hand out the frame that was just copied (waits for it); used for the
 * last frame, which would otherwise never show.
 */
public class PixelReader implements AutoCloseable {

    private final int[] pboID = new int[2];
    private int w, h, pboWriteIndex = 0;
    private long pboByteSize;
    private boolean previousUnread;

    public PixelReader(int w, int h) {
        glGenBuffers(pboID);
        resize(w, h);
    }

    public void resize(int w, int h) {
        pboByteSize = (long) w * h * 4;
        for (int i = 0; i < 2; i++) {
            glBindBuffer(GL_PIXEL_PACK_BUFFER, pboID[i]);
            glBufferData(GL_PIXEL_PACK_BUFFER, pboByteSize, GL_STREAM_READ);
        }
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        this.w = w;
        this.h = h;
        previousUnread = false;
    }

    /**
     * Reads the currently bound framebuffer.
     *
     * @param consumer       gets the BGRA bytes ({@code w * h * 4}); only valid inside the call
     * @param flushImmediate true: hand out this frame (waits for the GPU); false: hand out the previous frame
     */
    public void read(Consumer<ByteBuffer> consumer, boolean flushImmediate) {
        glBindBuffer(GL_PIXEL_PACK_BUFFER, pboID[pboWriteIndex]);
        glReadPixels(0, 0, w, h, GL_BGRA, GL_UNSIGNED_BYTE, 0L);

        int previousIndex = 1 - pboWriteIndex;
        if (flushImmediate) {
            map(pboWriteIndex, consumer);
            // The previous frame is older than what was just shown, never show it
            previousUnread = false;
        } else {
            if (previousUnread) {
                map(previousIndex, consumer);
            }
            previousUnread = true;
        }

        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
        pboWriteIndex = previousIndex;
    }

    private void map(int index, Consumer<ByteBuffer> consumer) {
        glBindBuffer(GL_PIXEL_PACK_BUFFER, pboID[index]);
        ByteBuffer mapped = glMapBufferRange(GL_PIXEL_PACK_BUFFER, 0, pboByteSize, GL_MAP_READ_BIT);
        if (mapped != null) {
            try {
                consumer.accept(mapped);
            } finally {
                glUnmapBuffer(GL_PIXEL_PACK_BUFFER);
            }
        }
    }

    @Override
    public void close() {
        glDeleteBuffers(pboID);
    }
}
