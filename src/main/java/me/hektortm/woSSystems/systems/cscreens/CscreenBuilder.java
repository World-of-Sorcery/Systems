package me.hektortm.woSSystems.systems.cscreens;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.body.ItemDialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.NumberRangeDialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.debug.DebugFormat;
import me.hektortm.woSSystems.utils.model.Condition;
import me.hektortm.woSSystems.utils.model.Cscreen;
import me.hektortm.woSSystems.utils.types.ConditionType;
import me.hektortm.wosCore.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns a custom screen into one of Minecraft's dialogs for one player: the
 * elements whose conditions the player meets, with the placeholders filled in
 * for them. An element the server can't build (an unknown material, an input
 * without a usable key …) is left out with a warning in the server log; the
 * rest of the screen still shows.
 */
final class CscreenBuilder {

    /** How long after opening a screen its buttons still work. */
    private static final Duration CLICK_LIFETIME = Duration.ofHours(1);

    /** An input that is on the screen, for reading its value out of a click. */
    record ShownInput(String key, CscreenSettings.Input settings) {}

    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final DAOHub hub;
    private final CscreenManager manager;

    CscreenBuilder(DAOHub hub, CscreenManager manager) {
        this.hub = hub;
        this.manager = manager;
    }

    Dialog build(Cscreen screen, Player player) {
        List<String> debug = plugin.getDebugMode().isOn(player) ? new ArrayList<>() : null;
        if (debug != null) debug.add(DebugFormat.header("cscreen", screen.id(), screen.type()));

        List<DialogBody> body = new ArrayList<>();
        for (Cscreen.Element<CscreenSettings.Body> e : passing(screen, "body", screen.body(), player, debug)) {
            DialogBody built = attempt(screen, "body", e.id(), () -> body(e.settings(), player));
            if (built != null) body.add(built);
        }

        List<ShownInput> shown = new ArrayList<>();
        List<DialogInput> inputs = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (Cscreen.Element<CscreenSettings.Input> e : passing(screen, "input", screen.inputs(), player, debug)) {
            String key = e.settings().key();
            if (!CscreenRules.validKey(key) || !keys.add(key)) {
                warn(screen, "input", e.id(), "its key '" + key + "' is empty, used twice or holds something other than letters, digits and _");
                continue;
            }
            DialogInput built = attempt(screen, "input", e.id(), () -> input(e.settings(), player));
            if (built == null) {
                keys.remove(key);
                continue;
            }
            inputs.add(built);
            shown.add(new ShownInput(key, e.settings()));
        }

        List<Cscreen.Element<CscreenSettings.Button>> buttons = passing(screen, "button", screen.buttons(), player, debug);
        List<Boolean> exits = buttons.stream().map(b -> b.settings().exit()).toList();
        CscreenRules.Layout<Cscreen.Element<CscreenSettings.Button>> layout = CscreenRules.layout(screen.type(), buttons, exits);
        boolean staysOpen = screen.settings().afterAction().equals("none");

        List<ActionButton> main = new ArrayList<>();
        for (Cscreen.Element<CscreenSettings.Button> e : layout.buttons()) {
            ActionButton built = attempt(screen, "button", e.id(), () -> button(screen, e, player, shown, staysOpen));
            if (built != null) main.add(built);
        }
        ActionButton exit = layout.exit() == null ? null
                : attempt(screen, "button", layout.exit().id(), () -> button(screen, layout.exit(), player, shown, staysOpen));

        if (debug != null) debug.forEach(player::sendMessage);

        DialogBase base = DialogBase.builder(text(screen.title(), player))
                .externalTitle(text(screen.settings().externalTitle().isBlank() ? screen.title() : screen.settings().externalTitle(), player))
                .canCloseWithEscape(screen.settings().canCloseWithEscape())
                .pause(false)
                .afterAction(afterAction(screen.settings().afterAction()))
                .body(body)
                .inputs(inputs)
                .build();
        DialogType type = type(screen, player, main, exit);
        return Dialog.create(factory -> factory.empty().base(base).type(type));
    }

    // ── which elements show ─────────────────────────────────────────────────────

