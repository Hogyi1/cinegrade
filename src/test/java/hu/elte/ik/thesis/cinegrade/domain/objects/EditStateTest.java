package hu.elte.ik.thesis.cinegrade.domain.objects;

import hu.elte.ik.thesis.cinegrade.domain.enums.WhiteBalanceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EditState Domain Logic Unit Tests")
class EditStateTest {

    @Test
    @DisplayName("Default constructor initializes neutral editing values")
    void testDefaultValues() {
        EditState state = new EditState("photo-123");

        assertEquals("photo-123", state.getPhotoId());
        assertEquals(0.0f, state.getExposure(), 1e-6);
        assertEquals(0.0f, state.getContrast(), 1e-6);
        assertEquals(0.0f, state.getHighlights(), 1e-6);
        assertEquals(0.0f, state.getShadows(), 1e-6);
        assertEquals(0.0f, state.getWhites(), 1e-6);
        assertEquals(0.0f, state.getBlacks(), 1e-6);
        assertEquals(5500.0f, state.getTemperature(), 1e-6);
        assertEquals(0.0f, state.getTint(), 1e-6);
        assertEquals(0.0f, state.getTemperatureOffset(), 1e-6);
        assertEquals(WhiteBalanceType.AS_SHOT, state.getWhiteBalance());
        assertNotNull(state.getHsl());
        assertEquals(EditState.HSL_SIZE, state.getHsl().length);
    }

    @Test
    @DisplayName("Copy constructor performs deep copy of mutable arrays")
    void testCopyConstructorDeepCopy() {
        EditState original = new EditState("photo-original");
        original.setExposure(1.25f);
        original.setContrast(20.0f);
        original.getHsl()[0] = 15.0f; // Red hue shift

        EditState copy = new EditState("photo-copy", original);

        assertEquals("photo-copy", copy.getPhotoId());
        assertEquals(1.25f, copy.getExposure(), 1e-6);
        assertEquals(20.0f, copy.getContrast(), 1e-6);
        assertEquals(15.0f, copy.getHsl()[0], 1e-6);

        // Mutating the copy must not mutate the original snapshot
        copy.getHsl()[0] = 50.0f;
        assertEquals(15.0f, original.getHsl()[0], 1e-6, "Original HSL must remain untouched");
    }

    @Test
    @DisplayName("White balance presets set correct Kelvin and tint values")
    void testApplyWhiteBalancePresets() {
        EditState state = new EditState("photo-wb");
        state.applyAsShot(5200.0f, 4.0f);

        // Apply DAYLIGHT preset
        state.applyWhiteBalance(WhiteBalanceType.DAYLIGHT);
        assertEquals(WhiteBalanceType.DAYLIGHT, state.getWhiteBalance());
        assertEquals(5500.0f, state.getTemperature(), 1e-6);
        assertEquals(0.0f, state.getTint(), 1e-6);

        // Apply TUNGSTEN preset
        state.applyWhiteBalance(WhiteBalanceType.TUNGSTEN);
        assertEquals(WhiteBalanceType.TUNGSTEN, state.getWhiteBalance());
        assertEquals(2850.0f, state.getTemperature(), 1e-6);
        assertEquals(0.0f, state.getTint(), 1e-6);

        // Reset to AS_SHOT
        state.applyWhiteBalance(WhiteBalanceType.AS_SHOT);
        assertEquals(WhiteBalanceType.AS_SHOT, state.getWhiteBalance());
        assertEquals(5200.0f, state.getTemperature(), 1e-6);
        assertEquals(4.0f, state.getTint(), 1e-6);
    }

    @Test
    @DisplayName("Adjusting temperature switches type to CUSTOM or restores AS_SHOT")
    void testUpdateWhiteBalanceType() {
        EditState state = new EditState("photo-wb-custom");
        state.applyAsShot(5600.0f, -2.0f);

        assertEquals(WhiteBalanceType.AS_SHOT, state.getWhiteBalance());

        // Change temperature away from neutral
        state.setTemperature(6200.0f);
        state.updateWhiteBalanceType();
        assertEquals(WhiteBalanceType.CUSTOM, state.getWhiteBalance());

        // Revert back to exact neutral
        state.setTemperature(5600.0f);
        state.updateWhiteBalanceType();
        assertEquals(WhiteBalanceType.AS_SHOT, state.getWhiteBalance());
    }

    @Test
    @DisplayName("Defaults helper preserves camera as-shot metadata")
    void testDefaultsPreservesAsShot() {
        EditState state = new EditState("photo-reset");
        state.applyAsShot(4800.0f, 6.0f);
        state.setExposure(2.5f);
        state.setContrast(35.0f);

        EditState reset = state.defaults();

        assertEquals(4800.0f, reset.getAsShotTemperature(), 1e-6);
        assertEquals(6.0f, reset.getAsShotTint(), 1e-6);
        assertEquals(4800.0f, reset.getTemperature(), 1e-6);
        assertEquals(6.0f, reset.getTint(), 1e-6);
        assertEquals(0.0f, reset.getExposure(), 1e-6, "Exposure must reset to 0.0");
        assertEquals(0.0f, reset.getContrast(), 1e-6, "Contrast must reset to 0.0");
    }

    @Test
    @DisplayName("Structural equality checks match all grading parameters")
    void testEqualsAndHashCode() {
        EditState s1 = new EditState("p1");
        EditState s2 = new EditState("p2");

        // Both are default state, so grading parameters are identical
        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());

        s2.setExposure(0.5f);
        assertNotEquals(s1, s2);
    }
}
