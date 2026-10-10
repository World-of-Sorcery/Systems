package me.hektortm.woSSystems.systems.guis;

import me.hektortm.woSSystems.utils.model.GUICheck;
import me.hektortm.woSSystems.utils.model.GUIItemBehaviour;
import me.hektortm.woSSystems.utils.model.GUISlotConfig;
import me.hektortm.woSSystems.utils.types.CheckType;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link GuiRules}: click lists, post-use, fluid layout, requirements, looks. */
class GuiRulesTest {

    private static GUIItemBehaviour behaviour(String costCurrency, int cost, GUIItemBehaviour.Trade trade,
                                              List<String> shiftRight, List<String> drop) {
        return new GUIItemBehaviour(null, null, true, "citem", costCurrency, cost, true, null, List.of(), List.of("shift-left"), shiftRight, drop, trade, true, "default", null);
    }

    private static GuiRules.PlayerState player(int freeSlots, Map<String, Integer> citems, Map<String, Long> balances) {
        return new GuiRules.PlayerState() {
            @Override public int freeSlots() { return freeSlots; }
            @Override public int citemCount(String id) { return citems.getOrDefault(id, 0); }
            @Override public long balance(String currency) { return balances.getOrDefault(currency, 0L); }
        };
    }

    private static GUISlotConfig config(List<String> global, List<String> left, List<String> right, GUIItemBehaviour b) {
        return new GUISlotConfig("shop", 0, 4, "0", "ALWAYS", 1, true, "STONE", "Stone", "[]", null, null, null, false,
                null, global, right, left, false, null, List.of(), List.of(), b);
    }

    @Nested
    class ClickLists {
        private final GUISlotConfig item = config(List.of("any"), List.of("left"), List.of(),
                behaviour(null, 0, GUIItemBehaviour.Trade.NONE, List.of("shift"), List.of("drop")));

        @Test
        void eachClickTypeRunsItsOwnList() {
            assertThat(GuiRules.actionsFor(ClickType.LEFT, item)).containsExactly("left");
            assertThat(GuiRules.actionsFor(ClickType.SHIFT_LEFT, item)).containsExactly("shift-left");
            assertThat(GuiRules.actionsFor(ClickType.SHIFT_RIGHT, item)).containsExactly("shift");
            assertThat(GuiRules.actionsFor(ClickType.DROP, item)).containsExactly("drop");
            assertThat(GuiRules.actionsFor(ClickType.CONTROL_DROP, item)).containsExactly("drop");
        }

        @Test
        void aClickTypeWithoutAListRunsTheAnyClickList() {
            assertThat(GuiRules.actionsFor(ClickType.RIGHT, item)).containsExactly("any"); // right list is empty
            assertThat(GuiRules.actionsFor(ClickType.MIDDLE, item)).containsExactly("any");
        }
    }

    @Nested
    class PostUse {
        @Test
        void itemSettingWinsOverTheGuis() {
            assertThat(GuiRules.afterClick("close", null, "stay", null, 0, 3)).isInstanceOf(GuiRules.Close.class);
            assertThat(GuiRules.afterClick("gui", "bank", "stay", null, 0, 3)).isEqualTo(new GuiRules.OpenGui("bank"));
        }

        @Test
        void defaultUsesTheGuis() {
            assertThat(GuiRules.afterClick("default", null, "page", "2", 0, 3)).isEqualTo(new GuiRules.OpenPage(2));
            assertThat(GuiRules.afterClick("default", null, "stay", null, 1, 3)).isInstanceOf(GuiRules.Redraw.class);
        }

        @Test
        void nextAndPreviousStopAtTheEnds() {
            assertThat(GuiRules.afterClick("next", null, "stay", null, 1, 3)).isEqualTo(new GuiRules.OpenPage(2));
            assertThat(GuiRules.afterClick("next", null, "stay", null, 2, 3)).isInstanceOf(GuiRules.Redraw.class);
            assertThat(GuiRules.afterClick("previous", null, "stay", null, 1, 3)).isEqualTo(new GuiRules.OpenPage(0));
            assertThat(GuiRules.afterClick("previous", null, "stay", null, 0, 3)).isInstanceOf(GuiRules.Redraw.class);
        }

