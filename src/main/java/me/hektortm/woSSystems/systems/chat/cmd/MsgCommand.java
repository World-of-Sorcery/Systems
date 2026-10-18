package me.hektortm.woSSystems.systems.chat.cmd;

import me.hektortm.woSSystems.systems.chat.MsgRules;
import me.hektortm.woSSystems.systems.chat.MsgService;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.wosCore.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /msg &lt;player&gt; &lt;message&gt; (also /tell, /w, /whisper): a private message to a player on any server.
 * The console, command blocks and content run as the console send it as {@value MsgRules#SERVER_NAME}.
 */
public class MsgCommand implements CommandExecutor {
    private final MsgService msg;

    public MsgCommand(MsgService msg) {
        this.msg = msg;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!PermissionUtil.hasPermission(sender, Permissions.CHAT_MSG)) return true;
        if (args.length < 2) {
            Utils.error(sender, "chat", "error.usage-msg");
            return true;
        }
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        if (sender instanceof Player player) msg.send(player, args[0], text);
        else msg.sendFromServer(sender, args[0], text);
        return true;
    }
}