    /** The elements whose conditions the player meets; in debug mode the others are listed with their conditions. */
    private <T> List<Cscreen.Element<T>> passing(Cscreen screen, String kind, List<Cscreen.Element<T>> elements, Player player,
                                                 @Nullable List<String> debug) {
        List<Cscreen.Element<T>> out = new ArrayList<>();
        for (Cscreen.Element<T> e : elements) {
            List<Condition> conditions = hub.getConditionDAO().getConditions(ConditionType.CSCREEN, screen.id() + ":" + kind + ":" + e.id());
            int met = 0;
            for (Condition c : conditions) {
                if (plugin.getConditionHandler().evaluate(player, c, null)) met++;
            }
            if (CscreenRules.shows(e.matchtype(), conditions.size(), met)) {
                out.add(e);
                continue;
            }
            if (debug == null) continue;
            debug.add(DebugFormat.note(kind + " " + e.id() + " not shown: needs "
                    + ("one".equalsIgnoreCase(e.matchtype()) ? "one of these" : "all of these")));
            for (Condition c : conditions) {
                debug.add(DebugFormat.condition(c.getName(), c.getValue(), c.getParameter(),
                        plugin.getConditionHandler().evaluate(player, c, null), plugin.getConditionHandler().actual(player, c, null)));
            }
        }
        return out;
    }

    // ── the screen's type ───────────────────────────────────────────────────────

    private DialogType type(Cscreen screen, Player player, List<ActionButton> main, @Nullable ActionButton exit) {
        CscreenSettings.Screen s = screen.settings();
        switch (screen.type()) {
            case "confirmation": {
                ActionButton yes = main.size() > 0 ? main.get(0) : plain(lang("button.yes"));
                ActionButton no = main.size() > 1 ? main.get(1) : plain(lang("button.no"));
                return DialogType.confirmation(yes, no);
            }
            case "links":
                return DialogType.serverLinks(exit, s.columns(), s.buttonWidth());
            case "list": // a button screen whose buttons open the listed screens
                return buttons(screenButtons(screen, player), exit, s.columns());
            case "buttons":
                return buttons(main, exit, s.columns());
            default:
                return main.isEmpty() ? DialogType.notice() : DialogType.notice(main.get(0));
        }
    }

    /** A screen of buttons; Minecraft needs at least one, so without any it is a notice with the exit button. */
    private static DialogType buttons(List<ActionButton> buttons, @Nullable ActionButton exit, int columns) {
        if (buttons.isEmpty()) return exit == null ? DialogType.notice() : DialogType.notice(exit);
        return DialogType.multiAction(buttons).exitAction(exit).columns(columns).build();
    }

    /** One button per listed screen that exists, labelled with that screen's short title. */
    private List<ActionButton> screenButtons(Cscreen screen, Player player) {
        List<ActionButton> out = new ArrayList<>();
        for (String id : screen.settings().screens()) {
            Cscreen target = hub.getCscreenDAO().getCscreen(id);
            if (target == null) {
                plugin.getLogger().warning("[Cscreens] " + screen.id() + " lists the screen '" + id + "', which does not exist");
                continue;
            }
            String label = target.settings().externalTitle().isBlank() ? target.title() : target.settings().externalTitle();
            out.add(ActionButton.builder(text(label.isBlank() ? id : label, player))
                    .width(screen.settings().buttonWidth())
                    .action(DialogAction.customClick((response, audience) -> manager.open(player, id), options(false)))
                    .build());
        }
        return out;
    }

    // ── elements ────────────────────────────────────────────────────────────────

    private DialogBody body(CscreenSettings.Body s, Player player) {
        if (!s.item()) return DialogBody.plainMessage(text(s.text(), player), s.width());

        ItemStack stack;
        if (s.citem()) {
            ItemStack citem = hub.getCitemDAO().getCitem(s.citemId());
            if (citem == null) throw new IllegalArgumentException("no custom item '" + s.citemId() + "'");
            stack = plugin.getCitemManager().personalize(citem.clone(), player);
        } else {
            Material material = Material.matchMaterial(s.material());
            if (material == null || !material.isItem() || material.isAir()) throw new IllegalArgumentException("no item '" + s.material() + "'");
            stack = new ItemStack(material);
        }
        stack.setAmount(Math.min(s.amount(), Math.max(1, stack.getMaxStackSize())));
        ItemDialogBody.Builder item = DialogBody.item(stack)
                .showDecorations(s.showDecorations()).showTooltip(s.showTooltip()).width(s.width()).height(s.height());
        if (!s.description().isBlank()) item.description(DialogBody.plainMessage(text(s.description(), player)));
        return item.build();
    }

