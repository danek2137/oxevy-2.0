package me.alpha432.oxevy.mixin.render.gui;

import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents the keybind expansion exploit on signs.
 * 
 * Exploit: Server sends a sign with text like "key.oxevy.clickgui"
 * When client renders/edits the sign, Minecraft expands "key.*" patterns
 * to the actual keybind name (e.g., "clickgui").
 * 
 * This mixin sanitizes the text to prevent the expansion by escaping
 * the "key." prefix with a zero-width space or replacing it.
 * 
 * Targets AbstractSignEditScreen (intermediary: class_7743)
 * Methods (intermediary names):
 * - setMessage -> method_49913(String)
 * - getMessages -> method_45652(int) -> String[]
 * - validate/setMessage -> method_45658(String) -> boolean
 */
@Mixin(AbstractSignEditScreen.class)
public class MixinSignEditScreen {

    /**
     * Sanitize sign text when setting a message on a line.
     * Intermediary: method_49913(String)
     */
    @Inject(method = "method_49913", at = @At("HEAD"))
    private void onSetMessage(String message, CallbackInfo ci) {
        if (message == null) return;
        if (containsKeybindPattern(message)) {
            // Log the attempt for debugging
            System.out.println("[Oxevy] Blocked sign keybind exploit attempt (setMessage): " + message);
        }
    }

    /**
     * Sanitize text when getting messages from the sign to prevent
     * the expanded text from being sent back to the server.
     * Intermediary: method_45652(int) -> String[]
     */
    @Inject(method = "method_45652", at = @At("RETURN"), cancellable = true)
    private void onGetMessages(int line, CallbackInfoReturnable<String[]> cir) {
        String[] messages = cir.getReturnValue();
        if (messages == null) return;
        
        boolean modified = false;
        for (int i = 0; i < messages.length; i++) {
            String msg = messages[i];
            if (msg != null && containsKeybindPattern(msg)) {
                // Sanitize the message
                messages[i] = sanitizeString(msg);
                modified = true;
            }
        }
        
        if (modified) {
            cir.setReturnValue(messages);
        }
    }

    /**
     * Intercept message validation to prevent expanded keybinds from being accepted.
     * Intermediary: method_45658(String) -> boolean
     */
    @Inject(method = "method_45658", at = @At("HEAD"), cancellable = true)
    private void onValidateMessage(String message, CallbackInfoReturnable<Boolean> cir) {
        if (message != null && containsKeybindPattern(message)) {
            // Block the message from being set
            System.out.println("[Oxevy] Blocked sign keybind exploit attempt (validate): " + message);
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    /**
     * Check if text contains keybind patterns that could be exploited.
     * Patterns: key.oxevy.*, key.minecraft.*, key.keyboard.*, etc.
     */
    private boolean containsKeybindPattern(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("key.oxevy.") || 
               lower.contains("key.minecraft.") ||
               lower.contains("key.keyboard.") ||
               lower.contains("key.mouse.") ||
               lower.contains("key.gamepad.");
    }

    /**
     * Sanitize text by replacing keybind patterns with a safe version.
     * Replaces "key." with "key\u200B." (zero-width space) to prevent expansion.
     */
    private String sanitizeString(String text) {
        // Replace key. with key​. (zero-width space) to break the pattern
        return text.replaceAll("(?i)key\\.", "key\u200B.");
    }
}