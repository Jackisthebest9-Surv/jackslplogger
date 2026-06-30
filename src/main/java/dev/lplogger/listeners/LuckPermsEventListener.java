package dev.lplogger.listeners;

import dev.lplogger.editor.EditorChangeTracker;
import net.luckperms.api.event.node.NodeAddEvent;
import net.luckperms.api.event.node.NodeClearEvent;
import net.luckperms.api.event.node.NodeRemoveEvent;
import net.luckperms.api.model.PermissionHolder;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;

public class LuckPermsEventListener {

    private final EditorChangeTracker editorTracker;

    public LuckPermsEventListener(EditorChangeTracker editorTracker) {
        this.editorTracker = editorTracker;
    }

    public void onNodeAdd(NodeAddEvent event) {
        PermissionHolder holder = event.getTarget();
        Node node = event.getNode();

        String holderName = getHolderName(holder);
        String holderType = getHolderType(holder);
        String nodeKey = formatNode(node);

        if (editorTracker.isTracking()) {
            editorTracker.recordAddition(holderName, holderType, nodeKey);
        }
    }

    public void onNodeRemove(NodeRemoveEvent event) {
        PermissionHolder holder = event.getTarget();
        Node node = event.getNode();

        String holderName = getHolderName(holder);
        String holderType = getHolderType(holder);
        String nodeKey = formatNode(node);

        if (editorTracker.isTracking()) {
            editorTracker.recordRemoval(holderName, holderType, nodeKey);
        }
    }

    public void onNodeClear(NodeClearEvent event) {
        PermissionHolder holder = event.getTarget();
        String holderName = getHolderName(holder);
        String holderType = getHolderType(holder);

        if (editorTracker.isTracking()) {
            editorTracker.recordClear(holderName, holderType, event.getNodes().size());
        }
    }

    private String getHolderName(PermissionHolder holder) {
        return holder.getFriendlyName();
    }

    private String getHolderType(PermissionHolder holder) {
        if (holder instanceof User) return "user";
        if (holder instanceof Group) return "group";
        return "unknown";
    }

    private String formatNode(Node node) {
        StringBuilder sb = new StringBuilder();

        if (!node.getValue()) sb.append("-");
        sb.append(node.getKey());

        if (node.hasExpiry()) {
            long seconds = node.getExpiry().getEpochSecond() - (System.currentTimeMillis() / 1000);
            sb.append(" (expires in ").append(formatDuration(seconds)).append(")");
        }

        // ContextSet uses forEach(ContextConsumer) - iterate via toSet()
        node.getContexts().toSet().forEach(ctx ->
                sb.append(" [").append(ctx.getKey()).append("=").append(ctx.getValue()).append("]"));

        return sb.toString();
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        if (seconds < 86400) return (seconds / 3600) + "h";
        return (seconds / 86400) + "d";
    }
}
