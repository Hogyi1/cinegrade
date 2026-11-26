package hu.elte.ik.thesis.cinegrade.engine;

import hu.elte.ik.thesis.cinegrade.domain.objects.EditState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("GradeParam Parameter Engine Unit Tests")
class GradeParamTest {

    @Test
    @DisplayName("Read and write operations update EditState accurately")
    void testReadAndWrite() {
        EditState state = new EditState("photo-test");

        GradeParam.EXPOSURE.write(state, 1.5f);
        assertEquals(1.5f, GradeParam.EXPOSURE.read(state), 1e-6);

        GradeParam.CONTRAST.write(state, 40.0f);
        assertEquals(40.0f, GradeParam.CONTRAST.read(state), 1e-6);

        GradeParam.TEMPERATURE.write(state, 6500.0f);
        assertEquals(6500.0f, GradeParam.TEMPERATURE.read(state), 1e-6);

        GradeParam.TINT.write(state, -15.0f);
        assertEquals(-15.0f, GradeParam.TINT.read(state), 1e-6);

        GradeParam.HUE_RED.write(state, 25.0f);
        assertEquals(25.0f, GradeParam.HUE_RED.read(state), 1e-6);
    }

    @Test
    @DisplayName("Slider position mapping for linear sliders passes through raw parameter value")
    void testLinearSliderPositionMapping() {
        GradeParam exposure = GradeParam.EXPOSURE; // -5.0 .. +5.0, def: 0.0

        // Neutral center
        assertEquals(0.0, exposure.toSliderPosition(0.0f), 1e-5);
        assertEquals(0.0f, exposure.fromSliderPosition(0.0), 1e-5f);

        // Maximum (+5.0 EV)
        assertEquals(5.0, exposure.toSliderPosition(5.0f), 1e-5);
        assertEquals(5.0f, exposure.fromSliderPosition(5.0), 1e-5f);

        // Minimum (-5.0 EV)
        assertEquals(-5.0, exposure.toSliderPosition(-5.0f), 1e-5);
        assertEquals(-5.0f, exposure.fromSliderPosition(-5.0), 1e-5f);
    }

    @Test
    @DisplayName("Slider position mapping handles asymmetric Kelvin temperature range")
    void testAsymmetricTemperatureSliderMapping() {
        GradeParam temp = GradeParam.TEMPERATURE; // 2000K .. 12000K, def: 5500K

        // Default neutral position maps to 0.0
        assertEquals(0.0, temp.toSliderPosition(5500.0f), 1e-5);
        assertEquals(5500.0f, temp.fromSliderPosition(0.0), 1.0f);

        // Minimum Kelvin (2000K) maps to -1.0
        assertEquals(-1.0, temp.toSliderPosition(2000.0f), 1e-5);
        assertEquals(2000.0f, temp.fromSliderPosition(-1.0), 1.0f);

        // Maximum Kelvin (12000K) maps to +1.0
        assertEquals(1.0, temp.toSliderPosition(12000.0f), 1e-5);
        assertEquals(12000.0f, temp.fromSliderPosition(1.0), 1.0f);
    }

    @Test
    @DisplayName("Parameters belong to appropriate UI sections")
    void testSections() {
        assertEquals(GradeParam.Section.BASIC, GradeParam.EXPOSURE.getSection());
        assertEquals(GradeParam.Section.BASIC, GradeParam.TEMPERATURE.getSection());
        assertEquals(GradeParam.Section.BASIC, GradeParam.CONTRAST.getSection());
        assertEquals(GradeParam.Section.COLOR_MIXER, GradeParam.HUE_RED.getSection());
        assertEquals(GradeParam.Section.COLOR_MIXER, GradeParam.SAT_BLUE.getSection());
        assertEquals(GradeParam.Section.COLOR_MIXER, GradeParam.LUM_GREEN.getSection());
    }
}
