package dev.lplogger.listeners;

import dev.lplogger.LPDiscordLogger;
import dev.lplogger.editor.EditorChangeTracker;
import dev.lplogger.webhook.DiscordWebhook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CommandListener implements Listener {

    private final LPDiscordLogger plugin;
    private final DiscordWebhook webhook;
    private final EditorChangeTracker editorTracker;

    // Commands that open the web editor
    private static final Set<String> EDITOR_SUBCOMMANDS = new HashSet<>(Arrays.asList(
            "editor", "edit"
    ));

    public CommandListener(LPDiscordLogger plugin, DiscordWebhook webhook, EditorChangeTracker editorTracker) {
        this.plugin = plugin;
        this.webhook = webhook;
        this.editorTracker = editorTracker;
    }

    /**
     * Intercepts player LP commands.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfig().getBoolean("log-players", true)) return;

        String message = event.getMessage().toLowerCase().trim();
        if (!isLuckPermsCommand(message)) return;

        Player player = event.getPlayer();
        String fullCommand = event.getMessage().trim();
        String senderName = player.getName();

        // Check if this is an editor command
        if (isEditorCommand(fullCommand)) {
            editorTracker.registerEditorSession(senderName, fullCommand);
        }

        // Log after a tick so LuckPerms has time to process
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            webhook.sendCommandLog(senderName, false, fullCommand, resolveCommandSummary(fullCommand));
        }, 1L);
    }

    /**
     * Intercepts console LP commands.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsoleCommand(ServerCommandEvent event) {
        if (!plugin.getConfig().getBoolean("log-console", true)) return;

        String command = event.getCommand().toLowerCase().trim();
        if (!isLuckPermsCommand("/" + command) && !isLuckPermsCommand(command)) return;

        String fullCommand = event.getCommand().trim();

        // Check if editor
        if (isEditorCommand(fullCommand)) {
            editorTracker.registerEditorSession("Console", fullCommand);
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            webhook.sendCommandLog("Console", true, fullCommand, resolveCommandSummary(fullCommand));
        }, 1L);
    }

    /**
     * Returns true if the command starts with /lp or /luckperms.
     */
    private boolean isLuckPermsCommand(String msg) {
        String lower = msg.toLowerCase();
        return lower.startsWith("/lp ") || lower.equals("/lp")
                || lower.startsWith("/luckperms ") || lower.equals("/luckperms")
                || lower.startsWith("lp ") || lower.equals("lp")
                || lower.startsWith("luckperms ") || lower.equals("luckperms");
    }

    /**
     * Returns true if the command is an editor open command.
     */
    private boolean isEditorCommand(String fullCommand) {
        String[] parts = fullCommand.replaceFirst("(?i)^/?luck(perms)?\\s*", "").trim().split("\\s+");
        if (parts.length == 0) return false;
        String sub = parts[0].toLowerCase();
        return EDITOR_SUBCOMMANDS.contains(sub);
    }

    /**
     * Attempts to build a human-readable description of what the command does.
     * This is a best-effort parse — the actual outcome is confirmed via LuckPerms events.
     */
    private String resolveCommandSummary(String fullCommand) {
        // Strip "/lp" or "/luckperms" prefix
        String args = fullCommand.replaceFirst("(?i)^/?luck(perms)?\\s*", "").trim();
        if (args.isEmpty()) return "Opened LuckPerms help.";

        String[] parts = args.split("\\s+");
        String sub = parts[0].toLowerCase();

        return switch (sub) {
            case "editor", "edit" -> "Opened the LuckPerms web editor.";
            case "user" -> parts.length > 2 ? "User command on **" + parts[1] + "**: `" + parts[2] + "`" : "User command issued.";
            case "group" -> parts.length > 2 ? "Group command on **" + parts[1] + "**: `" + parts[2] + "`" : "Group command issued.";
            case "track" -> parts.length > 2 ? "Track command on **" + parts[1] + "**: `" + parts[2] + "`" : "Track command issued.";
            case "sync" -> "Synchronised LuckPerms data.";
            case "info" -> "Displayed LuckPerms info.";
            case "verbose" -> "Toggled verbose mode.";
            case "tree" -> "Displayed permission tree.";
            case "search" -> parts.length > 1 ? "Searched for permission: `" + parts[1] + "`" : "Searched permissions.";
            case "check" -> "Checked a permission.";
            case "networksync" -> "Triggered network sync.";
            case "reload" -> "Reloaded LuckPerms configuration.";
            case "export" -> "Exported LuckPerms data.";
            case "import" -> "Imported LuckPerms data.";
            case "bulkupdate" -> "Bulk update command issued.";
            case "apply-edits" -> "Applied editor session changes.";
            default -> "Command `" + sub + "` executed.";
        };
    }
}
