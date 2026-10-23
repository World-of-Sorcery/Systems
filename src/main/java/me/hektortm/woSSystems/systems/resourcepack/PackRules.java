package me.hektortm.woSSystems.systems.resourcepack;

import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;

/** Pure rules for the resource pack: what to do with a client's answer, and which clients are too old. */
public final class PackRules {

    /** The first protocol version of Minecraft 1.21.8, the oldest client the pack supports. */
    public static final int MIN_PROTOCOL = 772;

    public enum Outcome { NONE, KICK_DECLINED, KICK_FAILED, LOG_FAILED }

    private PackRules() {
    }

    /**
     * What to do with a client's answer. A failure kicks only for the join request (the player
     * has no pack yet); a failed refresh is logged and the player keeps the pack they have.
     *
     * @param joinRequest true while the player has not loaded the pack since joining
     */
    public static Outcome onStatus(Status status, boolean joinRequest) {
        if (status == null) return Outcome.NONE;
        return switch (status) {
            case DECLINED -> Outcome.KICK_DECLINED;
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> joinRequest ? Outcome.KICK_FAILED : Outcome.LOG_FAILED;
            default -> Outcome.NONE;
        };
    }

    /** True for a known protocol below 1.21.8; an unknown protocol (zero or negative) is let through. */
    public static boolean tooOld(int protocol) {
        return protocol > 0 && protocol < MIN_PROTOCOL;
    }
}
