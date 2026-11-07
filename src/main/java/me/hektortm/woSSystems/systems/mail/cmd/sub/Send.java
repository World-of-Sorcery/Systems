package me.hektortm.woSSystems.systems.mail.cmd.sub;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.systems.mail.MailClient;
import me.hektortm.woSSystems.systems.mail.MailService;
import me.hektortm.woSSystems.systems.mail.cmd.MailCommand;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.woSSystems.utils.TabArg;
import me.hektortm.wosCore.Utils;
import me.hektortm.wosCore.api.ApiException;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * {@code /mail send <player>} (a package, players) and {@code /mail send <player|@p|online|all> <preset>} (a preset,
 * {@code mail.admin.send}; content sends it as the console, which needs no node). The console gets no answer line
 * for a preset send, so command lists don't spam; failures go to the server log.
 */
public class Send extends SubCommand {
    private final MailService service;
    private final MailClient client;

    public Send(MailService service, MailClient client) {
        this.service = service;
        this.client = client;
    }

    @Override
    public String getName() {
        return "send";
    }

    @Override
    public Permissions getPermission() {
        return Permissions.MAIL_SEND;
    }

    @Override
    public List<TabArg> arguments() {
        TabArg targets = sender -> {
            List<String> out = new ArrayList<>(TabArg.PLAYER.options(sender));
            if (PermissionUtil.hasPermission(sender, Permissions.MAIL_ADMIN_SEND)) out.addAll(List.of("online", "all"));
            return out;
        };
        return List.of(targets, TabArg.content("mailpresets"));
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Object parsed = MailCommand.parseSend(args);
        if (parsed instanceof MailCommand.SendPlayer p) {
            if (sender instanceof Player player) service.startPackage(player, p.player());
            else Utils.info(sender, "mail", "error.usage.send");
            return;
        }
        if (parsed instanceof MailCommand.SendPreset preset) {
            if (PermissionUtil.hasPermission(sender, Permissions.MAIL_ADMIN_SEND)) sendPreset(sender, preset);
            return;
        }
        Utils.info(sender, "mail", "error.usage.send");
    }

    private void sendPreset(CommandSender sender, MailCommand.SendPreset preset) {
        String source = sender instanceof Player p ? "player:" + p.getName() : "console";
        UUID actor = sender instanceof Player p ? p.getUniqueId() : null;
        targets(preset.targets())
                .thenCompose(targets -> client.sendPreset(preset.preset(), targets, source, actor))
                .whenComplete((count, err) -> Bukkit.getScheduler().runTask(WoSSystems.getInstance(), () -> answer(sender, preset, count, err)));
    }

    /** "online" / "all" as they are; a name or uuid becomes the player's uuid (online here, else asked of the API). */
    private CompletableFuture<Object> targets(String targets) {
        if (MailCommand.SendPreset.everyone(targets)) return CompletableFuture.completedFuture(targets.toLowerCase(Locale.ROOT));
        Player online = Bukkit.getPlayerExact(targets);
        if (online != null) return CompletableFuture.completedFuture(List.of(online.getUniqueId().toString()));
        try {
            return CompletableFuture.completedFuture(List.of(UUID.fromString(targets).toString()));
        } catch (IllegalArgumentException notAUuid) {
            return client.uuidOf(targets).thenApply(uuid -> List.of(uuid.toString()));
        }
    }

    private static void answer(CommandSender sender, MailCommand.SendPreset preset, Integer count, Throwable err) {
        if (err == null) {
            if (sender instanceof Player) Utils.success(sender, "mail", "send.preset-done", "%preset%", preset.preset(), "%count%", String.valueOf(count));
            return;
        }
        Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
        String code = cause instanceof ApiException api ? api.code() : "";
        if ("preset_not_found".equals(code)) {
            Utils.error(sender, "mail", "send.preset-unknown", "%preset%", preset.preset());
        } else if ("player_not_found".equals(code) || "http_404".equals(code)) {
            Utils.error(sender, "mail", "error.unknown", "%player%", preset.targets());
        } else {
            Utils.error(sender, "mail", "error.unavailable");
        }
        WoSSystems.getInstance().getLogger().warning("[Mail] mail send " + preset.targets() + " " + preset.preset() + " failed: " + cause.getMessage());
    }
}
