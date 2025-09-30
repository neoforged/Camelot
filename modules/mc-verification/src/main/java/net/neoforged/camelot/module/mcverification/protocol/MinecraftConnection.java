package net.neoforged.camelot.module.mcverification.protocol;

import net.neoforged.camelot.util.Utils;
import org.jetbrains.annotations.Nullable;

import javax.crypto.Cipher;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyPair;

public class MinecraftConnection {
    private final Socket socket;
    private final KeyPair keyPair;

    private InboundMinecraftPacket.State state = InboundMinecraftPacket.State.HANDSHAKE;

    @Nullable
    private PacketCipher cipher;

    public MinecraftConnection(Socket socket, KeyPair keyPair) {
        this.socket = socket;
        this.keyPair = keyPair;
    }

    public void switchState(InboundMinecraftPacket.State state) {
        this.state = state;
    }

    public <T extends InboundMinecraftPacket> T expectPacket(Class<T> type) {
        var packet = waitForPacket();
        if (type.isInstance(packet)) {
            return type.cast(packet);
        }
        throw new RuntimeException("Expected packet of type " + type + " but found " + packet);
    }

    public InboundMinecraftPacket waitForPacket() {
        try {
            InboundMinecraftPacket packet;
            if (cipher == null) {
                var buf = new MinecraftReadByteBuf(socket.getInputStream());
                packet = InboundMinecraftPacket.read(state, buf);
            } else {
                var bytes = cipher.decipher(socket.getInputStream().readAllBytes());
                packet = InboundMinecraftPacket.read(state, new MinecraftReadByteBuf(new ByteArrayInputStream(bytes)));
            }

            if (packet instanceof InboundMinecraftPacket.Key keyPacket && state == InboundMinecraftPacket.State.LOGIN) {
                var key = keyPacket.getSecretKey(keyPair.getPrivate());
                this.cipher = new PacketCipher(
                        Crypt.getCipher(Cipher.ENCRYPT_MODE, key),
                        Crypt.getCipher(Cipher.DECRYPT_MODE, key)
                );
            }

            return packet;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void send(OutboundMinecraftPacket packet) {
        byte[] serialisedPacket;
        {
            var bos = new ByteArrayOutputStream();
            var buf = new MinecraftWriteByteBuf(bos);
            buf.writeVarInt(packet.protocol());
            packet.write(buf);
            serialisedPacket = bos.toByteArray();
        }

        try {
            var os = socket.getOutputStream();
            if (cipher == null) {
                new MinecraftWriteByteBuf(os).writeByteArray(serialisedPacket);
            } else {
                var bos = new ByteArrayOutputStream();
                new MinecraftWriteByteBuf(bos).writeByteArray(serialisedPacket);
                cipher.encipher(bos.toByteArray(), os);
            }
            os.flush();
        } catch (Exception ex) {
            Utils.sneakyThrow(ex);
        }
    }

    public void disconnect(String message) {
        send(new OutboundMinecraftPacket.Disconnect(message));
    }

    public InetAddress getAddress() {
        return socket.getInetAddress();
    }
}
