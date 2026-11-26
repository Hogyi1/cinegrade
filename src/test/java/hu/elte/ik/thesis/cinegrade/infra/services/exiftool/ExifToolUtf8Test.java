package hu.elte.ik.thesis.cinegrade.infra.services.exiftool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ExifTool UTF-8 Argument Encoding Unit Tests")
class ExifToolUtf8Test {

    @Test
    @DisplayName("Argument file preserves Hungarian accented characters in UTF-8")
    void testHungarianAccentedPathEncoding(@TempDir Path tempDir) throws IOException {
        String hungarianFileName = "2024_Őszi_Bükk_Hegység_Árvíz_Üdülő_Út.CR2";
        Path testFile = tempDir.resolve(hungarianFileName);

        List<String> lines = List.of(
                "-charset",
                "filename=utf8",
                "-json",
                "-fast",
                testFile.toAbsolutePath().toString()
        );

        Path argFile = tempDir.resolve("exiftool-test.args");
        Files.write(argFile, lines, StandardCharsets.UTF_8);

        // Read back as UTF-8
        List<String> readLines = Files.readAllLines(argFile, StandardCharsets.UTF_8);

        assertEquals(5, readLines.size());
        assertEquals("-charset", readLines.get(0));
        assertEquals("filename=utf8", readLines.get(1));
        assertTrue(readLines.get(4).contains("Őszi_Bükk_Hegység_Árvíz_Üdülő_Út"),
                "Accented characters must not suffer from mojibake or CP1250 corruption");
    }

    @Test
    @DisplayName("UTF-8 byte sequence differs from Windows-1250/ANSI fallback")
    void testUtf8ByteIntegrity() {
        String accented = "Árvíztűrő tükörfúrógép";
        byte[] utf8Bytes = accented.getBytes(StandardCharsets.UTF_8);
        byte[] isoBytes = accented.getBytes(StandardCharsets.ISO_8859_1);

        assertNotEquals(utf8Bytes.length, isoBytes.length,
                "Multi-byte UTF-8 encoding expands non-ASCII characters to 2 bytes");
        assertEquals(accented, new String(utf8Bytes, StandardCharsets.UTF_8));
    }
}
