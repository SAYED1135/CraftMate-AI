package ai.craftmate.connector;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
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

        String detectedHost = environmentDetector.detectHostProvider();
        getLogger().info("========================================================");
        getLogger().info("CraftMate Connector v0.3.0 enabled successfully!");
        getLogger().info("Detected Host Profile : " + detectedHost);
        getLogger().info("Security Read-Only    : " + getConfig().getBoolean("security.read-only-mode", true));
        getLogger().info("Dashboard & Docs      : https://sayed1135.github.io/CraftMate-AI/");
        getLogger().info("========================================================");
    }

    @Override
    public void onDisable() {
        if (this.heartbeatTask != null) {
            this.heartbeatTask.cancel();
        }

        // Notify Backend whether this is a normal empty-server Hibernation (0 players) or regular stop (FR-16)
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        if (this.connectorClient != null) {
            this.connectorClient.sendCleanShutdownSignal(onlinePlayers);
        }

        getLogger().info("CraftMate Connector v0.3.0 disabled cleanly.");
    }

    public AllowlistTaskExecutor getTaskExecutor() {
        return taskExecutor;
    }
}
