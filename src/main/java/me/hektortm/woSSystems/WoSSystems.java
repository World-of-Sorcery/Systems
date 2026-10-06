package me.hektortm.woSSystems;

import com.github.retrooper.packetevents.PacketEventsAPI;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import me.hektortm.woSSystems.core.JoinListener;
import me.hektortm.woSSystems.core.QuitListener;
import me.hektortm.woSSystems.systems.backpack.BackpackListener;
import me.hektortm.woSSystems.systems.bugs.BugCommand;
import me.hektortm.woSSystems.systems.chat.ChatListener;
import me.hektortm.woSSystems.systems.chat.ChatManager;
import me.hektortm.woSSystems.systems.chat.NicknameManager;
import me.hektortm.woSSystems.systems.chat.cmd.NicknameCommand;
import me.hektortm.woSSystems.systems.citems.*;
import me.hektortm.woSSystems.systems.cosmetic.CosmeticManager;
import me.hektortm.woSSystems.systems.cosmetic.cmd.CosmeticCommand;
import me.hektortm.woSSystems.systems.cosmetic.cmd.QuickCommands;
import me.hektortm.woSSystems.database.AsyncWriteQueue;
import me.hektortm.woSSystems.database.DAOHub;
import me.hektortm.woSSystems.systems.economy.EcoManager;
import me.hektortm.woSSystems.systems.economy.cmd.BalanceCommand;
import me.hektortm.woSSystems.systems.economy.cmd.Coinflip;
import me.hektortm.woSSystems.systems.economy.cmd.EcoCommand;
import me.hektortm.woSSystems.systems.economy.cmd.PayCommand;
import me.hektortm.woSSystems.systems.guis.InventoryClickListener;
import me.hektortm.woSSystems.systems.interactions.InterListener;
import me.hektortm.woSSystems.systems.linking.LinkCommand;
import me.hektortm.woSSystems.systems.professions.crafting.CraftingListener;
import me.hektortm.woSSystems.systems.professions.crafting.CraftingManager;
import me.hektortm.woSSystems.systems.profiles.ProfileCommand;
import me.hektortm.woSSystems.systems.profiles.ProfileDialogs;
import me.hektortm.woSSystems.systems.regions.RegionHandler;
import me.hektortm.woSSystems.systems.regions.RegionBossBar;
import me.hektortm.woSSystems.systems.citems.cmd.SignCommand;
import me.hektortm.woSSystems.systems.commands.BasicCommandExecutor;
import me.hektortm.woSSystems.systems.cooldowns.CooldownManager;
import me.hektortm.woSSystems.systems.cooldowns.cmd.CooldownCommand;
import me.hektortm.woSSystems.systems.debug.DebugCommand;
import me.hektortm.woSSystems.systems.debug.DebugLabels;
import me.hektortm.woSSystems.systems.debug.DebugMode;
import me.hektortm.woSSystems.systems.guis.GUIManager;
import me.hektortm.woSSystems.systems.guis.cmd.GUICommand;
//import me.hektortm.woSSystems.systems.interactions.HologramHandler;
import me.hektortm.woSSystems.systems.interactions.InteractionManager;
import me.hektortm.woSSystems.systems.loottables.LoottableManager;
import me.hektortm.woSSystems.systems.loottables.cmd.LoottableCommand;
import me.hektortm.woSSystems.tablist.TablistManager;
import me.hektortm.woSSystems.systems.time.BossBarManager;
import me.hektortm.woSSystems.systems.time.TimeEvents;
import me.hektortm.woSSystems.systems.time.TimeManager;
import me.hektortm.woSSystems.systems.time.cmd.Calender;
import me.hektortm.woSSystems.systems.time.cmd.TimeCommand;
import me.hektortm.woSSystems.systems.professions.fishing.FishingListener;
import me.hektortm.woSSystems.systems.citems.cmd.CgiveCommand;
import me.hektortm.woSSystems.systems.citems.cmd.CitemCommand;
import me.hektortm.woSSystems.systems.citems.cmd.CremoveCommand;
import me.hektortm.woSSystems.systems.interactions.cmd.InteractionCommand;
import me.hektortm.woSSystems.systems.stats.StatsManager;
import me.hektortm.woSSystems.systems.stats.cmd.GlobalStatCommand;
import me.hektortm.woSSystems.systems.stats.cmd.StatsCommand;
import me.hektortm.woSSystems.systems.quests.QuestDAO;
import me.hektortm.woSSystems.systems.quests.QuestListener;
import me.hektortm.woSSystems.systems.quests.QuestManager;
import me.hektortm.woSSystems.systems.quests.cmd.QuestCommand;
import me.hektortm.woSSystems.systems.unlockables.UnlockableManager;
import me.hektortm.woSSystems.systems.unlockables.cmd.UnlockableCommand;
import me.hektortm.woSSystems.utils.*;
import me.hektortm.woSSystems.utils.model.BasicCommand;
import me.hektortm.wosCore.LangManager;
import me.hektortm.wosCore.Utils;
import me.hektortm.wosCore.WoSCore;
import me.hektortm.woSSystems.content.ContentRegistry;

