package dev.aether.modules.pest.helpers;

import dev.aether.config.AetherConfig;
import dev.aether.macro.MacroWorkerThread;
import dev.aether.modules.failsafe.FailsafeManager;
import dev.aether.modules.gear.GearManager;
import dev.aether.modules.pest.PestManager;
import dev.aether.modules.rotation.RotationManager;
import dev.aether.util.ClientUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Dedicated pre-cleaning AOTV route used by Ballsack Shredder. */
final class PestBallsackShredder {
    private static final long AOTV_TIMEOUT_MS = 10_000L;
    private static final double POSITION_CHANGE_DISTANCE_SQR = 1.0;

    private PestBallsackShredder() {
    }

    static boolean run(Minecraft client, int sessionId) throws InterruptedException {
        if (client == null || client.player == null || client.options == null) {
            return false;
        }

        int aotvSlot = GearManager.findAspectOfTheVoidSlot(client);
        if (aotvSlot < 0 || aotvSlot >= 9) {
            ClientUtils.sendDebugMessage("Ballsack Shredder: no AOTV found; continuing without the route.");
            return true;
        }

        // This route intentionally preserves the current aim while firing the AOTV.
        GearManager.swapToAOTVSync(client);
        PestClientThread.run(client, () -> {
            ClientUtils.setKeyMappingState(client.options.keyShift, true);
            ClientUtils.setKeyMappingState(client.options.keyUse, true);
        });

        int positionChanges = waitForPositionChanges(client, sessionId);
        releaseAotvKeys(client);
        if (shouldAbort(client, sessionId)) {
            return false;
        }
        if (positionChanges < 2) {
            ClientUtils.sendDebugMessage("Ballsack Shredder: AOTV timed out after "
                    + positionChanges + " position change(s); continuing pest cleaning.");
            return true;
        }

        lookDownAndHoldVacuum(client, sessionId);
        return !shouldAbort(client, sessionId);
    }

    private static int waitForPositionChanges(Minecraft client, int sessionId) throws InterruptedException {
        Vec3 lastPosition = PestClientThread.call(client, () -> client.player.position(), null);
        if (lastPosition == null) {
            return 0;
        }

        int changes = 0;
        long deadline = System.currentTimeMillis() + AOTV_TIMEOUT_MS;
        while (changes < 2 && System.currentTimeMillis() < deadline && !shouldAbort(client, sessionId)) {
            MacroWorkerThread.sleep(25);
            Vec3 previousPosition = lastPosition;
            Vec3 currentPosition = PestClientThread.call(client, () -> client.player.position(), previousPosition);
            if (currentPosition.distanceToSqr(previousPosition) >= POSITION_CHANGE_DISTANCE_SQR) {
                changes++;
                lastPosition = currentPosition;
            }
        }
        return changes;
    }

    private static void lookDownAndHoldVacuum(Minecraft client, int sessionId) throws InterruptedException {
        PestClientThread.run(client, () -> RotationManager.rotateToYawPitch(
                client, client.player.getYRot(), 90.0f, AetherConfig.ROTATION_TIME.get(), true));
        long rotationDeadline = System.currentTimeMillis() + AetherConfig.ROTATION_TIME.get() + 1_000L;
        while (RotationManager.isRotating() && System.currentTimeMillis() < rotationDeadline
                && !shouldAbort(client, sessionId)) {
            MacroWorkerThread.sleep(20);
        }

        int vacuumSlot = PestLoadoutHelper.findVacuumHotbarSlot(client);
        if (vacuumSlot < 0 || vacuumSlot >= 9) {
            return;
        }
        PestClientThread.run(client, () -> {
            FailsafeManager.selectHotbarSlot(client, vacuumSlot);
            ClientUtils.setKeyMappingState(client.options.keyUse, true);
        });
        long holdUntil = System.currentTimeMillis() + AetherConfig.BALLSACK_LOOK_DOWN_TIME_MS.get();
        while (System.currentTimeMillis() < holdUntil && !shouldAbort(client, sessionId)) {
            MacroWorkerThread.sleep(20);
        }
    }

    private static void releaseAotvKeys(Minecraft client) {
        PestClientThread.run(client, () -> {
            ClientUtils.setKeyMappingState(client.options.keyShift, false);
            ClientUtils.setKeyMappingState(client.options.keyUse, false);
        });
    }

    private static boolean shouldAbort(Minecraft client, int sessionId) {
        return MacroWorkerThread.shouldAbortTask(client)
                || sessionId != PestManager.getCurrentPestSessionId()
                || !PestManager.isCleaningInProgress();
    }
}