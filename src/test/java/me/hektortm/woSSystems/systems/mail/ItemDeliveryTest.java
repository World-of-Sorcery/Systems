package me.hektortm.woSSystems.systems.mail;

import me.hektortm.woSSystems.systems.mail.model.MailAttachment;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for {@link ItemDelivery} and {@link Stacks#summary}: what doesn't fit goes to the mail. */
class ItemDeliveryTest {

    private record Sent(UUID player, String reason, List<MailAttachment> items) {}

    private final List<Sent> sent = new ArrayList<>();
    private final List<String> notices = new ArrayList<>();
    private boolean apiUp = true;

    private ItemDelivery delivery() {
        return new ItemDelivery(
                (uuid, reason, items) -> {
                    sent.add(new Sent(uuid, reason, items));
                    return CompletableFuture.completedFuture(apiUp);
                },
                stack -> MailAttachment.stack("DATA", new MailAttachment.Summary("dirt", stack.getAmount(), "", "")),
                stack -> null,
                Runnable::run,
                (player, key, count) -> notices.add(key + ":" + count),
                Logger.getAnonymousLogger());
    }

    private static ItemStack stack(int amount) {
        ItemStack s = mock(ItemStack.class);
        when(s.getAmount()).thenReturn(amount);
        when(s.getType()).thenReturn(Material.DIRT);
        return s;
    }

    private static Player player(PlayerInventory inv, World world) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.fromString("11111111-2222-3333-4444-555555555555"));
        when(p.getInventory()).thenReturn(inv);
        when(p.isOnline()).thenReturn(true);
        when(p.getWorld()).thenReturn(world);
        when(p.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        return p;
    }

    @Test
    void restGoesToMail() {
        PlayerInventory inv = mock(PlayerInventory.class);
        ItemStack rest = stack(5);
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        leftover.put(0, rest);
        when(inv.addItem(any(ItemStack[].class))).thenReturn(leftover);
        World world = mock(World.class);
        delivery().give(player(inv, world), List.of(stack(10)), "Loot table: chest");
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).reason()).isEqualTo("Loot table: chest");
        assertThat(sent.get(0).items()).hasSize(1);
        assertThat(sent.get(0).items().get(0).amount()).isEqualTo(5);
        assertThat(notices).containsExactly("overflow.notice:1");
        verify(world, never()).dropItemNaturally(any(), any());
    }

    @Test
    void obtainedCountsWhatWentIn() {
        // Bukkit hands back the same stack with its amount lowered to what was left, so
        // the amounts must be the ones from before addItem.
        ItemStack whole = stack(10), part = stack(4), none = stack(3), plain = stack(1);
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        leftover.put(1, part);  // 6 of 10 fitted
        leftover.put(2, none);  // 0 of 3 fitted
        List<ItemStack> stacks = List.of(whole, part, none, plain);
        assertThat(ItemDelivery.obtained(stacks, new int[]{10, 10, 3, 1}, leftover,
                s -> s == whole ? "ruby" : s == part ? "opal" : s == none ? "jade" : null))
                .containsExactly("ruby", "opal");
    }

    @Test
    void nothingToMailWhenEverythingFits() {
        PlayerInventory inv = mock(PlayerInventory.class);
        when(inv.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());
        delivery().give(player(inv, mock(World.class)), List.of(stack(10)), "x");
        assertThat(sent).isEmpty();
        assertThat(notices).isEmpty();
    }

    @Test
    void anOfflinePlayerGetsEverythingByMail() {
        // A player who left (a claim answered after they quit, a package given back): their
        // inventory object is no longer saved, so everything goes to the mailbox.
        PlayerInventory inv = mock(PlayerInventory.class);
        Player p = player(inv, mock(World.class));
        when(p.isOnline()).thenReturn(false);
        delivery().give(p, List.of(stack(10), stack(3)), "Mail: Gift");
        verify(inv, never()).addItem(any(ItemStack[].class));
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0).items()).hasSize(2);
    }

    @Test
    void dropsWhenApiDown() {
        apiUp = false;
        PlayerInventory inv = mock(PlayerInventory.class);
        ItemStack rest = stack(5);
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        leftover.put(0, rest);
        when(inv.addItem(any(ItemStack[].class))).thenReturn(leftover);
        World world = mock(World.class);
        delivery().give(player(inv, world), List.of(stack(10)), "x");
        verify(world).dropItemNaturally(any(), any());
        assertThat(notices).containsExactly("overflow.dropped:1");
    }

    @Test
    void summaryStripsColours() {
        ItemStack s = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(s.getType()).thenReturn(Material.DIAMOND_SWORD);
        when(s.getAmount()).thenReturn(1);
        when(s.hasItemMeta()).thenReturn(true);
        when(s.getItemMeta()).thenReturn(meta);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn("§6Excali§lbur");
        MailAttachment.Summary sum = Stacks.summary(s, "excalibur");
        assertThat(sum.material()).isEqualTo("diamond_sword");
        assertThat(sum.name()).isEqualTo("Excalibur");
        assertThat(sum.citem()).isEqualTo("excalibur");
        assertThat(sum.amount()).isEqualTo(1);
    }
}