import me.hektortm.wosCore.database.DatabaseManager;
import me.hektortm.wosCore.discord.DiscordLog;
import me.hektortm.wosCore.discord.DiscordLogger;
import me.hektortm.wosCore.logging.LogManager;
import org.aselstudios.luxdialoguesapi.DialogueProvider;
import org.aselstudios.luxdialoguesapi.LuxDialoguesAPI;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main plugin class for WoSSystems.
 *
 * <p>Responsible for the full plugin lifecycle: initialising all managers,
 * registering WorldGuard flags, loading language files, wiring commands and
 * event listeners, and tearing everything down cleanly on disable.</p>
 *
 * <p>A singleton instance is available via {@link #getInstance()} after
 * {@link #onEnable()} returns.</p>
 */
public final class WoSSystems extends JavaPlugin {

    private static WoSSystems instance;
    private PacketEventsAPI packetEventsApi;
    private DAOHub daoHub;
    private WebhookServer webhookServer;
    private WoSCore core;
    private static LangManager lang;
    private LogManager log;
 //   private HologramHandler hologramHandler;
    private CitemManager citemManager;
    private CooldownManager cooldownManager;
    private EcoManager ecoManager;
    private StatsManager statsManager;
    private UnlockableManager unlockableManager;
    private ActionHandler actionHandler;
    private InteractionManager interactionManager;
    private PlaceholderResolver resolver;
    private ConditionHandler conditionHandler;
    private CraftingManager craftingManager;
    private ChatManager chatManager;
    private NicknameManager nickManager;
    private Coinflip coinflipCommand;
    private LoottableManager lootTableManager;
    private GUIManager guiManager;
    private me.hektortm.woSSystems.systems.cscreens.CscreenManager cscreenManager;
    private BossBarManager bossBarManager;
    private TimeEvents timeEvents;
    private TimeManager timeManager;
    private RegionBossBar regionBossBarManager;
    private TablistManager tab;
    private CosmeticManager cosmeticManager;
    private ProfileDialogs profileDialogs;
    private LuxDialoguesAPI luxApi;
    private CitemDisplays citemDisplays;
    private DailyReset dailyReset;
    private QuestManager questManager;
    private final DebugMode debugMode = new DebugMode();
    private final DebugLabels debugLabels = new DebugLabels(debugMode);


    public static StringFlag DISPLAY_NAME;
    public static StringFlag ENTER_INTERACTION;
    public static StringFlag LEAVE_INTERACTION;
    public final Map<UUID, String> playerRegions = new HashMap<>();

    @Override
    public void onEnable() {
        instance = this;
//        PacketEvents.getAPI().init();
//        packetEventsApi =  PacketEvents.getAPI();
//        SpigotEntityLibPlatform platform = new SpigotEntityLibPlatform(this);
//        APIConfig settings = new APIConfig(PacketEvents.getAPI())
//                .debugMode()
//                .tickTickables()
//                .trackPlatformEntities()
//                .usePlatformLogger();
//
//        EntityLib.init(platform, settings);
        luxApi = LuxDialoguesAPI.getAPI();
        try {
            core = WoSCore.getPlugin(WoSCore.class);
            if (core == null) {
                getLogger().severe("WoSCore not found. Disabling plugin.");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }

            DatabaseManager databaseManager = core.getDatabaseManager();

            // Content and player/game state come from wos-api (only recipes still use MySQL).
            ContentRegistry content = new ContentRegistry(this);
            daoHub = new DAOHub(databaseManager, core.getApi(), content);
            // Tab completion suggests the ids of whatever content is loaded.
            me.hektortm.woSSystems.utils.TabCompletion.contentSource(type -> {
                var store = content.store(type);
                return store == null ? java.util.List.of() : store.asMap().keySet();
            });
            // Registered before anything else so sessions load ahead of other login handlers.
            eventReg(daoHub.getSessions());

            databaseManager.initializeAllDAOs();

            // Content must be loaded before the managers below are built — several
            // (time events, custom commands) read definitions on construction.
            if (!content.preloadAll()) {
                getLogger().severe("Could not load game content from wos-api (" + getCore().getConfig().getString("api.url")
                        + "). Check that the API is running and WoSCore's api.token is set. Disabling WoSSystems.");
                Bukkit.getPluginManager().disablePlugin(this);
                return;
            }
        } catch (SQLException e) {
            System.out.println("Failed to connect to Database"+ e.getMessage());
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }



     //   hologramHandler = new HologramHandler(daoHub);
        bossBarManager = new BossBarManager();
        regionBossBarManager = new RegionBossBar();
        tab = new TablistManager(daoHub);
        timeEvents = new TimeEvents(daoHub, bossBarManager);
        timeManager = new TimeManager(timeEvents, daoHub);
        lang = new LangManager(core);
        log = new LogManager(lang, core);

        statsManager = new StatsManager(daoHub);
        ecoManager = new EcoManager(daoHub);
        unlockableManager = new UnlockableManager(daoHub);

        citemManager = new CitemManager(daoHub);
        citemDisplays = new CitemDisplays(daoHub);

        resolver = new PlaceholderResolver(daoHub);
        conditionHandler = new ConditionHandler(daoHub);
        guiManager = new GUIManager(daoHub);

        actionHandler = new ActionHandler(daoHub);
        cscreenManager = new me.hektortm.woSSystems.systems.cscreens.CscreenManager(daoHub);
        interactionManager = new InteractionManager(daoHub);
        cooldownManager = new CooldownManager(daoHub);
        nickManager = new NicknameManager(daoHub);
        profileDialogs = new ProfileDialogs(daoHub, nickManager);
        chatManager = new ChatManager(daoHub, nickManager, profileDialogs);

        lootTableManager = new LoottableManager(daoHub);
        coinflipCommand = new Coinflip(ecoManager, this, lang);
        cosmeticManager = new CosmeticManager(daoHub);

// Initialize the remaining managers
        craftingManager = new CraftingManager(daoHub); // TODO: interactions
        questManager = new QuestManager(this, daoHub);

        new CraftingListener(daoHub); // TODO: interactions

        dailyReset = new DailyReset(daoHub);



        // Check for core initialization
        if (core != null) {
            lang.loadLangFileExternal(this, "citems", core);
            lang.loadLangFileExternal(this, "stats", core);
            lang.loadLangFileExternal(this, "unlockables", core);
            lang.loadLangFileExternal(this, "economy", core);
            lang.loadLangFileExternal(this, "crecipes", core);
            lang.loadLangFileExternal(this, "nicknames", core);
            lang.loadLangFileExternal(this, "loottables", core);
            lang.loadLangFileExternal(this, "cosmetics", core);
            lang.loadLangFileExternal(this, "cooldowns", core);
            lang.loadLangFileExternal(this, "interactions", core);
            lang.loadLangFileExternal(this, "guis", core);
            lang.loadLangFileExternal(this, "cscreens", core);
            lang.loadLangFileExternal(this, "dialogs", core);
            lang.loadLangFileExternal(this, "global_stats", core);
            lang.loadLangFileExternal(this, "bugs", core);
            lang.loadLangFileExternal(this, "debug", core);
            lang.loadLangFileExternal(this, "help", core);
        } else {
            getLogger().severe("WoSCore not found. Disabling WoSSystems");
        }

        // Finalize initialization

        timeManager.loadConfiguration();
        timeManager.loadGameStateConfig();
        timeManager.loadGameState();
        timeManager.loadConfig();
        timeManager.initializeMonthNames();
        timeManager.startInGameClock();
        for (Player p : Bukkit.getOnlinePlayers()) {
            bossBarManager.removeBossBar(p);
            bossBarManager.createBossBar(p);
            regionBossBarManager.removeBossBar(p);
            regionBossBarManager.createBossBar(p);
        }
        registerBasicCommands();
        //recipeManager.loadRecipes();
        registerCommands();
        registerEvents();
        //interactionManager.loadInteraction();
        interactionManager.interactionTask();
        guiManager.startRefresh();
        tab.runTablist();
        cooldownManager.start();
        craftingManager.loadAll();
        dailyReset.startResetTimer();

        // Optional: without it, portal edits just don't hot-reload — never disable the plugin over it.
        try {
            webhookServer = new WebhookServer(this, daoHub);
            webhookServer.start();
        } catch (IOException e) {
            getLogger().severe("[Webhook] Failed to start on port " + getConfig().getInt("webhook.port")
                    + ": " + e.getMessage() + " — portal edits will not hot-reload (is another process on that port? wos-api uses 8080)");
        }
        this.saveDefaultConfig();
        PermissionRegistry.registerAll(this, PermissionDefault.OP);
    }

    @Override
    public void onDisable() {
        // onEnable may have stopped early (missing WoSCore, API unreachable), so any
        // of these can still be null here.
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (bossBarManager != null) bossBarManager.removeBossBar(p);
            if (regionBossBarManager != null) regionBossBarManager.removeBossBar(p);
            if (interactionManager != null) interactionManager.getHologramManager().removeAllHolograms(p);
            if (interactionManager != null) interactionManager.getDisplayManager().removeAllDisplays(p);
        }
        if (timeManager != null) timeManager.saveGameState();
//        PacketEvents.getAPI().terminate();
        if (webhookServer != null) webhookServer.stop();
        PermissionRegistry.unregisterAll();
        AsyncWriteQueue.shutdown(); // flush all pending DB writes before the JVM exits
    }

    @Override
    public void onLoad() {
//        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
//        PacketEvents.getAPI().load();

        FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();

        DISPLAY_NAME = registerStringFlag("display-name", registry);
        ENTER_INTERACTION = registerStringFlag("enter-interaction", registry);
        LEAVE_INTERACTION = registerStringFlag("leave-interaction", registry);

    }

    /**
     * Attempts to register a {@link StateFlag} with WorldGuard.  If a flag with
     * the same name already exists and is a {@code StateFlag}, no action is taken.
     *
     * @param flagName the WorldGuard flag name
     * @param registry the WorldGuard flag registry
     */
    private void registerStateFlag(String flagName, FlagRegistry registry) {
        try {
            StateFlag flag = new StateFlag(flagName, false);
            registry.register(flag);
        } catch (FlagConflictException e) {
            Flag<?> existing = registry.get(flagName);
            if (existing instanceof StateFlag) {
                Bukkit.getLogger().warning("Flag already exists");
            } else {
                Bukkit.getLogger().warning("wtf is happening");
            }
        }
    }

    /**
     * Attempts to register a {@link StringFlag} with WorldGuard.  If a flag with
     * the same name already exists and is a {@code StringFlag}, the existing flag
     * is returned.  Returns {@code null} if there is a type conflict.
     *
     * @param flagName the WorldGuard flag name
     * @param registry the WorldGuard flag registry
     * @return the registered or pre-existing {@link StringFlag}, or {@code null}
     */
    private StringFlag registerStringFlag(String flagName, FlagRegistry registry) {
        try {
            StringFlag flag = new StringFlag(flagName);
            registry.register(flag);
            return flag;
        } catch (FlagConflictException e) {
            Flag<?> existing = registry.get(flagName);
            if (existing instanceof StringFlag) {
                Bukkit.getLogger().warning("Flag already exists");
                return (StringFlag) existing;
            } else {
                Bukkit.getLogger().warning("Flag conflict with incompatible type!");
                return null;
            }
        }
    }

    /**
     * Dynamically registers a Bukkit command executor for each {@link BasicCommand}
     * loaded from the database, binding each command to an interaction.
     */
    private void registerBasicCommands() {
        for (BasicCommand command : daoHub.getCommandsDAO().getCommands()) {
            registerCommand(command.getCommand(), new BasicCommandExecutor(command));
        }
    }

    /**
     * Dynamically registers a plugin command at runtime by reflectively accessing
     * the server's {@code commandMap}.  Used for commands that are not declared in
     * {@code plugin.yml} (e.g. database-driven basic commands).
     *
     * @param commandName the name of the command to register
     * @param executor    the executor that handles the command
     */
    private void registerCommand(String commandName, CommandExecutor executor) {
        try {
            // Get the server's command map
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            // Create a new PluginCommand
            Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);
            PluginCommand command = constructor.newInstance(commandName, this);

            // Set the executor
            command.setExecutor(executor);

            // Register the command
            commandMap.register(commandName, command);
        } catch (Exception e) {
            getLogger().severe("Failed to register command: " + commandName);
            e.printStackTrace();
        }
    }

    private void registerCommands() {

        //cmdReg("opengui", new GUIcommand(guiManager, interactionManager));
        cmdReg("interaction", new InteractionCommand(daoHub));
        cmdReg("citem", new CitemCommand(daoHub));
        cmdReg("cgive", new CgiveCommand(citemManager));
        cmdReg("cremove", new CremoveCommand(daoHub));
        cmdReg("stats", new StatsCommand(statsManager));
        cmdReg("globalstats", new GlobalStatCommand(statsManager));
        cmdReg("unlockable", new UnlockableCommand(daoHub));
        cmdReg("economy", new EcoCommand(ecoManager, lang, log));
        cmdReg("balance", new BalanceCommand(ecoManager, core));
        cmdReg("pay", new PayCommand(ecoManager, lang));
        cmdReg("baltop", new me.hektortm.woSSystems.systems.economy.cmd.BaltopCommand(ecoManager, lang, core.getApi()));
        cmdReg("coinflip", coinflipCommand);
        cmdReg("nickname", new NicknameCommand());
        cmdReg("loottable", new LoottableCommand(daoHub, lootTableManager));
        cmdReg("sign", new SignCommand(citemManager, ecoManager));
        cmdReg("time", new TimeCommand(timeManager, this, lang));
        cmdReg("cosmetic", new CosmeticCommand(cosmeticManager, daoHub));
        cmdReg("prefixes", new QuickCommands.PrefixCommand());
        cmdReg("badges", new QuickCommands.BadgeCommand());
        cmdReg("titles", new QuickCommands.TitleCommand());
        cmdReg("profile", new ProfileCommand(profileDialogs));
        cmdReg("gui", new GUICommand(daoHub));
        cmdReg("cscreen", new me.hektortm.woSSystems.systems.cscreens.cmd.CscreenCommand(cscreenManager));
        cmdReg("debugcmd", new debug(daoHub));
        cmdReg("wosdebug", new DebugCommand(debugMode));
        cmdReg("woshelp", new me.hektortm.woSSystems.systems.help.StaffHelpCommand());
        cmdReg("cooldown", new CooldownCommand(daoHub));
        cmdReg("calendar", new Calender());
        cmdReg("link", new LinkCommand());
        cmdReg("dialog", new me.hektortm.woSSystems.systems.dialogs.cmd.DialogCommand(daoHub));
        cmdReg("quest", new QuestCommand(questManager, daoHub));
        cmdReg("bug", new BugCommand(this));
       // cmdReg("unlockrecipe", new RecipeCommand());
    }

    private void registerEvents() {
        eventReg(new InterListener(daoHub));
        eventReg(new DropListener());
        eventReg(new HoverListener(citemManager));
        eventReg(new CitemListener(daoHub));
        eventReg(new WearListener());
        eventReg(new StashListener());
        eventReg(new QuitListener(core, unlockableManager, daoHub, coinflipCommand, this));
        eventReg(new FishingListener(daoHub));
        eventReg(new JoinListener(this, daoHub));
        eventReg(new ChatListener(chatManager, unlockableManager));
        eventReg(new RegionHandler(regionBossBarManager));
        eventReg(new BackpackListener());
        //eventReg(new HologramHandler(daoHub));
        eventReg(guiManager); // the same instance that opens GUIs, so clicks see what it opened
        eventReg(new QuestListener(this, questManager));
        eventReg(debugMode);
        eventReg(debugLabels);
        getServer().getPluginManager().registerEvents(new InventoryClickListener(ecoManager, coinflipCommand, lang, nickManager.getNickRequests() ,nickManager, daoHub), this);
    }

    /**
     * Writes a log entry using the JUL {@link Logger} with the given name.
     *
     * @param name    the logger name (typically a class or system identifier)
     * @param level   the logging level
     * @param message the message to log
     */
    public void writeLog(String name, Level level, String message) {
        Logger LOGGER = Logger.getLogger(name);
        LOGGER.log(level, message);
    }

    private void eventReg(Listener l) {
        getServer().getPluginManager().registerEvents(l, this);
    }

    private void cmdReg(String cmd, CommandExecutor e) {
        this.getCommand(cmd).setExecutor(e);
    }

    /**
     * Sends a localised economy-prefixed message to a command sender.
     *
     * @param sender the recipient
     * @param file   the language file key
     * @param msg    the message key within that file
     */
    public static void ecoMsg(CommandSender sender, String file, String msg) {
        sender.sendMessage(lang.getMessage("general","prefix.economy")+lang.getMessage(file, msg));
    }

    /**
     * Sends a localised economy message with one placeholder substitution.
     *
     * @param sender  the recipient
     * @param file    the language file key
     * @param msg     the message key within that file
     * @param oldChar the placeholder string to replace
     * @param value   the replacement value
     */
    public static void ecoMsg1Value(CommandSender sender,String file, String msg, String oldChar, String value) {
        String message = lang.getMessage(file, msg).replace(oldChar, value);
        String newMessage = lang.getMessage("general","prefix.economy")+message;
        sender.sendMessage(Utils.replaceColorPlaceholders(newMessage));
    }

    /**
     * Sends a localised economy message with two placeholder substitutions.
     *
     * @param sender   the recipient
     * @param file     the language file key
     * @param msg      the message key within that file
     * @param oldChar1 first placeholder string to replace
     * @param value1   replacement for the first placeholder
     * @param oldChar2 second placeholder string to replace
     * @param value2   replacement for the second placeholder
     */
    public static void ecoMsg2Values(CommandSender sender,String file, String msg, String oldChar1, String value1, String oldChar2, String value2) {
        String message = lang.getMessage(file, msg).replace(oldChar1, value1).replace(oldChar2, value2);
        String newMessage = lang.getMessage("general","prefix.economy")+message;
        sender.sendMessage(Utils.replaceColorPlaceholders(newMessage));
    }

    /**
     * Sends a localised economy message with three placeholder substitutions.
     *
     * @param sender   the recipient
     * @param file     the language file key
     * @param msg      the message key within that file
     * @param oldChar1 first placeholder string to replace
     * @param value1   replacement for the first placeholder
     * @param oldChar2 second placeholder string to replace
     * @param value2   replacement for the second placeholder
     * @param oldChar3 third placeholder string to replace
     * @param value3   replacement for the third placeholder
     */
    public static void ecoMsg3Values(CommandSender sender,String file, String msg, String oldChar1, String value1, String oldChar2, String value2, String oldChar3, String value3) {
        String message = lang.getMessage(file, msg).replace(oldChar1, value1).replace(oldChar2, value2).replace(oldChar3, value3);
        String newMessage = lang.getMessage("general", "prefix.economy") + message;
        sender.sendMessage(Utils.replaceColorPlaceholders(newMessage));
    }

    /**
     * Posts a structured log entry to Discord via {@link DiscordLogger}.
     *
     * @param level   the severity level
     * @param uuid    a short identifier string for the log entry (e.g. a code location tag)
     * @param message the log message
     * @param e       an optional exception to include; may be {@code null}
     */
    public static void discordLog(Level level, String uuid, String message, @Nullable Exception e) {
        DiscordLogger.log(new DiscordLog(
                level,
                WoSSystems.getPlugin(WoSSystems.class),
                uuid,
                message,
                e
        ));
    }

    /** @return the singleton plugin instance */
    public static WoSSystems getInstance() {
        return instance;
    }
    public WoSCore getCore() {
        return core;
    }
    public LogManager getLogManager() {
        return log;
    }
    public LangManager getLangManager() {
        return lang;
    }
    public PlaceholderResolver getPlaceholderResolver() {
        return resolver;
    }
    public StatsManager getStatsManager() {
        return statsManager;
    }
    public EcoManager getEcoManager() {
        return ecoManager;
    }
    public CooldownManager getCooldownManager() {
        return cooldownManager;
    }
    public UnlockableManager getUnlockableManager() {
        return unlockableManager;
    }
    public CitemManager getCitemManager() {
        return citemManager;
    }
    public CitemDisplays getCitemDisplays() {
        return citemDisplays;
    }
    public BossBarManager getBossBarManager() {
        return bossBarManager;
    }
    public RegionBossBar getRegionBossBarManager() {
        return regionBossBarManager;
    }
    public ChatManager getChatManager() {
        return chatManager;
    }
    public CosmeticManager getCosmeticManager() {
        return cosmeticManager;
    }
    public TimeManager getTimeManager() {
        return timeManager;
    }
    public ProfileDialogs getProfileDialogs() {
        return profileDialogs;
    }
    public ConditionHandler getConditionHandler() {
        return conditionHandler;
    }
    public ActionHandler getActionHandler() {
        return actionHandler;
    }
    public InteractionManager getInteractionManager() {
        return interactionManager;
    }
    public GUIManager getGuiManager() {
        return guiManager;
    }
    public me.hektortm.woSSystems.systems.cscreens.CscreenManager getCscreenManager() {
        return cscreenManager;
    }
    public DebugMode getDebugMode() {
        return debugMode;
    }
    public DebugLabels getDebugLabels() {
        return debugLabels;
    }
    public NicknameManager getNickManager() {
        return nickManager;
    }
    public DialogueProvider getDialogueApi() {
        return LuxDialoguesAPI.getProvider();
    }
    public PacketEventsAPI getPacketEventsAPI() {
        return packetEventsApi;
    }
    public CraftingManager getCraftingManager() {
        return craftingManager;
    }
    public QuestManager getQuestManager() {
        return questManager;
    }
    public QuestDAO getQuestDAO() {
        return daoHub.getQuestDAO();
    }
    public LoottableManager getLootTableManager() {
        return lootTableManager;
    }

    /** @return the shared map of player UUID to their current WorldGuard region name */
    public Map<UUID, String> getPlayerRegions() {
        return playerRegions;
    }

}
