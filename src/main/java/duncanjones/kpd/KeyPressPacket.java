package duncanjones.kpd;  // Package declaration

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;  // Server networking handler
import net.minecraft.network.PacketByteBuf;  // Minecraft packet buffer
import net.minecraft.network.codec.PacketCodec;  // Packet codec for encoding/decoding
import net.minecraft.network.packet.CustomPayload;  // Custom packet payload interface
import net.minecraft.util.Identifier;  // Resource identifier

// Record representing a key press packet sent from client to server
public record KeyPressPacket(int keyCode, String eventType) implements CustomPayload {
    // Unique identifier for this packet type
    public static final Identifier KEYPRESS_ID = Identifier.of(KeyPressDetector.MOD_ID, "keypress");
    public static final Id<KeyPressPacket> ID = new Id<>(KEYPRESS_ID);

    // Codec for serializing and deserializing packets
    public static final PacketCodec<PacketByteBuf, KeyPressPacket> CODEC = PacketCodec.of(
            KeyPressPacket::write,  // Serialization method
            KeyPressPacket::read    // Deserialization method
    );

    // Read packet from network buffer
    public static KeyPressPacket read(PacketByteBuf buf) {
        int keyCode = buf.readInt();  // Read key code (4 bytes)
        String eventType = buf.readString();  // Read event type (String)
        return new KeyPressPacket(keyCode, eventType);
    }

    // Write packet to network buffer
    public void write(PacketByteBuf buf) {
        buf.writeInt(keyCode);  // Write key code (4 bytes)
        buf.writeString(eventType);  // Write event type (String)
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;  // Return packet identifier
    }

    public int getKeyCode() {
        return keyCode;  // Getter for key code
    }

    public String getEventType() {
        return eventType;  // Getter for event type
    }

    // Register packet handler on server side
    public static void registerServerReceiver() {
        ServerPlayNetworking.registerGlobalReceiver(ID, KeyPressPacket::receiveKeyPressPacket);
    }

    // Handle incoming key press packet
    private static void receiveKeyPressPacket(KeyPressPacket packet, ServerPlayNetworking.Context context) {
        // Execute on server thread to avoid concurrency issues
        context.server().execute(() -> 
            KeyDetectionManager.executeBindings(context.player(), packet.getKeyCode(), packet.getEventType()));
    }
}
