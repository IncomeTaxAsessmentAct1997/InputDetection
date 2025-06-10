package duncanjones.kpd;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

public class KeyPressDetectorClient implements ClientModInitializer {
	private static final Map<Integer, Boolean> prevKeyStates = new HashMap<>();
	private static final Map<Integer, Long> keyHoldStartTimes = new HashMap<>();

	@Override
	public void onInitializeClient() {
		KeyPressDetector.LOGGER.info("Initializing Key Press Detector Client");

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.getWindow() == null) return;

			for (int keyCode = 0; keyCode <= 255; keyCode++) {
				boolean currentState = GLFW.glfwGetKey(client.getWindow().getHandle(), keyCode) == GLFW.GLFW_PRESS;
				boolean previousState = prevKeyStates.getOrDefault(keyCode, false);

				if (currentState && !previousState) {
					sendKeyEvent(keyCode, "press");
					keyHoldStartTimes.put(keyCode, System.currentTimeMillis());
				}
				else if (!currentState && previousState) {
					sendKeyEvent(keyCode, "release");
					keyHoldStartTimes.remove(keyCode);
				}
				else if (currentState) {
					Long startTime = keyHoldStartTimes.get(keyCode);
					if (startTime != null && (System.currentTimeMillis() - startTime) >= 500) {
						sendKeyEvent(keyCode, "hold");
						keyHoldStartTimes.remove(keyCode);
					}
				}

				prevKeyStates.put(keyCode, currentState);
			}
		});
	}

	private void sendKeyEvent(int keyCode, String eventType) {
		KeyPressPacket packet = new KeyPressPacket(keyCode, eventType);
		ClientPlayNetworking.send(packet);
	}
}