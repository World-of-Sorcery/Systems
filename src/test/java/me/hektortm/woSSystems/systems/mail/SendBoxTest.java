package me.hektortm.woSSystems.systems.mail;

import me.hektortm.woSSystems.systems.mail.model.MailAttachment;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit tests for {@link SendBox}: a package is sent once, or every item goes back to the sender. */
class SendBoxTest {

    private static final UUID SENDER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID RECIPIENT = UUID.fromString("22222222-3333-4444-5555-666666666666");

    private final List<List<MailAttachment>> sent = new ArrayList<>();
    private final List<ItemStack> givenBack = new ArrayList<>();
    private final List<String> notices = new ArrayList<>();
    private String answer; // the API's error code, null = sent
    private long now = 1_000_000;
    private SendBox box;
    private Player player;

    @BeforeEach
    void setUp() {
        box = new SendBox(
                (recipient, sender, subject, body, items) -> {
                    sent.add(items);
                    return CompletableFuture.completedFuture(answer);
                },
                stack -> MailAttachment.stack("DATA", new MailAttachment.Summary(stack.getType().name().toLowerCase(), stack.getAmount(), "", "")),
                stack -> stack.getType() == Material.BARRIER, // the test's "unstashable" flag
                (p, items) -> givenBack.addAll(items),
                Runnable::run,
                (p, key, replacements) -> notices.add(key),
                () -> now);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(SENDER);
        when(player.getName()).thenReturn("Alex");
        when(player.isOnline()).thenReturn(true);
    }

    private static ItemStack stack(Material m, int amount) {
        ItemStack s = mock(ItemStack.class);
        when(s.getType()).thenReturn(m);
        when(s.getAmount()).thenReturn(amount);
        return s;
    }

    @Test
    void sendsOnceAndStartsTheCooldown() {
        box.hold(player, RECIPIENT, "Steve", List.of(stack(Material.DIAMOND, 3)));
        box.submit(player, "Gift", "for you");
        assertThat(sent).hasSize(1);
        assertThat(sent.get(0)).hasSize(1);
        assertThat(notices).containsExactly("send.done");
        assertThat(givenBack).isEmpty();
        box.submit(player, "Again", ""); // nothing held any more
        assertThat(sent).hasSize(1);
        box.hold(player, RECIPIENT, "Steve", List.of(stack(Material.DIAMOND, 1)));
        now += 10_000;
        box.submit(player, "Too soon", "");
        assertThat(notices).endsWith("send.cooldown");
        assertThat(givenBack).hasSize(1);
    }

    @Test
    void returnsItemsOnCancel() {
        ItemStack a = stack(Material.DIAMOND, 3);
        box.hold(player, RECIPIENT, "Steve", List.of(a));
        box.cancel(player);
        assertThat(givenBack).containsExactly(a);
        assertThat(sent).isEmpty();
        box.cancel(player); // twice gives nothing twice
        assertThat(givenBack).hasSize(1);
    }

    @Test
    void returnsItemsOnFull() {
        answer = "mailbox_full";
        ItemStack a = stack(Material.DIAMOND, 3);
        box.hold(player, RECIPIENT, "Steve", List.of(a));
        box.submit(player, "Gift", "");
        assertThat(givenBack).containsExactly(a);
        assertThat(notices).containsExactly("send.full");
    }

    @Test
    void refusesUnstashableAtSend() {
        ItemStack bad = stack(Material.BARRIER, 1);
        ItemStack ok = stack(Material.DIAMOND, 1);
        box.hold(player, RECIPIENT, "Steve", List.of(ok, bad));
        box.submit(player, "Gift", "");
        assertThat(sent).isEmpty();
        assertThat(givenBack).containsExactly(ok, bad);
        assertThat(notices).containsExactly("send.unstashable");
    }

    @Test
    void undroppableIsSent() {
        // Undroppable items may be sent (the owner's choice): only the unstashable flag refuses.
        box.hold(player, RECIPIENT, "Steve", List.of(stack(Material.NETHERITE_SWORD, 1)));
        box.submit(player, "Gift", "");
        assertThat(sent).hasSize(1);
    }

    @Test
    void returnAllGivesBackEveryHeldPackage() {
        // The server stops while a package waits for its dialog: nothing may stay in memory.
        ItemStack a = stack(Material.DIAMOND, 3);
        box.hold(player, RECIPIENT, "Steve", List.of(a));
        box.returnAll(uuid -> uuid.equals(SENDER) ? player : null);
        assertThat(givenBack).containsExactly(a);
        box.returnAll(uuid -> player);
        assertThat(givenBack).hasSize(1);
    }

    @Test
    void emptyPackageSendsNothing() {
        box.hold(player, RECIPIENT, "Steve", List.of());
        box.submit(player, "Gift", "");
        assertThat(sent).isEmpty();
        assertThat(notices).containsExactly("send.empty");
    }
}
