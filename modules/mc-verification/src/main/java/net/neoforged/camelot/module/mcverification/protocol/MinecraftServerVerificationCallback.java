package net.neoforged.camelot.module.mcverification.protocol;

import com.mojang.authlib.GameProfile;

@FunctionalInterface
public interface MinecraftServerVerificationCallback {
    String onVerify(String serverAddress, GameProfile user);
}