        @Test
        void aBadTargetRedrawsInstead() {
            assertThat(GuiRules.afterClick("page", "two", "stay", null, 0, 3)).isInstanceOf(GuiRules.Redraw.class);
            assertThat(GuiRules.afterClick("page", "7", "stay", null, 0, 3)).isInstanceOf(GuiRules.Redraw.class);
            assertThat(GuiRules.afterClick("gui", " ", "stay", null, 0, 3)).isInstanceOf(GuiRules.Redraw.class);
        }
    }

    @Nested
    class PageSound {
        private static final String FLIP = GuiRules.PAGE_TURN_SOUND;

        @Test
        void goingToAnotherPagePlaysTheGuisSound() {
            assertThat(GuiRules.pageSound(new GuiRules.OpenPage(1), FLIP, null)).isEqualTo(FLIP);
            assertThat(GuiRules.pageSound(new GuiRules.OpenPage(1), " wos:custom.flip ", " ")).isEqualTo("wos:custom.flip");
        }

        @Test
        void notWithoutASoundOrWhenTheItemHasItsOwn() {
            assertThat(GuiRules.pageSound(new GuiRules.OpenPage(1), "", null)).isNull();
            assertThat(GuiRules.pageSound(new GuiRules.OpenPage(1), null, null)).isNull();
            assertThat(GuiRules.pageSound(new GuiRules.OpenPage(1), FLIP, "minecraft:ui.button.click")).isNull();
        }

        @Test
        void notWhenTheClickStaysClosesOrOpensAnotherGui() {
            assertThat(GuiRules.pageSound(new GuiRules.Redraw(), FLIP, null)).isNull(); // also "next" on the last page
            assertThat(GuiRules.pageSound(new GuiRules.Close(), FLIP, null)).isNull();
            assertThat(GuiRules.pageSound(new GuiRules.OpenGui("bank"), FLIP, null)).isNull();
        }
    }

    @Nested
    class LiveRefresh {
        @Test
        void aPageWithACooldownPlaceholderIsRefreshed() {
            assertThat(GuiRules.showsCooldown(Arrays.asList(null, "§7Ready in {cooldowns.duration:daily}"))).isTrue();
            assertThat(GuiRules.showsCooldown(List.of("[\"{cooldowns.seconds:chest}\"]"))).isTrue();
        }

        @Test
        void otherPlaceholdersAreNot() {
            assertThat(GuiRules.showsCooldown(List.of("{stats.amount:kills}", "{cooldowns.duration}", "plain"))).isFalse();
            assertThat(GuiRules.showsCooldown(List.of())).isFalse();
        }
    }

    @Nested
    class PlayerView {
        private final GuiRules.After stay = new GuiRules.Redraw();

        @Test
        void aCommandThatOpensAGuiIsAMove() {
            assertThat(GuiRules.viewMove(List.of("send_message hi", "gui open @p bank"), "Xyz", stay))
                    .isEqualTo(new GuiRules.OpenGui("bank", 0));
            assertThat(GuiRules.viewMove(List.of("\"gui open xyz bank:2\""), "Xyz", stay)).isEqualTo(new GuiRules.OpenGui("bank", 2));
            assertThat(GuiRules.viewMove(List.of("gui open @p bank:two"), "Xyz", stay)).isEqualTo(new GuiRules.OpenGui("bank", 0));
        }

        @Test
        void closingIsAMove() {
            assertThat(GuiRules.viewMove(List.of("close_gui"), "Xyz", stay)).isInstanceOf(GuiRules.Close.class);
        }

        @Test
        void aCommandWinsOverThePostUse() {
            assertThat(GuiRules.viewMove(List.of("gui open @p bank"), "Xyz", new GuiRules.OpenPage(1)))
                    .isEqualTo(new GuiRules.OpenGui("bank", 0));
        }

        @Test
        void otherCommandsLeadWhereThePostUseDoes() {
            List<String> actions = List.of("eco give @p gold 5", "gui open Someone bank", "gui open @p", "cgive @p gui 1");
            assertThat(GuiRules.viewMove(actions, "Xyz", stay)).isSameAs(stay);
            assertThat(GuiRules.viewMove(actions, "Xyz", new GuiRules.OpenPage(1))).isEqualTo(new GuiRules.OpenPage(1));
            assertThat(GuiRules.viewMove(List.of(), "Xyz", stay)).isSameAs(stay);
        }
    }

    @Nested
    class Layout {
        @Test
        void staticKeepsEveryItemAtItsSlot() {
            assertThat(GuiRules.layout(List.of(13, 4, 22), 27, false)).isEqualTo(Map.of(4, 4, 13, 13, 22, 22));
        }

