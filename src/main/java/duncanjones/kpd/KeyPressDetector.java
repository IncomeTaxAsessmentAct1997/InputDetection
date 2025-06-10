package duncanjones.kpd;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KeyPressDetector implements ModInitializer {
	public static final String MOD_ID = "key-press-detector";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.playC2S().register(KeyPressPacket.ID, KeyPressPacket.CODEC);
		PayloadTypeRegistry.playS2C().register(KeyPressPacket.ID, KeyPressPacket.CODEC);

		KeyDetectionCommand.register();
		KeyPressPacket.registerServerReceiver();
	}
}