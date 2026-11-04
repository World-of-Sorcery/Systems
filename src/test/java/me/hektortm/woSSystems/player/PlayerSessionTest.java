package me.hektortm.woSSystems.player;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.hektortm.woSSystems.utils.types.CosmeticType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerSessionTest {
    private static final String UUID_STR = "0f8fad5b-d9cb-469f-a165-70867728950e";

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void parsesTheFullSessionDocument() {
        PlayerSession s = PlayerSession.fromJson(obj("""
            {
              "player": {"uuid": "%s", "username": "Steve"},
              "balances": [{"currency": "gold", "amount": 120}],
              "stats": [{"stat_id": "kills", "value": 7}],
              "unlockables": [{"unlockable_id": "door", "temp": false}, {"unlockable_id": "daily_x", "temp": true}],
              "cosmetics": [
                {"cosmetic_type": "badge", "cosmetic_id": "mage", "obtained_at": "Mon, Jan 01 2024", "equipped": true},
                {"cosmetic_type": "title", "cosmetic_id": "hero", "obtained_at": "Tue, Jan 02 2024", "equipped": false}
              ],
              "nickname": "Stevie",
              "cooldowns": [{"cooldown_id": "chest", "started_at": "2026-01-01T10:00:00Z"}],
              "local_cooldowns": [{"cooldown_id": "lever", "interaction_key": "world,1,2,3", "started_at": "2026-01-01T11:00:00Z"}],
              "profile": {"background": "bg", "background_id": "e", "picture": null, "picture_id": null, "bio": "Hi!"},
              "showcase": [{"slot": 0, "citem_id": "sword"}, {"slot": 2, "citem_id": "tome"}],
              "quests": [{"quest_id": "intro", "status": "active", "active_nodes": ["n1"]}],
              "completed_quests": ["tutorial"]
            }""".formatted(UUID_STR)));

        assertThat(s.uuid()).isEqualTo(UUID.fromString(UUID_STR));
        assertThat(s.username()).isEqualTo("Steve");
        assertThat(s.balances).containsEntry("gold", 120L);
        assertThat(s.stats).containsEntry("kills", 7L);
        assertThat(s.permanentUnlocks).containsExactly("door");
        assertThat(s.tempUnlocks).containsExactly("daily_x");
        assertThat(s.cosmetics).containsKeys(PlayerSession.cosmeticKey(CosmeticType.BADGE, "mage"),
                PlayerSession.cosmeticKey(CosmeticType.TITLE, "hero"));
        assertThat(s.equippedCosmetics).containsExactly(java.util.Map.entry(CosmeticType.BADGE, "mage"));
        assertThat(s.nickname()).isEqualTo("Stevie");
        assertThat(s.cooldowns).containsEntry("chest", Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(s.localCooldowns).containsEntry(PlayerSession.localCooldownKey("lever", "world,1,2,3"),
                Instant.parse("2026-01-01T11:00:00Z"));
        assertThat(s.bio()).isEqualTo("Hi!");
        assertThat(s.showcase).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(0, "sword", 2, "tome"));
        assertThat(s.quests).containsKey("intro");
        assertThat(s.completedQuests).containsExactly("tutorial");
    }

    @Test
    void aNewPlayerHasEmptyState() {
        PlayerSession s = PlayerSession.fromJson(obj("""
            {"player": {"uuid": "%s", "username": "New"}, "profile": null, "nickname": null}""".formatted(UUID_STR)));

        assertThat(s.balances).isEmpty();
        assertThat(s.cosmetics).isEmpty();
        assertThat(s.nickname()).isNull();
        assertThat(s.bio()).isNull();
        assertThat(s.showcase).isEmpty();
    }

    @Test
    void skipsCosmeticTypesThisVersionDoesNotKnow() {
        PlayerSession s = PlayerSession.fromJson(obj("""
            {"player": {"uuid": "%s", "username": "X"},
             "cosmetics": [{"cosmetic_type": "aura", "cosmetic_id": "fire", "equipped": true},
                           {"cosmetic_type": "prefix", "cosmetic_id": "mage", "equipped": true}]}""".formatted(UUID_STR)));

        assertThat(s.cosmetics).isEmpty();
        assertThat(s.equippedCosmetics).isEmpty();
    }
}
