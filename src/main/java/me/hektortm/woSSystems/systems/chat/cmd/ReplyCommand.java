package me.hektortm.woSSystems.systems.chat.cmd;

import me.hektortm.woSSystems.systems.chat.MsgService;
import me.hektortm.woSSystems.utils.PermissionUtil;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.wosCore.Utils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** /r &lt;message&gt; (also /reply): answers the last player you messaged or got a message from. */
public class ReplyCommand implements CommandExecutor {
    private final MsgService msg;

    public ReplyCommand(MsgService msg) {
        this.msg = msg;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!PermissionUtil.isPlayer(sender) || !PermissionUtil.hasPermission(sender, Permissions.CHAT_MSG)) return true;
        if (args.length < 1) {
            Utils.error(sender, "chat", "error.usage-reply");
            return true;
        }
        msg.reply((Player) sender, String.join(" ", args));
        return true;
    }
}
