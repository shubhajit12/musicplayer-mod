package com.musicplayer.playlist;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.musicplayer.MusicPlayerMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PlaylistManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path SAVE_DIR = FabricLoader.getInstance()
            .getGameDir().resolve("musicplayer").resolve("playlists");

    private static Playlist activePlaylist;

    static {
        try {
            Files.createDirectories(SAVE_DIR);
        } catch (IOException e) {
            MusicPlayerMod.LOGGER.error("[MusicPlayer] Cannot create playlist directory", e);
        }
        activePlaylist = new Playlist("Default");
        loadDefault();
    }

    public static Playlist getPlaylist() {
        return activePlaylist;
    }

    public static void save(String filename) {
        Path file = SAVE_DIR.resolve(filename.endsWith(".json") ? filename : filename + ".json");
        try {
            String json = GSON.toJson(activePlaylist.toJson());
            Files.writeString(file, json);
            MusicPlayerMod.LOGGER.info("[MusicPlayer] Saved playlist to {}", file);
        } catch (IOException e) {
            MusicPlayerMod.LOGGER.error("[MusicPlayer] Failed to save playlist", e);
        }
    }

    public static void load(String filename) {
        Path file = SAVE_DIR.resolve(filename.endsWith(".json") ? filename : filename + ".json");
        if (!Files.exists(file)) return;
        try {
            String json = Files.readString(file);
            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            activePlaylist = Playlist.fromJson(obj);
            MusicPlayerMod.LOGGER.info("[MusicPlayer] Loaded playlist from {}", file);
        } catch (Exception e) {
            MusicPlayerMod.LOGGER.error("[MusicPlayer] Failed to load playlist", e);
        }
    }

    private static void loadDefault() {
        load("default");
    }

    public static void saveDefault() {
        save("default");
    }

    public static String[] listSavedPlaylists() {
        try {
            return Files.list(SAVE_DIR)
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replace(".json", ""))
                    .toArray(String[]::new);
        } catch (IOException e) {
            return new String[0];
        }
    }
}
