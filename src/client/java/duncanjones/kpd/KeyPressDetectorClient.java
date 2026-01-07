package duncanjones.kpd;  // Package declaration

import net.fabricmc.api.ClientModInitializer;  // Interface for client-side mod initialization
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;  // Event for client ticks
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;  // Client networking API
import org.lwjgl.glfw.GLFW;  // Low-level window/input library

import java.util.HashMap;
import java.util.Map;

public class KeyPressDetectorClient implements ClientModInitializer {
    // Map to store previous key states for detecting changes
    private static final Map<Integer, Boolean> prevKeyStates = new HashMap<>();
    // Map to track if a key is in sustained mode (active while held)
    private static final Map<Integer, Boolean> sustainedKeyActive = new HashMap<>();
    // Map to track when a key was first pressed for hold detection
    private static final Map<Integer, Long> keyHoldStartTimes = new HashMap<>();

    @Override
    public void onInitializeClient() {
        KeyPressDetector.LOGGER.info("Initializing Key Press Detector Client");

        // Register a callback that runs at the end of every client tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Check if the client has a player and a window
            if (client.player == null || client.getWindow() == null) return;

            // Loop through all possible key codes (32 to 348 covers most keys)
            for (int keyCode = 32; keyCode <= 348; keyCode++) {
                // Get current key state (pressed or not)
                boolean currentState = GLFW.glfwGetKey(client.getWindow().getHandle(), keyCode) == GLFW.GLFW_PRESS;
                // Get previous key state from our tracking map
                boolean previousState = prevKeyStates.getOrDefault(keyCode, false);
                // Check if this key is configured for sustained mode
                Boolean isSustainedActive = sustainedKeyActive.getOrDefault(keyCode, false);

                // Handle sustained mode: if key is held and in sustained mode, keep sending events
                if (isSustainedActive && currentState) {
                    // Continuously send sustained events while key is held
                    sendKeyEvent(keyCode, "sustained");
                }

                // Detect key press (key was just pressed)
                if (currentState && !previousState) {
                    sendKeyEvent(keyCode, "press");  // Send press event to server
                    keyHoldStartTimes.put(keyCode, System.currentTimeMillis());  // Record press time

                    // If this key should use sustained mode, mark it as active
                    if (shouldSendSustained(keyCode)) {  // This method needs implementation
                        sustainedKeyActive.put(keyCode, true);
                    }
                }
                // Detect key release (key was just released)
                else if (!currentState && previousState) {
                    sendKeyEvent(keyCode, "release");  // Send release event
                    keyHoldStartTimes.remove(keyCode);  // Remove timing info

                    // If key was in sustained mode, deactivate it
                    if (sustainedKeyActive.containsKey(keyCode)) {
                        sustainedKeyActive.put(keyCode, false);
                        sendKeyEvent(keyCode, "sustained_release");  // Special release for sustained mode
                    }
                }
                // Detect hold (key has been held for 500ms)
                else if (currentState) {
                    Long startTime = keyHoldStartTimes.get(keyCode);
                    if (startTime != null && (System.currentTimeMillis() - startTime) >= 500) {
                        sendKeyEvent(keyCode, "hold");  // Send hold event
                        keyHoldStartTimes.remove(keyCode);  // Clear timing to avoid repeated hold events
                    }
                }

                // Update the previous state for next tick
                prevKeyStates.put(keyCode, currentState);
            }
        });
    }

    // Send a key event packet to the server
    private void sendKeyEvent(int keyCode, String eventType) {
        KeyPressPacket packet = new KeyPressPacket(keyCode, eventType);
        ClientPlayNetworking.send(packet);  // Send via Fabric networking
    }
    
    // Check if a key should trigger sustained events (needs implementation)
    private boolean shouldSendSustained(int keyCode) {
        // TODO: Check server-side configuration for this key
        // Need to sync binding info from server or store locally
        return true;  // Temporary: always return true
    }
}
