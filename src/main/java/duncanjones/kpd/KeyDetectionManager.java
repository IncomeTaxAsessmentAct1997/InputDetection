package duncanjones.kpd;

import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KeyDetectionManager {
	private static final Map<String, List<KeyBinding>> bindings = new ConcurrentHashMap<>();

	public static void addBinding(KeyBinding binding) {
		String key = createKey(binding.player(), binding.keyCode(), binding.keyType());
		bindings.computeIfAbsent(key, k -> new ArrayList<>()).add(binding);
	}

	public static boolean removeBinding(ServerPlayerEntity player, int keyCode, String keyType) {
		String key = createKey(player, keyCode, keyType);
		return bindings.remove(key) != null;
	}

	public static void removeAllBindings(ServerPlayerEntity player) {
		bindings.entrySet().removeIf(entry -> entry.getKey().startsWith(player.getUuid().toString()));
	}

	public static List<KeyBinding> getBindings(ServerPlayerEntity player) {
		List<KeyBinding> playerBindings = new ArrayList<>();
		String playerUuid = player.getUuid().toString();

		for (List<KeyBinding> bindingList : bindings.values()) {
			for (KeyBinding binding : bindingList) {
				if (binding.player().getUuid().toString().equals(playerUuid)) {
					playerBindings.add(binding);
				}
			}
		}

		return playerBindings;
	}

	public static void executeBindings(ServerPlayerEntity player, int keyCode, String keyType) {
		String key = createKey(player, keyCode, keyType);
		List<KeyBinding> playerBindings = bindings.get(key);

		if (playerBindings != null && !playerBindings.isEmpty()) {
			ServerCommandSource source = player.getServer().getCommandSource()
					.withSilent()
					.withLevel(2);

			for (KeyBinding binding : playerBindings) {
				String command = "function " + binding.functionId().toString();
				player.getServer().getCommandManager().executeWithPrefix(source, command);
			}
		}
	}

	private static String createKey(ServerPlayerEntity player, int keyCode, String keyType) {
		return player.getUuid().toString() + ":" + keyCode + ":" + keyType;
	}
}