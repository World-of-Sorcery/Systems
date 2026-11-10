package me.hektortm.woSSystems.utils.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

/**
 * A time event ("activity"): it happens at a real date and time, once or on a repeat.
 * {@code startsAt} is a wall clock time in the schedule time zone the API hands out
 * (see {@code TimeDAO.zone()}), so "Saturdays at 20:00" stays at 20:00 across summer
 * time changes. When it happens is worked out by {@code ActivitySchedule}.
 */
public class Activity extends BaseEntity {

    public enum Repeat {
        NONE, DAILY, WEEKLY, MONTHLY, YEARLY;

        /** The API's lower-case name ("weekly"); null or empty is {@link #NONE}. */
        public static Repeat of(String name) {
            return name == null || name.isEmpty() ? NONE : valueOf(name.toUpperCase(Locale.ROOT));
        }
    }

    private final boolean isEnabled;
    private final String name;
    private final String message;
    private final LocalDateTime startsAt;
    private final int durationMinutes;
    private final Repeat repeat;
    private final int repeatInterval;
    private final Set<DayOfWeek> repeatWeekdays;
    private final LocalDate repeatUntil;
    private final String startInteraction;
    private final String endInteraction;

    /**
     * @param repeatWeekdays weekly only; empty = the weekday of {@code startsAt}
     * @param repeatUntil    the last day an occurrence may start; null = forever
     */
    public Activity(String id, boolean isEnabled, String name, String message,
                    LocalDateTime startsAt, int durationMinutes, Repeat repeat, int repeatInterval,
                    Set<DayOfWeek> repeatWeekdays, LocalDate repeatUntil,
                    String startInteraction, String endInteraction) {
        super(id);
        this.isEnabled        = isEnabled;
        this.name             = name;
        this.message          = message;
        this.startsAt         = startsAt;
        this.durationMinutes  = durationMinutes;
        this.repeat           = repeat == null ? Repeat.NONE : repeat;
        this.repeatInterval   = repeatInterval;
        this.repeatWeekdays   = repeatWeekdays == null ? Set.of() : Set.copyOf(repeatWeekdays);
        this.repeatUntil      = repeatUntil;
        this.startInteraction = startInteraction;
        this.endInteraction   = endInteraction;
    }

    public boolean        getIsEnabled()        { return isEnabled;        }
    public String         getName()             { return name;             }
    public String         getMessage()          { return message;          }
    public LocalDateTime  getStartsAt()         { return startsAt;         }
    public int            getDurationMinutes()  { return durationMinutes;  }
    public Repeat         getRepeat()           { return repeat;           }
    public int            getRepeatInterval()   { return repeatInterval;   }
    public Set<DayOfWeek> getRepeatWeekdays()   { return repeatWeekdays;   }
    public LocalDate      getRepeatUntil()      { return repeatUntil;      }
    public String         getStartInteraction() { return startInteraction; }
    public String         getEndInteraction()   { return endInteraction;   }
}
