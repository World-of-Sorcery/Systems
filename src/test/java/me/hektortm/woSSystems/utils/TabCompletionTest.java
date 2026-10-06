package me.hektortm.woSSystems.utils;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Unit tests for {@link TabCompletion}: what is suggested while a command is typed. */
class TabCompletionTest {

    private static SubCommand sub(String name, Permissions permission, TabArg... arguments) {
        return new SubCommand() {
            @Override public String getName() { return name; }
            @Override public Permissions getPermission() { return permission; }
            @Override public void execute(CommandSender sender, String[] args) { }
            @Override public List<TabArg> arguments() { return List.of(arguments); }
        };
    }

    private static CommandSender senderWith(String... permissions) {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(anyString())).thenAnswer(call -> List.of(permissions).contains(call.getArgument(0)));
        return sender;
    }

    private static Map<String, SubCommand> interaction() {
        Map<String, SubCommand> subs = new LinkedHashMap<>();
        subs.put("trigger", sub("trigger", Permissions.INTER_TRIGGER, TabArg.of("Alex", "Steve"), TabArg.content("interactions")));
        subs.put("bind", sub("bind", Permissions.INTER_BIND, TabArg.content("interactions")));
        subs.put("info", sub("info", Permissions.INTER_INFO, TabArg.of("npc", "block")));
        subs.put("help", sub("help", null));
        return subs;
    }

    @AfterEach
    void forgetTheContent() {
        TabCompletion.contentSource(type -> List.of());
    }

    @Test
    void theFirstWordIsASubCommandTheSenderMayUse() {
        CommandSender sender = senderWith("interaction.bind", "interaction.info");

        assertThat(TabCompletion.complete(interaction(), sender, new String[]{""}))
                .containsExactly("bind", "help", "info");
        assertThat(TabCompletion.complete(interaction(), sender, new String[]{"B"})).containsExactly("bind");
    }

    @Test
    void laterWordsComeFromTheSubCommandsArguments() {
        TabCompletion.contentSource(type -> type.equals("interactions") ? List.of("shop_keeper", "bank", "shop_door") : List.of());
        CommandSender sender = senderWith("interaction.trigger", "interaction.info");

        assertThat(TabCompletion.complete(interaction(), sender, new String[]{"trigger", "st"})).containsExactly("Steve");
        assertThat(TabCompletion.complete(interaction(), sender, new String[]{"trigger", "Steve", "shop"}))
                .containsExactly("shop_door", "shop_keeper");
        assertThat(TabCompletion.complete(interaction(), sender, new String[]{"INFO", ""})).containsExactly("block", "npc");
    }

    @Test
    void nothingIsSuggestedPastTheLastArgumentOrForAnUnknownSubCommand() {
        CommandSender sender = senderWith("interaction.info");

        assertThat(TabCompletion.complete(interaction(), sender, new String[]{"info", "npc", ""})).isEmpty();
        assertThat(TabCompletion.complete(interaction(), sender, new String[]{"nope", ""})).isEmpty();
    }

    @Test
    void aSubCommandWithoutPermissionSuggestsNothing() {
        TabCompletion.contentSource(type -> List.of("bank"));

        assertThat(TabCompletion.complete(interaction(), senderWith(), new String[]{"bind", ""})).isEmpty();
    }

    @Test
    void aCommandWithoutSubCommandsCompletesItsOwnArguments() {
        TabCompletion.contentSource(type -> type.equals("citems") ? List.of("wand", "wizard_hat") : List.of());
        List<TabArg> cgive = List.of(TabArg.of("Alex"), TabArg.content("citems"), TabArg.NONE);

        assertThat(TabCompletion.complete(cgive, senderWith(), new String[]{"Alex", "wa"})).containsExactly("wand");
        assertThat(TabCompletion.complete(cgive, senderWith(), new String[]{"Alex", "wand", ""})).isEmpty();
    }

    @Test
    void aLongListIsCut() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < TabCompletion.MAX_SUGGESTIONS + 20; i++) many.add("item_" + i);

        assertThat(TabCompletion.matching(many, "item")).hasSize(TabCompletion.MAX_SUGGESTIONS);
    }
}
