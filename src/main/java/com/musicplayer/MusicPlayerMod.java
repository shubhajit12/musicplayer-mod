package com.musicplayer;

import com.musicplayer.client.hud.MusicHud;
import com.musicplayer.client.screen.MusicPlayerScreen;
import com.musicplayer.command.MusicCommand;
import com.musicplayer.config.ModConfig;
import com.musicplayer.player.MusicEngine;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class MusicPlayerMod implements ClientModInitializer {

    public static final String MOD_ID = "musicplayer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Singleton instances
    public static MusicEngine engine;
    public static ModConfig config;
    public static MusicHud hud;

    // Keybindings
    public static KeyBinding keyOpen;
    public static KeyBinding keyPlayPause;
    public static KeyBinding keyStop;
    public static KeyBinding keyNext;
    public static KeyBinding keyPrev;

    @Override
    public void onInitializeClient() {
        LOGGER.info("[MusicPlayer] Initializing...");

        // Load config
        config = ModConfig.load();

        // Init audio engine (LavaPlayer-backed)
        engine = new MusicEngine();

        // Init HUD
        hud = new MusicHud();

        // Register keybindings
        keyOpen = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.musicplayer.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.musicplayer"));
        keyPlayPause = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.musicplayer.play_pause", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.musicplayer"));
        keyStop = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.musicplayer.stop", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.musicplayer"));
        keyNext = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.musicplayer.next", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.musicplayer"));
        keyPrev = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.musicplayer.prev", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.musicplayer"));

        // Register HUD renderer
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> hud.render(drawContext, tickDelta));

        // Register tick listener for keybinds
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (keyOpen.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new MusicPlayerScreen());
                }
            }
            while (keyPlayPause.wasPressed()) {
                engine.togglePause();
            }
            while (keyStop.wasPressed()) {
                engine.stop();
            }
            while (keyNext.wasPressed()) {
                engine.skipNext();
            }
            while (keyPrev.wasPressed()) {
                engine.skipPrevious();
            }
        });

        LOGGER.info("[MusicPlayer] Ready! Press F8 to open.");

        // Register /music chat commands
        MusicCommand.register();
    }
}
