package dev.lplogger.webhook;

import dev.lplogger.LPDiscordLogger;
import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

public class DiscordWebhook {

    private final LPDiscordLogger plugin;
    private final String webhookUrl;

    public DiscordWebhook(LPDiscordLogger plugin, String webhookUrl) {
        this.plugin = plugin;
        this.webhookUrl = webhookUrl;
    }

    /**
     * Sends a plain command log embed to Discord.
     */
    public void sendCommandLog(String senderName, boolean isConsole, String command, String summary) {
        int color = plugin.getConfig().getInt("embed-color-command", 5814783);
        String avatarUrl = plugin.getConfig().getString("webhook-avatar-url", "https://luckperms.net/logo.png");
        String username = plugin.getConfig().getString("webhook-username", "LuckPerms Logger");

        StringBuilder embed = new StringBuilder();
        embed.append("{");
        embed.append("\"embeds\": [{");
        embed.append("\"title\": \"").append(escapeJson("LuckPerms Command")).append("\",");
        embed.append("\"color\": ").append(color).append(",");
        embed.append("\"fields\": [");

        // Sender field
        embed.append("{\"name\": \"").append(escapeJson("Sender"))
                .append("\", \"value\": \"").append(escapeJson(isConsole ? "Console" : senderName))
                .append("\", \"inline\": true},");

        // Command field
        if (plugin.getConfig().getBoolean("show-full-command", true)) {
            embed.append("{\"name\": \"").append(escapeJson("Command"))
                    .append("\", \"value\": \"```").append(escapeJson(command)).append("```")
                    .append("\", \"inline\": false},");
        }

        // Summary field
        if (summary != null && !summary.isEmpty()) {
            embed.append("{\"name\": \"").append(escapeJson("Summary"))
                    .append("\", \"value\": \"").append(escapeJson(summary))
                    .append("\", \"inline\": false}");
        } else {
            // Remove trailing comma if no outcome
            int lastComma = embed.lastIndexOf(",");
            if (lastComma != -1) embed.deleteCharAt(lastComma);
        }

        embed.append("],");
        embed.append("\"timestamp\": \"").append(Instant.now().toString()).append("\"");
        embed.append("}],");
        embed.append("\"username\": \"").append(escapeJson(username)).append("\",");
        embed.append("\"avatar_url\": \"").append(escapeJson(avatarUrl)).append("\"");
        embed.append("}");

        sendAsync(embed.toString());
    }

    /**
     * Sends an editor session change embed to Discord.
     */
    public void sendEditorLog(String sessionInitiator, String targetName, String targetType,
                               List<String> addedNodes, List<String> removedNodes, int totalChanges) {
        int color = plugin.getConfig().getInt("embed-color-editor", 5763719);
        String avatarUrl = plugin.getConfig().getString("webhook-avatar-url", "https://luckperms.net/logo.png");
        String username = plugin.getConfig().getString("webhook-username", "LuckPerms Logger");
        int maxNodes = plugin.getConfig().getInt("max-nodes-listed", 20);

        StringBuilder embed = new StringBuilder();
        embed.append("{");
        embed.append("\"embeds\": [{");
        embed.append("\"title\": \"").append(escapeJson("LuckPerms Editor Changes")).append("\",");
        embed.append("\"color\": ").append(color).append(",");
        embed.append("\"fields\": [");

        // Initiator field
        embed.append("{\"name\": \"").append(escapeJson("Session Started By"))
                .append("\", \"value\": \"").append(escapeJson(sessionInitiator))
                .append("\", \"inline\": true},");

        // Target field
        embed.append("{\"name\": \"").append(escapeJson("Target"))
                .append("\", \"value\": \"").append(escapeJson(targetName + " (" + targetType + ")"))
                .append("\", \"inline\": true},");

        // Total changes
        embed.append("{\"name\": \"").append(escapeJson("Changes"))
                .append("\", \"value\": \"").append(totalChanges)
                .append("\", \"inline\": true},");

        // Added nodes
        if (!addedNodes.isEmpty()) {
            StringBuilder addedVal = new StringBuilder();
            int shown = 0;
            for (String node : addedNodes) {
                if (shown >= maxNodes) {
                    addedVal.append("- *...and ").append(addedNodes.size() - shown).append(" more*");
                    break;
                }
                addedVal.append("- `").append(node).append("`\n");
                shown++;
            }
            embed.append("{\"name\": \"").append(escapeJson("Added Permissions (" + addedNodes.size() + ")"))
                    .append("\", \"value\": \"").append(escapeJson(addedVal.toString().trim()))
                    .append("\", \"inline\": false},");
        }

        // Removed nodes
        if (!removedNodes.isEmpty()) {
            StringBuilder removedVal = new StringBuilder();
            int shown = 0;
            for (String node : removedNodes) {
                if (shown >= maxNodes) {
                    removedVal.append("- *...and ").append(removedNodes.size() - shown).append(" more*");
                    break;
                }
                removedVal.append("- `").append(node).append("`\n");
                shown++;
            }
            // Remove trailing comma before adding last field
            embed.append("{\"name\": \"").append(escapeJson("Removed Permissions (" + removedNodes.size() + ")"))
                    .append("\", \"value\": \"").append(escapeJson(removedVal.toString().trim()))
                    .append("\", \"inline\": false}");
        } else {
            // Remove trailing comma
            int lastComma = embed.lastIndexOf(",");
            if (lastComma > embed.indexOf("fields") && lastComma != -1) {
                embed.deleteCharAt(lastComma);
            }
        }

        embed.append("],");
        embed.append("\"timestamp\": \"").append(Instant.now().toString()).append("\"");
        embed.append("}],");
        embed.append("\"username\": \"").append(escapeJson(username)).append("\",");
        embed.append("\"avatar_url\": \"").append(escapeJson(avatarUrl)).append("\"");
        embed.append("}");

        sendAsync(embed.toString());
    }

    /**
     * Sends the embed asynchronously to avoid blocking the main thread.
     */
    private void sendAsync(String json) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL(webhookUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("User-Agent", "LPDiscordLogger/1.0");
                connection.setDoOutput(true);
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode != 200 && responseCode != 204) {
                    plugin.getLogger().warning("Discord webhook returned HTTP " + responseCode);
                }
                connection.disconnect();

            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send Discord webhook: " + e.getMessage());
            }
        });
    }

    /**
     * Escapes a string for safe JSON inclusion.
     */
    private String escapeJson(String input) {
        if (input == null) return "";
        return input
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
