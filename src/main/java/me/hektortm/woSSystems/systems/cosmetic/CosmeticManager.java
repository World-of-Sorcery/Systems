package me.hektortm.woSSystems.systems.cosmetic;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.utils.PlaceholderResolver;
import me.hektortm.woSSystems.utils.types.CosmeticType;
import me.hektortm.wosCore.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The cosmetic menu, as a single Paper dialog.
 *
 * <p>The page tabs (Titles, Prefixes, Badges) are clickable text at the top; below them
 * a 5-wide button grid holds "None" plus the cosmetics of that type the player owns — including ones
 * granted by a permission node.  Clicking a cosmetic equips it and reopens the
 * dialog on the same tab.</p>
 */
public class CosmeticManager {

    /** Page order and labels. */
    private static final List<Tab> TABS = List.of(
            new Tab(CosmeticType.TITLE, "Titles", "Title"),
            new Tab(CosmeticType.PREFIX, "Prefixes", "Prefix"),
            new Tab(CosmeticType.BADGE, "Badges", "Badge"));

    /** Cosmetic grid: {@value} buttons per row. */
    private static final int GRID_COLUMNS = 5;
    private static final int BUTTON_WIDTH = 90;
    /** Wide enough that the tab bar stays on one line. */
    private static final int TAB_BAR_WIDTH = 400;

    /** Menu buttons stay clickable while the dialog is open, however often it is reopened. */
    private static final ClickCallback.Options CALLBACK_OPTIONS =
            ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build();

    private record Tab(CosmeticType type, String label, String singular) {}

    private final DAOHub hub;
    private final PlaceholderResolver placeholderResolver;

    /**
     * @param hub the DAO hub used to access cosmetic and player data
     */
    public CosmeticManager(DAOHub hub, PlaceholderResolver placeholderResolver) {
        this.hub = hub;
        this.placeholderResolver = placeholderResolver;
    }

    /** Opens the menu on the first tab (Titles). */
    public void open(Player p) {
        open(p, TABS.get(0).type());
    }

    /** Opens the menu on the tab for {@code type}. */
    public void open(Player p, CosmeticType type) {
        p.showDialog(menu(p, tab(type)));
    }

    private Dialog menu(Player p, Tab current) {
        String equippedId = hub.getCosmeticsDAO().getCurrentCosmeticId(p, current.type());

        List<ActionButton> cosmetics = new ArrayList<>();
        for (String id : owned(p, current.type())) {
            String display = hub.getCosmeticsDAO().getCosmeticDisplay(current.type(), id);
            if (display == null) continue; // definition was deleted
            cosmetics.add(cosmeticButton(p, current, id, display, id.equals(equippedId)));
        }

        // Dialogs always draw text above the buttons, so the page tabs are clickable text
        // at the top and the button grid below holds only the cosmetics.
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(tabBar(p, current), TAB_BAR_WIDTH));
        if (cosmetics.isEmpty()) {
            body.add(DialogBody.plainMessage(Component.text("You don't have any " + current.label().toLowerCase() + " yet.", NamedTextColor.GRAY)));
        }

        List<ActionButton> buttons = new ArrayList<>();
        if (!cosmetics.isEmpty()) {
            buttons.add(noneButton(p, current, equippedId == null));
            buttons.addAll(cosmetics);
        }

