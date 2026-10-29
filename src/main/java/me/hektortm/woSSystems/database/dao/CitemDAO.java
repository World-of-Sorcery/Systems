package me.hektortm.woSSystems.database.dao;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.content.ApiSource;
import me.hektortm.woSSystems.content.ContentStore;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.player.ApiServices;
import me.hektortm.woSSystems.player.ApiWriter;
import me.hektortm.woSSystems.systems.citems.CitemBuilder;
import me.hektortm.woSSystems.utils.Keys;
import me.hektortm.woSSystems.utils.Parsers;
import me.hektortm.wosCore.api.ApiException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

import static me.hektortm.woSSystems.player.ApiWriter.body;

/**
 * Custom items: the definitions come from wos-api ({@code /v1/content/citems}),
 * built into {@link ItemStack}s once and cached. Items placed in the world as
 * displays ({@code /v1/server/placed-citems}) are loaded at startup, kept in
 * memory keyed by block location, and written through on every change.
 */
public class CitemDAO {
    /** An item placed in the world as a display entity. */
    public record Placed(String blockLocation, String citemId, UUID owner, String displayLocation, boolean creative) {}

    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final String logName = "CitemDAO";
    private final ContentStore<ItemStack> store;
    private final ContentStore<Placed> placed;
    private final ApiWriter writer;
    private final me.hektortm.wosCore.api.WosApi api;

    /** A placed item a pull-back took away: its display and block are still in this server's world. */
    public record Removal(String blockLocation, String citemId, String displayLocation) {}

    /** A placed item whose custom item has a placed interaction: where it stands and which interaction. */
    public record PlacedAction(Location block, String interactionId) {}

    /** The placed items with a placed interaction; rebuilt after either store changed. */
    private volatile List<PlacedAction> placedActions = List.of();
    private volatile boolean placedActionsDirty = true;

    public CitemDAO(ApiServices s) {
        this.writer = s.writer();
        this.api = s.api();
        this.store = s.content().register(new ContentStore<>("citems", "Citem",
                ApiSource.flat(s.api(), "/v1/content/citems", "id",
                        j -> CitemBuilder.build(Json.str(j, "id"), Json.str(j, "data", "{}")), s.log())));
        this.placed = s.content().register(new ContentStore<>("placed-citems", "Placed item",
                ApiSource.flat(s.api(), "/v1/server/placed-citems", "block_location", j -> new Placed(
                        Json.str(j, "block_location"), Json.str(j, "citem_id"), UUID.fromString(Json.str(j, "owner_uuid")),
                        Json.str(j, "display_location"), Json.bool(j, "creative_placed", false)), s.log())));
        store.onChange(x -> placedActionsDirty = true);
        placed.onChange(x -> placedActionsDirty = true);
    }

    /**
     * The placed items whose custom item has a placed interaction, for the
     * interaction task to draw that interaction's particles, holograms and
     * displays at. Built from the stores only after one of them changed, so a
     * pass costs no item lookups. An item in a world that isn't loaded is left
     * out and looked for again in the next pass. Main thread.
     */
    public List<PlacedAction> placedActions() {
        if (!placedActionsDirty) return placedActions;
        placedActionsDirty = false; // before reading: a change while building marks it again
        Map<String, String> actionOf = new HashMap<>();
        List<PlacedAction> result = new ArrayList<>();
        for (Placed p : placed.all()) {
            String interactionId = actionOf.computeIfAbsent(p.citemId(), this::placedActionOf);
            if (interactionId.isEmpty()) continue;
            String world = p.blockLocation().split(",", 2)[0];
            if (Bukkit.getWorld(world) == null) {
                placedActionsDirty = true;
                continue;
            }
            Location block = Parsers.stringToLocation(p.blockLocation());
            if (block != null) result.add(new PlacedAction(block, interactionId));
        }
        placedActions = List.copyOf(result);
        return placedActions;
    }

    /** The item's placed interaction, or "" when it has none (or the item is unknown). */
    private String placedActionOf(String citemId) {
        ItemStack item = store.get(citemId);
        if (item == null || !item.hasItemMeta()) return "";
        String id = item.getItemMeta().getPersistentDataContainer().get(Keys.PLACED_ACTION.get(), PersistentDataType.STRING);
        return id == null ? "" : id;
    }

