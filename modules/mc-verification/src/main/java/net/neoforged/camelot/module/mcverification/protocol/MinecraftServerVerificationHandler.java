package net.neoforged.camelot.module.mcverification.protocol;

import com.google.common.primitives.Ints;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;

import java.math.BigInteger;
import java.security.KeyPair;
import java.util.Random;
import java.util.concurrent.Callable;

public class MinecraftServerVerificationHandler implements Callable<Void> {
    private static final String SERVER_ID = "camelot-mc-verify";

    private final MinecraftConnection connection;
    private final YggdrasilAuthenticationService service;
    private final KeyPair keyPair;
    private final MinecraftServerVerificationCallback callback;

    private final byte[] challenge;

    public MinecraftServerVerificationHandler(MinecraftConnection connection, YggdrasilAuthenticationService service, KeyPair keyPair, MinecraftServerVerificationCallback callback) {
        this.connection = connection;
        this.service = service;
        this.keyPair = keyPair;
        this.callback = callback;
        this.challenge = Ints.toByteArray(new Random().nextInt());
    }

    @Override
    public Void call() throws Exception {
        var intention = connection.expectPacket(InboundMinecraftPacket.Intention.class);
        if (intention.intent() != 2) {
            connection.disconnect("Expected intention to be login");
            return null;
        }
        connection.switchState(InboundMinecraftPacket.State.LOGIN);

        var hello = connection.expectPacket(InboundMinecraftPacket.Hello.class);
        var requestedUsername = hello.name();

        connection.send(new OutboundMinecraftPacket.Hello(
                SERVER_ID, keyPair.getPublic().getEncoded(),
                challenge, true
        ));

        var encryptionResponse = connection.expectPacket(InboundMinecraftPacket.Key.class);
        if (!encryptionResponse.isChallengeValid(challenge, keyPair.getPrivate())) {
            connection.disconnect("Challenge invalid. Contact server moderators for assistance");
            return null;
        }

        var secretKey = encryptionResponse.getSecretKey(keyPair.getPrivate());

        var serverId = new BigInteger(Crypt.digestData(SERVER_ID, keyPair.getPublic(), secretKey)).toString(16);
        var profile = service.createMinecraftSessionService()
                .hasJoinedServer(requestedUsername, serverId, null);

        if (profile == null) {
            connection.disconnect("Failed to verify that you've connected to this server! Contact server moderators for assistance");
        } else {
            connection.disconnect(callback.onVerify(intention.address(), profile.profile()));
        }
        return null;
    }
}
