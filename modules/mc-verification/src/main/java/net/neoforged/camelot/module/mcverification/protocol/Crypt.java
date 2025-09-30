package net.neoforged.camelot.module.mcverification.protocol;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;

public class Crypt {
    public static SecretKey decryptByteToSecretKey(PrivateKey privatekey, byte[] bytes) throws Exception {
        byte[] key = decryptUsingKey(privatekey, bytes);
        return new SecretKeySpec(key, "AES");
    }

    public static byte[] decryptUsingKey(Key key, byte[] bytes) throws Exception {
        return cipherData(2, key, bytes);
    }

    private static byte[] cipherData(int opMode, Key key, byte[] bytes) throws Exception {
        return setupCipher(opMode, key.getAlgorithm(), key).doFinal(bytes);
    }

    private static Cipher setupCipher(int opMode, String s, Key key) throws Exception {
        Cipher cipher = Cipher.getInstance(s);
        cipher.init(opMode, key);
        return cipher;
    }

    public static byte[] digestData(String str, PublicKey publickey, SecretKey secretkey) throws Exception {
        return digestData(str.getBytes("ISO_8859_1"), secretkey.getEncoded(), publickey.getEncoded());
    }

    private static byte[] digestData(byte[]... data) throws Exception {
        MessageDigest messagedigest = MessageDigest.getInstance("SHA-1");

        for (byte[] component : data) {
            messagedigest.update(component);
        }

        return messagedigest.digest();
    }

    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keypairgenerator = KeyPairGenerator.getInstance("RSA");
            keypairgenerator.initialize(1024);
            return keypairgenerator.generateKeyPair();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    public static Cipher getCipher(int opMode, Key key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CFB8/NoPadding");
            cipher.init(opMode, key, new IvParameterSpec(key.getEncoded()));
            return cipher;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
