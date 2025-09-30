package net.neoforged.camelot.module.mcverification.protocol;

import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public sealed interface InboundMinecraftPacket {
    static InboundMinecraftPacket read(State state, MinecraftReadByteBuf buf) {
        var length = buf.readVarInt();
        int initialRead = buf.bytesRead();
        var packetId = buf.readVarInt();
        length -= (buf.bytesRead() - initialRead);
        var reader = state.readers.get(packetId);
        if (reader == null) {
            buf.consume(length);
            return new Unknown(packetId);
        }
        return reader.apply(buf);
    }

    record Unknown(int id) implements InboundMinecraftPacket {}

    record Intention(int protocol, String address, int port, int intent) implements InboundMinecraftPacket {}

    record Hello(String name, UUID profileUuid) implements InboundMinecraftPacket {}
    record Key(byte[] secret, byte[] encryptedChallenge) implements InboundMinecraftPacket {
        public SecretKey getSecretKey(PrivateKey privatekey) throws Exception {
            return Crypt.decryptByteToSecretKey(privatekey, this.secret);
        }

        public boolean isChallengeValid(byte[] challenge, PrivateKey privatekey) {
            try {
                return Arrays.equals(challenge, Crypt.decryptUsingKey(privatekey, this.encryptedChallenge));
            } catch (Exception ex) {
                return false;
            }
        }
    }

    enum State {
        HANDSHAKE(Map.of(0x00, buf -> new Intention(
                buf.readVarInt(), buf.readUtf8(), buf.readUnsignedShort(), buf.readVarInt()
        ))),
        LOGIN(Map.of(0x00, buf -> new Hello(buf.readUtf8(), buf.readUuid()),
                0x01, buf -> new Key(buf.readByteArray(), buf.readByteArray())));

        private final Map<Integer, Function<MinecraftReadByteBuf, InboundMinecraftPacket>> readers;

        State(Map<Integer, Function<MinecraftReadByteBuf, InboundMinecraftPacket>> readers) {
            this.readers = readers;
        }
    }
}
