package net.neoforged.camelot.module.mcverification.protocol;

import org.json.JSONObject;

public sealed interface OutboundMinecraftPacket {
    int protocol();
    void write(MinecraftWriteByteBuf buf);

    record Disconnect(String message) implements OutboundMinecraftPacket {
        @Override
        public void write(MinecraftWriteByteBuf buf) {
            var json = new JSONObject();
            json.put("text", message);
            buf.writeUtf8(json.toString());
        }

        @Override
        public int protocol() {
            return 0x00;
        }
    }

    record Hello(String serverId, byte[] publicKey, byte[] challenge, boolean shouldAuthenticate) implements OutboundMinecraftPacket {
        @Override
        public void write(MinecraftWriteByteBuf buf) {
            buf.writeUtf8(serverId);
            buf.writeByteArray(publicKey);
            buf.writeByteArray(challenge);
            buf.writeBoolean(shouldAuthenticate);
        }

        @Override
        public int protocol() {
            return 0x01;
        }
    }
}