        ActionButton close = ActionButton.builder(Component.text("Close")).build();
        Component title = Component.text("Cosmetics \u203A ", NamedTextColor.GOLD).append(Component.text(current.label(), NamedTextColor.YELLOW));
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .body(body)
                        .build())
                .type(buttons.isEmpty()
                        ? DialogType.notice(close) // a multi-action dialog needs at least one button
                        : DialogType.multiAction(buttons).columns(GRID_COLUMNS).exitAction(close).build()));
    }

    /** {@code [▶ Titles ◀]   [Prefixes]   [Badges]} — the active page is gold, the others clickable. */
    private Component tabBar(Player p, Tab current) {
        Component bar = Component.empty();
        for (int i = 0; i < TABS.size(); i++) {
            Tab tab = TABS.get(i);
            if (i > 0) bar = bar.append(Component.text("     "));
            if (tab == current) {
                bar = bar.append(Component.text("[ " + tab.label() + " ]", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)
                        .hoverEvent(HoverEvent.showText(Component.text("You are viewing your " + tab.label().toLowerCase(), NamedTextColor.GRAY))));
            } else {
                bar = bar.append(Component.text("[" + tab.label() + "]", NamedTextColor.GRAY)
                        .hoverEvent(HoverEvent.showText(Component.text("Show your " + tab.label().toLowerCase(), NamedTextColor.AQUA)))
                        .clickEvent(ClickEvent.callback(audience -> open(p, tab.type()), CALLBACK_OPTIONS)));
            }
        }
        return bar;
    }

    /** Unequips the current cosmetic of this tab's type. */
    private ActionButton noneButton(Player p, Tab tab, boolean selected) {
        Component label = selected
                ? Component.text("\u2714 ", NamedTextColor.GREEN).append(Component.text("None", NamedTextColor.GRAY))
                : Component.text("None", NamedTextColor.GRAY);
        Component tooltip = selected
                ? Component.text("No " + tab.singular().toLowerCase() + " equipped", NamedTextColor.RED)
                : Component.text("Click to unequip your " + tab.singular().toLowerCase(), NamedTextColor.AQUA);
        return ActionButton.builder(label)
                .width(BUTTON_WIDTH)
                .tooltip(tooltip)
                .action(DialogAction.customClick((response, audience) -> {
                    if (!selected) {
                        hub.getCosmeticsDAO().unequipCosmetic(p, tab.type());
                        p.sendMessage(Component.text("You have unequipped your " + tab.singular().toLowerCase() + ".", NamedTextColor.GREEN));
                    }
                    open(p, tab.type());
                }, CALLBACK_OPTIONS))
                .build();
    }

    /** One cosmetic: the tooltip shows the full name, description and obtained date; click to equip. */
    private ActionButton cosmeticButton(Player p, Tab tab, String id, String display, boolean equipped) {
        Component name = Utils.parseColorCodes(placeholderResolver.resolvePlaceholders(display, p));
        Component label = equipped ? Component.text("\u2714 ", NamedTextColor.GREEN).append(name) : name;

        // Long names get cut off on a narrow button, so the tooltip starts with the full name.
        Component tooltip = name.append(Component.newline())
                .append(Utils.parseColorCodes(placeholderResolver.resolvePlaceholders(hub.getCosmeticsDAO().getCosmeticDescription(tab.type(), id), p)));
        String obtained = hub.getCosmeticsDAO().getPlayerObtainedTime(p, id);
        if (obtained != null && !obtained.isEmpty()) {
            tooltip = tooltip.append(Component.newline()).append(Component.text("Obtained " + obtained, NamedTextColor.GOLD));
        }
        tooltip = tooltip.append(Component.newline()).append(Component.newline()).append(equipped
                ? Component.text("Currently equipped", NamedTextColor.RED)
                : Component.text("Click to equip", NamedTextColor.AQUA));

        return ActionButton.builder(label)
                .width(BUTTON_WIDTH)
                .tooltip(tooltip)
                .action(DialogAction.customClick((response, audience) -> {
                    if (!equipped) equip(p, tab, id, display);
                    open(p, tab.type());
                }, CALLBACK_OPTIONS))
                .build();
    }

    private void equip(Player p, Tab tab, String id, String display) {
        // Re-check at click time: the dialog may be stale (e.g. the cosmetic was taken since).
        if (!owned(p, tab.type()).contains(id)) return;
        hub.getCosmeticsDAO().equipCosmetic(p, tab.type(), id);
        Utils.success(p, "cosmetics", "equipped", "%type%", tab.singular(), "%display%", Utils.parseColorCodeString(display));
    }

    /** Owned cosmetics of {@code type}, plus any the player gets through a permission node. */
    private List<String> owned(Player p, CosmeticType type) {
        Set<String> ids = new LinkedHashSet<>(hub.getCosmeticsDAO().getPlayerCosmetics(p, type));
        for (Map.Entry<String, String> entry : hub.getCosmeticsDAO().getPermissionCosmetics(type).entrySet()) {
            String node = entry.getValue();
            if (entry.getKey() != null && node != null && !node.isBlank() && p.hasPermission(node)) ids.add(entry.getKey());
        }
        return new ArrayList<>(ids);
    }

    private static Tab tab(CosmeticType type) {
        return TABS.stream().filter(t -> t.type() == type).findFirst().orElse(TABS.get(0));
    }
}
