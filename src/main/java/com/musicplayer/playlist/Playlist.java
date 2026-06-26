package com.musicplayer.playlist;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Playlist {

    private String name;
    private final List<Track> tracks = new ArrayList<>();
    private int currentIndex = -1;
    private boolean shuffle = false;
    private boolean repeatAll = false;
    private boolean repeatOne = false;

    // Shuffle order — indices into `tracks`
    private final List<Integer> shuffleOrder = new ArrayList<>();

    public Playlist(String name) {
        this.name = name;
    }

    // ── Track management ──────────────────────────────────────────────────────

    public void addTrack(Track t) {
        tracks.add(t);
        shuffleOrder.add(tracks.size() - 1);
        if (currentIndex < 0) currentIndex = 0;
    }

    public void removeTrack(int index) {
        if (index < 0 || index >= tracks.size()) return;
        tracks.remove(index);
        shuffleOrder.clear();
        for (int i = 0; i < tracks.size(); i++) shuffleOrder.add(i);
        if (tracks.isEmpty()) { currentIndex = -1; return; }
        if (currentIndex >= tracks.size()) currentIndex = tracks.size() - 1;
    }

    public void moveTrack(int from, int to) {
        if (from < 0 || from >= tracks.size() || to < 0 || to >= tracks.size()) return;
        Track t = tracks.remove(from);
        tracks.add(to, t);
        if (currentIndex == from) currentIndex = to;
        else if (currentIndex > from && currentIndex <= to) currentIndex--;
        else if (currentIndex < from && currentIndex >= to) currentIndex++;
        rebuildShuffleOrder();
    }

    public void clear() {
        tracks.clear();
        shuffleOrder.clear();
        currentIndex = -1;
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    public Track getCurrentTrack() {
        if (currentIndex < 0 || currentIndex >= tracks.size()) return null;
        return tracks.get(currentIndex);
    }

    /** Advance and return the next track, or null if playlist ended. */
    public Track next() {
        if (tracks.isEmpty()) return null;
        if (repeatOne) return getCurrentTrack();

        int next;
        if (shuffle) {
            next = nextShuffleIndex();
        } else {
            next = currentIndex + 1;
        }

        if (next >= tracks.size()) {
            if (repeatAll) {
                next = 0;
                if (shuffle) reshuffleOrder();
            } else {
                return null; // end of list
            }
        }
        currentIndex = next;
        return tracks.get(currentIndex);
    }

    public Track previous() {
        if (tracks.isEmpty()) return null;
        int prev = (shuffle) ? prevShuffleIndex() : currentIndex - 1;
        if (prev < 0) prev = tracks.size() - 1;
        currentIndex = prev;
        return tracks.get(currentIndex);
    }

    public void jumpTo(int index) {
        if (index >= 0 && index < tracks.size()) currentIndex = index;
    }

    // ── Shuffle internals ─────────────────────────────────────────────────────

    private void rebuildShuffleOrder() {
        shuffleOrder.clear();
        for (int i = 0; i < tracks.size(); i++) shuffleOrder.add(i);
    }

    private void reshuffleOrder() {
        rebuildShuffleOrder();
        Collections.shuffle(shuffleOrder);
    }

    private int nextShuffleIndex() {
        int pos = shuffleOrder.indexOf(currentIndex);
        return (pos + 1 < shuffleOrder.size()) ? shuffleOrder.get(pos + 1) : tracks.size();
    }

    private int prevShuffleIndex() {
        int pos = shuffleOrder.indexOf(currentIndex);
        return (pos - 1 >= 0) ? shuffleOrder.get(pos - 1) : shuffleOrder.get(shuffleOrder.size() - 1);
    }

    // ── Getters / setters ─────────────────────────────────────────────────────

    public String getName()             { return name; }
    public void setName(String n)       { this.name = n; }
    public List<Track> getTracks()      { return tracks; }
    public int getCurrentIndex()        { return currentIndex; }
    public int size()                   { return tracks.size(); }
    public boolean isEmpty()            { return tracks.isEmpty(); }

    public boolean isShuffle()          { return shuffle; }
    public void setShuffle(boolean v)   { shuffle = v; if (v) reshuffleOrder(); }
    public boolean isRepeatAll()        { return repeatAll; }
    public void setRepeatAll(boolean v) { repeatAll = v; if (v) repeatOne = false; }
    public boolean isRepeatOne()        { return repeatOne; }
    public void setRepeatOne(boolean v) { repeatOne = v; if (v) repeatAll = false; }

    // ── Serialization ─────────────────────────────────────────────────────────

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("currentIndex", currentIndex);
        o.addProperty("shuffle", shuffle);
        o.addProperty("repeatAll", repeatAll);
        o.addProperty("repeatOne", repeatOne);
        JsonArray arr = new JsonArray();
        for (Track t : tracks) arr.add(t.toJson());
        o.add("tracks", arr);
        return o;
    }

    public static Playlist fromJson(JsonObject o) {
        Playlist pl = new Playlist(o.has("name") ? o.get("name").getAsString() : "Playlist");
        pl.currentIndex = o.has("currentIndex") ? o.get("currentIndex").getAsInt() : -1;
        pl.shuffle = o.has("shuffle") && o.get("shuffle").getAsBoolean();
        pl.repeatAll = o.has("repeatAll") && o.get("repeatAll").getAsBoolean();
        pl.repeatOne = o.has("repeatOne") && o.get("repeatOne").getAsBoolean();
        if (o.has("tracks")) {
            for (var el : o.getAsJsonArray("tracks")) {
                pl.tracks.add(Track.fromJson(el.getAsJsonObject()));
            }
        }
        pl.rebuildShuffleOrder();
        if (pl.shuffle) Collections.shuffle(pl.shuffleOrder);
        return pl;
    }
}
