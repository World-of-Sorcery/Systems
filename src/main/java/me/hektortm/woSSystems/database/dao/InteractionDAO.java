package me.hektortm.woSSystems.database.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.hektortm.woSSystems.content.ApiSource;
import me.hektortm.woSSystems.content.ContentStore;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.player.ApiServices;
import me.hektortm.woSSystems.player.ApiWriter;
import me.hektortm.woSSystems.utils.Parsers;
import me.hektortm.woSSystems.utils.model.Interaction;
import me.hektortm.woSSystems.utils.model.InteractionAction;
import me.hektortm.woSSystems.utils.model.InteractionDisplay;
import me.hektortm.woSSystems.utils.model.InteractionHologram;
import me.hektortm.woSSystems.utils.model.InteractionParticles;
import me.hektortm.wosCore.api.WosApi;
import org.bukkit.Location;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static me.hektortm.woSSystems.player.ApiWriter.body;

/**
 * Interactions: the definition (actions, particles, holograms, displays and their
 * conditions) comes from wos-api ({@code /v1/content/interactions/{id}}); the
 * in-world bindings (NPCs and blocks an interaction is attached to) come from
 * {@code /v1/server/interaction-bindings}, are indexed in memory and written
 * through when changed in-game.
 */
public class InteractionDAO {
    private static final Set<String> CONDITION_TYPES = Set.of("interaction", "particle", "hologram", "display");

    private final ConditionDAO conditions;
    private final ApiWriter writer;
    private final ContentStore<Interaction> store;

    /** Serialised block location → interaction id. */
    private final Map<String, String> blockIndex = new ConcurrentHashMap<>();
    /** Citizens NPC id → interaction id. */
    private final Map<Integer, String> npcIndex = new ConcurrentHashMap<>();

    public InteractionDAO(ApiServices s, ConditionDAO conditions) {
        this.conditions = conditions;
        this.writer = s.writer();
        // Registered before the interactions: map() reads the binding indexes.
        s.content().register(new ContentStore<>("interaction-bindings", "Interaction bindings", bindingsSource(s.api())))
                .onChange(bindings -> {
                    JsonObject doc = bindings.get("all");
                    if (doc != null) loadBindings(doc);
                });
        this.store = s.content().register(new ContentStore<>("interactions", "Interaction",
                ApiSource.tree(s.api(), "/v1/content/interactions", this::map, s.log())));
    }

    /** All bindings as one document under the key {@code "all"}. */
    private static ContentStore.Source<JsonObject> bindingsSource(WosApi api) {
        return new ContentStore.Source<>() {
            @Override
            public Map<String, JsonObject> loadAll() throws me.hektortm.wosCore.api.ApiException {
                return Map.of("all", api.getJson("/v1/server/interaction-bindings").getAsJsonObject());
            }

            @Override
            public Optional<JsonObject> loadOne(String id) throws me.hektortm.wosCore.api.ApiException {
                return Optional.of(loadAll().get("all"));
            }
        };
    }

    // ── Bindings ───────────────────────────────────────────────────────────────

    /** Refills the binding indexes from a {@code GET /v1/server/interaction-bindings} document. */
    private void loadBindings(JsonObject doc) {
        npcIndex.clear();
        blockIndex.clear();
        for (JsonElement el : Json.array(doc, "npcs")) {
            JsonObject b = el.getAsJsonObject();
            npcIndex.put(Json.integer(b, "npc_id", 0), Json.str(b, "interaction_id"));
        }
        for (JsonElement el : Json.array(doc, "blocks")) {
            JsonObject b = el.getAsJsonObject();
            blockIndex.put(Json.str(b, "location"), Json.str(b, "interaction_id"));
        }
    }

    /** Binds an NPC; {@code false} if the interaction is unknown or the NPC is already bound. */
    public boolean bindNPC(String id, int npcId) {
        if (!store.exists(id) || npcIndex.putIfAbsent(npcId, id) != null) return false;
        writer.put("/v1/server/interaction-bindings/npcs/" + npcId, body("interaction_id", id));
        store.get(id).getNpcIDs().add(npcId);
        return true;
    }

    /** Binds a block; {@code false} if the interaction is unknown or the block is already bound. */
    public boolean bindBlock(String id, Location loc) {
        String block = Parsers.locationToString(loc);
        if (!store.exists(id) || blockIndex.putIfAbsent(block, id) != null) return false;
        writer.put("/v1/server/interaction-bindings/blocks", body("interaction_id", id, "location", block));
        store.get(id).getBlockLocations().add(loc);
        return true;
    }

