package duncanjones.kpd;  // Package declaration

import net.fabricmc.api.ModInitializer;  // Interface for mod initialization
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;  // Server lifecycle events
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;  // Packet type registration
import org.slf4j.Logger;  // Logger interface
import org.slf4j.LoggerFactory;  // Logger factory

public class KeyPressDetector implements ModInitializer {
    public static final String MOD_ID = "key-press-detector";  // Mod identifier
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);  // Logger instance

    @Override
    public void onInitialize() {
        // Register packet types for client-server communication
        PayloadTypeRegistry.playC2S().register(KeyPressPacket.ID, KeyPressPacket.CODEC);  // Client to server
        PayloadTypeRegistry.playS2C().register(KeyPressPacket.ID, KeyPressPacket.CODEC);  // Server to client

        KeyDetectionCommand.register();  // Register commands
        KeyPressPacket.registerServerReceiver();  // Register packet handler

        // Clean up resources when server stops
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            KeyDetectionManager.shutdown();  // Shutdown scheduled executor
            LOGGER.info("Key Press Detector resources cleaned up");
        });
    }
}