        @Test
        void fluidFillsFromTheFirstSlotInSlotOrder() {
            assertThat(GuiRules.layout(List.of(13, 4, 22), 27, true)).containsExactly(
                    Map.entry(0, 4), Map.entry(1, 13), Map.entry(2, 22));
        }

        @Test
        void onlyAsManyAsFit() {
            assertThat(GuiRules.layout(List.of(0, 1, 2, 30), 9, false)).containsOnlyKeys(0, 1, 2);
            assertThat(GuiRules.layout(List.of(5, 6, 7), 2, true)).containsOnlyKeys(0, 1);
        }
    }

    @Nested
    class Requirements {
        private final GUIItemBehaviour.Trade trade = new GUIItemBehaviour.Trade(
                List.of(new GUIItemBehaviour.Entry("citem", "bass", 3), new GUIItemBehaviour.Entry("currency", "gold", 20)),
                List.of(new GUIItemBehaviour.Entry("citem", "stew", 1)), false);

        @Test
        void costAndTradeAddUpPerCurrency() {
            GUIItemBehaviour b = behaviour("gold", 25, trade, List.of(), List.of());
            assertThat(GuiRules.charges(b)).isEqualTo(Map.of("currency:gold", 45L, "citem:bass", 3L));
            assertThat(GuiRules.firstUnmet(List.of(), b, player(0, Map.of("bass", 3), Map.of("gold", 44L))))
                    .isEqualTo(new GuiRules.Unmet("currency", "gold", 45));
            assertThat(GuiRules.firstUnmet(List.of(), b, player(0, Map.of("bass", 3), Map.of("gold", 45L)))).isNull();
        }

        @Test
        void checksComeFirst() {
            GUIItemBehaviour b = behaviour("gold", 25, trade, List.of(), List.of());
            List<GUICheck> checks = List.of(new GUICheck(CheckType.INVENTORY, null, 2), new GUICheck(CheckType.CITEM, "key", 1));
            assertThat(GuiRules.firstUnmet(checks, b, player(1, Map.of(), Map.of())))
                    .isEqualTo(new GuiRules.Unmet("inventory", null, 2));
            assertThat(GuiRules.firstUnmet(checks, b, player(2, Map.of(), Map.of())))
                    .isEqualTo(new GuiRules.Unmet("citem", "key", 1));
        }

        @Test
        void aFreeItemWithoutChecksNeedsNothing() {
            assertThat(GuiRules.firstUnmet(List.of(), GUIItemBehaviour.PLAIN, player(0, Map.of(), Map.of()))).isNull();
        }
    }

    @Nested
    class Looks {
        @Test
        void aTextureUrlBecomesTheSkinValue() {
            String value = GuiRules.skinTexture(" https://textures.minecraft.net/texture/abc ");
            String json = new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
            assertThat(json).isEqualTo("{\"textures\":{\"SKIN\":{\"url\":\"https://textures.minecraft.net/texture/abc\"}}}");
        }

        @Test
        void aBase64ValueIsKept() {
            assertThat(GuiRules.skinTexture("eyJ0ZXh0dXJlcyI6e319")).isEqualTo("eyJ0ZXh0dXJlcyI6e319");
        }

        @Test
        void aHeadIsAPlayerNameOrATexture() {
            assertThat(GuiRules.headSkin(" HektorTM ")).isEqualTo(new GuiRules.Owner("HektorTM"));
            assertThat(GuiRules.headSkin("Notch_2")).isEqualTo(new GuiRules.Owner("Notch_2"));
            assertThat(GuiRules.headSkin("https://textures.minecraft.net/texture/abc"))
                    .isEqualTo(new GuiRules.Texture(GuiRules.skinTexture("https://textures.minecraft.net/texture/abc")));
            assertThat(GuiRules.headSkin("eyJ0ZXh0dXJlcyI6e319")).isEqualTo(new GuiRules.Texture("eyJ0ZXh0dXJlcyI6e319"));
            assertThat(GuiRules.headSkin("  ")).isNull();
            assertThat(GuiRules.headSkin(null)).isNull();
        }

        @Test
        void anUnfilledPlaceholderIsNotAName() {
            // {player_name} with no player stays as written: braces, so a texture value (shows no skin), not a lookup.
            assertThat(GuiRules.headSkin("{player_name}")).isInstanceOf(GuiRules.Texture.class);
        }

