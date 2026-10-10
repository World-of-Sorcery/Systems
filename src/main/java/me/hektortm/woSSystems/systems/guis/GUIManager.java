package me.hektortm.woSSystems.systems.guis;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.DataComponentTypes;
import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.debug.DebugFormat;
import me.hektortm.woSSystems.utils.ActionHandler;
import me.hektortm.woSSystems.utils.ConditionHandler;
import me.hektortm.woSSystems.utils.model.Condition;
import me.hektortm.woSSystems.utils.model.GUI;
import me.hektortm.woSSystems.utils.model.GUIItemBehaviour;
import me.hektortm.woSSystems.utils.model.GUIPage;
import me.hektortm.woSSystems.utils.model.GUISlot;
import me.hektortm.woSSystems.utils.model.GUISlotConfig;
import me.hektortm.wosCore.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static io.papermc.paper.datacomponent.item.DyedItemColor.dyedItemColor;
import static me.hektortm.woSSystems.utils.Parsers.hexToBukkitColor;

/**
 * Opens GUIs and draws them: each page's items (static at their slots, or fluid
 * in order without gaps), and keeps what each player has open so clicks map back
 * to the right item. Clicks themselves are handled by {@link GUIClickHandler}.
 */
public class GUIManager implements Listener {

    private final WoSSystems plugin = WoSSystems.getPlugin(WoSSystems.class);
    private final ConditionHandler conditions = plugin.getConditionHandler();
    private final DAOHub hub;
    private final ActionHandler actionHandler;
    private final GUIClickHandler clicks;
    private final HeadProfiles heads = new HeadProfiles(plugin);

    /**
     * What a player has open: the GUI, the page, which configured slot each
     * inventory slot shows, and the inventory itself (to tell it from a newer one).
     * {@code subject} is whose values it shows: the player themself, or in a
     * player view ({@code /gui playerview}) the player being looked at.
     */
    record OpenView(String guiId, int page, Map<Integer, Integer> slots, Inventory inventory, UUID subject) {
        /** True if {@code viewer} is looking at this GUI as another player sees it. */
        boolean isPlayerView(Player viewer) {
            return !subject.equals(viewer.getUniqueId());
        }
    }

    private final Map<UUID, OpenView> views = new ConcurrentHashMap<>();

    /**
     * Players whose GUI is being replaced by another screen of ours (redraw,
     * another page, the confirm screen): that close isn't a real close.
     */
    private final Set<UUID> switching = ConcurrentHashMap.newKeySet();

    public GUIManager(DAOHub hub) {
        this.hub = hub;
        actionHandler = new ActionHandler(hub);
        clicks = new GUIClickHandler(this, hub, actionHandler);
    }

    // ── Opening ─────────────────────────────────────────────────────────────────

    public void openGUI(Player player, String guiId) {
        openGUI(player, guiId, 0);
    }

    /** Opens a GUI (runs its open actions). */
    public void openGUI(Player player, String guiId, int pageIndex) {
        GUI gui = hub.getGuiDAO().getGUIbyId(guiId);
        if (gui == null || gui.getPages().isEmpty()) return;
        Inventory inventory = draw(player, player, gui, pageIndex);
        if (gui.getOpenActions() != null && !gui.getOpenActions().isEmpty()) {
            actionHandler.executeActions(player, gui.getOpenActions(), ActionHandler.SourceType.GUI, guiId, null, "on open");
        }
        player.openInventory(inventory);
    }

    /**
     * Opens a GUI for {@code viewer} as {@code subject} sees it (their
     * placeholders, conditions, prices). Its open and close actions don't run,
     * and clicks only move around (see {@link GUIClickHandler}).
     */
    public void openView(Player viewer, Player subject, String guiId, int pageIndex) {
        GUI gui = hub.getGuiDAO().getGUIbyId(guiId);
        if (gui == null || gui.getPages().isEmpty()) return;
        showPage(viewer, subject, gui, pageIndex);
    }

    /** Shows another page of the GUI, or redraws it: no open / close actions. */
    void showPage(Player player, GUI gui, int pageIndex) {
        showPage(player, player, gui, pageIndex);
    }

    void showPage(Player viewer, Player subject, GUI gui, int pageIndex) {
        Inventory inventory = draw(viewer, subject, gui, pageIndex);
        switchTo(viewer, () -> viewer.openInventory(inventory));
    }

