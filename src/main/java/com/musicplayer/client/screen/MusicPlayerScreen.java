package com.musicplayer.client.screen;

import com.musicplayer.MusicPlayerMod;
import com.musicplayer.config.ModConfig;
import com.musicplayer.player.MusicEngine;
import com.musicplayer.playlist.Playlist;
import com.musicplayer.playlist.PlaylistManager;
import com.musicplayer.playlist.Track;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Main Music Player GUI screen.
 *
 * Layout:
 *  ┌──────────────────────────────────────────────┐
 *  │  🎵 Music Player          [×]                │
 *  │  [Playlist] [Search] [Settings]              │
 *  ├──────────────────────────────────────────────┤
 *  │  <tab content>                               │
 *  ├──────────────────────────────────────────────┤
 *  │  [◀◀] [▶/⏸] [⏹] [▶▶]   Vol [--][+]  🔀 🔁  │
 *  │  Now Playing: Track Title                    │
 *  │  ██████████░░░░░░░░░░  0:42 / 3:20           │
 *  └──────────────────────────────────────────────┘
 */
public class MusicPlayerScreen extends Screen {

    // ── Layout constants ─────────────────────────────────────────────────────
    private static final int W = 340;
    private static final int H = 230;
    private static final int TAB_H = 20;
    private static final int CTRL_H = 42;  // bottom control strip height
    private static final int SCROLL_AMOUNT = 10;

    // Colours
    private static final int BG           = 0xE0101010;
    private static final int PANEL_BG     = 0xFF1A1A2E;
    private static final int TAB_ACTIVE   = 0xFF16213E;
    private static final int TAB_INACTIVE = 0xFF0F3460;
    private static final int ACCENT       = 0xFF44CC44;
    private static final int BORDER       = 0xFF2A2A4A;
    private static final int TEXT_WHITE   = 0xFFFFFFFF;
    private static final int TEXT_GRAY    = 0xFFAAAAAA;
    private static final int TEXT_GREEN   = 0xFF44FF44;
    private static final int TEXT_YELLOW  = 0xFFFFFF44;
    private static final int TEXT_RED     = 0xFFFF4444;

    // ── State ────────────────────────────────────────────────────────────────
    private enum Tab { PLAYLIST, SEARCH, SETTINGS }
    private Tab activeTab = Tab.PLAYLIST;

    private int guiLeft, guiTop;
    private int scrollOffset = 0;

    // Widgets — playlist tab
    private TextFieldWidget urlField;
    private ButtonWidget addButton;
    private ButtonWidget clearButton;
    private ButtonWidget saveButton;
    private ButtonWidget loadButton;

    // Widgets — search tab
    private TextFieldWidget searchField;
    private ButtonWidget searchButton;
    private List<String> searchResults = List.of();
    private boolean searching = false;

    // Widgets — controls
    private ButtonWidget prevBtn, playPauseBtn, stopBtn, nextBtn;
    private ButtonWidget volDownBtn, volUpBtn;
    private ButtonWidget shuffleBtn, repeatBtn;

    // Widgets — tab
    private ButtonWidget tabPlaylist, tabSearch, tabSettings;

    // Settings toggles
    private ButtonWidget hudToggle;
    private ButtonWidget hudPosBtn;
    private ButtonWidget muteGameBtn;

    // Progress bar drag
    private boolean draggingProgress = false;

    public MusicPlayerScreen() {
        super(Text.translatable("musicplayer.title"));
    }

    // ── init ─────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        guiLeft = (width - W) / 2;
        guiTop  = (height - H) / 2;

        // ── Tab buttons ──
        int tabY = guiTop + 14;
        int tabW = 80;
        tabPlaylist = addButton(tabY, 0, tabW, "Playlist", b -> { activeTab = Tab.PLAYLIST; refreshWidgets(); });
        tabSearch   = addButton(tabY, tabW, tabW, "Search",   b -> { activeTab = Tab.SEARCH;   refreshWidgets(); });
        tabSettings = addButton(tabY, tabW*2, tabW, "Settings", b -> { activeTab = Tab.SETTINGS; refreshWidgets(); });
        addDrawableChild(tabPlaylist);
        addDrawableChild(tabSearch);
        addDrawableChild(tabSettings);

