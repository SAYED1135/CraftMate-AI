package ai.craftmate.connector;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * CraftMate Connector API Client v0.3.
 *
 * Real pairing lifecycle:
 * 1. /craftmate link CM-XXXX-XXXX sends the one-time code to POST /v1/claim.
 * 2. The Connector API exchanges it for a long-lived server token.
 * 3. The token is stored only in this server's plugin config and sent as a
 *    Bearer credential for heartbeat, diagnosis and clean shutdown calls.
 *
 * All network work stays off the Minecraft main thread, except the short
 * best-effort shutdown signal during onDisable.
 */
public final class ConnectorClient {

    public static final String CONNECTOR_VERSION = "0.3.0";

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
        String token = token();
        String serverId = serverId();
        return token != null && !token.isBlank() && serverId != null && !serverId.isBlank();
    }

    public String getLastSyncStatus() {
        return lastSyncStatus;
    }

    public String getLastSyncTimestamp() {
        return lastSyncTimestamp;
    }

    public String getServerId() {
        return serverId();
    }

    private String token() {
        return plugin.getConfig().getString("backend.connector-token", "");
    }

    private String serverId() {
        return plugin.getConfig().getString("backend.server-id", "");
    }

    private String apiBase() {
        String configured = plugin.getConfig().getString("backend.api-url",
                plugin.getConfig().getString("backend.endpoint-url", ""));
        if (configured == null) return "";
        return configured.trim().replaceAll("/+$", "");
    }

    private boolean apiConfigured() {
        String base = apiBase();
        return !base.isBlank()
                && !base.contains("YOUR-SUBDOMAIN")
                && !base.contains("YOUR-WORKER")
                && !base.contains("supabase.co/rest");
    }

    private HttpRequest.Builder request(String path, String json) {
        return HttpRequest.newBuilder()
                .uri(URI.create(apiBase() + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-CraftMate-Connector-Version", CONNECTOR_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(json));
    }

    private JsonObject postJson(String path, JsonObject body, boolean authenticated) throws Exception {
        if (!apiConfigured()) {
            throw new IllegalStateException("Connector API URL is not configured in config.yml (backend.api-url)");
        }
        HttpRequest.Builder builder = request(path, body.toString());
        if (authenticated && isLinked()) {
            builder.header("Authorization", "Bearer " + token());
            builder.header("X-CraftMate-Token", token());
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String snippet = response.body() == null ? "" : response.body().substring(0, Math.min(240, response.body().length()));
            throw new IllegalStateException("Connector API HTTP " + response.statusCode() + ": " + snippet);
        }
        if (response.body() == null || response.body().isBlank()) return new JsonObject();
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    /** Exchanges a short-lived dashboard pairing code for a long-lived server token. */
    public CompletableFuture<Boolean> registerWithPairingCode(String pairingCode, int onlinePlayers) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("pairing_code", pairingCode.trim().toUpperCase());
                payload.addProperty("connector_version", CONNECTOR_VERSION);
                payload.add("environment", detector.collectEnvironmentSnapshot(onlinePlayers));

                JsonObject result = postJson("/v1/claim", payload, false);
                if (!result.has("server_id") || !result.has("connector_token")) {
                    throw new IllegalStateException("Connector API returned an incomplete claim response");
                }

                plugin.getConfig().set("backend.server-id", result.get("server_id").getAsString());
                plugin.getConfig().set("backend.connector-token", result.get("connector_token").getAsString());
                plugin.saveConfig();

                this.lastSyncStatus = "LINKED_ONLINE (" + detector.detectHostProvider() + ")";
                this.lastSyncTimestamp = Instant.now().toString();
                plugin.getLogger().info("[CraftMate] Server claimed successfully. Server ID: " + result.get("server_id").getAsString());
                return true;
            } catch (Exception e) {
                this.lastSyncStatus = "LINK_FAILED";
                plugin.getLogger().warning("[CraftMate] Pairing failed: " + e.getMessage());
                return false;
            }
        });
    }

    /** Sends the periodic real heartbeat and environment snapshot. */
    public CompletableFuture<Void> sendHeartbeatAsync(int onlinePlayers) {
        return CompletableFuture.runAsync(() -> {
            if (!isLinked()) {
                this.lastSyncStatus = "UNLINKED (Use /craftmate link <CODE>)";
                return;
            }
            try {
                JsonObject payload = baseAuthenticatedPayload(onlinePlayers);
                payload.addProperty("status", "ONLINE");
                payload.add("environment", detector.collectEnvironmentSnapshot(onlinePlayers));
                JsonObject result = postJson("/v1/heartbeat", payload, true);
                this.lastSyncStatus = result.has("status") ? result.get("status").getAsString() + "_SYNCED" : "ONLINE_SYNCED";
                this.lastSyncTimestamp = Instant.now().toString();
            } catch (Exception ex) {
                this.lastSyncStatus = "BACKEND_UNREACHABLE_FAILSAFE";
                plugin.getLogger().fine("[CraftMate] Heartbeat deferred: " + ex.getMessage());
            }
        });
    }

    /** Scrubs latest.log locally, then uploads only the scrubbed snapshot. */
    public CompletableFuture<LogScrubber.ScrubResult> uploadScrubbedLogSnapshotAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                int maxLines = plugin.getConfig().getInt("security.max-log-lines-per-snapshot", 250);
                boolean redact = plugin.getConfig().getBoolean("security.redact-ips-and-secrets", true);
                java.io.File logFile = new java.io.File("logs/latest.log");
                List<String> rawLines = LogScrubber.readLastLines(logFile, maxLines);
                LogScrubber.ScrubResult scrubbed = LogScrubber.scrubAndDeduplicate(rawLines, redact);

                if (!isLinked()) {
                    this.lastSyncStatus = "DIAGNOSIS_LOCAL_ONLY (Link the server to upload)";
                    return scrubbed;
                }

                JsonObject payload = baseAuthenticatedPayload(0);
                payload.addProperty("host_provider", detector.detectHostProvider());
                payload.addProperty("redacted_secrets", scrubbed.getRedactedSecretsCount());
                payload.addProperty("compressed_lines", scrubbed.getCompressedLineCount());
                payload.addProperty("scrubbed_log_excerpt", scrubbed.getScrubbedText());

                JsonObject result = postJson("/v1/diagnose", payload, true);
                this.lastSyncStatus = result.has("ok") && result.get("ok").getAsBoolean()
                        ? "DIAGNOSIS_UPLOADED"
                        : "DIAGNOSIS_SENT";
                this.lastSyncTimestamp = Instant.now().toString();
                return scrubbed;
            } catch (Exception e) {
                this.lastSyncStatus = "DIAGNOSIS_UPLOAD_FAILED";
                plugin.getLogger().warning("[CraftMate] Diagnosis upload failed safely: " + e.getMessage());
                return new LogScrubber.ScrubResult("", 0, 0, 0);
            }
        });
    }

    /** Best-effort synchronous clean shutdown / hibernation signal on disable. */
    public void sendCleanShutdownSignal(int onlinePlayers) {
        if (!isLinked() || !plugin.getConfig().getBoolean("polling.notify-hibernation-on-empty-shutdown", true)) {
            return;
        }
        try {
            JsonObject payload = baseAuthenticatedPayload(onlinePlayers);
            payload.addProperty("online_players_at_shutdown", onlinePlayers);
            JsonObject result = postJson("/v1/shutdown", payload, true);
            String state = result.has("status") ? result.get("status").getAsString() : (onlinePlayers == 0 ? "HIBERNATING" : "OFFLINE");
            plugin.getLogger().info("[CraftMate] Sent clean shutdown state (" + state + ") to CraftMate AI.");
        } catch (Exception ignored) {
            // Fail silently during server shutdown; never delay or crash disable.
        }
    }

    /** Revokes the long-lived token server-side, then clears it locally. */
    public CompletableFuture<Boolean> revokeAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (isLinked()) {
                    postJson("/v1/revoke", baseAuthenticatedPayload(0), true);
                }
                plugin.getConfig().set("backend.connector-token", "");
                plugin.getConfig().set("backend.server-id", "");
                plugin.saveConfig();
                this.lastSyncStatus = "UNLINKED";
                return true;
            } catch (Exception e) {
                plugin.getLogger().warning("[CraftMate] Server-side revoke failed; clearing local credentials anyway: " + e.getMessage());
                plugin.getConfig().set("backend.connector-token", "");
                plugin.getConfig().set("backend.server-id", "");
                plugin.saveConfig();
                this.lastSyncStatus = "UNLINKED_LOCAL";
                return false;
            }
        });
    }

    private JsonObject baseAuthenticatedPayload(int onlinePlayers) {
        JsonObject payload = new JsonObject();
        payload.addProperty("server_id", serverId());
        payload.addProperty("connector_token", token());
        payload.addProperty("connector_version", CONNECTOR_VERSION);
        payload.addProperty("online_players", onlinePlayers);
        payload.addProperty("timestamp", Instant.now().toString());
        return payload;
    }
}
