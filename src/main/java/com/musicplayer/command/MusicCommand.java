package com.musicplayer.command;

import com.musicplayer.MusicPlayerMod;
import com.musicplayer.client.screen.MusicPlayerScreen;
import com.musicplayer.player.MusicEngine;
import com.musicplayer.playlist.Playlist;
import com.musicplayer.playlist.PlaylistManager;
import com.musicplayer.playlist.Track;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;

/**
 * Registers all /music chat commands.
 *
 * Commands:
 *   /music open          — opens the Music Player GUI
 *   /music play <url>    — loads and plays a URL / file path immediately
 *   /music pause         — toggles pause/resume
 *   /music stop          — stops playback
 *   /music next          — skips to next track in playlist
 *   /music prev          — goes back to previous track
 *   /music volume <0-100>— sets volume
 *   /music volume        — shows current volume
 *   /music queue         — lists current playlist tracks
 *   /music nowplaying    — shows the currently playing track
 *   /music help          — prints command reference
 */
public class MusicCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {

            dispatcher.register(
                literal("music")

                    // /music open
                    .then(literal("open")
                        .executes(ctx -> {
                            MinecraftClient client = MinecraftClient.getInstance();
                            client.execute(() -> client.setScreen(new MusicPlayerScreen()));
                            sendFeedback("Opening Music Player...");
                            return 1;
                        })
                    )

                    // /music play <url or search term>
                    .then(literal("play")
                        .then(argument("source", greedyString())
                            .executes(ctx -> {
                                String source = getString(ctx, "source");
                                MusicEngine engine = MusicPlayerMod.engine;
                                sendFeedback("Loading: " + source);
                                engine.loadAndPlay(source);
                                return 1;
                            })
                        )
                    )

                    // /music pause
                    .then(literal("pause")
                        .executes(ctx -> {
                            MusicEngine engine = MusicPlayerMod.engine;
                            engine.togglePause();
                            boolean paused = engine.isPaused();
                            sendFeedback(paused ? "Paused." : "Resumed.");
                            return 1;
                        })
                    )

                    // /music stop
                    .then(literal("stop")
                        .executes(ctx -> {
                            MusicPlayerMod.engine.stop();
                            sendFeedback("Stopped playback.");
                            return 1;
                        })
                    )

                    // /music next
                    .then(literal("next")
                        .executes(ctx -> {
                            MusicPlayerMod.engine.skipNext();
                            sendFeedback("Skipping to next track...");
                            return 1;
                        })
                    )

                    // /music prev
                    .then(literal("prev")
                        .executes(ctx -> {
                            MusicPlayerMod.engine.skipPrevious();
                            sendFeedback("Going to previous track...");
                            return 1;
                        })
                    )

                    // /music volume [0-100]
                    .then(literal("volume")
                        // /music volume <number>
                        .then(argument("level", integer(0, 100))
                            .executes(ctx -> {
                                int level = getInteger(ctx, "level");
                                MusicPlayerMod.engine.setVolume(level);
                                sendFeedback("Volume set to " + level + "%");
                                return 1;
                            })
                        )
                        // /music volume (no arg — show current)
                        .executes(ctx -> {
                            int vol = MusicPlayerMod.engine.getVolume();
                            sendFeedback("Current volume: " + vol + "%");
                            return 1;
                        })
                    )

                    // /music nowplaying
                    .then(literal("nowplaying")
                        .executes(ctx -> {
                            MusicEngine engine = MusicPlayerMod.engine;
                            MusicEngine.State state = engine.getState();
                            if (state == MusicEngine.State.PLAYING || state == MusicEngine.State.PAUSED) {
                                String title = engine.getCurrentTitle();
                                String author = engine.getCurrentAuthor();
                                String pos = engine.getFormattedPosition();
                                String dur = engine.getFormattedDuration();
                                String stateStr = state == MusicEngine.State.PAUSED ? " [PAUSED]" : "";
                                sendFeedback("Now Playing" + stateStr + ": " + author + " - " + title + "  [" + pos + " / " + dur + "]");
                            } else if (state == MusicEngine.State.LOADING) {
                                sendFeedback("Loading: " + engine.getStatusMessage());
                            } else if (state == MusicEngine.State.ERROR) {
                                sendError("Playback error: " + engine.getErrorMessage());
                            } else {
                                sendFeedback("Nothing is playing.");
                            }
                            return 1;
                        })
                    )

                    // /music queue
                    .then(literal("queue")
                        .executes(ctx -> {
                            Playlist playlist = PlaylistManager.getPlaylist();
                            if (playlist == null || playlist.getTracks().isEmpty()) {
                                sendFeedback("Playlist is empty. Add tracks via /music play <url> or open the GUI with /music open.");
                                return 1;
                            }
                            List<Track> tracks = playlist.getTracks();
                            sendFeedback("-- Playlist (" + tracks.size() + " tracks) --");
                            int currentIndex = 0;
                            for (int i = 0; i < tracks.size(); i++) {
                                Track t = tracks.get(i);
                                String marker = (i == currentIndex) ? " ◀ NOW" : "";
                                sendFeedback("  " + (i + 1) + ". " + t.getTitle() + marker);
                            }
                            return 1;
                        })
                    )

                    // /music help
                    .then(literal("help")
                        .executes(ctx -> {
                            sendFeedback("=== Music Player Commands ===");
                            sendFeedback("/music open          — Open the GUI");
                            sendFeedback("/music play <url>    — Play a URL, YouTube/SoundCloud link, or local file path");
                            sendFeedback("/music pause         — Toggle pause/resume");
                            sendFeedback("/music stop          — Stop playback");
                            sendFeedback("/music next          — Skip to next track");
                            sendFeedback("/music prev          — Previous track");
                            sendFeedback("/music volume        — Show current volume");
                            sendFeedback("/music volume <0-100>— Set volume");
                            sendFeedback("/music nowplaying    — Show currently playing track");
                            sendFeedback("/music queue         — List playlist tracks");
                            sendFeedback("Keybind: F8 also opens the GUI directly.");
                            return 1;
                        })
                    )

                    // /music (no subcommand) → open GUI
                    .executes(ctx -> {
                        MinecraftClient client = MinecraftClient.getInstance();
                        client.execute(() -> client.setScreen(new MusicPlayerScreen()));
                        sendFeedback("Opening Music Player... (tip: /music help for all commands)");
                        return 1;
                    })
            );
        });
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static void sendFeedback(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                Text.literal("[MusicPlayer] ").formatted(Formatting.GREEN)
                    .append(Text.literal(message).formatted(Formatting.WHITE)),
                false
            );
        }
    }

    private static void sendError(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                Text.literal("[MusicPlayer] ").formatted(Formatting.RED)
                    .append(Text.literal(message).formatted(Formatting.RED)),
                false
            );
        }
    }
}
