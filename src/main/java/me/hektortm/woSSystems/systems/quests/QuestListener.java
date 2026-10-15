package me.hektortm.woSSystems.systems.quests;

import me.hektortm.woSSystems.systems.citems.CitemManager;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.aselstudios.luxdialoguesapi.Events.DialogueAnsweredEvent;
import org.aselstudios.luxdialoguesapi.Events.DialogueStopEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Map;

/**
 * Loads a player's runs at login and saves them at logout, and reports the
 * events that come straight from the server, Citizens and LuxDialogues
 * (the other systems report through {@link QuestHooks}).
 */
public final class QuestListener implements Listener {

    private final QuestEngine engine;

    public QuestListener(QuestEngine engine) {
        this.engine = engine;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        engine.load(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        engine.unload(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onNpc(NPCRightClickEvent event) {
        engine.report(event.getClicker(), "npc", "right_click", Map.of("npc", String.valueOf(event.getNPC().getId())));
    }

    @EventHandler
    public void onDialogEnd(DialogueStopEvent event) {
        if (event.getPlayer() == null || event.getDialogue() == null) return;
        engine.report(event.getPlayer(), "dialog", "finished", Map.of("dialog", event.getDialogue().getDialogueID()));
    }

    @EventHandler
    public void onDialogAnswer(DialogueAnsweredEvent event) {
        if (event.getPlayer() == null || event.getDialogue() == null || event.getAnswer() == null) return;
        engine.report(event.getPlayer(), "dialog", "answer_picked",
                Map.of("dialog", event.getDialogue().getDialogueID(), "answer", String.valueOf(event.getAnswer().getAnswerID())));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        String id = CitemManager.citemIdOf(event.getItem().getItemStack());
        if (id != null) engine.report(p, "citem", "obtained", Map.of("citem", id));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String id = CitemManager.citemIdOf(event.getItem());
        if (id != null) engine.report(event.getPlayer(), "citem", "used", Map.of("citem", id));
    }
}
