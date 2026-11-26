package hu.elte.ik.thesis.cinegrade.engine.grade;

import hu.elte.ik.thesis.cinegrade.domain.objects.EditState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WhiteBalancer Color Calculation Unit Tests")
class WhiteBalancerTest {

    @Test
    @DisplayName("withAsShot with null preserves the original EditState reference")
    void testWithAsShotNullPreservesInstance() {
        EditState original = new EditState("photo-preserve");
        EditState result = WhiteBalancer.withAsShot(original, null);

        assertSame(original, result, "Should return the exact same instance if asShot is null");
    }

    @Test
    @DisplayName("withAsShot with new values clones EditState and sets camera neutral baseline")
    void testWithAsShotAppliesNewBaseline() {
        EditState original = new EditState("photo-neutral");
        WhiteBalancer.WhiteBalance cameraWb = new WhiteBalancer.WhiteBalance(6100.0f, 8.0f);

        EditState result = WhiteBalancer.withAsShot(original, cameraWb);

        assertNotSame(original, result, "Should return a cloned copy with new baseline");
        assertEquals(6100.0f, result.getAsShotTemperature(), 1e-6);
        assertEquals(8.0f, result.getAsShotTint(), 1e-6);
        assertEquals(6100.0f, result.getTemperature(), 1e-6);
        assertEquals(8.0f, result.getTint(), 1e-6);
    }

    @Test
    @DisplayName("fromCamera calculates accurate white balance from camera multipliers")
    void testFromCameraMultipliers() {
        // Multipliers equal daylight -> should yield roughly 5500K neutral
        float[] camMul = new float[]{2.0f, 1.0f, 1.5f};
        float[] preMul = new float[]{2.0f, 1.0f, 1.5f};

        Optional<WhiteBalancer.WhiteBalance> wbOpt = WhiteBalancer.fromCamera(camMul, preMul);

        assertTrue(wbOpt.isPresent());
        WhiteBalancer.WhiteBalance wb = wbOpt.get();
        assertEquals(5500.0f, wb.temperature(), 50.0f);
        assertEquals(0.0f, wb.tint(), 5.0f);
    }

    @Test
    @DisplayName("fromCamera rejects invalid or non-positive camera multipliers")
    void testFromCameraRejectsInvalidInput() {
        assertFalse(WhiteBalancer.fromCamera(null, new float[]{1.0f, 1.0f, 1.0f}).isPresent());
        assertFalse(WhiteBalancer.fromCamera(new float[]{1.0f, 1.0f}, new float[]{1.0f, 1.0f, 1.0f}).isPresent());
        assertFalse(WhiteBalancer.fromCamera(new float[]{0.0f, 1.0f, 1.0f}, new float[]{1.0f, 1.0f, 1.0f}).isPresent());
    }
}
