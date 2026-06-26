package com.musicplayer.player;

import com.musicplayer.MusicPlayerMod;
import com.musicplayer.playlist.Playlist;
import com.musicplayer.playlist.PlaylistManager;
import com.musicplayer.playlist.Track;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.SoundSystem;

import javax.sound.sampled.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Core audio engine backed by LavaPlayer.
 * Plays audio via Java's javax.sound (SourceDataLine) to the system audio output.
 * Supports YouTube, SoundCloud, Bandcamp, Vimeo, Twitch, local files, and HTTP streams.
 */
public class MusicEngine {

    public enum State { IDLE, LOADING, PLAYING, PAUSED, ERROR }

    private final AudioPlayerManager manager;
    private final AudioPlayer player;

    private State state = State.IDLE;
    private String statusMessage = "";
    private String errorMessage = "";

    // Volume 0-100
    private final AtomicInteger volume = new AtomicInteger(80);

    // Playback thread
    private Thread playbackThread;
    private SourceDataLine audioLine;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // Currently playing LavaPlayer track
    private AudioTrack currentLavaTrack;

    // Callback fired when a track ends (used to auto-advance playlist)
    private Consumer<AudioTrackEndReason> onTrackEnd;

    public MusicEngine() {
        manager = new DefaultAudioPlayerManager();

        // Register ALL sources: YouTube, SoundCloud, Bandcamp, Vimeo, Twitch, HTTP, local files
        AudioSourceManagers.registerRemoteSources(manager);
        AudioSourceManagers.registerLocalSource(manager);

        player = manager.createPlayer();
        player.setVolume(volume.get());

        player.addListener(new AudioEventAdapter() {
            @Override
            public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
                if (endReason.mayStartNext) {
                    if (onTrackEnd != null) onTrackEnd.accept(endReason);
                }
            }

            @Override
            public void onTrackException(AudioPlayer player, AudioTrack track,
                    com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception) {
                state = State.ERROR;
                errorMessage = exception.getMessage();
                MusicPlayerMod.LOGGER.error("[MusicPlayer] Track error: {}", exception.getMessage());
            }

            @Override
            public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
                state = State.ERROR;
                errorMessage = "Track stuck — skipping...";
                if (onTrackEnd != null) onTrackEnd.accept(AudioTrackEndReason.LOAD_FAILED);
            }
        });

        // Auto-advance: when a track ends, play the next one in the playlist
        onTrackEnd = reason -> {
            Playlist pl = PlaylistManager.getPlaylist();
            Track next = pl.next();
            if (next != null) {
                loadAndPlay(next.getSource());
            } else {
                state = State.IDLE;
                stopAudioLine();
            }
        };

        startPlaybackThread();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Load and play a URL or file path. */
    public void loadAndPlay(String source) {
        if (source == null || source.isBlank()) return;
        state = State.LOADING;
        statusMessage = "Loading...";
        errorMessage = "";

        manager.loadItem(source, new PlayerLoadResultHandler(this, source));
    }

    /** Called by PlayerLoadResultHandler once the track is ready. */
    void onTrackLoaded(AudioTrack track) {
        player.playTrack(track);
        currentLavaTrack = track;
        state = State.PLAYING;
        statusMessage = track.getInfo().title;
        ensureAudioLineOpen();
    }

    void onLoadFailed(String reason) {
        state = State.ERROR;
        errorMessage = reason;
        statusMessage = "Error: " + reason;
    }

    public void togglePause() {
        if (state == State.PLAYING) {
            player.setPaused(true);
            state = State.PAUSED;
        } else if (state == State.PAUSED) {
            player.setPaused(false);
            state = State.PLAYING;
        }
    }

    public void pause() {
        if (state == State.PLAYING) {
            player.setPaused(true);
            state = State.PAUSED;
        }
    }

    public void resume() {
        if (state == State.PAUSED) {
            player.setPaused(false);
            state = State.PLAYING;
        }
    }

    public void stop() {
        player.stopTrack();
        currentLavaTrack = null;
        state = State.IDLE;
        statusMessage = "";
        stopAudioLine();
    }

    public void skipNext() {
        Playlist pl = PlaylistManager.getPlaylist();
        Track next = pl.next();
        if (next != null) loadAndPlay(next.getSource());
        else stop();
    }

    public void skipPrevious() {
        Playlist pl = PlaylistManager.getPlaylist();
        Track prev = pl.previous();
        if (prev != null) loadAndPlay(prev.getSource());
    }

    public void playFromPlaylist(int index) {
        Playlist pl = PlaylistManager.getPlaylist();
        pl.jumpTo(index);
        Track t = pl.getCurrentTrack();
        if (t != null) loadAndPlay(t.getSource());
    }

    /** Seek to position (ms). Only works on seekable tracks. */
    public void seekTo(long positionMs) {
        if (currentLavaTrack != null && currentLavaTrack.isSeekable()) {
            currentLavaTrack.setPosition(positionMs);
        }
    }

    public void setVolume(int vol) {
        int clamped = Math.max(0, Math.min(150, vol));
        volume.set(clamped);
        player.setVolume(clamped);
    }

    // ── Audio output via javax.sound ──────────────────────────────────────────

    private void ensureAudioLineOpen() {
        if (audioLine != null && audioLine.isOpen()) return;
        try {
            AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    48000, 16, 2, 4, 48000, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            audioLine = (SourceDataLine) AudioSystem.getLine(info);
            audioLine.open(format, 4096);
            audioLine.start();
        } catch (LineUnavailableException e) {
            MusicPlayerMod.LOGGER.error("[MusicPlayer] Cannot open audio line", e);
            state = State.ERROR;
            errorMessage = "Audio device unavailable";
        }
    }

    private void stopAudioLine() {
        if (audioLine != null) {
            audioLine.drain();
            audioLine.stop();
            audioLine.close();
            audioLine = null;
        }
    }

    private void startPlaybackThread() {
        running.set(true);
        playbackThread = new Thread(() -> {
            byte[] buffer = new byte[4096];
            while (running.get()) {
                if (state == State.PLAYING && audioLine != null && audioLine.isOpen()) {
                    AudioFrame frame = player.provide();
                    if (frame != null) {
                        byte[] data = frame.getData();
                        audioLine.write(data, 0, data.length);
                    } else {
                        sleepMs(5);
                    }
                } else {
                    sleepMs(20);
                }
            }
        }, "MusicPlayer-Playback");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    private void sleepMs(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    public void shutdown() {
        running.set(false);
        stop();
        manager.shutdown();
    }

    // ── State accessors ───────────────────────────────────────────────────────

    public State getState()           { return state; }
    public String getStatusMessage()  { return statusMessage; }
    public String getErrorMessage()   { return errorMessage; }
    public int getVolume()            { return volume.get(); }
    public boolean isPlaying()        { return state == State.PLAYING; }
    public boolean isPaused()         { return state == State.PAUSED; }

    /** Position in ms, or -1 if no track. */
    public long getPosition() {
        return currentLavaTrack != null ? currentLavaTrack.getPosition() : -1;
    }

    /** Duration in ms, or -1 if unknown/stream. */
    public long getDuration() {
        return currentLavaTrack != null ? currentLavaTrack.getDuration() : -1;
    }

    /** 0.0–1.0 progress, or -1 for streams. */
    public float getProgress() {
        long dur = getDuration();
        if (dur <= 0) return -1f;
        return (float) getPosition() / dur;
    }

    public String getCurrentTitle() {
        if (currentLavaTrack == null) return "";
        return currentLavaTrack.getInfo().title;
    }

    public String getCurrentAuthor() {
        if (currentLavaTrack == null) return "";
        return currentLavaTrack.getInfo().author;
    }

    public String getFormattedPosition() {
        long pos = getPosition();
        if (pos < 0) return "--:--";
        long s = pos / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    public String getFormattedDuration() {
        long dur = getDuration();
        if (dur <= 0) return "LIVE";
        long s = dur / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
