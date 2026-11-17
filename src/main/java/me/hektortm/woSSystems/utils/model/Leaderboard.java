package me.hektortm.woSSystems.utils.model;

/**
 * A leaderboard definition: it ranks one stat ({@code sourceType} "stat",
 * highest first) or one timer's best times ("timer", fastest first) and shows
 * {@link #getSize()} entries. The line format and empty line hold
 * {@code %rank%}, {@code %name%}, {@code %value%}. Rewards are the API's business.
 */
public class Leaderboard extends BaseEntity {
    private final String sourceType;
    private final String sourceId;
    private final int size;
    private final String lineFormat;
    private final String emptyLine;

    public Leaderboard(String id, String sourceType, String sourceId, int size, String lineFormat, String emptyLine) {
        super(id);
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.size = size;
        this.lineFormat = lineFormat;
        this.emptyLine = emptyLine;
    }

    public String getSourceType() { return sourceType; }
    public String getSourceId()   { return sourceId;   }
    public int getSize()          { return size;       }
    public String getLineFormat() { return lineFormat; }
    public String getEmptyLine()  { return emptyLine;  }

    public boolean isTimer() { return "timer".equals(sourceType); }
}
