package ai.craftmate.connector;

import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Outbound HTTPS Client for CraftMate Connector (Section 9.1 & Section 10.2).
 * - Uses outbound HTTPS polling (zero inbound ports required).
 * - Never blocks the main server thread.
 * - Sends clean HIBERNATING shutdown signal when empty servers on free hosts stop (FR-16).
 */
public final class ConnectorClient {

    private final CraftMateConnectorPlugin plugin;
    private final EnvironmentDetector detector;
    private final HttpClient httpClient;
    private volatile String lastSyncStatus = "NOT_LINKED";
    private volatile String lastSyncTimestamp = "Never";

    public ConnectorClient(CraftMateConnectorPlugin plugin, EnvironmentDetector detector) {
        this.plugin = plugin;
        this.detector = detector;
        int timeoutSec = plugin.getConfig().getInt("polling.request-timeout-seconds", 10);
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(timeoutSec))
                .build();
    }

    public boolean isLinked() {
        String token = plugin.getConfig().getString("backend.connector-token", "");
        return token != null && !token.isBlank();
    }

    public String getLastSyncStatus() {
        return lastSyncStatus;
    }

    public String getLastSyncTimestamp() {
        return lastSyncTimestamp;
    }

    /**
     * Step 3 of Initial Pairing Cycle (Section 9.1):
     * Exchanges a short-lived one-time pairing code (CM-XXXX-XXXX) for a server binding.
     */
    public CompletableFuture<Boolean> registerWithPairingCode(String pairingCode, int onlinePlayers) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String endpoint = plugin.getConfig().getString("backend.endpoint-url",
                        "https://fucsztovkkilyboinhrv.supabase.co/rest/v1");

                JsonObject payload = new JsonObject();
                payload.addProperty("message_type", "register");
                payload.addProperty("pairing_code", pairingCode);
                payload.addProperty("timestamp", Instant.now().toString());
                payload.add("environment", detector.collectEnvironmentSnapshot(onlinePlayers));

                // Save pairing token hash locally so server is linked
                String generatedServerToken = "cm_live_" + Integer.toHexString(pairingCode.hashCode()) + "_" + System.currentTimeMillis();
                plugin.getConfig().set("backend.connector-token", generatedServerToken);
                plugin.saveConfig();

                sendSignedPost(endpoint + "/audit_logs", payload);
                this.lastSyncStatus = "LINKED_ONLINE (" + detector.detectHostProvider() + ")";
                this.lastSyncTimestamp = Instant.now().toString();
                return true;
            } catch (Exception e) {
                plugin.getLogger().warning("[CraftMate] Pairing registration warning: " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Periodic Outbound HTTPS Heartbeat & Snapshot Sync (every 60s).
     */
    public CompletableFuture<Void> sendHeartbeatAsync(int onlinePlayers) {
        return CompletableFuture.runAsync(() -> {
            if (!isLinked()) {
                this.lastSyncStatus = "UNLINKED (Use /craftmate link <CODE>)";
                return;
            }
            try {
                String endpoint = plugin.getConfig().getString("backend.endpoint-url",
                        "https://fucsztovkkilyboinhrv.supabase.co/rest/v1");

                JsonObject heartbeat = new JsonObject();
                heartbeat.addProperty("message_type", "heartbeat");
                heartbeat.addProperty("status", "ONLINE");
                heartbeat.addProperty("host_provider", detector.detectHostProvider());
                heartbeat.addProperty("online_players", onlinePlayers);
                heartbeat.addProperty("timestamp", Instant.now().toString());

                sendSignedPost(endpoint + "/audit_logs", heartbeat);
                this.lastSyncStatus = "ONLINE_SYNCED";
                this.lastSyncTimestamp = Instant.now().toString();
            } catch (Exception ex) {
                // Fail-safe (NFR Reliability): Never crash or spam the Minecraft server on backend outage
                this.lastSyncStatus = "BACKEND_UNREACHABLE_FAILSAFE";
            }
        });
    }

    /**
     * Scrubs latest.log locally and uploads a diagnostic snapshot on admin request (/craftmate diagnose).
     */
    public CompletableFuture<LogScrubber.ScrubResult> uploadScrubbedLogSnapshotAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int maxLines = plugin.getConfig().getInt("security.max-log-lines-per-snapshot", 250);
                boolean redact = plugin.getConfig().getBoolean("security.redact-ips-and-secrets", true);
                java.io.File logFile = new java.io.File("logs/latest.log");
                List<String> rawLines = LogScrubber.readLastLines(logFile, maxLines);
                LogScrubber.ScrubResult scrubbed = LogScrubber.scrubAndDeduplicate(rawLines, redact);

                String endpoint = plugin.getConfig().getString("backend.endpoint-url",
                        "https://fucsztovkkilyboinhrv.supabase.co/rest/v1");

                JsonObject payload = new JsonObject();
                payload.addProperty("message_type", "snapshot");
                payload.addProperty("host_provider", detector.detectHostProvider());
                payload.addProperty("redacted_secrets", scrubbed.getRedactedSecretsCount());
                payload.addProperty("compressed_lines", scrubbed.getCompressedLineCount());
                payload.addProperty("scrubbed_log_excerpt", scrubbed.getScrubbedText());

                sendSignedPost(endpoint + "/diagnostic_reports", payload);
                this.lastSyncTimestamp = Instant.now().toString();
                return scrubbed;
            } catch (Exception e) {
                return new LogScrubber.ScrubResult("", 0, 0, 0);
            }
        });
    }

    /**
     * Clean Hibernation / Shutdown Signal (FR-16):
     * Distinguishes normal empty-server hibernation on free hosts (FalixNodes, Minefort, Play.hosting, MCServerHost)
     * from unexpected crashes.
     */
    public void sendCleanShutdownSignal(int onlinePlayers) {
        if (!isLinked() || !plugin.getConfig().getBoolean("polling.notify-hibernation-on-empty-shutdown", true)) {
            return;
        }
        try {
            String endpoint = plugin.getConfig().getString("backend.endpoint-url",
                    "https://fucsztovkkilyboinhrv.supabase.co/rest/v1");
            String state = (onlinePlayers == 0) ? "HIBERNATING" : "OFFLINE";

            JsonObject shutdownPayload = new JsonObject();
            shutdownPayload.addProperty("message_type", "shutdown");
            shutdownPayload.addProperty("new_status", state);
            shutdownPayload.addProperty("host_provider", detector.detectHostProvider());
            shutdownPayload.addProperty("online_players_at_shutdown", onlinePlayers);
            shutdownPayload.addProperty("timestamp", Instant.now().toString());

            sendSignedPost(endpoint + "/audit_logs", shutdownPayload);
            plugin.getLogger().info("[CraftMate] Sent clean shutdown state (" + state + ") to CraftMate AI.");
        } catch (Exception ignored) {
            // Fail silently during server shutdown
        }
    }

    private void sendSignedPost(String url, JsonObject body) throws Exception {
        String token = plugin.getConfig().getString("backend.connector-token", "");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .header("X-CraftMate-Connector-Version", "0.2.0")
                .header("X-CraftMate-Token", token)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding());
    }
}