    /** Runs {@code open} (which replaces the player's GUI screen) without it counting as a close. */
    void switchTo(Player player, Runnable open) {
        switching.add(player.getUniqueId());
        try {
            open.run();
        } finally {
            switching.remove(player.getUniqueId());
        }
    }

    /** Builds the page's inventory with {@code player}'s values and records it as the viewer's open view. */
    private Inventory draw(Player viewer, Player player, GUI gui, int pageIndex) {
        List<GUIPage> pages = gui.getPages();
        int page = Math.max(0, Math.min(pageIndex, pages.size() - 1));
        boolean raw = plugin.getDebugMode().isOn(viewer); // debug mode: texts as written
        Inventory inventory = Bukkit.createInventory(new GUIHolder(gui.getGuiId()), gui.getSize() * 9, new Text(player, raw).line(gui.getTitle()));

        Map<Integer, ItemStack> items = pageItems(player, pages.get(page), raw);
        Map<Integer, Integer> layout = GuiRules.layout(new ArrayList<>(items.keySet()), inventory.getSize(), gui.isFluid());
        layout.forEach((at, slot) -> inventory.setItem(at, items.get(slot)));

        views.put(viewer.getUniqueId(), new OpenView(gui.getGuiId(), page, layout, inventory, player.getUniqueId()));
        return inventory;
    }

    /** The items a page shows to {@code player}, by the slot they're configured at. */
    private Map<Integer, ItemStack> pageItems(Player player, GUIPage page, boolean raw) {
        Map<Integer, ItemStack> items = new HashMap<>();
        for (GUISlot slot : page.getSlots()) {
            if (!slot.isActive()) continue;
            GUISlotConfig config = resolveConfig(player, slot);
            if (config == null || !config.isVisible()) continue;
            items.put(slot.getSlot_id(), buildItem(config, player, raw));
        }
        return items;
    }

    // ── Live refresh ────────────────────────────────────────────────────────────

