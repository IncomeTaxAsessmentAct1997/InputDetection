package duncanjones.kpd;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record KeyPressPacket(int keyCode, String eventType) implements CustomPacketPayload {
	public static final Identifier KEYPRESS_ID = Identifier.fromNamespaceAndPath(KeyPressDetector.MOD_ID, "keypress");
	public static final Type<KeyPressPacket> ID = new Type<>(KEYPRESS_ID);

	public static final StreamCodec<FriendlyByteBuf, KeyPressPacket> CODEC = StreamCodec.ofMember(
			KeyPressPacket::write,
			KeyPressPacket::read
	);

	public static KeyPressPacket read(FriendlyByteBuf buf) {
		int keyCode = buf.readInt();
		String eventType = buf.readUtf();
		return new KeyPressPacket(keyCode, eventType);
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeInt(keyCode);
		buf.writeUtf(eventType);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
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
