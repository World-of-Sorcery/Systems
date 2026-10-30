package me.hektortm.woSSystems.systems.magic.cast;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;

/**
 * The WorldGuard flag {@code wos-spells} (allow / deny; allowed where unset): deny forbids
 * casting in a region such as spawn (magic spec §8). Registered in {@code onLoad}.
 */
public final class MagicFlags {

    @Nullable
    private static StateFlag spells;

    private MagicFlags() { }

    public static void register(FlagRegistry registry) {
        try {
            StateFlag flag = new StateFlag("wos-spells", true);
            registry.register(flag);
            spells = flag;
        } catch (FlagConflictException e) {
            Flag<?> existing = registry.get("wos-spells");
            if (existing instanceof StateFlag s) spells = s;
            else Bukkit.getLogger().warning("[Magic] the WorldGuard flag wos-spells exists with another type");
        }
    }

    /** Whether the player may cast where they stand. */
    public static boolean castAllowed(Player p) {
        StateFlag flag = spells;
        if (flag == null) return true;
        return WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery()
                .testState(BukkitAdapter.adapt(p.getLocation()), WorldGuardPlugin.inst().wrapPlayer(p), flag);
    }
}
