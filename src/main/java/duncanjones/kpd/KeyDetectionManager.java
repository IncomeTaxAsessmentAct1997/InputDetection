package duncanjones.kpd;

import com.mojang.brigadier.ParseResults;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public class KeyDetectionManager {
	private static final Map<String, List<KeyBinding>> bindings = new ConcurrentHashMap<>();

	public static void addBinding(KeyBinding binding) {
		String key = createKey(binding.player(), binding.keyCode(), binding.keyType());
		bindings.computeIfAbsent(key, k -> new ArrayList<>()).add(binding);
	}

	public static boolean removeBinding(ServerPlayer player, int keyCode, String keyType) {
		String key = createKey(player, keyCode, keyType);
		return bindings.remove(key) != null;
	}

	public static void removeAllBindings(ServerPlayer player) {
		bindings.entrySet().removeIf(entry -> entry.getKey().startsWith(player.getUUID().toString()));
	}

	public static List<KeyBinding> getBindings(ServerPlayer player) {
		List<KeyBinding> playerBindings = new ArrayList<>();
		String playerUuid = player.getUUID().toString();

		for (List<KeyBinding> bindingList : bindings.values()) {
			for (KeyBinding binding : bindingList) {
				if (binding.player().getUUID().toString().equals(playerUuid)) {
					playerBindings.add(binding);
				}
			}
		}

		return playerBindings;
	}

	public static void executeBindings(ServerPlayer player, int keyCode, String keyType) {
		String key = createKey(player, keyCode, keyType);
		List<KeyBinding> playerBindings = bindings.get(key);

		if (playerBindings != null && !playerBindings.isEmpty()) {
			CommandSourceStack source = player.createCommandSourceStack().withSuppressedOutput();

			for (KeyBinding binding : playerBindings) {
				String command = "function " + binding.functionId().toString();
				ParseResults<CommandSourceStack> parseResults = source.getServer().getCommands().getDispatcher().parse(command, source);
				source.getServer().getCommands().performCommand(parseResults, command);
			}
		}
	}

	private static String createKey(ServerPlayer player, int keyCode, String keyType) {
		return player.getUUID().toString() + ":" + keyCode + ":" + keyType;
	}
}
