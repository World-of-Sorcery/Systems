package me.hektortm.woSSystems.systems.magic.cast;

import org.bukkit.entity.Player;

/**
 * Whether a caster may harm another player here (magic spec §8: both have PvP on, or the
 * region forces it). The PvP plugin answers this in part 5; until then no player harms
 * another with a spell ({@link #DENY}).
 */
@FunctionalInterface
public interface PvpGate {

    PvpGate DENY = (attacker, victim) -> false;

    boolean mayHurt(Player attacker, Player victim);
}
