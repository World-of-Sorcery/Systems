package me.hektortm.woSSystems.utils.model;

import java.util.List;

/**
 * A GUI background that applies while its conditions pass. {@code matchtype}
 * is "all" or "one" (any); rules are tried in order and the first that passes wins.
 */
public record BackgroundRule(int id, String background, String matchtype, List<Condition> conditions) {}