        private static final GuiRules.PriceFormats FORMATS = new GuiRules.PriceFormats(
                "Cost: %amount% %currency%", "Cost (short): %amount% %currency%", "Price:", "+ %entry%", "- %entry%", "You get:", "> %entry%");

        @Test
        void whatTheTradeGivesIsAddedWhenShown() {
            List<GUIItemBehaviour.Entry> give = List.of(new GUIItemBehaviour.Entry("citem", "stew", 1), new GUIItemBehaviour.Entry("currency", "gems", 5));
            GUIItemBehaviour shown = behaviour(null, 0, new GUIItemBehaviour.Trade(shownTrade.take(), give, true, true), List.of(), List.of());
            assertThat(GuiRules.loreWithPrice(List.of("Stew"), shown, FORMATS, rich, NAMES, CURRENCIES))
                    .containsExactly("Stew", "", "Price:", "+ 3× Fresh Bass", "+ 20 gold", "You get:", "> 1× stew", "> 5 gems");
            // Shown on its own, without the price.
            GUIItemBehaviour only = behaviour(null, 0, new GUIItemBehaviour.Trade(shownTrade.take(), give, false, true), List.of(), List.of());
            assertThat(GuiRules.loreWithPrice(List.of(), only, FORMATS, rich, NAMES, CURRENCIES)).containsExactly("You get:", "> 1× stew", "> 5 gems");
            GUIItemBehaviour hidden = behaviour(null, 0, new GUIItemBehaviour.Trade(shownTrade.take(), give, false, false), List.of(), List.of());
            assertThat(GuiRules.loreWithPrice(List.of("Stew"), hidden, FORMATS, rich, NAMES, CURRENCIES)).containsExactly("Stew");
        }
        /** Custom items are listed by name; one without a name by its id. */
        private static final java.util.function.UnaryOperator<String> NAMES = id -> id.equals("bass") ? "Fresh Bass" : id;
        /** Currencies are listed by name too; one without a name by its id. */
        private static final java.util.function.UnaryOperator<String> CURRENCIES = id -> id.equals("silver") ? "Silver Coins" : id;
        private final GuiRules.PlayerState rich = player(0, Map.of("bass", 3), Map.of("gold", 100L));

        @Test
        void aCurrencyIsListedByItsNameNotItsId() {
            GUIItemBehaviour.Trade trade = new GUIItemBehaviour.Trade(
                    List.of(new GUIItemBehaviour.Entry("currency", "silver", 5)),
                    List.of(new GUIItemBehaviour.Entry("currency", "silver", 2)), true, true);
            GUIItemBehaviour shown = behaviour("silver", 25, trade, List.of(), List.of());
            assertThat(GuiRules.loreWithPrice(List.of(), shown, FORMATS, rich, NAMES, CURRENCIES))
                    .anyMatch(line -> line.endsWith("25 Silver Coins"))
                    .anyMatch(line -> line.endsWith("5 Silver Coins"))
                    .anyMatch(line -> line.endsWith("2 Silver Coins"))
                    .noneMatch(line -> line.contains("silver"));
        }

        @Test
        void theCostLineIsAddedOnlyWhenShown() {
            GUIItemBehaviour shown = behaviour("gold", 25, GUIItemBehaviour.Trade.NONE, List.of(), List.of());
            assertThat(GuiRules.loreWithPrice(List.of("Fresh fish"), shown, FORMATS, rich, NAMES, CURRENCIES)).containsExactly("Fresh fish", "", "Cost: 25 gold");
            assertThat(GuiRules.loreWithPrice(List.of(), shown, FORMATS, rich, NAMES, CURRENCIES)).containsExactly("Cost: 25 gold");
            assertThat(GuiRules.loreWithPrice(List.of("x"), GUIItemBehaviour.PLAIN, FORMATS, rich, NAMES, CURRENCIES)).containsExactly("x");
        }

        @Test
        void theCostLineSaysWhenThePlayerCantPay() {
            GUIItemBehaviour shown = behaviour("gold", 25, GUIItemBehaviour.Trade.NONE, List.of(), List.of());
            assertThat(GuiRules.loreWithPrice(List.of(), shown, FORMATS, player(0, Map.of(), Map.of("gold", 24L)), NAMES, CURRENCIES))
                    .containsExactly("Cost (short): 25 gold");
        }

