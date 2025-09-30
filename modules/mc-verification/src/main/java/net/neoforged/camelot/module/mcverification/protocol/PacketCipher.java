package net.neoforged.camelot.module.mcverification.protocol;

import net.neoforged.camelot.util.Utils;

import javax.crypto.Cipher;
import javax.crypto.ShortBufferException;
import java.io.OutputStream;

class PacketCipher {
    private final Cipher encoding, decoding;
    private byte[] heapOut = new byte[0];

    PacketCipher(Cipher encoding, Cipher decoding) {
        this.encoding = encoding;
        this.decoding = decoding;
    }

    protected byte[] decipher(byte[] in) {
        int i = in.length;
        byte[] newArray = new byte[this.decoding.getOutputSize(i)];
        try {
            this.decoding.update(in, 0, i, newArray, 0);
        } catch (ShortBufferException ex) {
            Utils.sneakyThrow(ex);
        }
        return newArray;
    }

    public void encipher(byte[] in, OutputStream out) {
        int i = in.length;
        int outputSize = this.encoding.getOutputSize(i);
        if (this.heapOut.length < outputSize) {
            this.heapOut = new byte[outputSize];
        }

        try {
            out.write(this.heapOut, 0, this.encoding.update(in, 0, i, this.heapOut));
        } catch (Exception ex) {
            Utils.sneakyThrow(ex);
        }
    }
}
