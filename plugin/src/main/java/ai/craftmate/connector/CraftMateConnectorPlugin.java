package ai.craftmate.connector;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import com.google.gson.JsonObject;
import org.bukkit.scheduler.BukkitTask;

/**
 * CraftMate Connector — Main Paper/Purpur Plugin Entrypoint (PRD v0.2 Section 9.1 & 10.2).
 *
 * Key Architectural Principles:
 * 1. Outbound HTTPS Polling only (zero inbound ports or RCON exposure).
 * 2. All network & file operations run asynchronously off the main server thread.
 * 3. Host-aware capability discovery & guardrails for:
 *    - play.hosting
 *    - falixnodes.net
 *    - mcserverhost.com
 *    - minefort.com (enforces mandatory bungeecord: true protection)
 * 4. Sends clean HIBERNATING status on empty-server shutdown to avoid false crash alerts.
 */
public final class CraftMateConnectorPlugin extends JavaPlugin {

    private EnvironmentDetector environmentDetector;
    private AllowlistTaskExecutor taskExecutor;
    private ConnectorClient connectorClient;
    private BukkitTask heartbeatTask;
    private BukkitTask pendingLinkTask;
    private BukkitTask controlTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        this.environmentDetector = new EnvironmentDetector(this);
        this.taskExecutor = new AllowlistTaskExecutor(this, environmentDetector);
        this.connectorClient = new ConnectorClient(this, environmentDetector);

        // Register /craftmate command
        PluginCommand cmd = getCommand("craftmate");
        if (cmd != null) {
            CraftMateCommand executor = new CraftMateCommand(this, environmentDetector, connectorClient);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        // Schedule asynchronous periodic heartbeat (default: every 60 seconds)
        if (getConfig().getBoolean("polling.enabled", true)) {
            int intervalSeconds = Math.max(30, getConfig().getInt("polling.heartbeat-interval-seconds", 60));
            long intervalTicks = intervalSeconds * 20L;

            this.heartbeatTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                    this,
                    () -> {
                        int onlinePlayers = Bukkit.getOnlinePlayers().size();
                        connectorClient.sendHeartbeatAsync(onlinePlayers);
                    },
                    100L, // initial delay 5s after boot
                    intervalTicks
            );
        }

        if (getConfig().getBoolean("control.task-polling-enabled", true)) {
            int taskPollSeconds = Math.max(10, getConfig().getInt("polling.task-poll-interval-seconds", 15));
            long taskPollTicks = taskPollSeconds * 20L;
            this.controlTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                    this,
                    this::pollAndExecuteControlTask,
                    200L,
                    taskPollTicks
            );
        }

        if (connectorClient.hasValidPendingPairingCode()) {
            startPendingLinkPolling();
            getLogger().info("A pending CraftMate link code is waiting to be registered in the dashboard.");
        }

        String detectedHost = environmentDetector.detectHostProvider();
        getLogger().info("========================================================");
        getLogger().info("CraftMate Connector v" + getDescription().getVersion() + " enabled successfully!");
        getLogger().info("Detected Host Profile : " + detectedHost);
        getLogger().info("Security Read-Only    : " + getConfig().getBoolean("security.read-only-mode", true));
        getLogger().info("Dashboard & Docs      : https://sayed1135.github.io/CraftMate-AI/");
        getLogger().info("========================================================");
    }

    /** Polls quietly until the owner enters the plugin-generated code in the dashboard. */
    public synchronized void startPendingLinkPolling() {
        if (pendingLinkTask != null) return;
        int intervalSeconds = Math.max(5, getConfig().getInt("linking.auto-claim-interval-seconds", 10));
        long intervalTicks = intervalSeconds * 20L;
        pendingLinkTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                this,
                () -> {
                    if (connectorClient == null || connectorClient.isLinked()) {
                        stopPendingLinkPolling();
                        return;
                    }
                    if (!connectorClient.hasValidPendingPairingCode()) {
                        connectorClient.clearPendingPairingCode();
                        stopPendingLinkPolling();
                        return;
                    }
                    connectorClient.attemptPendingPairingClaim(Bukkit.getOnlinePlayers().size());
                },
                40L,
                intervalTicks
        );
    }

    public synchronized void stopPendingLinkPolling() {
        if (pendingLinkTask != null) {
            pendingLinkTask.cancel();
            pendingLinkTask = null;
        }
    }

    private void pollAndExecuteControlTask() {
        if (connectorClient == null || taskExecutor == null || !connectorClient.isLinked()) return;
        connectorClient.pollForTaskAsync(Bukkit.getOnlinePlayers().size()).thenAccept(task -> {
            if (task == null) return;
            Bukkit.getScheduler().runTask(this, () -> {
                String taskId = task.has("task_id") ? task.get("task_id").getAsString() : "";
                String taskType = task.has("task_type") ? task.get("task_type").getAsString() : "";
                JsonObject params = task.has("payload") && task.get("payload").isJsonObject()
                        ? task.getAsJsonObject("payload")
                        : new JsonObject();
                getLogger().info("[CraftMate] Executing website task " + taskType + " (" + taskId + ")");
                JsonObject result;
                try {
                    result = taskExecutor.executeTask(taskId, taskType, params, Bukkit.getOnlinePlayers().size());
                } catch (Exception ex) {
                    result = new JsonObject();
                    result.addProperty("task_id", taskId);
                    result.addProperty("task_type", taskType);
                    result.addProperty("status", "FAILED");
                    result.addProperty("error", ex.getMessage());
                }
                getLogger().info("[CraftMate] Website task " + taskType + " finished with status: "
                        + (result.has("status") ? result.get("status").getAsString() : "UNKNOWN"));
                connectorClient.sendTaskResultAsync(result);
            });
        });
    }

    @Override
    public void onDisable() {
        stopPendingLinkPolling();
        if (this.controlTask != null) {
            this.controlTask.cancel();
        }
        if (this.heartbeatTask != null) {
            this.heartbeatTask.cancel();
        }

        // Notify Backend whether this is a normal empty-server Hibernation (0 players) or regular stop (FR-16)
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        if (this.connectorClient != null) {
            this.connectorClient.sendCleanShutdownSignal(onlinePlayers);
        }

        getLogger().info("CraftMate Connector disabled cleanly.");
    }

    public AllowlistTaskExecutor getTaskExecutor() {
        return taskExecutor;
    }
}
