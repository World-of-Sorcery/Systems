package me.hektortm.woSSystems.systems.economy.cmd;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.player.ApiWriter;
import me.hektortm.woSSystems.systems.economy.Amounts;
import me.hektortm.woSSystems.systems.economy.EcoManager;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.model.Currency;
import me.hektortm.wosCore.LangManager;
import me.hektortm.wosCore.Utils;
import me.hektortm.wosCore.api.ApiException;
import me.hektortm.wosCore.api.WosApi;
import me.hektortm.woSSystems.utils.TabArg;
import me.hektortm.woSSystems.utils.TabCompletion;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static me.hektortm.wosCore.Utils.error;

/**
 * /baltop [currency] [page]: the richest players of a currency, ten per page,
 * counting everyone (online or not). Without a currency it shows the first one.
 * The list comes from wos-api, off the main thread.
 */
public class BaltopCommand implements CommandExecutor, TabCompleter {

    private static final List<TabArg> ARGUMENTS = List.of(TabArg.content("currencies"), TabArg.NONE);
    static final int PAGE_SIZE = 10;

    private final EcoManager ecoManager;
    private final LangManager lang;
    private final WosApi api;

    public BaltopCommand(EcoManager ecoManager, LangManager lang, WosApi api) {
        this.ecoManager = ecoManager;
        this.lang = lang;
        this.api = api;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String s, @NotNull String[] args) {
        if (!PermissionUtil.hasPermission(sender, Permissions.ECONOMY_BALTOP)) return true;
        if (ecoManager.getCurrencies().isEmpty()) {
            error(sender, "economy", "error.currencies");
            return true;
        }

        // "/baltop 2" is page 2 of the first currency; "/baltop gold 2" names it.
        int page = 1;
        String typedCurrency = null;
        for (String arg : args) {
            Integer number = pageNumber(arg);
            if (number != null) page = number;
            else typedCurrency = arg;
        }
        Currency currency = typedCurrency == null
                ? ecoManager.getCurrencies().values().iterator().next()
                : Eco.currency(ecoManager, sender, typedCurrency);
        if (currency == null) return true;

        int shownPage = page;
        WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<String> lines = fetch(currency, shownPage);
            Bukkit.getScheduler().runTask(plugin, () -> send(sender, currency, shownPage, lines));
        });
        return true;
    }

    /** A page number (1 and up), or null when the argument isn't one. */
    static Integer pageNumber(String arg) {
        try {
            int n = Integer.parseInt(arg);
            return n >= 1 ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The page's lines; null when wos-api can't be reached. */
    private List<String> fetch(Currency currency, int page) {
        try {
            JsonElement rows = api.getJson("/v1/economy/top?currency=" + ApiWriter.seg(currency.getId())
                    + "&limit=" + PAGE_SIZE + "&offset=" + (page - 1) * PAGE_SIZE);
            List<String> lines = new ArrayList<>();
            for (JsonElement el : rows.getAsJsonArray()) {
                JsonObject row = el.getAsJsonObject();
                lines.add(lang.getMessage("economy", "baltop.line")
                        .replace("%rank%", String.valueOf(Json.integer(row, "rank", 0)))
                        .replace("%player%", Json.str(row, "username"))
                        .replace("%color%", currency.getColor())
                        .replace("%amount%", Amounts.format(Json.lng(row, "amount", 0))));
            }
            return lines;
        } catch (ApiException | IllegalStateException e) {
            return null;
        }
    }

    private void send(CommandSender sender, Currency currency, int page, List<String> lines) {
        if (lines == null) {
            error(sender, "economy", "error.data");
            return;
        }
        if (lines.isEmpty()) {
            error(sender, "economy", page == 1 ? "error.baltop-empty" : "error.baltop-page");
            return;
        }
        WoSSystems.ecoMsg2Values(sender, "economy", "baltop.header", "%currency%", Eco.label(currency), "%page%", String.valueOf(page));
        for (String line : lines) sender.sendMessage(Utils.parseColorCodes(line));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return TabCompletion.complete(ARGUMENTS, sender, args);
    }
}
