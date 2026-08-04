package dev.aether.modules;

import dev.aether.macro.MacroState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TablistSetupManagerTest {
    @Test
    void matchesTheAetherGardenPreset() {
        assertTrue(TablistSetupManager.shouldEnableWidget("Visitors Widget"));
        assertTrue(TablistSetupManager.shouldEnableWidget("Pests Widget"));
        assertTrue(TablistSetupManager.shouldEnableWidget("Skills Widget"));
        assertTrue(TablistSetupManager.shouldEnableWidget("✔ Visitors Widget"));
        assertTrue(TablistSetupManager.shouldEnableWidget("✖ Pests Widget"));
        assertFalse(TablistSetupManager.shouldEnableWidget("Effects Widget"));
        assertFalse(TablistSetupManager.shouldEnableWidget("✔ Effects Widget"));

        assertTrue(TablistSetupManager.shouldEnableSetting("Wrapping", false));
        assertTrue(TablistSetupManager.shouldEnableSetting("✖ Spacing", false));
        assertTrue(TablistSetupManager.shouldEnableSetting("✖ Wrapping", false));
        assertFalse(TablistSetupManager.shouldEnableSetting("Show Offer Desire", false));
        assertTrue(TablistSetupManager.shouldEnableSetting("Show Plots", true));
    }

    @Test
    void recognizesTheSelectedVisitorCount() {
        assertTrue(TablistSetupManager.isFiveVisitorsSelected("Visitor Amount\n➟ 5 Visitors\nLeft/Right-click to change!"));
        assertFalse(TablistSetupManager.isFiveVisitorsSelected("Visitor Amount\n5 Visitors\n4 Visitors"));
        assertFalse(TablistSetupManager.isFiveVisitorsSelected("Visitor Amount\n➟ 1 Visitor"));
    }

    @Test
    void onlyRunsAutomaticSetupInTheGardenUntilCompleted() {
        assertTrue(TablistSetupManager.needsAutomaticSetup(false, MacroState.Location.GARDEN));
        assertFalse(TablistSetupManager.needsAutomaticSetup(true, MacroState.Location.GARDEN));
        assertFalse(TablistSetupManager.needsAutomaticSetup(false, MacroState.Location.HUB));
    }
}
