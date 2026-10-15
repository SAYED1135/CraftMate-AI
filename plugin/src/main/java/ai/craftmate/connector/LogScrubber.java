package ai.craftmate.connector;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dual-Layer Scrubbing (Layer 1 — Local inside Plugin) & Log Deduplicator.
 * Ensures player IPs, RCON passwords, DB credentials, and webhooks never leave the server,
 * and compresses repeated tick stacktraces to save Gemini API tokens.
 */
public final class LogScrubber {

    // Match IPv4 (excluding 0.0.0.0 and 127.0.0.1)
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "\\b(?!0\\.0\\.0\\.0|127\\.0\\.0\\.1)(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})(:\\d+)?\\b"
    );

    // Match Database URIs (mysql, postgres, mongodb, redis)
    private static final Pattern DB_URI_PATTERN = Pattern.compile(
            "(?i)(mysql|postgres|postgresql|mongodb|redis)://[^\\s\"']+"
    );

    // Match RCON / config password lines
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "(?i)(password\\s*[=:]?\\s*)([^\\s\"']+)"
    );

    // Match Discord Webhooks
    private static final Pattern WEBHOOK_PATTERN = Pattern.compile(
            "(?i)https://discord\\.com/api/webhooks/[^\\s\"']+"
    );

    // Strip leading timestamp for deduplication comparison
    private static final Pattern TIMESTAMP_PREFIX = Pattern.compile(
            "^\\[\\d{2}:\\d{2}:\\d{2}\\]\\s*"
    );

    public static class ScrubResult {
        private final String scrubbedText;
        private final int redactedSecretsCount;
        private final int originalLineCount;
        private final int compressedLineCount;

        public ScrubResult(String scrubbedText, int redactedSecretsCount, int originalLineCount, int compressedLineCount) {
            this.scrubbedText = scrubbedText;
            this.redactedSecretsCount = redactedSecretsCount;
            this.originalLineCount = originalLineCount;
            this.compressedLineCount = compressedLineCount;
        }

        public String getScrubbedText() {
            return scrubbedText;
        }

        public int getRedactedSecretsCount() {
            return redactedSecretsCount;
        }

        public int getOriginalLineCount() {
            return originalLineCount;
        }

        public int getCompressedLineCount() {
            return compressedLineCount;
        }
    }

    /**
     * Reads the last N lines of logs/latest.log without loading the entire file into memory.
     */
    public static List<String> readLastLines(File logFile, int maxLines) throws IOException {
        if (!logFile.exists() || !logFile.isFile()) {
            return Collections.emptyList();
        }
        List<String> lines = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(logFile, "r")) {
            long fileLength = raf.length();
            if (fileLength == 0) {
                return Collections.emptyList();
            }
            // Read at most the last 256 KB to guarantee zero lag on the server
            long maxBytesToRead = Math.min(fileLength, 256 * 1024L);
            raf.seek(fileLength - maxBytesToRead);
            byte[] buffer = new byte[(int) maxBytesToRead];
            raf.readFully(buffer);
            String chunk = new String(buffer, StandardCharsets.UTF_8);
            String[] split = chunk.split("\\r?\\n");
            int startIdx = Math.max(0, split.length - maxLines);
            for (int i = startIdx; i < split.length; i++) {
                lines.add(split[i]);
            }
        }
        return lines;
    }

    /**
     * Scrubs sensitive tokens/IPs and collapses consecutive duplicate log lines.
     */
    public static ScrubResult scrubAndDeduplicate(List<String> rawLines, boolean redactEnabled) {
        int redactedCount = 0;
        List<String> deduped = new ArrayList<>();
        String prevNormalized = null;
        int repeatCount = 0;

        for (String rawLine : rawLines) {
            String line = rawLine;
            if (redactEnabled) {
                Matcher ipMatcher = IPV4_PATTERN.matcher(line);
                while (ipMatcher.find()) {
                    redactedCount++;
                }
                line = ipMatcher.replaceAll("[REDACTED_PLAYER_IP]$2");

                Matcher dbMatcher = DB_URI_PATTERN.matcher(line);
                while (dbMatcher.find()) {
                    redactedCount++;
                }
                line = dbMatcher.replaceAll("$1://[REDACTED_DB_URI]");

                Matcher pwMatcher = PASSWORD_PATTERN.matcher(line);
                while (pwMatcher.find()) {
                    redactedCount++;
                }
                line = pwMatcher.replaceAll("$1[REDACTED_SECRET]");

                Matcher whMatcher = WEBHOOK_PATTERN.matcher(line);
                while (whMatcher.find()) {
                    redactedCount++;
                }
                line = whMatcher.replaceAll("https://discord.com/api/webhooks/[REDACTED_WEBHOOK]");
            }

            String normalized = TIMESTAMP_PREFIX.matcher(line).replaceFirst("").trim();
            if (!normalized.isEmpty() && normalized.equals(prevNormalized)) {
                repeatCount++;
            } else {
                if (repeatCount > 0) {
                    deduped.add("   ↳ [CraftMate Deduplicator: previous line repeated " + repeatCount + " more times]");
                    repeatCount = 0;
                }
                deduped.add(line);
                prevNormalized = normalized;
            }
        }

        if (repeatCount > 0) {
            deduped.add("   ↳ [CraftMate Deduplicator: previous line repeated " + repeatCount + " more times]");
        }

        return new ScrubResult(
                String.join("\n", deduped),
                redactedCount,
                rawLines.size(),
                deduped.size()
        );
    }
}
