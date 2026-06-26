package com.musicplayer.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.musicplayer.MusicPlayerMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir().resolve("musicplayer.json");

    // ── Settings ──────────────────────────────────────────────────────────────
    public boolean showHud = true;
    public HudPosition hudPosition = HudPosition.TOP_RIGHT;
    public boolean keybindsInGui = false;
    public boolean muteGameMusic = false;
    public int volume = 80;

    public enum HudPosition { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    // ── Persistence ───────────────────────────────────────────────────────────

    public static ModConfig load() {
        if (Files.exists(CONFIG_FILE)) {
            try {
                String json = Files.readString(CONFIG_FILE);
                ModConfig cfg = GSON.fromJson(json, ModConfig.class);
                if (cfg != null) return cfg;
            } catch (Exception e) {
                MusicPlayerMod.LOGGER.error("[MusicPlayer] Failed to read config", e);
            }
        }
        return new ModConfig();
    }

    public void save() {
        try {
            Files.writeString(CONFIG_FILE, GSON.toJson(this));
        } catch (IOException e) {
            MusicPlayerMod.LOGGER.error("[MusicPlayer] Failed to save config", e);
        }
    }
}
