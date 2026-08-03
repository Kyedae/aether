package dev.aether.modules.pest.helpers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PestLifecycleManagerTest {
    @Test
    void retriesSetSpawnOnlyWhileThePendingFarmingSessionIsValid() {
        assertEquals(2, PestLifecycleManager.SETSPAWN_MAX_ATTEMPTS);
        assertTrue(PestLifecycleManager.shouldRetrySetSpawn(
                PestLifecycleManager.Stage.PRE, 4, 4, false, false));
        assertFalse(PestLifecycleManager.shouldRetrySetSpawn(
                PestLifecycleManager.Stage.PRE, 4, 5, false, false));
        assertFalse(PestLifecycleManager.shouldRetrySetSpawn(
                PestLifecycleManager.Stage.PRE, 4, 4, true, false));
        assertFalse(PestLifecycleManager.shouldRetrySetSpawn(
                PestLifecycleManager.Stage.PRE, 4, 4, false, true));
    }
}
