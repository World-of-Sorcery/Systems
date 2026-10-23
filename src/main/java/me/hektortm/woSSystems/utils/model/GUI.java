package me.hektortm.woSSystems.utils.model;

import me.hektortm.woSSystems.database.annotation.Column;
import me.hektortm.woSSystems.database.annotation.Table;

import java.util.List;

@Table("guis")
public class GUI extends BaseEntity {

    @Column(name = "size", notNull = true)
    private final int size;

    @Column(name = "title", type = "TEXT")
    private final String title;

    @Column(name = "type", type = "TEXT")
    private final String type;

    @Column(name = "open_actions", type = "TEXT")
    private final List<String> open_actions;

    @Column(name = "close_actions", type = "TEXT")
    private final List<String> close_actions;

    /** Loaded via join from gui_slots — not a direct DB column. */
    private final List<GUIPage> pages;

    /** Run when a clicked item is on cooldown and has no cooldown commands of its own. */
    private final List<String> cooldown_actions;

    /** The items' default post-use ("stay", "close", "next", "previous", "page", "gui") and its target. */
    private final String post_use;
    private final String post_use_target;

    /** The sound when a click leads to another page of this GUI; empty for none. */
    private final String page_sound;

    /** The glyph id of the background this GUI draws by itself (null for none), and the rules that pick another. */
    private final String background;
    private final List<BackgroundRule> background_rules;

    public GUI(String guiId, int size, String title, String type, List<GUIPage> pages, List<String> openActions, List<String> closeActions,
               List<String> cooldownActions, String postUse, String postUseTarget, String pageSound,
               String background, List<BackgroundRule> backgroundRules) {
        super(guiId);
        this.size = size;
        this.title = title;
        this.type = type;
        this.pages = pages;
        open_actions = openActions;
        close_actions = closeActions;
        cooldown_actions = cooldownActions;
        post_use = postUse;
        post_use_target = postUseTarget;
        page_sound = pageSound;
        this.background = background;
        background_rules = backgroundRules == null ? List.of() : backgroundRules;
    }

    public String getGuiId()              { return getId();       }
    public int getSize()                  { return size;          }
    public String getTitle()              { return title;         }
    public List<GUIPage> getPages()       { return pages;         }
    public List<String> getOpenActions()  { return open_actions;  }
    public List<String> getCloseActions() { return close_actions; }
    public String getType()               { return type;          }
    /** Fluid: visible items fill the slots in order, without gaps. */
    public boolean isFluid()              { return "fluid".equalsIgnoreCase(type); }
    public List<String> getCooldownActions() { return cooldown_actions; }
    public String getPostUse()            { return post_use;      }
    public String getPostUseTarget()      { return post_use_target; }
    public String getPageSound()          { return page_sound;    }
    public String getBackground()         { return background;    }
    public List<BackgroundRule> getBackgroundRules() { return background_rules; }
}
