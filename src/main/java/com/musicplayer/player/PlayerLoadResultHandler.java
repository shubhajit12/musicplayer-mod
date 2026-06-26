package com.musicplayer.player;

import com.musicplayer.MusicPlayerMod;
import com.musicplayer.playlist.Playlist;
import com.musicplayer.playlist.PlaylistManager;
import com.musicplayer.playlist.Track;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

public class PlayerLoadResultHandler implements AudioLoadResultHandler {

    private final MusicEngine engine;
    private final String source;

    public PlayerLoadResultHandler(MusicEngine engine, String source) {
        this.engine = engine;
        this.source = source;
    }

    @Override
    public void trackLoaded(AudioTrack track) {
        MusicPlayerMod.LOGGER.info("[MusicPlayer] Loaded: {}", track.getInfo().title);
        engine.onTrackLoaded(track);
    }

    @Override
    public void playlistLoaded(AudioPlaylist playlist) {
        // If it was a YouTube/SoundCloud search result, take the first item.
        // If it's an actual playlist URL, import all tracks.
        Playlist pl = PlaylistManager.getPlaylist();

        if (playlist.isSearchResult()) {
            // Search result — play the first hit
            if (!playlist.getTracks().isEmpty()) {
                AudioTrack first = playlist.getTracks().get(0);
                MusicPlayerMod.LOGGER.info("[MusicPlayer] Search result: {}", first.getInfo().title);
                engine.onTrackLoaded(first);
            } else {
                engine.onLoadFailed("No results found");
            }
        } else {
            // Full playlist — import all, play from beginning
            MusicPlayerMod.LOGGER.info("[MusicPlayer] Playlist: {} ({} tracks)",
                    playlist.getName(), playlist.getTracks().size());
            for (AudioTrack t : playlist.getTracks()) {
                pl.addTrack(new Track(
                        t.getInfo().title,
                        t.getInfo().author,
                        t.getInfo().uri,
                        t.getInfo().length
                ));
            }
            PlaylistManager.saveDefault();
            // Play the selected track (or first)
            AudioTrack selected = playlist.getSelectedTrack();
            engine.onTrackLoaded(selected != null ? selected : playlist.getTracks().get(0));
        }
    }

    @Override
    public void noMatches() {
        MusicPlayerMod.LOGGER.warn("[MusicPlayer] No matches for: {}", source);
        engine.onLoadFailed("No matches found for: " + source);
    }

    @Override
    public void loadFailed(FriendlyException ex) {
        MusicPlayerMod.LOGGER.error("[MusicPlayer] Load failed: {}", ex.getMessage());
        engine.onLoadFailed(ex.getMessage());
    }
}
