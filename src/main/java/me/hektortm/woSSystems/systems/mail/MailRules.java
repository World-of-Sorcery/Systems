package me.hektortm.woSSystems.systems.mail;

import me.hektortm.woSSystems.systems.mail.model.Mail;

import java.util.List;
import java.util.Optional;

/**
 * The mail system's server-free rules (spec 2026-11-06-mail-system-design.md): how much room a claim needs, how a
 * sender reads, a mail's status, why player mail is refused, where claiming works. Unit-tested.
 */
public final class MailRules {

    /** Unclaimed claimable mails a mailbox holds before player mail is refused (the API enforces it). */
    public static final int LIMIT = 100;
    /** Stacks a player can put into one package. */
    public static final int PLAYER_STACKS = 9;
    /** Time between two packages one player sends. */
    public static final long PLAYER_COOLDOWN_MS = 30_000;
    public static final int MAX_SUBJECT = 64;
    public static final int MAX_PLAYER_BODY = 500;

    private MailRules() {}

    /**
     * One kind of item a claim gives: how many, its stack size, and how many more of it the inventory's partial
     * stacks of the same item still take (they need no free slot). The caller groups similar items.
     */
    public record StackNeed(int amount, int maxStack, int mergeRoom) {}

    /** Free slots a claim needs. */
    public static int slotsNeeded(List<StackNeed> needs) {
        int slots = 0;
        for (StackNeed n : needs) {
            int rest = Math.max(0, n.amount() - n.mergeRoom());
            int stack = Math.max(1, n.maxStack());
            slots += (rest + stack - 1) / stack;
        }
        return slots;
    }

    /** True when every item of a claim fits: the claim never gives part of a mail. */
    public static boolean fits(List<StackNeed> needs, int emptySlots) {
        return slotsNeeded(needs) <= emptySlots;
    }

    /**
     * Who sent it, as the mailbox shows it: a player's name; the server as {@code serverWord}, or "<name> ·
     * <serverWord>" when staff gave the sender a name of its own.
     */
    public static String senderLine(Mail m, String serverWord) {
        if ("player".equals(m.senderKind())) return m.senderName();
        String name = m.senderName() == null ? "" : m.senderName().trim();
        if (name.isEmpty() || name.equalsIgnoreCase("server") || name.equalsIgnoreCase(serverWord)) return serverWord;
        return name + " · " + serverWord;
    }

    /** unclaimed / claimed for a claimable mail; new / read for a message. */
    public static String status(Mail m) {
        if (m.claimable()) return m.claimed() ? "claimed" : "unclaimed";
        return m.read() ? "read" : "new";
    }

    /**
     * Why a package can't be sent (a key of lang/mail.yml), or empty: to oneself, to a player who never joined, an
     * unstashable item in it, nothing in it, or the sender's last package less than 30 s ago. Undroppable items may
     * be sent (the owner's choice, 2026-11-06).
     */
    public static Optional<String> refusePlayerMail(boolean self, boolean recipientKnown, List<Boolean> unstashable,
                                                    long lastSendMs, long nowMs) {
        if (self) return Optional.of("send.self");
        if (!recipientKnown) return Optional.of("send.unknown");
        if (unstashable.isEmpty()) return Optional.of("send.empty");
        if (unstashable.contains(Boolean.TRUE)) return Optional.of("send.unstashable");
        if (lastSendMs > 0 && nowMs - lastSendMs < PLAYER_COOLDOWN_MS) return Optional.of("send.cooldown");
        return Optional.empty();
    }

    /** Seconds left of the sender's cooldown (at least 1 while it runs). */
    public static long cooldownLeftSeconds(long lastSendMs, long nowMs) {
        long left = PLAYER_COOLDOWN_MS - (nowMs - lastSendMs);
        return left <= 0 ? 0 : Math.max(1, (left + 999) / 1000);
    }

    /** What a claim's answer leads to. */
    public enum ClaimAnswer { GIVE, MAIL_BACK, ALREADY, FAILED }

    /**
     * GIVE the items; MAIL_BACK when the API claimed it but the player left meanwhile (their inventory is no longer
     * saved, so the items go back to the mailbox); ALREADY when it was claimed before; FAILED otherwise.
     */
    public static ClaimAnswer claimAnswer(boolean online, MailClient.ClaimResult r) {
        if (r.mail() != null) return online ? ClaimAnswer.GIVE : ClaimAnswer.MAIL_BACK;
        return "mail_claimed".equals(r.error()) ? ClaimAnswer.ALREADY : ClaimAnswer.FAILED;
    }

    /** Items are per server; the hub's inventory isn't the game's, so nothing is claimed there. */
    public static boolean claimAllowedHere(String serverTag) {
        return !"hub".equalsIgnoreCase(serverTag);
    }

    /** The subject of an overflow mail: the reason, cut to 64 characters. */
    public static String overflowSubject(String reason) {
        String s = reason == null ? "" : reason.trim();
        if (s.isEmpty()) return "Inventory full";
        return s.length() > MAX_SUBJECT ? s.substring(0, MAX_SUBJECT) : s;
    }
}
