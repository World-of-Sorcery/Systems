package me.hektortm.woSSystems.systems.citems.cmd.sub;

import me.hektortm.woSSystems.utils.Keys;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.wosCore.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class Info extends SubCommand {
    @Override
    public String getName() {
        return "info";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.CITEM_INFO;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!PermissionUtil.isPlayer(sender)) return;
        Player p = (Player) sender;
        ItemStack item = p.getInventory().getItemInMainHand();

        // An empty hand has no meta.
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            Utils.error(sender, "citems", "error.holding-item");
            return;
        }

        PersistentDataContainer data = meta.getPersistentDataContainer();
        String itemId = data.get(Keys.ID.get(), PersistentDataType.STRING);
        if (itemId == null) {
            Utils.error(sender, "citems", "error.not-citem");
            return;
        }

        MiniMessage mm = MiniMessage.miniMessage();

        String url = "https://admin.worldofsorcery.com/dashboard/gamedesign/citems/" + itemId;

        p.sendMessage(mm.deserialize("<green>Item Information:"));

        if (itemId != null) {
            p.sendMessage(mm.deserialize(
                    "<gray>Identifier: <click:open_url:'<url>'>" +
                            "<hover:show_text:'<green>Click to open CItem'><yellow><underlined><id></underlined></yellow></hover>" +
                            "</click>",
                    Placeholder.parsed("url", url),
                    Placeholder.unparsed("id", itemId)
            ));
        }

        boolean undroppable = data.has(Keys.UNDROPPABLE.get(), PersistentDataType.BOOLEAN);
        boolean unusable = data.has(Keys.UNUSABLE.get(), PersistentDataType.BOOLEAN);
        boolean hasHideFlags = !meta.getItemFlags().isEmpty();

        p.sendMessage(mm.deserialize("<gray>Flags:"));
        p.sendMessage(mm.deserialize(" <gray>- <yellow>Undroppable: <white>" + (undroppable ? "Yes" : "No")));
        p.sendMessage(mm.deserialize(" <gray>- <yellow>Unusable: <white>" + (unusable ? "Yes" : "No")));
        p.sendMessage(mm.deserialize(" <gray>- <yellow>Unplaceable: <white>"
                + (data.has(Keys.UNPLACEABLE.get(), PersistentDataType.BOOLEAN) ? "Yes" : "No")));
        p.sendMessage(mm.deserialize(" <gray>- <yellow>Hide Flags: <white>" + (hasHideFlags ? "Yes" : "No")));

        NamespacedKey modelKey = meta.getItemModel();
        NamespacedKey tooltip = meta.getTooltipStyle();
        boolean hiddenTooltip = meta.isHideTooltip();

        p.sendMessage(mm.deserialize("<gray>Model: <white>" + (modelKey != null ? modelKey.toString() : "None")));
        p.sendMessage(mm.deserialize("<gray>Tooltip Style: <white>" + (tooltip != null ? tooltip.toString() : "None")));
        p.sendMessage(mm.deserialize("<gray>Hidden Tooltip: <white>" + (hiddenTooltip ? "Yes" : "No")));

        String leftAction = data.get(Keys.LEFT_ACTION.get(), PersistentDataType.STRING);
        String rightAction = data.get(Keys.RIGHT_ACTION.get(), PersistentDataType.STRING);
        String placedAction = data.get(Keys.PLACED_ACTION.get(), PersistentDataType.STRING);

        p.sendMessage(mm.deserialize("<gray>Actions:"));
        p.sendMessage(mm.deserialize(" <gray>- <yellow>Left-Click Action: <white>" + (leftAction != null ? leftAction : "None")));
        p.sendMessage(mm.deserialize(" <gray>- <yellow>Right-Click Action: <white>" + (rightAction != null ? rightAction : "None")));
        p.sendMessage(mm.deserialize(" <gray>- <yellow>Placed Action: <white>" + (placedAction != null ? placedAction : "None")));
    }
}
