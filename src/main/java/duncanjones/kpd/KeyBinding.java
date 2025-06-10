package duncanjones.kpd;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

public record KeyBinding(ServerPlayerEntity player, int keyCode, String keyType, Identifier functionId) {

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null || getClass() != obj.getClass()) return false;

		KeyBinding that = (KeyBinding) obj;
		return keyCode == that.keyCode &&
				player.getUuid().equals(that.player.getUuid()) &&
				keyType.equals(that.keyType);
	}

	@Override
	public int hashCode() {
		return player.getUuid().hashCode() + keyCode + keyType.hashCode();
	}

	@Override
	public @NotNull String toString() {
		return String.format("KeyBinding{player=%s, key=%d, type=%s, function=%s}",
				player.getName().getString(), keyCode, keyType, functionId);
	}
}