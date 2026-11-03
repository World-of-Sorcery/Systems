package me.hektortm.woSSystems.systems.chat;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.profiles.ProfileDialogs;
import me.hektortm.woSSystems.utils.PlaceholderResolver;
import me.hektortm.woSSystems.utils.types.CosmeticType;
import me.hektortm.wosCore.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Global chat.  There are no channels any more: every message goes to every
 * viewer of the chat event, rendered as {@code [badge] [prefix] name: message}.
 *
 * <ul>
 *   <li>Clicking the sender's name opens their profile ({@link ProfileDialogs}).</li>
 *   <li>{@code [item]} in a message is replaced by the sender's held item;
 *       clicking it opens a dialog showing a snapshot of that item.</li>
 * </ul>
 */
public class ChatManager {

    private static final Pattern ITEM_TOKEN = Pattern.compile("[item]", Pattern.LITERAL);

    private final DAOHub hub;
    private final NicknameManager nickManager;
    private final PlaceholderResolver placeholderResolver;
    private final ProfileDialogs profileDialogs;

    public ChatManager(DAOHub hub, NicknameManager nickManager, PlaceholderResolver placeholderResolver, ProfileDialogs profileDialogs) {
        this.hub = hub;
        this.nickManager = nickManager;
        this.placeholderResolver = placeholderResolver;
        this.profileDialogs = profileDialogs;
    }

    /**
     * Builds the full chat line for a message.  Rendered once per message
     * (viewer-unaware), so the item snapshot and click events are shared by all viewers.
     */
    public Component render(Player sender, Component message) {
        Component line = Component.empty();

        Component badge = badgeComponent(sender);
        if (badge != null) line = line.append(badge).append(Component.space());

        return line
                .append(nameComponent(sender))
                .append(Component.text(": ", NamedTextColor.GRAY))
                .append(withItem(sender, message).colorIfAbsent(NamedTextColor.WHITE));
    }

    /** The name the sender is shown under in chat (nickname if set). */
    public String displayName(Player player) {
        String nick = nickManager.getNickname(player);
        return nick != null ? nick.replace("_", " ") : player.getName();
    }

    // ── Components ───────────────────────────────────────────────────────

    private Component badgeComponent(Player sender) {
        String badgeId = hub.getCosmeticsDAO().getCurrentCosmeticId(sender, CosmeticType.BADGE);
        if (badgeId == null) return null;

        Component badge = Utils.parseColorCodes(placeholderResolver.resolvePlaceholders(hub.getCosmeticsDAO().getCurrentCosmetic(sender, CosmeticType.BADGE), sender));
        String description = hub.getCosmeticsDAO().getCosmeticDescription(CosmeticType.BADGE, badgeId);
        return description == null ? badge : badge.hoverEvent(HoverEvent.showText(Utils.parseColorCodes(description)));
    }

    /** {@code [prefix ]name} — hover hints at the click, click opens the player dialog. */
    private Component nameComponent(Player sender) {
        String prefix = hub.getCosmeticsDAO().getCurrentCosmetic(sender, CosmeticType.PREFIX);
        String name = displayName(sender);
        Component shown = Utils.parseColorCodes(prefix != null ? prefix + " " + name : name);

        UUID target = sender.getUniqueId();
        return shown
                .hoverEvent(HoverEvent.showText(Component.text(sender.getName(), NamedTextColor.GRAY)
                        .append(Component.newline())
                        .append(Component.text("Click to view profile", NamedTextColor.AQUA))))
                .clickEvent(ClickEvent.callback(viewer -> {
                    if (!(viewer instanceof Player player)) return;
                    Player targetPlayer = Bukkit.getPlayer(target);
                    if (targetPlayer == null) {
                        player.sendMessage(Component.text(name + " is no longer online.", NamedTextColor.GRAY));
                        return;
                    }
                    profileDialogs.open(player, targetPlayer);
                }, ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build()));
    }

    /** Replaces every {@code [item]} in the message with the sender's held item. */
    private Component withItem(Player sender, Component message) {
        ItemStack held = sender.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR) return message; // nothing to show — leave "[item]" as typed

        // Snapshot now: the dialog must show the item as it was when the message was sent.
        ItemStack snapshot = held.clone();
        Component itemName = snapshot.effectiveName();
        Dialog dialog = itemDialog(sender, snapshot, itemName);

        Component itemComponent = Component.text("[", NamedTextColor.GRAY)
                .append(itemName)
                .append(Component.text("]", NamedTextColor.GRAY))
                .hoverEvent(snapshot.asHoverEvent())
                .clickEvent(ClickEvent.showDialog(dialog));

        return message.replaceText(TextReplacementConfig.builder()
                .match(ITEM_TOKEN)
                .replacement(itemComponent)
                .build());
    }

    private Dialog itemDialog(Player sender, ItemStack item, Component itemName) {
        Component title = Component.text(displayName(sender) + "'s Item");
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        // No item description: that always sits to the right of the item.
                        // A separate text body stacks the name centred underneath instead.
                        .body(List.of(
                                DialogBody.item(item)
                                        .showTooltip(true)
                                        .showDecorations(true)
                                        .build(),
                                DialogBody.plainMessage(itemName.decoration(TextDecoration.ITALIC, false))))
                        .build())
                .type(DialogType.notice(ActionButton.builder(Component.text("Close")).build())));
    }
}
