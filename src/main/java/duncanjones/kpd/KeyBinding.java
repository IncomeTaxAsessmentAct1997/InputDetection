package duncanjones.kpd;  // Package declaration

import net.minecraft.server.network.ServerPlayerEntity;  // Server-side player object
import net.minecraft.util.Identifier;  // Minecraft's resource identifier type
import org.jetbrains.annotations.NotNull;  // Annotation for non-null parameters

// Record representing a key binding configuration
public record KeyBinding(ServerPlayerEntity player, int keyCode, String keyType, Identifier functionId) {
    
    @Override
    public boolean equals(Object obj) {
        // Check if comparing the same object
        if (this == obj) return true;
        // Check if object is null or different type
        if (obj == null || getClass() != obj.getClass()) return false;

        KeyBinding that = (KeyBinding) obj;
        // Two bindings are equal if they have same player, key code, and type
        return keyCode == that.keyCode &&
                player.getUuid().equals(that.player.getUuid()) &&  // Compare player UUIDs
                keyType.equals(that.keyType);  // Compare event type
    }

    @Override
    public int hashCode() {
        // Generate hash code based on player UUID, key code, and key type
        return player.getUuid().hashCode() + keyCode + keyType.hashCode();
    }

    @Override
    public @NotNull String toString() {
        // Create human-readable string representation
        return String.format("KeyBinding{player=%s, key=%d, type=%s, function=%s}",
                player.getName().getString(), keyCode, keyType, functionId);
    }
}
