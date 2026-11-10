package me.hektortm.woSSystems.database.dao;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.hektortm.woSSystems.content.ApiSource;
import me.hektortm.woSSystems.content.ContentRegistry;
import me.hektortm.woSSystems.content.ContentStore;
import me.hektortm.woSSystems.content.Json;
import me.hektortm.woSSystems.utils.model.Activity;
import me.hektortm.wosCore.api.ApiException;
import me.hektortm.wosCore.api.WosApi;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Scheduled time events ("activities"), served from {@code /v1/content/timeevents}, and the
 * one time zone their start times are written in ({@code /v1/content/timeevent-settings}).
 */
public class TimeDAO {

    /** Used until the API has answered, and when it can't be asked. */
    static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Berlin");

    private final ContentStore<Activity> store;
    private final WosApi api;
    private final Logger log;
    private volatile ZoneId zone = DEFAULT_ZONE;

    public TimeDAO(ContentRegistry registry, WosApi api, Logger log) {
        this.api = api;
        this.log = log;
        ApiSource<Activity> rows = ApiSource.flat(api, "/v1/content/timeevents", "id", j -> read(j, log), log);
        // The zone is loaded with every full load of the events (start, full reload), off the main thread like them.
        this.store = registry.register(new ContentStore<>("timeevents", "Time Event", new ContentStore.Source<>() {
            @Override
            public Map<String, Activity> loadAll() throws ApiException {
                loadZone();
                return rows.loadAll();
            }

            @Override
            public Optional<Activity> loadOne(String id) throws ApiException {
                return rows.loadOne(id);
            }
        }));
    }

    /** Every time-event definition. */
    public List<Activity> getAllActivities() {
        return new ArrayList<>(store.all());
    }

    /** The store, for those that follow reloads ({@link ContentStore#onChange}). */
    public ContentStore<Activity> store() {
        return store;
    }

    /** The schedule time zone: every {@link Activity#getStartsAt()} is a wall clock time in it. */
    public ZoneId zone() {
        return zone;
    }

    /** Keeps the zone it has when the API can't be asked or answers something unusable. */
    private void loadZone() {
        try {
            JsonElement answer = api.getJson("/v1/content/timeevent-settings");
            zone = ZoneId.of(Json.str(answer.getAsJsonObject(), "timezone"));
        } catch (ApiException | RuntimeException e) {
            log.warning("[Time Events] The schedule time zone could not be loaded (" + e.getMessage()
                    + "); using " + zone + ".");
        }
    }

    /** A row as the scheduler uses it; a row that can't be read is logged and never happens. */
    private static Activity read(JsonObject j, Logger log) {
        try {
            return fromJson(j);
        } catch (RuntimeException e) {
            String id = Json.str(j, "id");
            log.warning("[Time Events] '" + id + "' has a schedule that can't be read (" + e.getMessage()
                    + "); it is treated as switched off.");
            return new Activity(id, false, Json.str(j, "name"), null, null, 0, Activity.Repeat.NONE, 1,
                    Set.of(), null, null, null);
        }
    }

    /**
     * The API's row: {@code starts_at} "2026-11-14T20:00:00" (no offset), {@code repeat} one of
     * none / daily / weekly / monthly / yearly, {@code repeat_weekdays} 1 = Monday … 7 = Sunday,
     * {@code repeat_until} "2026-12-31" or null.
     *
     * @throws RuntimeException when a value can't be read
     */
    public static Activity fromJson(JsonObject j) {
        Set<DayOfWeek> weekdays = EnumSet.noneOf(DayOfWeek.class);
        for (JsonElement day : Json.array(j, "repeat_weekdays")) weekdays.add(DayOfWeek.of(day.getAsInt()));
        String until = Json.str(j, "repeat_until");
        return new Activity(
                Json.str(j, "id"),
                Json.bool(j, "is_enabled", false),
                Json.str(j, "name"),
                Json.str(j, "message"),
                LocalDateTime.parse(Json.str(j, "starts_at")),
                Json.integer(j, "duration_minutes", 0),
                Activity.Repeat.of(Json.str(j, "repeat")),
                Json.integer(j, "repeat_interval", 1),
                weekdays,
                until == null || until.isEmpty() ? null : LocalDate.parse(until),
                Json.str(j, "start_interaction"),
                Json.str(j, "end_interaction"));
    }
}
