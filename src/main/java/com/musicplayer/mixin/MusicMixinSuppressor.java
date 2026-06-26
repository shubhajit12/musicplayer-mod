package com.musicplayer.mixin;

import com.musicplayer.MusicPlayerMod;
import net.minecraft.client.sound.MusicTracker;
import net.minecraft.sound.MusicSound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optionally suppresses Minecraft's built-in background music
 * when "Mute Game Music" is enabled in the mod settings.
 */
@Mixin(MusicTracker.class)
public class MusicMixinSuppressor {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(CallbackInfo ci) {
        if (MusicPlayerMod.config != null
                && MusicPlayerMod.config.muteGameMusic
                && (MusicPlayerMod.engine.isPlaying() || MusicPlayerMod.engine.isPaused())) {
            ci.cancel();
        }
    }
}