    /** Reloads every item definition from the API (the {@code /citems reload} command). Blocking. */
    public void preloadAll() {
        try {
            store.preload();
            plugin.getLogger().info(logName + ": reloaded " + store.size() + " item(s).");
        } catch (ApiException e) {
            WoSSystems.discordLog(Level.SEVERE, "CID:preload", "Failed to reload items from the API: ", e);
        }
    }

    /** Runs {@code listener} after every load and reload of the item definitions (on the thread that loaded). */
    public void onChange(Runnable listener) {
        store.onChange(s -> listener.run());
    }

    /** A clone of the cached item, or {@code null} if it doesn't exist. */
    public ItemStack getCitem(String id) {
        ItemStack cached = store.get(id);
        return cached != null ? cached.clone() : null;
    }

    public boolean citemExists(String id) {
        return store.exists(id);
    }

    /** The id of the citem marked as the wand (magic spec §6.2; wos-api allows one), or null. */
    @javax.annotation.Nullable
    public String wandId() {
        for (Map.Entry<String, ItemStack> e : store.asMap().entrySet()) {
            ItemStack item = e.getValue();
            if (item != null && item.hasItemMeta()
                    && item.getItemMeta().getPersistentDataContainer().has(Keys.WAND.get(), PersistentDataType.BOOLEAN)) {
                return e.getKey();
            }
        }
        return null;
    }

    // ─── Placed item displays ───────────────────────────────────────────────────

    /** Records an item placed as a display at {@code blockLocation}. */
    public void createItemDisplay(String id, UUID ownerUUID, Location blockLocation, Location displayLocation, boolean isCreative) {
        save(new Placed(Parsers.locationToString(blockLocation), id, ownerUUID,
                Parsers.locationToString(displayLocation), isCreative));
    }

    /** Removes the placed item at {@code location} if {@code ownerUUID} placed it. */
    public void removeItemDisplay(UUID ownerUUID, Location location) {
        Placed p = at(location);
        if (p == null || !p.owner().equals(ownerUUID)) return;
        placed.remove(p.blockLocation());
        writer.delete("/v1/server/placed-citems?block_location=" + URLEncoder.encode(p.blockLocation(), StandardCharsets.UTF_8));
    }

    /** Who placed the item at {@code location}, or {@code null}. */
    public UUID getUUID(Location location) {
        Placed p = at(location);
        return p == null ? null : p.owner();
    }

    public boolean isCreativePlaced(Location location) {
        Placed p = at(location);
        return p != null && p.creative();
    }

    /** The display entity location of the item placed at {@code location}, or {@code null}. */
    public Location getDisplayLocation(Location location) {
        Placed p = at(location);
        return p == null ? null : Parsers.stringToLocation(p.displayLocation());
    }

    /** The citem id of the item placed at {@code location}, or {@code null}. */
    public String getItemDisplayID(Location location) {
        Placed p = at(location);
        return p == null ? null : p.citemId();
    }

    public boolean isItemDisplay(Location location) {
        return at(location) != null;
    }

    public boolean isItemDisplayOwner(Location location, UUID uuid) {
        Placed p = at(location);
        return p != null && p.owner().equals(uuid);
    }

    // ─── Placed items a pull-back took away ─────────────────────────────────────

    /**
     * The placed items a pull-back took away from this server's world set
     * ({@code GET /v1/server/placed-citems/removals}); their rows are gone, the
     * display entities and blocks are not. Blocking.
     */
    public List<Removal> placedRemovals() throws ApiException {
        List<Removal> out = new ArrayList<>();
        for (var el : api.getJson("/v1/server/placed-citems/removals").getAsJsonArray()) {
            var j = el.getAsJsonObject();
            out.add(new Removal(Json.str(j, "block_location"), Json.str(j, "citem_id"), Json.str(j, "display_location")));
        }
        return out;
    }

    /** The removal at {@code blockLocation} is done (or no longer wanted): its entry goes. */
    public void placedRemovalDone(String blockLocation) {
        writer.delete("/v1/server/placed-citems/removals?block_location=" + URLEncoder.encode(blockLocation, StandardCharsets.UTF_8));
    }

    private Placed at(Location location) {
        return placed.get(Parsers.locationToString(location));
    }

    private void save(Placed p) {
        placed.put(p.blockLocation(), p);
        writer.put("/v1/server/placed-citems", body(
                "block_location", p.blockLocation(), "citem_id", p.citemId(), "owner_uuid", p.owner().toString(),
                "display_location", p.displayLocation(), "creative_placed", p.creative()));
    }
}