        private final GUIItemBehaviour.Trade shownTrade = new GUIItemBehaviour.Trade(
                List.of(new GUIItemBehaviour.Entry("citem", "bass", 3), new GUIItemBehaviour.Entry("currency", "gold", 20)), List.of(), true);

        @Test
        void theTradePriceIsAddedWhenShown() {
            GUIItemBehaviour both = behaviour("gold", 25, shownTrade, List.of(), List.of());
            assertThat(GuiRules.loreWithPrice(List.of("Stew"), both, FORMATS, rich, NAMES, CURRENCIES))
                    .containsExactly("Stew", "", "Cost: 25 gold", "Price:", "+ 3× Fresh Bass", "+ 20 gold");
            GUIItemBehaviour hidden = behaviour(null, 0, new GUIItemBehaviour.Trade(shownTrade.take(), List.of(), false), List.of(), List.of());
            assertThat(GuiRules.loreWithPrice(List.of("Stew"), hidden, FORMATS, rich, NAMES, CURRENCIES)).containsExactly("Stew");
        }

        @Test
        void eachPriceEntrySaysWhetherThePlayerHasIt() {
            GUIItemBehaviour trade = behaviour(null, 0, shownTrade, List.of(), List.of());
            assertThat(GuiRules.loreWithPrice(List.of(), trade, FORMATS, player(0, Map.of("bass", 2), Map.of("gold", 20L)), NAMES, CURRENCIES))
                    .containsExactly("Price:", "- 3× Fresh Bass", "+ 20 gold");
        }

        @Test
        void aCurrencyChargedTwiceNeedsTheTotal() {
            // Cost 25 + trade 20: with 30 gold neither line is payable, as the click would be refused.
            GUIItemBehaviour both = behaviour("gold", 25, shownTrade, List.of(), List.of());
            assertThat(GuiRules.loreWithPrice(List.of(), both, FORMATS, player(0, Map.of("bass", 3), Map.of("gold", 30L)), NAMES, CURRENCIES))
                    .containsExactly("Cost (short): 25 gold", "Price:", "+ 3× Fresh Bass", "- 20 gold");
        }

        @Test
        void aCustomItemsLoreIsItsTheConfigsOrBoth() {
            List<String> item = List.of("Caught at dawn"), config = List.of("Click to buy");
            assertThat(GuiRules.citemLore(item, config, "citem")).containsExactly("Caught at dawn");
            assertThat(GuiRules.citemLore(item, config, "config")).containsExactly("Click to buy");
            assertThat(GuiRules.citemLore(item, config, "both")).containsExactly("Caught at dawn", "Click to buy");
        }

        @Test
        void aCustomItemKeepsItsNameUnlessSetOtherwise() {
            assertThat(GuiRules.citemName("Bass", "Fresh bass", true)).isEqualTo("Bass");
            assertThat(GuiRules.citemName("Bass", "Fresh bass", false)).isEqualTo("Fresh bass");
            assertThat(GuiRules.citemName("Bass", " ", false)).isEqualTo("Bass"); // no config name to use
        }
    }

    @Nested
    class Keys {
        @Test
        void hideOrHiddenHidesTheTooltip() {
            assertThat(GuiRules.hidesTooltip("hide")).isTrue();
            assertThat(GuiRules.hidesTooltip("Hidden")).isTrue();
            assertThat(GuiRules.hidesTooltip("tooltip/gold")).isFalse();
            assertThat(GuiRules.hidesTooltip(null)).isFalse();
        }

        @Test
        void bareKeysUseTheDefaultNamespace() {
            assertThat(GuiRules.resourceKey("gui/coin", "wos")).isEqualTo(new GuiRules.Key("wos", "gui/coin"));
            assertThat(GuiRules.resourceKey("  Gold ", "minecraft")).isEqualTo(new GuiRules.Key("minecraft", "gold"));
        }

        @Test
        void keepsAWrittenNamespace() {
            assertThat(GuiRules.resourceKey("wos:tooltip/gold", "minecraft")).isEqualTo(new GuiRules.Key("wos", "tooltip/gold"));
        }

        @Test
        void rejectsBlankAndInvalidKeys() {
            assertThat(GuiRules.resourceKey(null, "wos")).isNull();
            assertThat(GuiRules.resourceKey(" ", "wos")).isNull();
            assertThat(GuiRules.resourceKey("{stats.amount:kills}", "wos")).isNull(); // an unfilled placeholder
            assertThat(GuiRules.resourceKey("two words", "wos")).isNull();
        }
    }
}
