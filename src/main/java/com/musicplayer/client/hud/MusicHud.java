package com.musicplayer.client.hud;

import com.musicplayer.MusicPlayerMod;
import com.musicplayer.config.ModConfig;
import com.musicplayer.player.MusicEngine;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Renders the "Now Playing" mini-overlay in-game.
 * Shows title, author, progress bar, and controls hint.
 */
public class MusicHud {

    private static final int WIDTH  = 160;
    private static final int HEIGHT = 38;
    private static final int MARGIN = 4;
    private static final int BG_COLOR      = 0xB0000000;
    private static final int BAR_BG_COLOR  = 0xFF333333;
    private static final int BAR_FG_COLOR  = 0xFF44CC44;
    private static final int TITLE_COLOR   = 0xFFFFFFFF;
    private static final int SUB_COLOR     = 0xFFAAAAAA;

    public void render(DrawContext ctx, RenderTickCounter tickCounter) {
        ModConfig cfg = MusicPlayerMod.config;
        if (!cfg.showHud) return;

        MusicEngine engine = MusicPlayerMod.engine;
        if (!engine.isPlaying() && !engine.isPaused()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return; // hide when any screen is open

        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();

        // Calculate position
        int x, y;
        switch (cfg.hudPosition) {
            case TOP_LEFT     -> { x = MARGIN; y = MARGIN; }
            case BOTTOM_LEFT  -> { x = MARGIN; y = screenH - HEIGHT - MARGIN; }
            case BOTTOM_RIGHT -> { x = screenW - WIDTH - MARGIN; y = screenH - HEIGHT - MARGIN; }
            default           -> { x = screenW - WIDTH - MARGIN; y = MARGIN; } // TOP_RIGHT
        }

        // Background
        ctx.fill(x, y, x + WIDTH, y + HEIGHT, BG_COLOR);

        // Title (truncated)
        String title = engine.getCurrentTitle();
        if (title == null || title.isBlank()) title = "Unknown Track";
        if (title.length() > 24) title = title.substring(0, 22) + "…";
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal(title).formatted(Formatting.WHITE),
                x + 4, y + 4, TITLE_COLOR);

        // Author
        String author = engine.getCurrentAuthor();
        if (author != null && !author.isBlank()) {
            if (author.length() > 28) author = author.substring(0, 26) + "…";
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal(author).formatted(Formatting.GRAY),
                    x + 4, y + 14, SUB_COLOR);
        }

        // Progress bar
        int barX = x + 4;
        int barY = y + 26;
        int barW = WIDTH - 8;
        int barH = 4;

        ctx.fill(barX, barY, barX + barW, barY + barH, BAR_BG_COLOR);

        float progress = engine.getProgress();
        if (progress >= 0) {
            int filled = (int) (barW * progress);
            ctx.fill(barX, barY, barX + filled, barY + barH, BAR_FG_COLOR);
        }

        // Time
        String time = engine.getFormattedPosition() + " / " + engine.getFormattedDuration();
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal(time).formatted(Formatting.GRAY),
                x + 4, y + HEIGHT - 10, SUB_COLOR);

        // Paused indicator
        if (engine.isPaused()) {
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal("⏸").formatted(Formatting.YELLOW),
                    x + WIDTH - 14, y + 4, 0xFFFFFF00);
        }
    }
}
