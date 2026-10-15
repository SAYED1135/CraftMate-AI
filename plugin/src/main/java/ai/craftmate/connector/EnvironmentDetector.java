package ai.craftmate.connector;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

/**
 * Discovers server software, version, JVM memory limits, installed plugins,
 * Bedrock/Geyser presence, and resolves the active Host Profile (FR-03 & FR-04).
 */
public final class EnvironmentDetector {

    private final CraftMateConnectorPlugin plugin;

    public EnvironmentDetector(CraftMateConnectorPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Resolves which of the 4 official MVP hosts is running this server.
     */
    public String detectHostProvider() {
        String configured = plugin.getConfig().getString("host-provider", "auto").toLowerCase().trim();
        if (!configured.equals("auto") && !configured.isEmpty()) {
            return configured;
        }

        long maxRamMb = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        boolean bungeeEnabled = isBungeeCordEnabled();

        // Heuristic auto-detection when host-provider is "auto"
        if (bungeeEnabled && maxRamMb <= 1250) {
            return "minefort";
        }
        if (new File("/home/container").exists() && maxRamMb >= 2300 && maxRamMb <= 2800) {
            return "falixnodes";
        }
        return "play_hosting";
    }

    public boolean isBungeeCordEnabled() {
        File spigotFile = new File("spigot.yml");
        if (!spigotFile.exists()) {
            return false;
        }
        YamlConfiguration spigotConfig = YamlConfiguration.loadConfiguration(spigotFile);
        return spigotConfig.getBoolean("settings.bungeecord", false);
    }

    /**
     * Builds the Capability Model JSON (FR-04).
     */
    public JsonObject buildCapabilitiesJson() {
        String host = detectHostProvider();
        boolean readOnly = plugin.getConfig().getBoolean("security.read-only-mode", true);

        JsonObject caps = new JsonObject();
        caps.addProperty("read_logs", "available");
        caps.addProperty("read_config", "available");
        caps.addProperty("edit_config", readOnly ? "disabled_read_only_mode" : "available_with_approval");
        caps.addProperty("custom_jar_upload", host.equals("falixnodes") ? "restricted_on_free_tier_use_modrinth" : "available");
        caps.addProperty("extra_udp_ports", host.equals("minefort") ? "unavailable_behind_proxy" : "available");
        caps.addProperty("bungeecord_setting_locked", host.equals("minefort") ? "locked_true_mandatory" : "configurable");
        return caps;
    }

    /**
     * Collects a lightweight environment snapshot (safe to call from async thread with cached plugin data).
     */
    public JsonObject collectEnvironmentSnapshot(int onlinePlayersCount) {
        Server server = Bukkit.getServer();
        Runtime runtime = Runtime.getRuntime();
        long maxMb = runtime.maxMemory() / (1024 * 1024);
        long totalMb = runtime.totalMemory() / (1024 * 1024);
        long freeMb = runtime.freeMemory() / (1024 * 1024);
        long usedMb = totalMb - freeMb;

        String host = detectHostProvider();

        JsonObject snapshot = new JsonObject();
        snapshot.addProperty("host_provider", host);
        snapshot.addProperty("server_name", server.getName());
        snapshot.addProperty("server_version", server.getVersion());
        snapshot.addProperty("bukkit_version", server.getBukkitVersion());
        snapshot.addProperty("java_version", System.getProperty("java.version", "unknown"));
        snapshot.addProperty("max_ram_mb", maxMb);
        snapshot.addProperty("used_ram_mb", usedMb);
        snapshot.addProperty("online_players", onlinePlayersCount);
        snapshot.addProperty("bungeecord_enabled", isBungeeCordEnabled());

        // Read safe properties from server.properties
        File propsFile = new File("server.properties");
        if (propsFile.exists()) {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(propsFile)) {
                props.load(fis);
                snapshot.addProperty("server_port", props.getProperty("server-port", "25565"));
                snapshot.addProperty("online_mode", props.getProperty("online-mode", "true"));
                snapshot.addProperty("view_distance", props.getProperty("view-distance", "10"));
                snapshot.addProperty("simulation_distance", props.getProperty("simulation-distance", "10"));
            } catch (Exception ignored) {
            }
        }

        // Installed plugins and Bedrock integration detection (FR-13)
        JsonArray pluginsArray = new JsonArray();
        boolean hasGeyser = false;
        boolean hasFloodgate = false;
        boolean hasViaVersion = false;
        boolean hasSpark = false;

        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            JsonObject pObj = new JsonObject();
            pObj.addProperty("name", p.getName());
            pObj.addProperty("version", p.getDescription().getVersion());
            pObj.addProperty("enabled", p.isEnabled());
            pluginsArray.add(pObj);

            String lower = p.getName().toLowerCase();
            if (lower.contains("geyser")) hasGeyser = true;
            if (lower.contains("floodgate")) hasFloodgate = true;
            if (lower.contains("viaversion")) hasViaVersion = true;
            if (lower.contains("spark")) hasSpark = true;
        }
        snapshot.add("plugins", pluginsArray);

        JsonObject integrations = new JsonObject();
        integrations.addProperty("geyser_installed", hasGeyser);
        integrations.addProperty("floodgate_installed", hasFloodgate);
        integrations.addProperty("viaversion_installed", hasViaVersion);
        integrations.addProperty("spark_installed", hasSpark);
        snapshot.add("integrations", integrations);

        snapshot.add("capabilities", buildCapabilitiesJson());
        return snapshot;
    }
}