    public boolean unbindNpc(int npcId) {
        String interId = npcIndex.remove(npcId);
        if (interId == null) return false;
        writer.delete("/v1/server/interaction-bindings/npcs/" + npcId);
        Interaction inter = store.get(interId);
        if (inter != null) inter.getNpcIDs().remove((Integer) npcId);
        return true;
    }

    public boolean unbindBlock(Location loc) {
        String location = Parsers.locationToString(loc);
        String interId = blockIndex.remove(location);
        if (interId == null) return false;
        writer.delete("/v1/server/interaction-bindings/blocks?location=" + URLEncoder.encode(location, StandardCharsets.UTF_8));
        Interaction inter = store.get(interId);
        if (inter != null) inter.getBlockLocations().removeIf(l -> l == null || Parsers.locationToString(l).equals(location));
        return true;
    }

    // ── Lookups (memory only) ──────────────────────────────────────────────────

    public Interaction getInteractionByID(String id) {
        return store.get(id);
    }

    public String getBound(Location loc) {
        return blockIndex.get(Parsers.locationToString(loc));
    }

    public String getNpcBound(int npcId) {
        return npcIndex.get(npcId);
    }

    /** The blocks the interaction is bound to (the quest engine looks for a step's target). */
    public List<Location> blocksOf(String interactionId) {
        return blockIndex.entrySet().stream().filter(e -> interactionId.equals(e.getValue()))
                .map(e -> Parsers.stringToLocation(e.getKey())).filter(java.util.Objects::nonNull).collect(Collectors.toList());
    }

    /** The Citizens NPCs the interaction is bound to. */
    public List<Integer> npcsOf(String interactionId) {
        return npcIndex.entrySet().stream().filter(e -> interactionId.equals(e.getValue())).map(Map.Entry::getKey).collect(Collectors.toList());
    }

    public List<Location> getAllBlockLocations() {
        return blockIndex.keySet().stream().map(Parsers::stringToLocation).collect(Collectors.toList());
    }

    public boolean interactionExists(String id) {
        return store.exists(id);
    }

    public List<Interaction> cache() {
        return new ArrayList<>(store.all());
    }

    // ── tree → model ───────────────────────────────────────────────────────────

    private Interaction map(JsonObject tree) {
        String id = Json.str(tree, "id");
        JsonArray childConditions = new JsonArray();

        List<InteractionAction> actions = new ArrayList<>();
        for (JsonElement el : Json.array(tree, "actions")) {
            JsonObject a = el.getAsJsonObject();
            actions.add(new InteractionAction(id, Json.str(a, "description"), Json.str(a, "behaviour"), Json.str(a, "matchtype"),
                    Json.integer(a, "action_id", 0), Json.strings(a, "actions")));
            childConditions.addAll(Json.array(a, "conditions"));
        }

        List<InteractionParticles> particles = new ArrayList<>();
        for (JsonElement el : Json.array(tree, "particles")) {
            JsonObject p = el.getAsJsonObject();
            particles.add(new InteractionParticles(id, Json.str(p, "description"), Json.str(p, "behaviour"), Json.str(p, "matchtype"),
                    Json.integer(p, "particle_id", 0), Json.str(p, "particle"), Json.str(p, "particle_color")));
            childConditions.addAll(Json.array(p, "conditions"));
        }

        List<InteractionHologram> holograms = new ArrayList<>();
        for (JsonElement el : Json.array(tree, "holograms")) {
            JsonObject h = el.getAsJsonObject();
            holograms.add(new InteractionHologram(id, Json.integer(h, "hologram_id", 0), Json.str(h, "description"), Json.str(h, "behaviour"),
                    Json.str(h, "matchtype"), Json.strings(h, "hologram"), Json.str(h, "settings")));
            childConditions.addAll(Json.array(h, "conditions"));
        }

        List<InteractionDisplay> displays = new ArrayList<>();
        for (JsonElement el : Json.array(tree, "displays")) {
            JsonObject d = el.getAsJsonObject();
            displays.add(new InteractionDisplay(id, Json.integer(d, "display_id", 0), Json.str(d, "behaviour", "continue"),
                    Json.str(d, "matchtype", "all"), Json.str(d, "settings")));
            childConditions.addAll(Json.array(d, "conditions"));
        }

        conditions.replaceChildren(CONDITION_TYPES, id, childConditions);

        List<Location> blocks = blockIndex.entrySet().stream()
                .filter(e -> id.equals(e.getValue()))
                .map(e -> Parsers.stringToLocation(e.getKey()))
                // A block in a world this server doesn't have: kept out, so unbinding others can't trip on it.
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
        List<Integer> npcs = npcIndex.entrySet().stream()
                .filter(e -> id.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(ArrayList::new));

        return new Interaction(id, actions, holograms, displays, particles, blocks, npcs);
    }
}
