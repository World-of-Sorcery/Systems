package me.hektortm.woSSystems.database.dao;

import me.hektortm.woSSystems.content.ApiSource;
import me.hektortm.woSSystems.content.ContentStore;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.player.ApiServices;
import me.hektortm.woSSystems.player.ApiWriter;
import me.hektortm.woSSystems.player.PlayerSession;
import me.hektortm.woSSystems.player.PlayerSessions;
import me.hektortm.woSSystems.utils.model.Currency;
import me.hektortm.wosCore.api.ApiException;
import me.hektortm.wosCore.api.WosApi;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import static me.hektortm.woSSystems.player.ApiWriter.body;

/**
 * Currencies and balances. Definitions come from {@code /v1/content/currencies};
 * balances live in each player's {@link PlayerSession}. Every balance change is
 * persisted as one wos-api transaction, which updates the balance and writes the
 * economy log atomically (the legacy code wrote the two separately).
 */
public class EconomyDAO {
    private final ContentStore<Currency> currencies;
    private final PlayerSessions sessions;
    private final ApiWriter writer;
    private final WosApi api;
    private final Logger log;

    public EconomyDAO(ApiServices s) {
        this.sessions = s.sessions();
        this.writer = s.writer();
        this.api = s.api();
        this.log = s.log();
        this.currencies = s.content().register(new ContentStore<>("currencies", "Currency",
                ApiSource.flat(s.api(), "/v1/content/currencies", "id", j -> new Currency(
                        Json.str(j, "id"), Json.str(j, "name"),
                        Json.str(j, "icon"), Json.str(j, "color"), Json.bool(j, "hidden_if_zero", false),
                        Json.bool(j, "payable", true), Json.lng(j, "max_balance", 0)), log)));
    }

    public long getPlayerCurrency(UUID uuid, String currency) {
        PlayerSession s = sessions.getOrFetch(uuid);
        return s == null ? 0 : s.balances.getOrDefault(currency, 0L);
    }

    /**
     * Sets a balance to {@code newAmount}, recording where the change came from
     * (e.g. {@code "command"}/sender, {@code "interaction"}/id) in the economy log.
     */
    public void updatePlayerCurrency(UUID uuid, String currency, long newAmount, String sourceType, String source) {
        PlayerSession s = sessions.getOrFetch(uuid);
        if (s == null) {
            log.warning("[Economy] cannot change " + currency + " for unknown player " + uuid);
            return;
        }
        long previous = s.balances.getOrDefault(currency, 0L);
        long delta = newAmount - previous;
        if (delta == 0) return;
        s.balances.put(currency, newAmount);
        me.hektortm.woSSystems.systems.quests.QuestHooks.report(uuid, "currency", "balance_reached", java.util.Map.of("currency", currency, "amount", String.valueOf(newAmount)));
        if (delta > 0) me.hektortm.woSSystems.systems.quests.QuestHooks.report(uuid, "currency", "earned", java.util.Map.of("currency", currency, "amount", String.valueOf(delta)));
        writer.postOnce("/v1/economy/transactions", body(
                "uuid", uuid.toString(), "currency", currency, "delta", delta,
                "source_type", sourceType, "source", source == null ? "" : source));
    }

    /**
     * Moves {@code amount} from one player to the other: both cached balances
     * change together, and the move is persisted as ONE wos-api transfer
     * (debit, credit and both log rows in a single database transaction), so
     * money is never created or lost in between. The caller checks the funds.
     *
     * @return false when either player's data isn't available
     */
    public boolean transfer(UUID from, UUID to, String currency, long amount, String sourceType, String source) {
        PlayerSession sender = sessions.getOrFetch(from);
        PlayerSession receiver = sessions.getOrFetch(to);
        if (sender == null || receiver == null) {
            log.warning("[Economy] cannot move " + currency + " between " + from + " and " + to + ": player data missing");
            return false;
        }
        sender.balances.merge(currency, -amount, Long::sum);
        receiver.balances.merge(currency, amount, Long::sum);
        writer.postOnce("/v1/economy/transfers", body(
                "from", from.toString(), "to", to.toString(), "currency", currency, "amount", amount,
                "source_type", sourceType, "source", source == null ? "" : source));
        return true;
    }

    /** Portal changes already applied (by log id), so a redelivered message isn't applied twice. */
    private final java.util.Set<Long> appliedExternal = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** A balance change made outside the game: whose, which currency, by how much, and its log id. */
    public record ExternalChange(UUID uuid, String currency, long delta, long logId) {
        /** Parses "uuid|currency|delta|logId" (what wos-api sends); null if it isn't that. */
        public static ExternalChange parse(String message) {
            String[] parts = message == null ? new String[0] : message.split("\\|");
            if (parts.length != 4 || parts[1].isBlank()) return null;
            try {
                return new ExternalChange(UUID.fromString(parts[0]), parts[1], Long.parseLong(parts[2]), Long.parseLong(parts[3]));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    /**
     * Applies a balance change wos-api made itself (a portal adjustment) to the
     * online player's cached balance. It is NOT sent back to wos-api — it
     * already happened there. An offline player needs nothing: their balances
     * load fresh at the next login. Call on the main thread.
     */
    public void applyExternalChange(String message) {
        ExternalChange change = ExternalChange.parse(message);
        if (change == null) {
            log.warning("[Economy] ignoring a malformed balance change: " + message);
            return;
        }
        if (appliedExternal.size() > 10_000) appliedExternal.clear();
        if (!appliedExternal.add(change.logId())) return; // already applied
        PlayerSession s = sessions.get(change.uuid());
        if (s == null) return;
        s.balances.merge(change.currency(), change.delta(), (a, b) -> Math.max(0, a + b));
    }

    /**
     * The uuid of a player who has been on the server, by name (any case), or
     * null if there is none. Blocking — call off the main thread.
     */
    public UUID findPlayerUuid(String name) {
        try {
            var player = api.getJson("/v1/players/by-name/" + ApiWriter.seg(name)).getAsJsonObject();
            return UUID.fromString(Json.str(player, "uuid"));
        } catch (ApiException e) {
            if (!e.isNotFound()) log.warning("[Economy] looking up player " + name + " failed: " + e.getMessage());
            return null;
        } catch (IllegalArgumentException | IllegalStateException malformed) {
            log.warning("[Economy] looking up player " + name + ": unexpected answer (" + malformed.getMessage() + ")");
            return null;
        }
    }

    // ─── Currency definitions ───────────────────────────────────────────────────

    /** Every currency definition, keyed by id (read-only view). */
    public Map<String, Currency> getCurrencies() {
        return currencies.asMap();
    }

    public boolean currencyExists(String id) {
        return currencies.exists(id);
    }

    /** Force-refreshes the currency definitions from the API. Blocking — call off the main thread. */
    public void reloadCurrencies() {
        try {
            currencies.preload();
        } catch (ApiException e) {
            log.severe("[Economy] reloading currencies failed: " + e.getMessage());
        }
    }
}
