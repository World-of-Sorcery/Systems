package me.hektortm.woSSystems.systems.interactions;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.utils.ConditionHandler;
import me.hektortm.woSSystems.utils.types.ConditionType;
import me.hektortm.woSSystems.utils.model.Condition;
import me.hektortm.woSSystems.utils.model.Interaction;
import me.hektortm.woSSystems.utils.model.InteractionKey;
import me.hektortm.woSSystems.utils.model.InteractionParticles;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Objects;

public class ParticleHandler {

    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final ConditionHandler conditionHandler = plugin.getConditionHandler();
    private final DAOHub hub;

    public ParticleHandler(DAOHub hub) {
        this.hub = hub;
    }

    public void spawnParticlesForPlayer(Player player, Interaction inter, Location location, boolean npc, InteractionKey key) {
        List<InteractionParticles> particlesList = inter.getParticles();

        if (particlesList == null || particlesList.isEmpty()) {
            return; // No particles to spawn
        }

        for(InteractionParticles particles : particlesList) {
            String particleType = particles.getParticle().toLowerCase();
            String color = particles.getParticleColor();
            String matchType = particles.getMatchType();



            List<Condition> conditionList = hub.getConditionDAO().getConditions(
                    ConditionType.PARTICLE,
                    inter.getInteractionId() + ":" + particles.getParticleId()
            );

            if (!conditionList.isEmpty()) {
                boolean shouldRun;
                if ("one".equalsIgnoreCase(matchType)) {
                    shouldRun = conditionList.isEmpty() || conditionList.stream()
                            .anyMatch(cond -> conditionHandler.evaluate(player, cond, key));
                } else {
                    shouldRun = conditionHandler.checkConditions(player, conditionList, key);
                }

                if (!shouldRun) {
                    continue;
                }
            }

            spawnParticles(player, location, particleType, color, npc);

            if(Objects.equals(particles.getBehaviour(), "continue")) {
                continue;
            } else {
                break;
            }
        }

    }


    private void spawnParticles(Player player, Location location, String type, String color, Boolean npc) {
        if (npc) {
            switch (type) {
                case "redstone_dust":
                    spawnAroundNPC(player, location, Particle.DUST, dust(color));
                    break;
                case "redstone_dust_circle":
                    spawnRedstoneParticleCircle(player, location, color);
                    break;
                case "portal":
                    spawnAroundNPC(player, location, Particle.PORTAL, null);
                    break;
                case "villager_happy":
                    spawnAroundNPC(player, location, Particle.HAPPY_VILLAGER, null);
                    break;
                case "villager_happy_circle":
                    spawnVillagerHappyCircleParticles(player, location);
                    break;
                case "flame":
                    spawnAroundNPC(player, location, Particle.SMALL_FLAME, null);
                    break;
                case "totem":
                    spawnAroundNPC(player, location, Particle.TOTEM_OF_UNDYING, null);
                    break;
                case "smoke":
                    spawnAroundNPC(player, location, Particle.SMOKE, null);
                    break;
                case "explosion":
                    spawnAroundNPC(player, location, Particle.EXPLOSION, null);
                    break;
                case "mycelium":
                    spawnAroundNPC(player, location, Particle.MYCELIUM, null);
                    break;
                default:
                    // Default behavior if the particle type is unknown
                    break;

            }
        } else {
            switch (type) {
                case "redstone_dust":
                    spawnAroundBlock(player, location, Particle.DUST, dust(color));
                    break;
                case "redstone_dust_circle":
                    spawnRedstoneParticleCircle(player, location, color);
                    break;
                case "portal":
                    spawnAroundBlock(player, location, Particle.PORTAL, null);
                    break;
                case "villager_happy":
                    spawnAroundBlock(player, location, Particle.HAPPY_VILLAGER, null);
                    break;
                case "villager_happy_circle":
                    spawnVillagerHappyCircleParticles(player, location);
                    break;
                case "flame":
                    spawnAroundBlock(player, location, Particle.SMALL_FLAME, null);
                    break;
                case "totem":
                    spawnAroundBlock(player, location, Particle.TOTEM_OF_UNDYING, null);
                    break;
                case "smoke":
                    spawnAroundBlock(player, location, Particle.SMOKE, null);
                    break;
                case "explosion":
                    spawnAroundBlock(player, location, Particle.EXPLOSION, null);
                    break;
                case "mycelium":
                    spawnAroundBlock(player, location, Particle.MYCELIUM, null);
                    break;
                default:
                    // Default behavior if the particle type is unknown
                    break;
            }
        }
    }

    // One packet per effect: the client scatters the particles itself around the
    // point it is given. The spreads are those of the box / ring the particles
    // used to be sent in, one packet each.

    /** Particles around a block; {@code data} is null for particles that take none. */
    private <T> void spawnAroundBlock(Player player, Location location, Particle particle, T data) {
        player.spawnParticle(particle, location.getX() + 0.5, location.getY() + 0.5, location.getZ() + 0.5,
                18, 0.4, 0.3, 0.4, data);
    }

    /** Particles around an NPC, over its whole height; {@code data} is null for particles that take none. */
    private <T> void spawnAroundNPC(Player player, Location location, Particle particle, T data) {
        location.add(0, 1.0, 0); // Shift upward to center on NPC's body (adjust as needed)
        player.spawnParticle(particle, location.getX(), location.getY(), location.getZ(),
                10, 0.35, 0.6, 0.35, data);
    }

    /** The dust of a "#RRGGBB" colour. */
    private static Particle.DustOptions dust(String colorHex) {
        Color color = Color.fromRGB(
                Integer.valueOf(colorHex.substring(1, 3), 16),
                Integer.valueOf(colorHex.substring(3, 5), 16),
                Integer.valueOf(colorHex.substring(5, 7), 16)
        );
        return new Particle.DustOptions(color, 1.0F);
    }

    public void spawnVillagerHappyCircleParticles(Player player, Location location) {
        int count = 15; // Total number of particles
        double radius = 0.5; // Distance from the center of the block to spawn particles

        for (int i = 0; i < count; i++) {
            double angle = Math.random() * Math.PI * 2; // Random angle for circular distribution
            double yOffset = Math.random() * 0.5; // Randomly offset particles a little above and below the center
            double xOffset = radius * Math.cos(angle)+0.5; // Calculate x offset
            double zOffset = radius * Math.sin(angle)+0.5; // Calculate z offset

            // Spawn the particle around the block, using the center of the block
            player.spawnParticle(Particle.HAPPY_VILLAGER, location.getX() + xOffset, location.getY() + yOffset, location.getZ() + zOffset, 1);
        }
    }

    public void spawnRedstoneParticleCircle(Player player, Location location, String colorHex) {
        int count = 15; // Total number of particles
        double radius = 0.5; // Distance from the center of the block to spawn particles

        Particle.DustOptions dustOptions = dust(colorHex);

        for (int i = 0; i < count; i++) {
            double angle = Math.random() * Math.PI * 2; // Random angle for circular distribution
            double yOffset = Math.random() * 0.5; // Randomly offset particles a little above and below the center
            double xOffset = radius * Math.cos(angle)+0.5; // Calculate x offset
            double zOffset = radius * Math.sin(angle)+0.5; // Calculate z offset

            // Spawn the particle around the block, using the center of the block
            player.spawnParticle(Particle.DUST,
                    location.getX() + xOffset,
                    location.getY() + yOffset,
                    location.getZ() + zOffset,
                    1, dustOptions);
        }
    }
}