    /**
     * Starts the once-a-second refresh of open GUI pages that show a cooldown
     * placeholder, so the countdown runs without reopening.
     */
    public void startRefresh() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshCooldownPages, 20L, 20L);
    }

    private void refreshCooldownPages() {
        views.forEach((viewerId, view) -> {
            Player viewer = Bukkit.getPlayer(viewerId);
            // Not while another screen (the confirm screen) is in front of it.
            if (viewer == null || viewer.getOpenInventory().getTopInventory() != view.inventory()) return;
            Player subject = Bukkit.getPlayer(view.subject());
            GUI gui = hub.getGuiDAO().getGUIbyId(view.guiId());
            if (subject == null || gui == null || view.page() >= gui.getPages().size()) return;
            GUIPage page = gui.getPages().get(view.page());
            if (GuiRules.showsCooldown(pageTexts(page))) redrawInPlace(viewer, subject, gui, page, view);
        });
    }

    /**
     * Every text of a page that is drawn with placeholders: each config's name
     * and lore, and the name and lore of a custom item used as the look.
     */
    private List<String> pageTexts(GUIPage page) {
        List<String> texts = new ArrayList<>();
        for (GUISlot slot : page.getSlots()) {
            for (GUISlotConfig config : slot.getConfigs()) {
                texts.add(config.getDisplay_name());
                texts.add(config.getLore());
                String citemId = config.getBehaviour().citemId();
                if (citemId != null) texts.addAll(plugin.getCitemManager().templates(hub.getCitemDAO().getCitem(citemId)));
            }
        }
        return texts;
    }

    /**
     * Draws the page again into the inventory the viewer has open (no reopen,
     * no open / close actions); only items that changed are set.
     */
    private void redrawInPlace(Player viewer, Player subject, GUI gui, GUIPage page, OpenView view) {
        Inventory inventory = view.inventory();
        Map<Integer, ItemStack> items = pageItems(subject, page, plugin.getDebugMode().isOn(viewer));
        Map<Integer, Integer> layout = GuiRules.layout(new ArrayList<>(items.keySet()), inventory.getSize(), gui.isFluid());
        for (int at = 0; at < inventory.getSize(); at++) {
            Integer slot = layout.get(at);
            ItemStack item = slot == null ? null : items.get(slot);
            if (!Objects.equals(inventory.getItem(at), item)) inventory.setItem(at, item);
        }
        views.put(viewer.getUniqueId(), new OpenView(view.guiId(), view.page(), layout, inventory, view.subject()));
    }

    @Nullable
    OpenView viewOf(Player player) {
        return views.get(player.getUniqueId());
    }

    /** The first config whose conditions pass for this player, or null if none match. */
    @Nullable
    GUISlotConfig resolveConfig(Player player, GUISlot slot) {
        for (GUISlotConfig config : slot.getConfigs()) {
            List<Condition> conds = config.getConditions();
            if (conds.isEmpty()) return config;
            boolean passes;
            if ("one".equalsIgnoreCase(config.getMatchtype())) {
                passes = conds.stream().anyMatch(c -> conditions.evaluate(player, c, null));
            } else {
                passes = conditions.checkConditions(player, conds, null);
            }
            if (passes) return config;
        }
        return null;
    }

    // ── Events ──────────────────────────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof GUIClickHandler.ConfirmHolder confirm) {
            event.setCancelled(true);
            clicks.onConfirmClick(player, confirm, event.getRawSlot());
        } else if (holder instanceof GUIHolder) {
            event.setCancelled(true);
            clicks.onClick(player, event.getInventory(), event.getRawSlot(), event.getClick());
        }
    }

    /** Nothing can be dragged into a GUI or its confirm screen. */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof GUIHolder || holder instanceof GUIClickHandler.ConfirmHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        InventoryHolder holder = event.getInventory().getHolder();
        String guiId;
        if (holder instanceof GUIHolder h) guiId = h.getGuiId();
        else if (holder instanceof GUIClickHandler.ConfirmHolder c) guiId = c.guiId();
        else return;
        if (switching.contains(player.getUniqueId())) return;

        // Only forget the view this close is about (a newer GUI may already be open).
        OpenView view = views.get(player.getUniqueId());
        if (view != null && (view.inventory() == event.getInventory() || holder instanceof GUIClickHandler.ConfirmHolder)) {
            views.remove(player.getUniqueId());
            if (view.isPlayerView(player)) return; // only looked at: no close actions
        }

        GUI gui = hub.getGuiDAO().getGUIbyId(guiId);
        if (gui != null && gui.getCloseActions() != null && !gui.getCloseActions().isEmpty()) {
            actionHandler.executeActions(player, gui.getCloseActions(), ActionHandler.SourceType.GUI, gui.getGuiId(), null, "on close");
        }
    }

    /** A player view closes when the player being looked at leaves: their values are gone. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player gone = event.getPlayer();
        views.forEach((viewerId, view) -> {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || viewer.equals(gone) || !view.subject().equals(gone.getUniqueId())) return;
            viewer.closeInventory();
            Utils.info(viewer, "guis", "view.left", "%player%", gone.getName());
        });
    }

    // ── Items ───────────────────────────────────────────────────────────────────

    /**
     * The item a config shows to a player: a custom item or a material (head,
     * colour, lore, cost line …), with the placeholders in its name and lore filled in.
     */
    ItemStack buildItem(GUISlotConfig config, Player player) {
        return buildItem(config, player, plugin.getDebugMode().isOn(player));
    }

    /**
     * @param raw debug mode: the placeholders in names and lore stay as written,
     *            and a last lore line says which slot and config the item is
     */
    private ItemStack buildItem(GUISlotConfig config, Player player, boolean raw) {
        GUIItemBehaviour b = config.getBehaviour();
        ItemStack citem = b.citemId() == null ? null : hub.getCitemDAO().getCitem(b.citemId());
        if (!raw) citem = plugin.getCitemManager().personalize(citem, player);
        Text text = new Text(player, raw);
        ItemStack item = citem != null ? citemLook(citem, config, text) : materialLook(config, text);
        item.setAmount(Math.max(1, config.getAmount()));
        if (raw) addDebugLine(item, config);
        return item;
    }

    private void addDebugLine(ItemStack item, GUISlotConfig config) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        lore.add(DebugFormat.guiItem(config.getSlot_id(), config.getConfig_id()));
        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    /**
     * A custom item as the look: its model and data. Its name, unless set to use
     * the config's display name; its lore, the config's, or both, as set.
     */
    private ItemStack citemLook(ItemStack item, GUISlotConfig config, Text text) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        GUIItemBehaviour b = config.getBehaviour();
        String configName = config.getDisplay_name() == null ? null : text.line(config.getDisplay_name());
        String name = GuiRules.citemName(meta.hasDisplayName() ? meta.getDisplayName() : null, configName, b.citemName());
        if (name != null) meta.setDisplayName(name);

        List<String> configLore = text.lines(parseLore(config.getLore()));
        List<String> itemLore = meta.getLore() == null ? List.of() : meta.getLore();
        setLore(meta, GuiRules.citemLore(itemLore, configLore, b.citemLore()), b, text.player);
        applyTooltip(meta, config, text); // set on the config: overrides the custom item's
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack materialLook(GUISlotConfig config, Text text) {
        Material material = config.getMaterial() == null ? null : Material.getMaterial(config.getMaterial().toUpperCase());
        if (material == null) {
            plugin.writeLog("GUIManager", Level.WARNING, "GUI " + config.getGui_id() + ": unknown material '" + config.getMaterial() + "', showing PAPER");
            material = Material.PAPER;
        }
        ItemStack item = new ItemStack(material);

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta instanceof SkullMeta skull) applySkin(skull, config, text);
            applyMeta(meta, config, text);
            item.setItemMeta(meta);
        }

        if (config.getColor() != null && !config.getColor().isBlank()) {
            // A colour that isn't #RRGGBB is left out: the GUI must still open.
            Color color = hexToBukkitColor(config.getColor().trim());
            if (color == null) {
                plugin.writeLog("GUIManager", Level.WARNING, "GUI " + config.getGui_id() + ": invalid color '" + config.getColor() + "', ignored");
            } else {
                item.setData(DataComponentTypes.DYED_COLOR, dyedItemColor(color));
            }
        }
        return item;
    }

    /**
     * A player head's skin: the head value (placeholders filled in) is a texture
     * URL / base64 value or a player name ({player_name}: the viewer's own head);
     * without one, the legacy base64 in model.
     */
    private void applySkin(SkullMeta meta, GUISlotConfig config, Text text) {
        String head = config.getBehaviour().headTexture();
        GuiRules.HeadSkin skin = head != null ? GuiRules.headSkin(text.plain(head))
                : config.getModel() == null || config.getModel().isBlank() ? null : new GuiRules.Texture(config.getModel().trim());
        if (skin instanceof GuiRules.Owner owner) {
            meta.setPlayerProfile(heads.profile(owner.name()));
        } else if (skin instanceof GuiRules.Texture texture) {
            // The same texture gives the same profile, so a redrawn head counts as unchanged.
            PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(texture.value().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            profile.setProperty(new ProfileProperty("textures", texture.value()));
            meta.setPlayerProfile(profile);
        }
    }

    private void applyMeta(ItemMeta meta, GUISlotConfig config, Text text) {
        if (config.getDisplay_name() != null) {
            meta.setDisplayName(text.line(config.getDisplay_name()));
        }
        setLore(meta, text.lines(parseLore(config.getLore())), config.getBehaviour(), text.player);

        // A player head's model field held its skin before head textures; other items use it as the item model.
        if (!(meta instanceof SkullMeta)) {
            NamespacedKey model = key(config, "model", text.plain(config.getModel()), "wos");
            if (model != null) meta.setItemModel(model);
        }
        applyTooltip(meta, config, text);
        if (config.isEnchanted()) meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                ItemFlag.HIDE_DESTROYS,
                ItemFlag.HIDE_ARMOR_TRIM,
                ItemFlag.HIDE_PLACED_ON,
                ItemFlag.HIDE_STORED_ENCHANTS,
                ItemFlag.HIDE_DYE);
    }

    /** "hide" (or "hidden") hides the tooltip; anything else is a tooltip style ("ns:key", else minecraft:key). */
    private void applyTooltip(ItemMeta meta, GUISlotConfig config, Text text) {
        String tooltip = text.plain(config.getTooltip());
        if (GuiRules.hidesTooltip(tooltip)) {
            meta.setHideTooltip(true);
            return;
        }
        NamespacedKey style = key(config, "tooltip", tooltip, "minecraft");
        if (style != null) meta.setTooltipStyle(style);
    }

    /** The value as a resource key, or null (with a warning if it was set but isn't a valid key). */
    @Nullable
    private NamespacedKey key(GUISlotConfig config, String field, @Nullable String value, String defaultNamespace) {
        GuiRules.Key key = GuiRules.resourceKey(value, defaultNamespace);
        if (key == null) {
            if (value != null && !value.isBlank()) {
                plugin.writeLog("GUIManager", Level.WARNING, "GUI " + config.getGui_id() + ": invalid " + field + " '" + value + "', ignored");
            }
            return null;
        }
        return new NamespacedKey(key.namespace(), key.path());
    }

    /**
     * Colours and fills in the placeholders of an item's text, for one player.
     * In debug mode ({@code asWritten}) the texts a player reads keep their
     * placeholders; what decides the item's look is still filled in.
     */
    private final class Text {
        private final Player player;
        private final boolean asWritten;

        Text(Player player, boolean asWritten) {
            this.player = player;
            this.asWritten = asWritten;
        }

        /** Placeholders only, no colours (a model or tooltip key, a head). */
        String plain(@Nullable String raw) {
            return plugin.getPlaceholderResolver().resolvePlaceholders(raw, player);
        }

        String line(String raw) {
            return Utils.parseColorCodeString(asWritten ? raw : plugin.getPlaceholderResolver().resolvePlaceholders(raw, player));
        }

        /** A lore value with line breaks (like {citems.lore:id}) becomes several lines. */
        List<String> lines(List<String> raw) {
            List<String> lines = new ArrayList<>(asWritten ? raw : plugin.getPlaceholderResolver().resolveLines(raw, player));
            lines.replaceAll(Utils::parseColorCodeString);
            return lines;
        }
    }

    /** Sets the lore, with the cost / trade price lines the item shows. */
    private void setLore(ItemMeta meta, List<String> lore, GUIItemBehaviour b, Player player) {
        GuiRules.PriceFormats formats = new GuiRules.PriceFormats(message("cost"), message("cost-unmet"),
                message("price-header"), message("price-entry"), message("price-entry-unmet"),
                message("reward-header"), message("reward-entry"));
        List<String> withPrice = GuiRules.loreWithPrice(lore, b, formats, clicks.playerState(player), id -> citemName(id, player), this::currencyName);
        meta.setLore(withPrice.isEmpty() ? null : withPrice);
    }

    /**
     * The name a custom item is listed by in a price line: its display name
     * (placeholders filled in) without its colours, so the line keeps its own
     * colour. Its id if the item doesn't exist or has no name.
     */
    String citemName(String citemId, Player player) {
        ItemStack item = plugin.getCitemManager().personalize(hub.getCitemDAO().getCitem(citemId), player);
        ItemMeta meta = item == null ? null : item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return citemId;
        String name = ChatColor.stripColor(meta.getDisplayName());
        return name == null || name.isBlank() ? citemId : name.trim();
    }

    /**
     * The name a currency is listed by in a price line and in the "not enough"
     * message: its name without colours, its id if it doesn't exist or has none.
     */
    String currencyName(String currencyId) {
        me.hektortm.woSSystems.utils.model.Currency currency = hub.getEconomyDAO().getCurrencies().get(currencyId);
        String name = currency == null || currency.getName() == null ? null
                : ChatColor.stripColor(Utils.parseColorCodeString(currency.getName()));
        return name == null || name.isBlank() ? currencyId : name.trim();
    }

    private String message(String key) {
        return Utils.parseColorCodeString(plugin.getLangManager().getMessage("guis", key));
    }

    /** The lore as saved (a JSON array), as a mutable list. */
    private List<String> parseLore(String raw) {
        if (raw == null || raw.isBlank()) return new ArrayList<>();
        // wos-api delivers lore as a JSON array — parse it properly so lines keep
        // their commas and lose the quotes. Fall back to the legacy "[a, b]" split.
        try {
            com.google.gson.JsonElement json = com.google.gson.JsonParser.parseString(raw);
            if (json.isJsonArray()) {
                ArrayList<String> lines = new ArrayList<>();
                for (com.google.gson.JsonElement line : json.getAsJsonArray()) lines.add(line.getAsString());
                return lines;
            }
        } catch (Exception ignored) {
            // not JSON — legacy format below
        }
        return Arrays.stream(raw.replace("[", "").replace("]", "").split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    static class GUIHolder implements InventoryHolder {
        private final String guiId;

        public GUIHolder(String guiId) {
            this.guiId = guiId;
        }

        public String getGuiId() {
            return guiId;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