        // ── URL input (Playlist tab) ──
        urlField = new TextFieldWidget(textRenderer, guiLeft + 8, guiTop + 38, W - 70, 16,
                Text.translatable("musicplayer.add_url"));
        urlField.setMaxLength(512);
        urlField.setPlaceholder(Text.translatable("musicplayer.add_url.hint"));
        addDrawableChild(urlField);

        addButton2(guiTop + 38, W - 58, 50, "Add", b -> addTrackFromField());

        // Playlist management buttons
        clearButton = addButton2(guiTop + 36 + (H - TAB_H - CTRL_H - 20 - 16), 8,  60, "Clear All", b -> clearPlaylist());
        saveButton  = addButton2(guiTop + 36 + (H - TAB_H - CTRL_H - 20 - 16), 72, 60, "Save",      b -> savePlaylist());
        loadButton  = addButton2(guiTop + 36 + (H - TAB_H - CTRL_H - 20 - 16), 136,60, "Load",      b -> loadPlaylist());
        addDrawableChild(clearButton);
        addDrawableChild(saveButton);
        addDrawableChild(loadButton);

        // ── Search tab ──
        searchField = new TextFieldWidget(textRenderer, guiLeft + 8, guiTop + 38, W - 80, 16,
                Text.translatable("musicplayer.search.hint"));
        searchField.setMaxLength(256);
        searchField.setPlaceholder(Text.translatable("musicplayer.search.hint"));
        addDrawableChild(searchField);
        addButton2(guiTop + 38, W - 68, 60, "Search", b -> performSearch());

        // ── Settings tab ──
        hudToggle   = addButton2(guiTop + 50, 8, 150, "HUD: ON",  b -> toggleHud());
        hudPosBtn   = addButton2(guiTop + 70, 8, 150, "Position: Top Right", b -> cycleHudPos());
        muteGameBtn = addButton2(guiTop + 90, 8, 150, "Mute Game Music: OFF", b -> toggleMuteGame());
        addDrawableChild(hudToggle);
        addDrawableChild(hudPosBtn);
        addDrawableChild(muteGameBtn);

        // ── Bottom controls ──
        int ctrlY = guiTop + H - CTRL_H + 4;
        prevBtn      = addCtrl(ctrlY, 8,  26, "⏮", b -> MusicPlayerMod.engine.skipPrevious());
        playPauseBtn = addCtrl(ctrlY, 36, 36, "▶", b -> MusicPlayerMod.engine.togglePause());
        stopBtn      = addCtrl(ctrlY, 74, 26, "⏹", b -> MusicPlayerMod.engine.stop());
        nextBtn      = addCtrl(ctrlY, 102,26, "⏭", b -> MusicPlayerMod.engine.skipNext());
        volDownBtn   = addCtrl(ctrlY, 136,18, "−", b -> changeVolume(-5));
        volUpBtn     = addCtrl(ctrlY, 156,18, "+", b -> changeVolume(5));
        shuffleBtn   = addCtrl(ctrlY, 200,30, "⇀", b -> toggleShuffle());
        repeatBtn    = addCtrl(ctrlY, 232,30, "↻", b -> cycleRepeat());
        addDrawableChild(prevBtn);
        addDrawableChild(playPauseBtn);
        addDrawableChild(stopBtn);
        addDrawableChild(nextBtn);
        addDrawableChild(volDownBtn);
        addDrawableChild(volUpBtn);
        addDrawableChild(shuffleBtn);
        addDrawableChild(repeatBtn);

