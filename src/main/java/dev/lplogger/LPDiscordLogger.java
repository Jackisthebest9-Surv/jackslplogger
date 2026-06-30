package dev.lplogger;

import dev.lplogger.editor.EditorChangeTracker;
import dev.lplogger.listeners.CommandListener;
import dev.lplogger.listeners.LuckPermsEventListener;
import dev.lplogger.webhook.DiscordWebhook;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class LPDiscordLogger extends JavaPlugin {

    private static LPDiscordLogger instance;
    private LuckPerms luckPerms;
    private DiscordWebhook discordWebhook;
    private EditorChangeTracker editorChangeTracker;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config
        saveDefaultConfig();

        // Hook into LuckPerms
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager()
                .getRegistration(LuckPerms.class);

        if (provider == null) {
            getLogger().severe("LuckPerms not found! Disabling LPDiscordLogger.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        luckPerms = provider.getProvider();
        getLogger().info("Successfully hooked into LuckPerms.");

        // Initialize webhook
        String webhookUrl = getConfig().getString("webhook-url", "");
        if (webhookUrl.isEmpty()) {
            getLogger().severe("No webhook URL configured! Disabling LPDiscordLogger.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        discordWebhook = new DiscordWebhook(this, webhookUrl);

        // Initialize editor change tracker
        editorChangeTracker = new EditorChangeTracker(this, discordWebhook);

        // Register command listener (captures /lp and /luckperms)
        CommandListener commandListener = new CommandListener(this, discordWebhook, editorChangeTracker);
        Bukkit.getPluginManager().registerEvents(commandListener, this);

        // Register LuckPerms event listener (tracks node changes from editor)
        LuckPermsEventListener lpEventListener = new LuckPermsEventListener(editorChangeTracker);
        luckPerms.getEventBus().subscribe(this, net.luckperms.api.event.node.NodeAddEvent.class,
                lpEventListener::onNodeAdd);
        luckPerms.getEventBus().subscribe(this, net.luckperms.api.event.node.NodeRemoveEvent.class,
                lpEventListener::onNodeRemove);
        luckPerms.getEventBus().subscribe(this, net.luckperms.api.event.node.NodeClearEvent.class,
                lpEventListener::onNodeClear);

        getLogger().info("LPDiscordLogger has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        getLogger().info("LPDiscordLogger has been disabled.");
    }

    public static LPDiscordLogger getInstance() {
        return instance;
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    public DiscordWebhook getDiscordWebhook() {
        return discordWebhook;
    }

    public EditorChangeTracker getEditorChangeTracker() {
        return editorChangeTracker;
    }
}