    private DialogInput input(CscreenSettings.Input s, Player player) {
        Component label = text(s.label(), player);
        switch (s.kind()) {
            case "boolean":
                return DialogInput.bool(s.key(), label).initial(s.initialOn()).onTrue(s.onTrue()).onFalse(s.onFalse()).build();
            case "option": {
                if (s.options().isEmpty()) throw new IllegalArgumentException("it has no options");
                List<SingleOptionDialogInput.OptionEntry> entries = new ArrayList<>();
                for (CscreenSettings.Option option : s.options()) {
                    entries.add(SingleOptionDialogInput.OptionEntry.create(option.id(), text(option.label(), player), option.initial()));
                }
                return DialogInput.singleOption(s.key(), label, entries).width(s.width()).labelVisible(s.labelVisible()).build();
            }
            case "number": {
                NumberRangeDialogInput.Builder range = DialogInput.numberRange(s.key(), label, s.start(), s.end())
                        .width(s.width()).labelFormat(s.labelFormat()).initial(s.initialNumber());
                if (s.step() > 0) range.step(s.step());
                return range.build();
            }
            default: {
                String initial = plugin.getPlaceholderResolver().resolvePlaceholders(s.initial(), player);
                if (initial.length() > s.maxLength()) initial = initial.substring(0, s.maxLength());
                TextDialogInput.Builder field = DialogInput.text(s.key(), label)
                        .width(s.width()).labelVisible(s.labelVisible()).initial(initial).maxLength(s.maxLength());
                if (s.multiline()) field.multiline(TextDialogInput.MultilineOptions.create(s.maxLines(), s.height()));
                return field.build();
            }
        }
    }

    private ActionButton button(Cscreen screen, Cscreen.Element<CscreenSettings.Button> element, Player player,
                                List<ShownInput> inputs, boolean staysOpen) {
        CscreenSettings.Button s = element.settings();
        ActionButton.Builder button = ActionButton.builder(text(s.label().isBlank() ? lang("button.ok") : s.label(), player)).width(s.width());
        if (!s.tooltip().isBlank()) button.tooltip(text(s.tooltip(), player));

        String target = plugin.getPlaceholderResolver().resolvePlaceholders(s.target(), player);
        switch (s.action()) {
            case "screen":
                button.action(DialogAction.customClick((response, audience) -> manager.open(player, target), options(staysOpen)));
                break;
            case "url":
                if (!target.startsWith("https://") && !target.startsWith("http://")) {
                    throw new IllegalArgumentException("'" + target + "' is not a web address (https://…)");
                }
                button.action(DialogAction.staticAction(ClickEvent.openUrl(target)));
                break;
            case "copy":
                button.action(DialogAction.staticAction(ClickEvent.copyToClipboard(target)));
                break;
            case "close":
                break; // no action: the click only closes the screen
            default:
                button.action(DialogAction.customClick(
                        (response, audience) -> manager.clicked(player, screen, element, inputs, response), options(staysOpen)));
        }
        return button.build();
    }

    /** A button that only closes the screen. */
    private ActionButton plain(String label) {
        return ActionButton.builder(LegacyComponentSerializer.legacySection().deserialize(Utils.parseColorCodeString(label))).build();
    }

    /** A click works once; on a screen that stays open after a click, every time. */
    private static ClickCallback.Options options(boolean repeat) {
        return ClickCallback.Options.builder().uses(repeat ? ClickCallback.UNLIMITED_USES : 1).lifetime(CLICK_LIFETIME).build();
    }

    private static DialogBase.DialogAfterAction afterAction(String name) {
        return switch (name) {
            case "none" -> DialogBase.DialogAfterAction.NONE;
            case "wait_for_response" -> DialogBase.DialogAfterAction.WAIT_FOR_RESPONSE;
            default -> DialogBase.DialogAfterAction.CLOSE;
        };
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private interface Build<T> {
        T build() throws Exception;
    }

    /** The built element, or null (with a warning) if the server refuses it. */
    @Nullable
    private <T> T attempt(Cscreen screen, String kind, int id, Build<T> build) {
        try {
            return build.build();
        } catch (Exception e) {
            warn(screen, kind, id, e.getMessage() == null ? e.toString() : e.getMessage());
            return null;
        }
    }

    private void warn(Cscreen screen, String kind, int id, String why) {
        plugin.getLogger().warning("[Cscreens] " + screen.id() + ": " + kind + " " + id + " left out: " + why);
    }

    /** A text of the screen: placeholders filled in for the player, colour codes applied. */
    private Component text(String raw, Player player) {
        String resolved = plugin.getPlaceholderResolver().resolvePlaceholders(raw == null ? "" : raw, player);
        return LegacyComponentSerializer.legacySection().deserialize(Utils.parseColorCodeString(resolved));
    }

    private String lang(String key) {
        return plugin.getLangManager().getMessage("cscreens", key);
    }
}