        refreshWidgets();
    }

    // ── Helpers to build buttons without lambda field capture issues ──────────

    private ButtonWidget addButton(int y, int xOffset, int w, String label, ButtonWidget.PressAction action) {
        return ButtonWidget.builder(Text.literal(label), action)
                .dimensions(guiLeft + xOffset, y, w, 12).build();
    }

    private ButtonWidget addButton2(int y, int xOffset, int w, String label, ButtonWidget.PressAction action) {
        return ButtonWidget.builder(Text.literal(label), action)
                .dimensions(guiLeft + xOffset, y, w, 14).build();
    }

    private ButtonWidget addCtrl(int y, int xOffset, int w, String label, ButtonWidget.PressAction action) {
        return ButtonWidget.builder(Text.literal(label), action)
                .dimensions(guiLeft + xOffset, y, w, 16).build();
    }

    // ── Widget visibility by tab ──────────────────────────────────────────────

    private void refreshWidgets() {
        boolean pl = activeTab == Tab.PLAYLIST;
        boolean sr = activeTab == Tab.SEARCH;
        boolean st = activeTab == Tab.SETTINGS;

        urlField.setVisible(pl);
        clearButton.visible = pl;
        saveButton.visible  = pl;
        loadButton.visible  = pl;

        searchField.setVisible(sr);

        hudToggle.visible   = st;
        hudPosBtn.visible   = st;
        muteGameBtn.visible = st;

        updateSettingsLabels();
    }

    private void updateSettingsLabels() {
        ModConfig cfg = MusicPlayerMod.config;
        hudToggle.setMessage(Text.literal("HUD: " + (cfg.showHud ? "§aON" : "§cOFF")));
        hudPosBtn.setMessage(Text.literal("Position: " + cfg.hudPosition.name().replace('_', ' ')));
        muteGameBtn.setMessage(Text.literal("Mute Game Music: " + (cfg.muteGameMusic ? "§aON" : "§cOFF")));
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Dim background
        renderBackground(ctx, mouseX, mouseY, delta);

        int x = guiLeft, y = guiTop;

        // Main background
        ctx.fill(x, y, x + W, y + H, BG);
        // Border
        drawBorder(ctx, x, y, W, H, BORDER);

        // Title bar
        ctx.fill(x, y, x + W, y + 13, PANEL_BG);
        ctx.drawTextWithShadow(textRenderer, Text.literal("♪ Music Player"), x + 6, y + 3, TEXT_WHITE);

        // Tab bar background
        ctx.fill(x, y + 13, x + W, y + 27, TAB_INACTIVE);

        // Tab highlight
        int tabW = 80;
        int activeTabX = x + (activeTab == Tab.PLAYLIST ? 0 : activeTab == Tab.SEARCH ? tabW : tabW * 2);
        ctx.fill(activeTabX, y + 13, activeTabX + tabW, y + 27, TAB_ACTIVE);

        // Content area background
        ctx.fill(x, y + 27, x + W, y + H - CTRL_H, PANEL_BG);

        // ── Render tab content ──
        switch (activeTab) {
            case PLAYLIST -> renderPlaylistTab(ctx, mouseX, mouseY);
            case SEARCH   -> renderSearchTab(ctx, mouseX, mouseY);
            case SETTINGS -> renderSettingsTab(ctx, mouseX, mouseY);
        }

        // ── Controls strip ──
        renderControlsStrip(ctx, mouseX, mouseY);

        // Draw all child widgets last
        super.render(ctx, mouseX, mouseY, delta);

        updatePlayPauseButton();
    }

    // ── Playlist tab ──────────────────────────────────────────────────────────

    private void renderPlaylistTab(DrawContext ctx, int mouseX, int mouseY) {
        Playlist pl = PlaylistManager.getPlaylist();
        int x = guiLeft, y = guiTop + 57;
        int listH = H - CTRL_H - 57 - 18;
        int itemH = 14;
        int visibleItems = listH / itemH;

        if (pl.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("musicplayer.playlist.empty").formatted(Formatting.GRAY),
                    x + W / 2, y + 30, TEXT_GRAY);
            return;
        }

        List<Track> tracks = pl.getTracks();
        int maxScroll = Math.max(0, tracks.size() - visibleItems);
        scrollOffset = Math.min(scrollOffset, maxScroll);

        for (int i = 0; i < visibleItems && (i + scrollOffset) < tracks.size(); i++) {
            int trackIdx = i + scrollOffset;
            Track t = tracks.get(trackIdx);
            int ty = y + i * itemH;
            boolean isCurrent = trackIdx == pl.getCurrentIndex();

            // Row background
            if (isCurrent) ctx.fill(x + 4, ty, x + W - 4, ty + itemH, 0x33AAFFAA);
            else if (trackIdx % 2 == 0) ctx.fill(x + 4, ty, x + W - 4, ty + itemH, 0x11FFFFFF);

            // Hover highlight
            if (mouseX >= x + 4 && mouseX < x + W - 4 && mouseY >= ty && mouseY < ty + itemH) {
                ctx.fill(x + 4, ty, x + W - 4, ty + itemH, 0x22FFFFFF);
            }

            // Track number
            ctx.drawTextWithShadow(textRenderer, Text.literal((trackIdx + 1) + "."),
                    x + 6, ty + 3, isCurrent ? TEXT_GREEN : TEXT_GRAY);

            // Title
            String title = t.getDisplayTitle();
            if (title.length() > 30) title = title.substring(0, 28) + "…";
            ctx.drawTextWithShadow(textRenderer, Text.literal(title),
                    x + 22, ty + 3, isCurrent ? TEXT_GREEN : TEXT_WHITE);

            // Duration (right-aligned)
            String dur = t.getFormattedDuration();
            int durW = textRenderer.getWidth(dur);
            ctx.drawTextWithShadow(textRenderer, Text.literal(dur),
                    x + W - durW - 24, ty + 3, TEXT_GRAY);

            // Remove button [×]
            ctx.drawTextWithShadow(textRenderer, Text.literal("×").formatted(Formatting.RED),
                    x + W - 14, ty + 3, TEXT_RED);
        }

        // Scroll indicator
        if (tracks.size() > visibleItems) {
            int scrollBarX = x + W - 4;
            int scrollBarH = listH;
            float ratio = (float) scrollOffset / maxScroll;
            int indicatorH = Math.max(10, (int)(scrollBarH * ((float)visibleItems / tracks.size())));
            int indicatorY = y + (int)((scrollBarH - indicatorH) * ratio);
            ctx.fill(scrollBarX, y, scrollBarX + 2, y + scrollBarH, 0x44FFFFFF);
            ctx.fill(scrollBarX, indicatorY, scrollBarX + 2, indicatorY + indicatorH, 0xAAFFFFFF);
        }
    }

    // ── Search tab ────────────────────────────────────────────────────────────

    private void renderSearchTab(DrawContext ctx, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop + 60;
        if (searching) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("musicplayer.loading").formatted(Formatting.YELLOW),
                    x + W / 2, y + 20, TEXT_YELLOW);
            return;
        }
        if (searchResults.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Type to search YouTube or SoundCloud").formatted(Formatting.GRAY),
                    x + W / 2, y + 20, TEXT_GRAY);
            return;
        }
        for (int i = 0; i < searchResults.size(); i++) {
            String r = searchResults.get(i);
            int ty = y + i * 14;
            if (mouseX >= x + 4 && mouseX < x + W - 4 && mouseY >= ty && mouseY < ty + 14)
                ctx.fill(x + 4, ty, x + W - 4, ty + 14, 0x33FFFFFF);
            ctx.drawTextWithShadow(textRenderer, Text.literal(r), x + 8, ty + 3, TEXT_WHITE);
        }
    }

    // ── Settings tab ──────────────────────────────────────────────────────────

    private void renderSettingsTab(DrawContext ctx, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop + 38;
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Volume: " + MusicPlayerMod.engine.getVolume() + "%"),
                x + 8, y + 8, TEXT_WHITE);
    }

    // ── Controls strip ────────────────────────────────────────────────────────

    private void renderControlsStrip(DrawContext ctx, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop + H - CTRL_H;
        ctx.fill(x, y, x + W, y + CTRL_H, 0xFF0D0D1A);
        drawBorder(ctx, x, y, W, CTRL_H, BORDER);

        MusicEngine engine = MusicPlayerMod.engine;

        // Now Playing label
        String nowPlaying;
        if (engine.isPlaying() || engine.isPaused()) {
            String title = engine.getCurrentTitle();
            if (title != null && title.length() > 38) title = title.substring(0, 36) + "…";
            nowPlaying = (engine.isPaused() ? "⏸ " : "♪ ") + (title != null ? title : "");
        } else {
            nowPlaying = "Nothing Playing";
        }
        ctx.drawTextWithShadow(textRenderer, Text.literal(nowPlaying),
                x + 6, y + 20, engine.isPlaying() ? TEXT_GREEN : TEXT_GRAY);

        // Volume display
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Vol:" + engine.getVolume()),
                x + 176, y + 4, TEXT_GRAY);

        // Progress bar
        int barX = x + 6, barY = y + 31, barW = W - 12, barH = 4;
        ctx.fill(barX, barY, barX + barW, barY + barH, 0xFF333333);
        float prog = engine.getProgress();
        if (prog >= 0) {
            ctx.fill(barX, barY, barX + (int)(barW * prog), barY + barH, ACCENT);
            // Scrubber dot
            int dotX = barX + (int)(barW * prog);
            ctx.fill(dotX - 2, barY - 1, dotX + 2, barY + barH + 1, 0xFFFFFFFF);
        }

        // Time
        String time = engine.getFormattedPosition() + " / " + engine.getFormattedDuration();
        int timeW = textRenderer.getWidth(time);
        ctx.drawTextWithShadow(textRenderer, Text.literal(time), x + W - timeW - 4, y + 20, TEXT_GRAY);

        // Shuffle/Repeat colours
        Playlist pl = PlaylistManager.getPlaylist();
        shuffleBtn.setMessage(Text.literal(pl.isShuffle() ? "§a⇀" : "§7⇀"));
        if (pl.isRepeatOne()) repeatBtn.setMessage(Text.literal("§a↻¹"));
        else if (pl.isRepeatAll()) repeatBtn.setMessage(Text.literal("§a↻"));
        else repeatBtn.setMessage(Text.literal("§7↻"));
    }

    private void updatePlayPauseButton() {
        MusicEngine engine = MusicPlayerMod.engine;
        playPauseBtn.setMessage(Text.literal(engine.isPaused() ? "▶" : (engine.isPlaying() ? "⏸" : "▶")));
    }

    // ── Mouse events ──────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Progress bar click → seek
        int barX = guiLeft + 6;
        int barY = guiTop + H - CTRL_H + 31;
        int barW = W - 12;
        if (mouseX >= barX && mouseX <= barX + barW && mouseY >= barY - 4 && mouseY <= barY + 8) {
            float pct = (float)(mouseX - barX) / barW;
            long dur = MusicPlayerMod.engine.getDuration();
            if (dur > 0) {
                MusicPlayerMod.engine.seekTo((long)(dur * pct));
                return true;
            }
        }

        // Playlist item click
        if (activeTab == Tab.PLAYLIST) {
            int listY = guiTop + 57;
            int itemH = 14;
            Playlist pl = PlaylistManager.getPlaylist();
            int visibleItems = (H - CTRL_H - 57 - 18) / itemH;
            for (int i = 0; i < visibleItems; i++) {
                int trackIdx = i + scrollOffset;
                if (trackIdx >= pl.size()) break;
                int ty = listY + i * itemH;
                // Remove button
                if (mouseX >= guiLeft + W - 18 && mouseX <= guiLeft + W - 4
                        && mouseY >= ty && mouseY < ty + itemH && button == 0) {
                    pl.removeTrack(trackIdx);
                    PlaylistManager.saveDefault();
                    return true;
                }
                // Play on double-click (handled via single click for simplicity)
                if (mouseX >= guiLeft + 4 && mouseX < guiLeft + W - 20
                        && mouseY >= ty && mouseY < ty + itemH && button == 0) {
                    MusicPlayerMod.engine.playFromPlaylist(trackIdx);
                    return true;
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (activeTab == Tab.PLAYLIST) {
            scrollOffset = Math.max(0, scrollOffset - (int)Math.signum(verticalAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void addTrackFromField() {
        String src = urlField.getText().trim();
        if (src.isBlank()) return;

        Playlist pl = PlaylistManager.getPlaylist();
        // Add as a pending track with source as the URL — LavaPlayer will resolve title
        pl.addTrack(new Track(src, "", src, -1));
        PlaylistManager.saveDefault();
        urlField.setText("");

        // If nothing is playing, start this track
        if (!MusicPlayerMod.engine.isPlaying() && !MusicPlayerMod.engine.isPaused()) {
            MusicPlayerMod.engine.playFromPlaylist(pl.getCurrentIndex());
        }
    }

    private void performSearch() {
        String query = searchField.getText().trim();
        if (query.isBlank()) return;
        searching = true;
        searchResults = List.of();

        // LavaPlayer YouTube search prefix: "ytsearch:query" or "scsearch:query"
        String searchSrc = (query.startsWith("sc:") ? "scsearch:" : "ytsearch:") + query;

        // Run load in background thread, results come back via callback
        new Thread(() -> {
            MusicPlayerMod.engine.loadAndPlay(searchSrc);
            searching = false;
        }, "MusicPlayer-Search").start();
    }

    private void clearPlaylist() {
        PlaylistManager.getPlaylist().clear();
        MusicPlayerMod.engine.stop();
        PlaylistManager.saveDefault();
    }

    private void savePlaylist() {
        PlaylistManager.save("default");
    }

    private void loadPlaylist() {
        PlaylistManager.load("default");
    }

    private void changeVolume(int delta) {
        int newVol = MusicPlayerMod.engine.getVolume() + delta;
        MusicPlayerMod.engine.setVolume(newVol);
        MusicPlayerMod.config.volume = MusicPlayerMod.engine.getVolume();
        MusicPlayerMod.config.save();
    }

    private void toggleShuffle() {
        Playlist pl = PlaylistManager.getPlaylist();
        pl.setShuffle(!pl.isShuffle());
    }

    private void cycleRepeat() {
        Playlist pl = PlaylistManager.getPlaylist();
        if (!pl.isRepeatAll() && !pl.isRepeatOne()) pl.setRepeatAll(true);
        else if (pl.isRepeatAll()) { pl.setRepeatAll(false); pl.setRepeatOne(true); }
        else { pl.setRepeatOne(false); }
    }

    private void toggleHud() {
        MusicPlayerMod.config.showHud = !MusicPlayerMod.config.showHud;
        MusicPlayerMod.config.save();
        updateSettingsLabels();
    }

    private void cycleHudPos() {
        ModConfig.HudPosition[] positions = ModConfig.HudPosition.values();
        int idx = (MusicPlayerMod.config.hudPosition.ordinal() + 1) % positions.length;
        MusicPlayerMod.config.hudPosition = positions[idx];
        MusicPlayerMod.config.save();
        updateSettingsLabels();
    }

    private void toggleMuteGame() {
        MusicPlayerMod.config.muteGameMusic = !MusicPlayerMod.config.muteGameMusic;
        MusicPlayerMod.config.save();
        updateSettingsLabels();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);        // top
        ctx.fill(x, y + h - 1, x + w, y + h, color); // bottom
        ctx.fill(x, y, x + 1, y + h, color);        // left
        ctx.fill(x + w - 1, y, x + w, y + h, color); // right
    }

    @Override
    public boolean shouldPause() { return false; } // music continues while screen open
}
