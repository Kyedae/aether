package dev.aether.modules.pest.helpers;

import dev.aether.config.AetherConfig;
import dev.aether.macro.MacroState;
import dev.aether.macro.MacroStateManager;
import dev.aether.macro.MacroWorkerThread;
import dev.aether.macro.farming.FarmingMacroManager;
import dev.aether.modules.gear.helpers.LoadoutManager;
import dev.aether.modules.pest.ManualPestManager;
import dev.aether.modules.pest.PestManager;
import dev.aether.util.ClientUtils;
import dev.aether.util.CommandUtils;
import net.minecraft.client.Minecraft;

/**
 * Coordinates one pest cycle without coupling the setup and teardown work to
 * the implementation that actually clears the pests.
 */
public final class PestLifecycleManager {

    public enum Stage {
        IDLE,
        PRE,
        CLEANING,
        POST
    }

    private static volatile Stage stage = Stage.IDLE;
    private static volatile boolean sunsetPestsRestoreNight = false;
    private static final long SETSPAWN_RETRY_MS = 1_000L;
    static final int SETSPAWN_MAX_ATTEMPTS = 2;

    private PestLifecycleManager() {
    }

    public static Stage getStage() {
        return stage;
    }

    public static void reset() {
        stage = Stage.IDLE;
        sunsetPestsRestoreNight = false;
    }

    public static boolean restorePendingSunsetPestsNight(Minecraft client) {
        if (!sunsetPestsRestoreNight) {
            return true;
        }

        sunsetPestsRestoreNight = false;
        boolean switched = GardenTimeManager.switchToNightTime(client);
        if (!switched) {
            ClientUtils.sendDebugMessage("Sunset Pests: failed to switch garden time to night.");
        }
        return switched;
    }

    static boolean prepareSunsetPestsDaytime(Minecraft client) {
        if (!AetherConfig.SUNSET_PESTS.get()) {
            return true;
        }

        sunsetPestsRestoreNight = true;
        boolean switched = GardenTimeManager.switchToDaytime(client);
        if (!switched) {
            ClientUtils.sendDebugMessage("Sunset Pests: failed to switch garden time to day.");
        }
        return switched;
    }

    public static boolean start(Minecraft client, String plot, int pestCount, int sessionId) {
        if (stage != Stage.IDLE
                || PestManager.isCleaningInProgress()
                || LoadoutManager.isSwappingLoadout) {
            PestManager.clearCleaningTriggerPending();
            return false;
        }

        boolean manualMode = AetherConfig.MANUAL_PEST_MODE.get();
        stage = Stage.PRE;
        ClientUtils.sendDebugMessage("Pest lifecycle: waiting for /setspawn while farming continues.");
        MacroWorkerThread.getInstance().submit("PestSetSpawn-" + plot, () -> {
            for (int attempt = 0; attempt < SETSPAWN_MAX_ATTEMPTS && isSetSpawnRetryPending(client, sessionId);
                    attempt++) {
                if (CommandUtils.setSpawn(SETSPAWN_RETRY_MS)) {
                    client.execute(() -> beginPreStage(client, plot, pestCount, sessionId, manualMode));
                    return;
                }
                if (attempt + 1 < SETSPAWN_MAX_ATTEMPTS) {
                    ClientUtils.sendDebugMessage("Pest lifecycle: /setspawn not confirmed; farming and retrying once.");
                }
            }
            if (isSetSpawnRetryPending(client, sessionId)) {
                ClientUtils.sendDebugMessage("Pest lifecycle: /setspawn failed twice; continuing farming.");
                PestManager.startPestReentryCooldown();
            }
            cancelPendingStart(sessionId);
        });
        return true;
    }

    private static void beginPreStage(Minecraft client, String plot, int pestCount, int sessionId,
            boolean manualMode) {
        if (!isSetSpawnRetryPending(client, sessionId)) {
            cancelPendingStart(sessionId);
            return;
        }

        ClientUtils.sendDebugMessage("Pest lifecycle: /setspawn confirmed; entering PRE stage for plot " + plot + ".");
        FarmingMacroManager.disable(client);
        PestManager.setCleaningInProgress(true);
        PestManager.clearCleaningTriggerPending();
        LoadoutManager.shouldRestartFarmingAfterSwap = false;
        MacroStateManager.setCurrentState(MacroState.State.CLEANING);

        MacroWorkerThread.getInstance().submit("PestPre-" + plot, () -> {
            try {
                if (!PestPreStage.run(client, plot, sessionId)) {
                    abortPreStage(client, sessionId);
                    return;
                }
                client.execute(() -> startCleaningStage(client, plot, pestCount, sessionId, manualMode));
            } catch (Exception e) {
                e.printStackTrace();
                abortPreStage(client, sessionId);
            }
        });
    }

    static boolean isSetSpawnRetryPending(Minecraft client, int sessionId) {
        return shouldRetrySetSpawn(stage, sessionId, PestManager.getCurrentPestSessionId(),
                PestManager.isCleaningInProgress(),
                MacroWorkerThread.shouldAbortTask(client, MacroState.State.FARMING));
    }

    static boolean shouldRetrySetSpawn(Stage currentStage, int sessionId, int currentSessionId,
            boolean cleaning, boolean abort) {
        return currentStage == Stage.PRE && sessionId == currentSessionId && !cleaning && !abort;
    }

    private static void cancelPendingStart(int sessionId) {
        if (stage == Stage.PRE
                && sessionId == PestManager.getCurrentPestSessionId()
                && !PestManager.isCleaningInProgress()) {
            stage = Stage.IDLE;
            PestManager.clearCleaningTriggerPending();
        }
    }

    public static void startPostStage(Minecraft client) {
        if (stage == Stage.POST) {
            return;
        }

        stage = Stage.POST;
        ClientUtils.sendDebugMessage("Pest lifecycle: entering POST stage.");
        PestReturnManager.handlePestCleaningFinished(client);
    }

    public static void completePostStage() {
        stage = Stage.IDLE;
    }

    private static void startCleaningStage(Minecraft client, String plot, int pestCount, int sessionId,
            boolean manualMode) {
        if (stage != Stage.PRE
                || sessionId != PestManager.getCurrentPestSessionId()
                || !PestManager.isCleaningInProgress()) {
            return;
        }

        stage = Stage.CLEANING;
        ClientUtils.sendDebugMessage("Pest lifecycle: entering CLEANING stage ("
                + (manualMode ? "manual" : "automatic") + ").");

        if (manualMode) {
            if (!ManualPestManager.startCleaningStage(client, pestCount)) {
                PestManager.handlePestCleaningFinished(client);
            }
            return;
        }

        ClientUtils.sendMessage("\u00A76Starting Pest Cleaner script (" + plot + ")...", true);
        if (PestBonusManager.isBonusInactive()) {
            ClientUtils.sendMessage("\u00A7dBonus is INACTIVE! Triggering Phillip reactivation...", true);
            PestBonusManager.beginReactivation();
        }
        client.execute(() -> PestDestroyer.start(client, plot));
    }

    private static void abortPreStage(Minecraft client, int sessionId) {
        if (sessionId != PestManager.getCurrentPestSessionId() || stage != Stage.PRE) {
            return;
        }

        ClientUtils.sendDebugMessage("Pest lifecycle: PRE stage aborted; returning through POST stage.");
        startPostStage(client);
    }
}
