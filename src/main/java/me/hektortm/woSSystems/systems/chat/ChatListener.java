package me.hektortm.woSSystems.systems.chat;

import io.papermc.paper.chat.ChatRenderer;
import io.papermc.paper.event.player.AsyncChatEvent;
import me.hektortm.woSSystems.systems.unlockables.UnlockableManager;
import me.hektortm.wosCore.net.NetworkBus;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ChatListener implements Listener {
    private final ChatManager chatManager;
    private final UnlockableManager unlockableManager;
    private final NetworkBus bus;

    public ChatListener(ChatManager chatManager, UnlockableManager unlockableManager, NetworkBus bus) {
        this.chatManager = chatManager;
        this.unlockableManager = unlockableManager;
        this.bus = bus;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerChat(AsyncChatEvent event) {
        Player player = event.getPlayer();

        // Chat is being captured as mail input — leave the event alone.
        if (unlockableManager.getPlayerTempUnlockable(player, "core_mail")) {
            return;
        }

        // Render once for everyone; the line holds a one-off item snapshot and click callbacks.
        Component line = chatManager.render(player, event.message());
        event.renderer(ChatRenderer.viewerUnaware((source, displayName, message) -> line));

        // The other servers show the same line (no-op without Redis).
        NetworkChat.publish(bus, NetworkChat.GLOBAL, line, player.getName());
    }
}
