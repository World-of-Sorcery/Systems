package me.hektortm.woSSystems.database;

import me.hektortm.woSSystems.WoSSystems;
import me.hektortm.woSSystems.database.dao.*;
import me.hektortm.woSSystems.systems.quests.QuestDAO;
import me.hektortm.woSSystems.content.ContentRegistry;
import me.hektortm.wosCore.api.WosApi;
import me.hektortm.wosCore.database.DatabaseManager;

import me.hektortm.woSSystems.player.ApiServices;
import me.hektortm.woSSystems.player.ApiWriter;
import me.hektortm.woSSystems.player.PlayerSessions;

import java.util.UUID;
import java.util.logging.Logger;

/**
 * Central access point for all DAO instances in the WoSSystems plugin.
 *
 * <p>A single {@code DAOHub} is created during plugin startup and held by the
 * main {@link WoSSystems} class.  Every manager and handler that needs database
 * access receives the hub via constructor injection, then calls the appropriate
 * {@code get*DAO()} accessor.</p>
 *
 * <p>Player data is held by {@link PlayerSessions} (loaded at login); the hub
 * also exposes {@link #handleWebhookInvalidation}, which reloads a single
 * content entity when the admin portal signals a change.</p>
 */
public class DAOHub {
    private final WoSSystems plugin = WoSSystems.getInstance();
    private final ContentRegistry content;
    private final PlayerSessions sessions;

    private final EconomyDAO economyDAO;
    private final UnlockableDAO unlockableDAO;
    private final StatsDAO statsDAO;
    private final CitemDAO citemDAO;
    private final NicknameDAO nicknameDAO;
    private final ProfileDAO profileDAO;
    private final FishingDAO fishingDAO;
    private final CosmeticsDAO cosmeticsDAO;
    private final ConditionDAO conditionDAO;
    private final InteractionDAO interactionDAO;
    private final GUIDAO guiDAO;
    private final CscreenDAO cscreenDAO;
    private final CooldownDAO cooldownDAO;
    private final TimeDAO timeDAO;
    private final ConstantDAO constantDAO;
    private final DialogDAO dialogDAO;
    private final LoottablesDAO loottablesDAO;
    private final CommandsDAO commandsDAO;
    private final CraftingDAO craftingDAO;
    private final QuestDAO questDAO;


    /**
     * Constructs every DAO. Content definitions and player/game state both come
     * from wos-api: content through the {@link ContentRegistry} (registration
     * order = load order: conditions first, because the interaction/GUI stores
     * feed them), player state through {@link PlayerSessions}. Only crafting
     * recipes still use MySQL.
     */
    public DAOHub(DatabaseManager databaseManager, WosApi api, ContentRegistry content) {
        this.content = content;
        Logger log = plugin.getLogger();
        this.sessions = new PlayerSessions(plugin, api);
        ApiServices s = new ApiServices(api, content, sessions, new ApiWriter(api, log), log);

        this.conditionDAO   = new ConditionDAO(content, api);
        this.economyDAO     = new EconomyDAO(s);
        this.unlockableDAO  = new UnlockableDAO(s);
        this.statsDAO       = new StatsDAO(s);
        this.nicknameDAO    = new NicknameDAO(s);
        this.cosmeticsDAO   = new CosmeticsDAO(s);
        this.profileDAO     = new ProfileDAO(s);
        this.fishingDAO     = new FishingDAO(content, api, log);
        this.interactionDAO = new InteractionDAO(s, conditionDAO);
        this.guiDAO         = new GUIDAO(content, api, conditionDAO, log);
        this.cscreenDAO     = new CscreenDAO(content, api, conditionDAO, log);
        this.cooldownDAO    = new CooldownDAO(s);
        this.timeDAO        = new TimeDAO(content, api, log);
        this.citemDAO       = new CitemDAO(s);
        this.constantDAO    = new ConstantDAO(content, api, log);
        this.dialogDAO      = new DialogDAO(content, api, log);
        this.loottablesDAO  = new LoottablesDAO(content, api, log);
        this.commandsDAO    = new CommandsDAO(content, api, log);
        this.craftingDAO    = new CraftingDAO(databaseManager);
        this.questDAO       = new QuestDAO(s);
    }
    public EconomyDAO getEconomyDAO()           { return economyDAO;        }
    public UnlockableDAO getUnlockableDAO()     { return unlockableDAO;     }
    public StatsDAO getStatsDAO()               { return statsDAO;          }
    public NicknameDAO getNicknameDAO()         { return nicknameDAO;       }
    public CosmeticsDAO getCosmeticsDAO()       { return cosmeticsDAO;      }
    public ProfileDAO getProfileDAO()           { return profileDAO;        }
    public FishingDAO getFishingDAO()           { return fishingDAO;        }
    public ConditionDAO getConditionDAO()       { return conditionDAO;      }
    public InteractionDAO getInteractionDAO()   { return interactionDAO;    }
    public GUIDAO getGuiDAO()                   { return guiDAO;            }
    public CscreenDAO getCscreenDAO()           { return cscreenDAO;        }
    public CooldownDAO getCooldownDAO()         { return cooldownDAO;       }
    public TimeDAO getTimeDAO()                 { return timeDAO;           }
    public CitemDAO getCitemDAO()               { return citemDAO;          }
    public ConstantDAO getConstantDAO()         { return constantDAO;       }
    public DialogDAO getDialogDAO()             { return dialogDAO;         }
    public LoottablesDAO getLoottablesDAO()     { return loottablesDAO;     }
    public CommandsDAO getCommandsDAO()         { return commandsDAO;       }
    public CraftingDAO getCraftingDAO()         { return craftingDAO;       }
    public QuestDAO getQuestDAO()               { return questDAO;          }
    public PlayerSessions getSessions()         { return sessions;          }

    /**
     * Handles the portal's cache-invalidation webhook: reloads one content entity
     * from wos-api. Every registered content type is supported (no per-type
     * switch). Blocking — called off the main thread by {@code WebhookServer}.
     */
    public void handleWebhookInvalidation(String type, String id, UUID editorUUID) {
        // A message edited in the portal: every plugin's messages live in WoSCore.
        if ("lang".equals(type)) {
            me.hektortm.wosCore.LangManager.shared().reloadEdits();
            return;
        }
        // A balance changed in the portal: apply the difference to the player's
        // cached balance (on the main thread, like every other balance change).
        if ("player_balance".equals(type)) {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> economyDAO.applyExternalChange(id));
            return;
        }
        content.reload(type, id, editorUUID);
    }

    /** The content registry (startup preload readiness, per-type stores). */
    public ContentRegistry getContent() { return content; }
}
