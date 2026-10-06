package me.hektortm.woSSystems.systems.loottables.cmd.sub;

import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.utils.types.LoottableItemType;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.woSSystems.utils.model.Loottable;
import me.hektortm.woSSystems.utils.model.LoottableItem;
import me.hektortm.wosCore.Utils;
import me.hektortm.woSSystems.utils.TabArg;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class Chest extends SubCommand {
    private final DAOHub hub;

    public Chest(DAOHub hub) {
        this.hub = hub;
    }

    @Override
    public String getName() {
        return "chest";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.LOOTTABLE_CHEST;
    }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.of("1", "2", "3", "4", "5", "6"), TabArg.PLAYER, TabArg.content("loottables"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {


        if (args.length < 3) {
            Utils.info(sender, "loottables", "info.usage.chest");
            return;
        }

        int rows = 1;
        try {
            rows = Integer.parseInt(args[0]);
            if (rows > 6 || rows < 1) {
                Utils.info(sender, "loottables", "info.invalid-rows");
                return;
            }
        } catch (NumberFormatException e) {
            Utils.error(sender, "loottables", "info.not-number");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        String id = args[2];
        if (target == null) {
            Utils.error(sender, "general", "error.online");
            return;
        }
        if (hub.getLoottablesDAO().getLoottable(id) == null) {
            Utils.error(sender, "loottables", "error.not-found", "%id%", id);
            return;
        }
        Inventory inv = getChest(rows, id, target);
        target.openInventory(inv);
        Utils.success(sender, "loottables", "chest", "%id%", id, "%player%", target.getName());
    }


    private Inventory getChest(int rows, String id, Player target) {
        Loottable lt = hub.getLoottablesDAO().getLoottable(id);
        List<LoottableItem> items = lt.getRandomItems(lt.getAmount());

        List<ItemStack> itemStacks = new ArrayList<>();

        for (LoottableItem item : items) {
            if (item.getType() == LoottableItemType.CITEM) {
                ItemStack i = hub.getCitemDAO().getCitem(item.getValue());
                if (i == null) {
                    Utils.error(target, "loottables", "error.item", "%id%", item.getValue());
                    continue;
                }
                int am = item.getParameter();
                i.setAmount(am);
                itemStacks.add(i);
            }
        }

        Inventory inv = Bukkit.createInventory(null, rows*9, Utils.parseColorCodeString(lt.getName()));

        for (ItemStack item : itemStacks) {
            int r = ThreadLocalRandom.current().nextInt(rows*9);
            while (!(inv.getItem(r) == null)) {
                r = ThreadLocalRandom.current().nextInt(rows*9);
            }
            if (inv.getItem(r) == null) {
                inv.setItem(r, item);
            }
        }

        return inv;
    }
}
