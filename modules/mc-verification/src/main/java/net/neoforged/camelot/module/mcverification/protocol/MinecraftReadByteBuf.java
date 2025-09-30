package net.neoforged.camelot.module.mcverification.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MinecraftReadByteBuf {
    private final InputStream input;
    private int bytesRead;

    public MinecraftReadByteBuf(InputStream input) {
        this.input = input;
    }

    public int bytesRead() {
        return bytesRead;
    }

    public int readVarInt() {
        int i = 0;
        int j = 0;

        byte b;
        do {
            b = next();
            i |= (b & 127) << j++ * 7;
            if (j > 5) {
                throw new RuntimeException("VarInt too big");
            }
        } while (hasContinuationBit(b));

        return i;
    }

    public String readUtf8() {
        return readUtf8(Short.MAX_VALUE);
    }

    public String readUtf8(int maxLength) {
        var length = readVarInt();
        var maxBytes = maxLength * 3;
        if (length > maxBytes) {
            throw new RuntimeException("The received encoded string buffer length is longer than maximum allowed (" + length + " > " + maxLength + ")");
        } else if (length < 0) {
            throw new RuntimeException("The received encoded string buffer length is less than zero!");
        }
        var chars = next(length);
        var string = new String(chars, StandardCharsets.UTF_8);
        if (string.length() > maxBytes) {
            throw new RuntimeException("The received string length is longer than maximum allowed (" + string.length() + " > " + maxLength + ")");
        }
        return string;
    }

    public int readUnsignedShort() {
        return (nextUnsigned() << 8) | nextUnsigned();
    }

    public long readLong() {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            int readByte = next();
            if (readByte == -1) {
                throw new RuntimeException("End of stream reached before reading 8 bytes.");
            }
            result = (result << 8) | (readByte & 0xFF);
        }
        return result;
    }

    public byte[] readByteArray() {
        return next(readVarInt());
    }

    public UUID readUuid() {
        return new UUID(readLong(), readLong());
    }

    public void consume(int amount) {
        while (amount > 0) {
            next();
            amount--;
        }
    }

    private byte[] next(int length) {
        try {
            var bytes = new byte[length];
            var read = input.read(bytes);
            if (read != length) {
                throw new RuntimeException("Expected " + length + " bytes, but found " + read + "!");
            }
            bytesRead += length;
            return bytes;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private int nextUnsigned() {
        return next() & 0xFF;
    }

    private byte next() {
        try {
            var b = (byte) input.read();
            bytesRead++;
            return b;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean hasContinuationBit(byte b) {
        return (b & 128) == 128;
    }
}
