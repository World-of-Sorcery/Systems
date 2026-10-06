package me.hektortm.woSSystems.systems.quests.cmd.sub;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.quests.QuestManager;
import me.hektortm.woSSystems.systems.quests.model.PlayerQuestState;
import me.hektortm.woSSystems.systems.quests.model.Quest;
import me.hektortm.woSSystems.systems.quests.model.QuestNode;
import me.hektortm.woSSystems.utils.Permissions;
import me.hektortm.woSSystems.utils.SubCommand;
import me.hektortm.woSSystems.utils.TabArg;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;

/**
 * /quest status [player]
 *
 * <p>Shows all active quests and objective progress.
 * Omitting [player] shows the sender's own status.</p>
 */
public class Status extends SubCommand {

    private final QuestManager questManager;
    private final DAOHub hub;

    public Status(QuestManager questManager, DAOHub hub) {
        this.questManager = questManager;
        this.hub          = hub;
    }

    @Override
    public String getName() { return "status"; }

    @Override
    public Permissions getPermission() { return null; }

    @Override
    public List<TabArg> arguments() {
        return List.of(TabArg.PLAYER);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 1) {
            if (!sender.hasPermission(Permissions.QUEST_ADMIN_START.getPermission())) {
                sender.sendMessage("§cYou don't have permission to view others' quests.");
                return;
            }
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("§cPlayer not found: " + args[0]);
                return;
            }
        } else {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cProvide a player name.");
                return;
            }
            target = p;
        }

        Collection<PlayerQuestState> states = questManager.getActiveStates(target.getUniqueId());

        if (states.isEmpty()) {
            sender.sendMessage("§7" + target.getName() + " has no active quests.");
            return;
        }

        sender.sendMessage("§6=== Active Quests for " + target.getName() + " ===");
        for (PlayerQuestState state : states) {
            Quest quest = hub.getQuestDAO().getQuest(state.getQuestId());
            String title = quest != null ? quest.getTitle() : state.getQuestId();
            sender.sendMessage("§e" + title + " §7(" + state.getQuestId() + ")");

            // Show active objectives with progress
            for (String nodeId : state.getActiveNodes()) {
                if (quest == null) continue;
                QuestNode node = quest.getNode(nodeId);
                if (node == null || !"objective".equals(node.getType())) continue;
                int current  = state.getObjectiveProgress().getOrDefault(nodeId, 0);
                int required = node.getObjectiveQuantity();
                sender.sendMessage("§7  - " + node.getObjectiveLabel() + ": §f" + current + "/" + required);
            }
        }
    }
}
