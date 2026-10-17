package ai.craftmate.connector;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Local administrative command handler for /craftmate <status|link|sync|diagnose|disconnect>.
 * Requires permission `craftmate.admin` (default: OP).
 */
public final class CraftMateCommand implements CommandExecutor, TabCompleter {

    private final CraftMateConnectorPlugin plugin;
    private final EnvironmentDetector detector;
    private final ConnectorClient client;

    public CraftMateCommand(CraftMateConnectorPlugin plugin, EnvironmentDetector detector, ConnectorClient client) {
        this.plugin = plugin;
        this.detector = detector;
        this.client = client;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("craftmate.admin")) {
            sender.sendMessage(ChatColor.RED + "[CraftMate AI] You do not have permission (craftmate.admin).");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendStatusOverview(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "link": {
                if (args.length < 2 || args[1].equalsIgnoreCase("new")) {
                    if (client.isLinked()) {
                        sender.sendMessage(ChatColor.GREEN + "[CraftMate AI] This server is already linked. Use /craftmate disconnect first if you want to link another account.");
                        return true;
                    }
                    boolean forceNew = args.length >= 2;
                    String generated = client.getOrCreatePendingPairingCode(forceNew);
                    plugin.startPendingLinkPolling();
                    sender.sendMessage(ChatColor.AQUA + "[CraftMate AI] Your server-generated link code is:");
                    sender.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + generated);
                    sender.sendMessage(ChatColor.GRAY + "Open your CraftMate dashboard, choose this server, and enter the code in the plugin-code field.");
                    sender.sendMessage(ChatColor.GRAY + "I will keep trying automatically for 30 minutes. Do not share this code publicly.");
                    sender.sendMessage(ChatColor.GRAY + "Need a fresh code? Run /craftmate link new");
                    return true;
                }
                String code = args[1].trim();
                int players = Bukkit.getOnlinePlayers().size();
                sender.sendMessage(ChatColor.AQUA + "[CraftMate AI] Linking server with pairing code " + code + "...");
                client.registerWithPairingCode(code, players).thenAccept(success -> {
                    if (success) {
                        sender.sendMessage(ChatColor.GREEN + "[CraftMate AI] Server linked successfully! Detected Host Profile: "
                                + ChatColor.BOLD + detector.detectHostProvider());
                    } else {
                        sender.sendMessage(ChatColor.RED + "[CraftMate AI] Could not complete pairing. Verify your code or internet connection.");
                    }
                });
                return true;
            }

            case "sync": {
                int players = Bukkit.getOnlinePlayers().size();
                sender.sendMessage(ChatColor.AQUA + "[CraftMate AI] Sending async heartbeat & environment snapshot...");
                client.sendHeartbeatAsync(players).thenRun(() ->
                        sender.sendMessage(ChatColor.GREEN + "[CraftMate AI] Heartbeat & capability profile synced!")
                );
                return true;
            }

            case "diagnose": {
                sender.sendMessage(ChatColor.AQUA + "[CraftMate AI] Scrubbing latest.log (removing IPs/secrets) and deduplicating stacktraces...");
                client.uploadScrubbedLogSnapshotAsync().thenAccept(res -> {
                    sender.sendMessage(ChatColor.GREEN + "[CraftMate AI] Log snapshot prepared! "
                            + ChatColor.GRAY + "(Original lines: " + res.getOriginalLineCount()
                            + ", Compressed: " + res.getCompressedLineCount()
                            + ", Secrets redacted: " + res.getRedactedSecretsCount() + ")");
                });
                return true;
            }

            case "disconnect": {
                sender.sendMessage(ChatColor.AQUA + "[CraftMate AI] Revoking this server token...");
                client.revokeAsync().thenAccept(revoked ->
                        sender.sendMessage(ChatColor.YELLOW + "[CraftMate AI] Connector unlinked. "
                                + (revoked ? "Server token revoked in CraftMate Cloud." : "Local credentials cleared."))
                );
                return true;
            }

            default:
                sender.sendMessage(ChatColor.YELLOW + "Usage: /craftmate <status|link|sync|diagnose|disconnect>");
                return true;
        }
    }

    private void sendStatusOverview(CommandSender sender) {
        Runtime rt = Runtime.getRuntime();
        long maxMb = rt.maxMemory() / (1024 * 1024);
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        String host = detector.detectHostProvider();
        boolean readOnly = plugin.getConfig().getBoolean("security.read-only-mode", true);

        sender.sendMessage(ChatColor.DARK_GRAY + "================================================");
        sender.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "⚡ CraftMate AI Connector v" + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.GRAY + "Host Profile: " + ChatColor.AQUA + host
                + (host.equals("minefort") ? ChatColor.YELLOW + " (BungeeCord Guardrail Active)" : ""));
        sender.sendMessage(ChatColor.GRAY + "Connection Status: " + ChatColor.WHITE + client.getLastSyncStatus());
        if (client.hasValidPendingPairingCode()) {
            sender.sendMessage(ChatColor.GRAY + "Pending Link: " + ChatColor.YELLOW + "Waiting for dashboard registration (expires "
                    + new java.util.Date(client.pendingPairingExpiresAtMillis()) + ")");
        }
        sender.sendMessage(ChatColor.GRAY + "Server ID: " + ChatColor.WHITE + (client.getServerId() == null || client.getServerId().isBlank() ? "—" : client.getServerId()));
        sender.sendMessage(ChatColor.GRAY + "Task Channel: " + (plugin.getConfig().getBoolean("control.task-polling-enabled", true)
                ? ChatColor.GREEN + "ENABLED (executes website full-control tasks)"
                : ChatColor.RED + "DISABLED in config.yml"));
        sender.sendMessage(ChatColor.GRAY + "Last Sync: " + ChatColor.WHITE + client.getLastSyncTimestamp());
        sender.sendMessage(ChatColor.GRAY + "JVM Memory: " + ChatColor.WHITE + usedMb + " MB / " + maxMb + " MB");
        sender.sendMessage(ChatColor.GRAY + "Security Mode: " + (readOnly
                ? ChatColor.GREEN + "READ-ONLY (Safe P2 Mode)"
                : ChatColor.GOLD + "ALLOWLIST EXECUTION (Backup .bak enabled)"));
        sender.sendMessage(ChatColor.DARK_GRAY + "================================================");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = Arrays.asList("status", "link", "sync", "diagnose", "disconnect");
            return subs.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
