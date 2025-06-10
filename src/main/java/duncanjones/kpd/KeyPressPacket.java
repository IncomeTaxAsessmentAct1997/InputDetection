package duncanjones.kpd;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record KeyPressPacket(int keyCode, String eventType) implements CustomPayload {
	public static final Identifier KEYPRESS_ID = Identifier.of(KeyPressDetector.MOD_ID, "keypress");
	public static final Id<KeyPressPacket> ID = new Id<>(KEYPRESS_ID);

	public static final PacketCodec<PacketByteBuf, KeyPressPacket> CODEC = PacketCodec.of(
			KeyPressPacket::write,
			KeyPressPacket::read
	);

	public static KeyPressPacket read(PacketByteBuf buf) {
		int keyCode = buf.readInt();
		String eventType = buf.readString();
		return new KeyPressPacket(keyCode, eventType);
	}

	public void write(PacketByteBuf buf) {
		buf.writeInt(keyCode);
		buf.writeString(eventType);
	}

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}

	public int getKeyCode() {
		return keyCode;
	}

	public String getEventType() {
		return eventType;
	}

	public static void registerServerReceiver() {
		ServerPlayNetworking.registerGlobalReceiver(ID, KeyPressPacket::receiveKeyPressPacket);
	}

	private static void receiveKeyPressPacket(KeyPressPacket packet, ServerPlayNetworking.Context context) {
		context.server().execute(() -> KeyDetectionManager.executeBindings(context.player(), packet.getKeyCode(), packet.getEventType()));
	}
}