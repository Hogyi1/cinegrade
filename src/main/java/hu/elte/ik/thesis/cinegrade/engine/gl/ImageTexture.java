package hu.elte.ik.thesis.cinegrade.engine.gl;

import hu.elte.ik.thesis.cinegrade.infra.services.libraw.LibrawService;
import lombok.Getter;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL30.GL_RGB16F;
import static org.lwjgl.opengl.GL30C.glGenerateMipmap;

/**
 * The source photo on the GPU. The shader samples it through texture unit 0.
 */
@Getter
public class ImageTexture implements AutoCloseable {

    private final int sourceTextureID;
    private int width;
    private int height;
    // RAWs come from LibRaw in linear light, JPEG/PNG are sRGB encoded: the shader decodes only the latter
    private boolean linear;

    public ImageTexture() {
        sourceTextureID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, sourceTextureID);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    /**
     * Uploads the pixels straight from the decoded native buffer (no extra copy).
     * The buffer is not freed here; the caller still owns it.
     */
    public void uploadTexture(LibrawService.DecodedImage decodedRaw) {
        bind();
        boolean is16Bit = decodedRaw.bits() == 16;
        int internalFormat = is16Bit ? GL_RGB16F : GL_RGBA8;
        int format = is16Bit ? GL_RGB : GL_RGBA;
        int uploadType = is16Bit ? GL_UNSIGNED_SHORT : GL_UNSIGNED_BYTE;

        // RGB16 rows are width * 6 bytes: only 2-byte aligned when the width is odd (GL assumes 4)
        glPixelStorei(GL_UNPACK_ALIGNMENT, is16Bit ? 2 : 4);
        glTexImage2D(GL_TEXTURE_2D, 0, internalFormat,
                decodedRaw.width(), decodedRaw.height(), 0, format, uploadType, decodedRaw.data());
        glGenerateMipmap(GL_TEXTURE_2D);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);

        width = decodedRaw.width();
        height = decodedRaw.height();
        linear = is16Bit;
    }

    public void bind() {
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sourceTextureID);
    }

    @Override
    public void close() {
        glDeleteTextures(sourceTextureID);
    }
}
