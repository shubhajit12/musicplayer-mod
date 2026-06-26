package com.musicplayer.playlist;

import com.google.gson.JsonObject;

public class Track {

    public enum SourceType { FILE, HTTP_STREAM, YOUTUBE, SOUNDCLOUD, BANDCAMP, VIMEO, TWITCH }

    private String title;
    private String author;
    private String source;          // raw URL / file path
    private SourceType sourceType;
    private long durationMs;        // -1 = unknown / stream

    public Track(String title, String author, String source, long durationMs) {
        this.title = title;
        this.author = author;
        this.source = source;
        this.durationMs = durationMs;
        this.sourceType = detectType(source);
    }

    private SourceType detectType(String src) {
        if (src == null) return SourceType.FILE;
        String s = src.toLowerCase();
        if (s.contains("youtube.com") || s.contains("youtu.be")) return SourceType.YOUTUBE;
        if (s.contains("soundcloud.com")) return SourceType.SOUNDCLOUD;
        if (s.contains("bandcamp.com")) return SourceType.BANDCAMP;
        if (s.contains("vimeo.com")) return SourceType.VIMEO;
        if (s.contains("twitch.tv")) return SourceType.TWITCH;
        if (s.startsWith("http://") || s.startsWith("https://")) return SourceType.HTTP_STREAM;
        return SourceType.FILE;
    }

    public String getDisplayTitle() {
        return (title != null && !title.isBlank()) ? title : source;
    }

    public String getDisplayAuthor() {
        return (author != null && !author.isBlank()) ? author : sourceType.name();
    }

    public String getFormattedDuration() {
        if (durationMs <= 0) return "LIVE";
        long s = durationMs / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    // Getters / setters
    public String getTitle()               { return title; }
    public void setTitle(String t)         { this.title = t; }
    public String getAuthor()              { return author; }
    public String getSource()              { return source; }
    public SourceType getSourceType()      { return sourceType; }
    public long getDurationMs()            { return durationMs; }
    public void setDurationMs(long d)      { this.durationMs = d; }

    public boolean isStream() { return durationMs <= 0; }

    // --- Serialization ---
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("title", title);
        o.addProperty("author", author);
        o.addProperty("source", source);
        o.addProperty("duration", durationMs);
        return o;
    }

    public static Track fromJson(JsonObject o) {
        return new Track(
            o.has("title")    ? o.get("title").getAsString()    : "Unknown",
            o.has("author")   ? o.get("author").getAsString()   : "",
            o.has("source")   ? o.get("source").getAsString()   : "",
            o.has("duration") ? o.get("duration").getAsLong()   : -1
        );
    }
}
