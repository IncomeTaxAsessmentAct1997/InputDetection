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
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null || client.getWindow() == null) return;

			for (int keyCode = 32; keyCode <= 348; keyCode++) {
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
				prevKeyStates.put(keyCode, currentState);
			}
		});
	}

	private void sendKeyEvent(int keyCode, String eventType) {
		KeyPressPacket packet = new KeyPressPacket(keyCode, eventType);
		ClientPlayNetworking.send(packet);
	}
}
