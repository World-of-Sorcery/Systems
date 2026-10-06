package me.hektortm.woSSystems.utils;

import org.bukkit.command.CommandSender;

import java.util.Collection;

/**
 * The {@code help} sub-command of a command: lists the command's sub-commands the sender may
 * use, with the texts under {@code help.} in the command's language file.
 */
public class HelpSubCommand extends SubCommand {

    private final Collection<SubCommand> subCommands;
    private final String langFile;

    /**
     * @param subCommands the command's sub-commands; read when help is asked for, so it may
     *                    be the live view of the command's own map
     * @param langFile    the language file holding {@code help.header}, {@code help.<name>}
     *                    and {@code help.help}
     */
    public HelpSubCommand(Collection<SubCommand> subCommands, String langFile) {
        this.subCommands = subCommands;
        this.langFile = langFile;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public Permissions getPermission() {
        return null;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        HelpUtil.sendHelp(subCommands, sender, langFile);
    }
}
