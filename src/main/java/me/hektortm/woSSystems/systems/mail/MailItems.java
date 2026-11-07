package me.hektortm.woSSystems.systems.mail;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.guis.GuiRules;
import me.hektortm.woSSystems.systems.mail.model.Mail;
import me.hektortm.woSSystems.systems.mail.model.MailAttachment;
import me.hektortm.woSSystems.systems.mail.model.MailIcon;
import me.hektortm.wosCore.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/** Items of the mail system: icons (a material, a custom item, a head, a pack model) and a mail's attachments. */
final class MailItems {

    private final DAOHub hub;
    private final Logger log;

    MailItems(DAOHub hub, Logger log) {
        this.hub = hub;
        this.log = log;
    }

    /** Text as staff type it: {@code &} and {@code §} codes and {@code #RRGGBB}, never italic. */
    static Component text(@Nullable String s) {
        if (s == null || s.isEmpty()) return Component.empty();
        return Utils.parseColorCodes(ChatColor.translateAlternateColorCodes('&', s)).decoration(TextDecoration.ITALIC, false);
    }

    /** The item an icon names, or null when it names nothing that exists. */
    @Nullable
    ItemStack icon(@Nullable MailIcon icon) {
        if (icon == null) return null;
        String id = icon.id().trim();
        return switch (icon.kind()) {
            case "citem" -> {
                ItemStack c = hub.getCitemDAO().getCitem(id);
                yield c == null ? null : c.clone();
            }
            case "head" -> head(id);
            case "model" -> model(id);
            default -> material(id, 1);
        };
    }

    @Nullable
    static ItemStack material(String id, int amount) {
        Material m = Material.matchMaterial(id);
        if (m == null || !m.isItem() || m.isAir()) return null;
        return new ItemStack(m, Math.max(1, amount));
    }

    @Nullable
    private static ItemStack head(String skin) {
        GuiRules.HeadSkin parsed = GuiRules.headSkin(skin);
        if (parsed == null) return null;
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        head.editMeta(SkullMeta.class, meta -> {
            if (parsed instanceof GuiRules.Owner owner) {
                meta.setPlayerProfile(Bukkit.createProfile(owner.name()));
            } else if (parsed instanceof GuiRules.Texture texture) {
                PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(texture.value().getBytes(StandardCharsets.UTF_8)));
                profile.setProperty(new ProfileProperty("textures", texture.value()));
                meta.setPlayerProfile(profile);
            }
        });
        return head;
    }

    @Nullable
    private static ItemStack model(String id) {
        NamespacedKey key = NamespacedKey.fromString(id.contains(":") ? id : "wos:" + id);
        if (key == null) return null;
        ItemStack paper = new ItemStack(Material.PAPER);
        paper.editMeta(meta -> meta.setItemModel(key));
        return paper;
    }

    /** What a claim gives, in order; {@code missing} collects what can't be built (named in the message and log). */
    List<ItemStack> attachments(Mail m, List<String> missing) {
        List<ItemStack> out = new ArrayList<>();
        for (MailAttachment a : m.attachments()) {
            ItemStack item = attachment(a);
            if (item == null) {
                missing.add(a.kind().equals("stack") ? "stack" : a.id());
                log.warning("[Mail] mail " + m.id() + ": can't build " + a.kind() + " " + a.id());
            } else {
                out.add(item);
            }
        }
        return out;
    }

    @Nullable
    ItemStack attachment(MailAttachment a) {
        return switch (a.kind()) {
            case "citem" -> {
                ItemStack c = hub.getCitemDAO().getCitem(a.id());
                if (c == null) yield null;
                c = c.clone();
                c.setAmount(Math.max(1, a.amount()));
                yield c;
            }
            case "material" -> material(a.id(), a.amount());
            case "stack" -> Stacks.decode(a.data());
            default -> null;
        };
    }
}
