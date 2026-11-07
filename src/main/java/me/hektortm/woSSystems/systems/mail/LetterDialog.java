package me.hektortm.woSSystems.systems.mail;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import me.hektortm.woSSystems.systems.mail.model.Mail;
import me.hektortm.woSSystems.systems.mail.model.MailAttachment;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A letter: one mail as a native dialog (the dialog API custom screens use): subject, sender and date, the message
 * with the reader's placeholders filled in, the items, and Claim / Delete / Back. Before it is claimed the player
 * sees who sent it, when, and whether a player or the server did (spec item 18).
 */
final class LetterDialog {

    /** A letter's buttons work once and for ten minutes. */
    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(10)).build();

    private final MailItems items;
    private final Function<String, String> lang;
    private final BiFunction<String, Player, String> placeholders;

    LetterDialog(MailItems items, Function<String, String> lang, BiFunction<String, Player, String> placeholders) {
        this.items = items;
        this.lang = lang;
        this.placeholders = placeholders;
    }

    /** What the letter's buttons do. */
    interface Actions {
        void claim();

        void delete();

        void back();
    }

    Dialog build(Player viewer, Mail m, MailSettings s, boolean readOnly, Actions actions) {
        MailSettings.Letter l = s.letter();
        List<DialogBody> body = new ArrayList<>();
        if (l.showSender()) body.add(line("&7" + lang.apply("letter.from").replace("%sender%", "&f" + MailRules.senderLine(m, lang.apply("box.server")))));
        if (l.showDate()) body.add(line("&7" + lang.apply("letter.received").replace("%date%", MailboxGui.date(m, s))));
        if (!m.body().isBlank()) body.add(DialogBody.plainMessage(MailItems.text(placeholders.apply(m.body(), viewer)), 300));
        if (l.showItems() && !m.attachments().isEmpty()) {
            body.add(line("&7" + lang.apply("letter.items")));
            for (MailAttachment a : m.attachments()) body.add(itemBody(a));
        }

        List<ActionButton> buttons = new ArrayList<>();
        if (!readOnly && m.claimable() && !m.claimed()) buttons.add(button(l.labels().get("claim"), actions::claim));
        if (!readOnly) buttons.add(button(l.labels().get("delete"), actions::delete));
        ActionButton back = button(l.labels().get("back"), actions::back);
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(MailItems.text(m.subject()))
                        .canCloseWithEscape(true)
                        .body(body)
                        .build())
                .type(DialogType.multiAction(buttons.isEmpty() ? List.of(back) : buttons)
                        .columns(Math.max(1, buttons.size()))
                        .exitAction(back)
                        .build()));
    }

    /** "Delete this mail?" for an unclaimed one: its items and rewards are lost. */
    Dialog confirmDelete(Runnable yes, Runnable no) {
        return Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(MailItems.text(lang.apply("delete.confirm"))).canCloseWithEscape(true).build())
                .type(DialogType.confirmation(button(lang.apply("delete.yes"), yes), button(lang.apply("delete.no"), no))));
    }

    private static DialogBody line(String text) {
        return DialogBody.plainMessage(MailItems.text(text), 300);
    }

    private DialogBody itemBody(MailAttachment a) {
        ItemStack item = items.attachment(a);
        if (item == null) {
            String what = a.summary() != null && !a.summary().name().isEmpty() ? a.summary().name() : lang.apply("letter.unreadable");
            return line("&c" + what + (a.kind().equals("stack") ? "" : " (" + a.id() + ")"));
        }
        return DialogBody.item(item).description(DialogBody.plainMessage(item.effectiveName()
                .append(Component.text(item.getAmount() > 1 ? " ×" + item.getAmount() : "")))).showTooltip(true).build();
    }

    private static ActionButton button(String label, Runnable action) {
        return ActionButton.builder(MailItems.text(label))
                .action(DialogAction.customClick((response, audience) -> action.run(), ONCE))
                .build();
    }
}
