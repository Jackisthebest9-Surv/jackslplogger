package dev.lplogger.editor;

import dev.lplogger.LPDiscordLogger;
import dev.lplogger.webhook.DiscordWebhook;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks LuckPerms editor session changes and batches them into a single Discord embed.
 *
 * How it works:
 *  1. When /lp editor (or a subcommand like /lp user <name> editor) is run, we register
 *     the session with the initiator's name.
 *  2. LuckPerms fires NodeAdd/NodeRemove events when the user clicks "Save & apply" in the
 *     web editor. We capture all of these within a short window (~3 seconds) and batch them.
 *  3. After the window closes, we fire a single Discord embed listing every change.
 */
public class EditorChangeTracker {

    private final LPDiscordLogger plugin;
    private final DiscordWebhook webhook;

    // Active editor session state
    private volatile boolean tracking = false;
    private String sessionInitiator = "Unknown";
    private String targetName = "Unknown";
    private String targetType = "unknown";

    // Accumulated changes keyed by holder: holderKey -> (added, removed)
    private final Map<String, HolderChanges> changeMap = new ConcurrentHashMap<>();

    // Debounce task: fires after node events stop coming in
    private BukkitTask flushTask = null;
    private static final long FLUSH_DELAY_TICKS = 60L; // 3 seconds

    // How long to keep tracking after an editor is opened (in ticks)
    // 12000 = 10 minutes — more than enough for a human to save
    private static final long TRACKING_WINDOW_TICKS = 12000L;
    private BukkitTask trackingTimeoutTask = null;

    public EditorChangeTracker(LPDiscordLogger plugin, DiscordWebhook webhook) {
        this.plugin = plugin;
        this.webhook = webhook;
    }

    /**
     * Called when a player/console runs an editor-opening command.
     *
     * @param initiatorName  Display name of initiator
     * @param fullCommand    The full command string
     */
    public void registerEditorSession(String initiatorName, String fullCommand) {
        // Reset any previous session
        resetTracking();

        this.tracking = true;
        this.sessionInitiator = initiatorName;

        // Parse target from command, e.g. "/lp user Notch editor" or "/lp group admin editor"
        parseTarget(fullCommand);

        plugin.getLogger().info("Editor session opened by " + initiatorName
                + " for " + targetType + " '" + targetName + "'. Tracking changes...");

        // Set a timeout: stop tracking after 10 minutes regardless
        trackingTimeoutTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (tracking) {
                plugin.getLogger().info("Editor tracking window expired for session by " + initiatorName + ".");
                resetTracking();
            }
        }, TRACKING_WINDOW_TICKS);
    }

    /**
     * Records a permission addition from an editor session.
     */
    public void recordAddition(String holderName, String holderType, String nodeKey) {
        if (!tracking) return;

        // Update target if we didn't know it before
        if ("Unknown".equals(targetName)) {
            targetName = holderName;
            targetType = holderType;
        }

        getOrCreate(holderName, holderType).added.add(nodeKey);
        scheduleFlush();
    }

    /**
     * Records a permission removal from an editor session.
     */
    public void recordRemoval(String holderName, String holderType, String nodeKey) {
        if (!tracking) return;

        if ("Unknown".equals(targetName)) {
            targetName = holderName;
            targetType = holderType;
        }

        getOrCreate(holderName, holderType).removed.add(nodeKey);
        scheduleFlush();
    }

    /**
     * Records a full clear from an editor session.
     */
    public void recordClear(String holderName, String holderType, int nodeCount) {
        if (!tracking) return;

        if ("Unknown".equals(targetName)) {
            targetName = holderName;
            targetType = holderType;
        }

        HolderChanges changes = getOrCreate(holderName, holderType);
        changes.removed.add("*All permissions cleared (" + nodeCount + " nodes removed)*");
        scheduleFlush();
    }

    public boolean isTracking() {
        return tracking;
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private HolderChanges getOrCreate(String holderName, String holderType) {
        String key = holderType + ":" + holderName;
        return changeMap.computeIfAbsent(key, k -> new HolderChanges(holderName, holderType));
    }

    /**
     * Cancels any existing flush task and schedules a new one.
     * Each new node event resets the timer so we wait until changes stop.
     */
    private void scheduleFlush() {
        if (flushTask != null) {
            flushTask.cancel();
        }
        flushTask = plugin.getServer().getScheduler().runTaskLater(plugin, this::flush, FLUSH_DELAY_TICKS);
    }

    /**
     * Fires the Discord embed and resets tracking state.
     */
    private void flush() {
        if (changeMap.isEmpty()) {
            resetTracking();
            return;
        }

        int totalChanges = 0;

        // If multiple holders changed, fire one embed per holder
        for (HolderChanges changes : changeMap.values()) {
            totalChanges += changes.added.size() + changes.removed.size();

            webhook.sendEditorLog(
                    sessionInitiator,
                    changes.holderName,
                    changes.holderType,
                    new ArrayList<>(changes.added),
                    new ArrayList<>(changes.removed),
                    changes.added.size() + changes.removed.size()
            );
        }

        plugin.getLogger().info("Editor session by " + sessionInitiator
                + " flushed: " + totalChanges + " total changes across "
                + changeMap.size() + " holder(s).");

        resetTracking();
    }

    private void resetTracking() {
        tracking = false;
        sessionInitiator = "Unknown";
        targetName = "Unknown";
        targetType = "unknown";
        changeMap.clear();

        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        if (trackingTimeoutTask != null) {
            trackingTimeoutTask.cancel();
            trackingTimeoutTask = null;
        }
    }

    /**
     * Parses the target name and type from a command string.
     * Examples:
     *   /lp user Notch editor      -> user "Notch"
     *   /lp group admin editor     -> group "admin"
     *   /lp editor                 -> unknown (will be filled from events)
     */
    private void parseTarget(String fullCommand) {
        String args = fullCommand.replaceFirst("(?i)^/?luck(perms)?\\s*", "").trim();
        String[] parts = args.split("\\s+");

        if (parts.length == 0) return;

        String sub = parts[0].toLowerCase();
        switch (sub) {
            case "user":
                if (parts.length > 1) {
                    targetName = parts[1];
                    targetType = "user";
                }
                break;
            case "group":
                if (parts.length > 1) {
                    targetName = parts[1];
                    targetType = "group";
                }
                break;
            case "editor":
                // global editor — target filled from first event
                targetType = "global";
                break;
            default:
                break;
        }
    }

    // -----------------------------------------------------------------------
    // Inner class
    // -----------------------------------------------------------------------

    private static class HolderChanges {
        final String holderName;
        final String holderType;
        final LinkedHashSet<String> added = new LinkedHashSet<>();
        final LinkedHashSet<String> removed = new LinkedHashSet<>();

        HolderChanges(String holderName, String holderType) {
            this.holderName = holderName;
            this.holderType = holderType;
        }
    }
}
