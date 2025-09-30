package net.neoforged.camelot.module.mcverification.protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MinecraftWriteByteBuf {
    private final OutputStream output;

    public MinecraftWriteByteBuf(OutputStream output) {
        this.output = output;
    }

    public void writeByteArray(byte[] bytes) {
        writeVarInt(bytes.length);
        write(bytes);
    }

    public void writeBoolean(boolean value) {
        writeVarInt(value ? 1 : 0);
    }

    public void writeVarInt(int i) {
        while ((i & -128) != 0) {
            write(i & 127 | 128);
            i >>>= 7;
        }
        write(i);
    }

    public void writeUtf8(String str) {
        writeByteArray(str.getBytes(StandardCharsets.UTF_8));
    }

    private void write(int b) {
        try {
            output.write(b);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void write(byte[] bytes) {
        try {
            output.write(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
