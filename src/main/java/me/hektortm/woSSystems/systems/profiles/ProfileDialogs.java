package me.hektortm.woSSystems.systems.profiles;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.chat.ChatManager;
import me.hektortm.woSSystems.systems.chat.NicknameManager;
import me.hektortm.woSSystems.systems.regions.RegionHandler;
import me.hektortm.woSSystems.utils.Keys;
import me.hektortm.woSSystems.utils.types.CosmeticType;
import me.hektortm.wosCore.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The player profile, shown as Paper dialogs: {@link #open} shows someone's
 * profile (from {@code /profile} or clicking a name in chat), and your own
 * profile has an "Edit Profile" button for the bio and item showcase.
 *
 * <p><b>To change the info lines, edit {@link #rows()}.</b>  Each row is a label
 * plus a function that turns the viewed player into the value; rows are shown
 * top to bottom in list order, and a row whose value is {@code null} is skipped.
 * The other sections (header, bio, showcase, buttons) are built in {@link #profile}.</p>
 *
 * <p>Showcase slots: everyone gets {@link #DEFAULT_SLOTS}; the permission
 * {@code profile.showcase.<n>} raises that to {@code n} (up to {@link #MAX_SLOTS}).
 * Only custom items can be showcased — a slot stores the citem id.</p>
 */
public class ProfileDialogs {

    /** One "Label: value" line.  Return {@code null} from {@code value} to hide the row. */
    private record Row(String label, Function<Player, Component> value) {}

    public static final int DEFAULT_SLOTS = 1;
    public static final int MAX_SLOTS = 5;
    private static final int BIO_MAX_LENGTH = 150;
    private static final NamedTextColor LABEL_COLOR = NamedTextColor.GRAY;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    /** Dialog buttons may be clicked more than once (e.g. reopening via chat history). */
    private static final ClickCallback.Options CALLBACK_OPTIONS =
            ClickCallback.Options.builder().uses(ClickCallback.UNLIMITED_USES).build();

    private final DAOHub hub;
    private final NicknameManager nickManager;

    public ProfileDialogs(DAOHub hub, NicknameManager nickManager) {
        this.hub = hub;
        this.nickManager = nickManager;
    }

    // ── Edit here ────────────────────────────────────────────────────────

    private List<Row> rows() {
        return List.of(
                new Row("Username", p -> Component.text(p.getName())),
                new Row("Location", p -> Utils.parseColorCodes(RegionHandler.getRegionDisplayName(p))),
                new Row("Gold", p -> Component.text(hub.getEconomyDAO().getPlayerCurrency(p.getUniqueId(), "gold"), NamedTextColor.YELLOW)),
                new Row("Joined", p -> p.getFirstPlayed() == 0 ? null : Component.text(DATE.format(Instant.ofEpochMilli(p.getFirstPlayed())))),
                new Row("Playtime", p -> Component.text(playtime(p)))
        );
    }

    // ── Opening ──────────────────────────────────────────────────────────

    /** Shows {@code target}'s profile to {@code viewer}. */
    public void open(Player viewer, Player target) {
        viewer.showDialog(profile(viewer, target));
    }

    /** Shows the edit dialog for the viewer's own profile. */
    public void openEditor(Player player) {
        player.showDialog(editor(player));
    }

    // ── Profile ──────────────────────────────────────────────────────────

    private Dialog profile(Player viewer, Player target) {
        boolean own = viewer.getUniqueId().equals(target.getUniqueId());
        List<DialogBody> body = new ArrayList<>();

        // Header: head with title + badge beside it.
        body.add(DialogBody.item(head(target))
                .description(DialogBody.plainMessage(header(target)))
                .showTooltip(false)
                .build());

        body.add(DialogBody.plainMessage(lines(target)));

        String bio = hub.getProfileDAO().getBio(target.getUniqueId());
        if (bio != null) {
            body.add(DialogBody.plainMessage(Component.text("“" + bio + "”", NamedTextColor.WHITE)
                    .decorate(TextDecoration.ITALIC)));
        }

        List<ItemStack> showcase = showcaseItems(target);
        if (!showcase.isEmpty() || own) {
            body.add(DialogBody.plainMessage(Component.text("── Showcase ──", NamedTextColor.GOLD)));
        }
        for (ItemStack item : showcase) {
            body.add(DialogBody.item(item)
                    .description(DialogBody.plainMessage(item.effectiveName()))
                    .showTooltip(true)
                    .build());
        }
        if (showcase.isEmpty() && own) {
            body.add(DialogBody.plainMessage(Component.text("Nothing showcased yet — use Edit Profile.", NamedTextColor.GRAY)));
        }

        List<ActionButton> buttons = new ArrayList<>();
        if (own) {
            buttons.add(button("Edit Profile", "Change your bio and showcase", (response, audience) -> openEditor(viewer)));
        } else {
            buttons.add(ActionButton.builder(Component.text("Add Friend"))
                    .tooltip(Component.text("Send " + target.getName() + " a friend request"))
                    .action(DialogAction.staticAction(ClickEvent.runCommand("/friend add " + target.getName())))
                    .build());
        }

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title(target))
                        .canCloseWithEscape(true)
                        .body(body)
                        .build())
                .type(DialogType.multiAction(buttons)
                        .columns(buttons.size())
                        .exitAction(ActionButton.builder(Component.text("Close")).build())
                        .build()));
    }

    /** As in chat: the LuckPerms prefix and the nickname (or name). */
    private Component title(Player target) {
        String nick = nickManager.getNickname(target);
        String name = nick != null ? nick.replace("_", " ") : target.getName();
        return Utils.parseColorCodes(ChatManager.withPrefix(target, name));
    }

    /** Title and badge cosmetics, shown beside the head. */
    private Component header(Player target) {
        Component title = cosmetic(target, CosmeticType.TITLE);
        Component badge = cosmetic(target, CosmeticType.BADGE);
        if (title == null && badge == null) return Component.text(target.getName(), NamedTextColor.GRAY);

        Component text = Component.empty();
        if (title != null) text = text.append(title);
        if (title != null && badge != null) text = text.append(Component.newline());
        if (badge != null) text = text.append(Component.text("Badge: ", LABEL_COLOR)).append(badge);
        return text;
    }

    private Component lines(Player target) {
        Component text = Component.empty();
        boolean first = true;
        for (Row row : rows()) {
            Component value = row.value().apply(target);
            if (value == null) continue;
            if (!first) text = text.append(Component.newline());
            text = text.append(Component.text(row.label() + ": ", LABEL_COLOR)).append(value);
            first = false;
        }
        return text;
    }

    // ── Editor ───────────────────────────────────────────────────────────

    private Dialog editor(Player player) {
        String bio = hub.getProfileDAO().getBio(player.getUniqueId());
        Map<Integer, String> showcase = hub.getProfileDAO().getShowcase(player.getUniqueId());
        int slots = allowedSlots(player);

        TextDialogInput bioInput = DialogInput.text("bio", Component.text("About me"))
                .initial(bio == null ? "" : bio)
                .maxLength(BIO_MAX_LENGTH)
                .width(300)
                .multiline(TextDialogInput.MultilineOptions.create(4, null))
                .build();

        // Every button also saves the bio, so typing then clicking a slot never loses text.
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button("Save", "Save your bio and go back", (response, audience) -> {
            saveBio(player, response.getText("bio"));
            open(player, player);
        }));
        for (int slot = 0; slot < slots; slot++) {
            int s = slot;
            ItemStack current = citem(showcase.get(s));
            Component label = Component.text("Slot " + (s + 1) + ": ")
                    .append(current != null ? current.effectiveName() : Component.text("Empty", NamedTextColor.GRAY));
            buttons.add(ActionButton.builder(label)
                    .tooltip(Component.text("Hold a custom item to showcase it here, or click with an empty hand to clear"))
                    .width(200)
                    .action(DialogAction.customClick((response, audience) -> {
                        saveBio(player, response.getText("bio"));
                        setSlotFromHand(player, s);
                        openEditor(player);
                    }, CALLBACK_OPTIONS))
                    .build());
        }

        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(Component.text("Edit Profile"))
                        .canCloseWithEscape(true)
                        .body(List.of(DialogBody.plainMessage(Component.text(
                                "Showcase: hold a custom item and click a slot. Click with an empty hand to clear it. "
                                        + "You have " + slots + " slot" + (slots == 1 ? "" : "s") + ".",
                                NamedTextColor.GRAY))))
                        .inputs(List.of(bioInput))
                        .build())
                .type(DialogType.multiAction(buttons)
                        .columns(1)
                        .exitAction(button("Back", "Back without saving the bio", (response, audience) -> open(player, player)))
                        .build()));
    }

    private void saveBio(Player player, String bio) {
        String current = hub.getProfileDAO().getBio(player.getUniqueId());
        String next = bio == null ? "" : bio.strip();
        if (!next.equals(current == null ? "" : current)) {
            hub.getProfileDAO().setBio(player.getUniqueId(), next);
        }
    }

    private void setSlotFromHand(Player player, int slot) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR) {
            hub.getProfileDAO().clearShowcaseSlot(player.getUniqueId(), slot);
            return;
        }
        String id = held.hasItemMeta()
                ? held.getItemMeta().getPersistentDataContainer().get(Keys.ID.get(), PersistentDataType.STRING)
                : null;
        if (id == null || !hub.getCitemDAO().citemExists(id)) {
            player.sendMessage(Component.text("Only custom items can be showcased.", NamedTextColor.RED));
            return;
        }
        hub.getProfileDAO().setShowcaseSlot(player.getUniqueId(), slot, id);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Slots from {@code profile.showcase.<n>} (highest wins), else {@link #DEFAULT_SLOTS}. */
    public static int allowedSlots(Player player) {
        for (int n = MAX_SLOTS; n > DEFAULT_SLOTS; n--) {
            if (player.hasPermission("profile.showcase." + n)) return n;
        }
        return DEFAULT_SLOTS;
    }

    /** Showcased items in slot order; slots beyond the player's allowance or with deleted citems are skipped. */
    private List<ItemStack> showcaseItems(Player target) {
        int slots = allowedSlots(target);
        List<ItemStack> items = new ArrayList<>();
        hub.getProfileDAO().getShowcase(target.getUniqueId()).forEach((slot, id) -> {
            ItemStack item = slot < slots ? citem(id) : null;
            if (item != null) items.add(item);
        });
        return items;
    }

    private ItemStack citem(String id) {
        return id == null ? null : hub.getCitemDAO().getCitem(id);
    }

    private Component cosmetic(Player p, CosmeticType type) {
        String value = hub.getCosmeticsDAO().getCurrentCosmetic(p, type);
        return value == null ? null : Utils.parseColorCodes(value);
    }

    private static ActionButton button(String label, String tooltip, DialogActionCallback onClick) {
        return ActionButton.builder(Component.text(label))
                .tooltip(Component.text(tooltip))
                .action(DialogAction.customClick(onClick, CALLBACK_OPTIONS))
                .build();
    }

    private static String playtime(Player p) {
        long minutes = p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20 / 60; // the statistic counts ticks
        long hours = minutes / 60;
        return hours > 0 ? hours + "h " + (minutes % 60) + "m" : minutes + "m";
    }

    private static ItemStack head(Player target) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(target));
        return head;
    }
}
