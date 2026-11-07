package me.hektortm.woSSystems.systems.mail;

import me.hektortm.woSSystems.systems.mail.model.MailAttachment;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nullable;
import java.util.Base64;
import java.util.Locale;

/** Exact items in mail: Paper's own item bytes, base64 in the attachment JSON, plus a plain summary for the portal. */
public final class Stacks {

    private Stacks() {}

    public static String encode(ItemStack stack) {
        return Base64.getEncoder().encodeToString(stack.serializeAsBytes());
    }

    /** The item, or null when the bytes can't be read back (a version change); the mail then keeps it. */
    @Nullable
    public static ItemStack decode(@Nullable String data) {
        if (data == null || data.isEmpty()) return null;
        try {
            return ItemStack.deserializeBytes(Base64.getDecoder().decode(data));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Material, amount, the name without colours, and the custom item id (empty for a vanilla item). */
    @SuppressWarnings("deprecation") // the legacy name string is what stripColor reads
    public static MailAttachment.Summary summary(ItemStack stack, @Nullable String citemId) {
        String name = "";
        if (stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null && meta.hasDisplayName()) {
                String plain = ChatColor.stripColor(meta.getDisplayName());
                name = plain == null ? "" : plain;
            }
        }
        return new MailAttachment.Summary(stack.getType().name().toLowerCase(Locale.ROOT), stack.getAmount(), name,
                citemId == null ? "" : citemId);
    }

    public static MailAttachment attachment(ItemStack stack, @Nullable String citemId) {
        return MailAttachment.stack(encode(stack), summary(stack, citemId));
    }
}
