package net.neoforged.camelot.util;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.kohsuke.github.GHApp;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.authorization.AuthorizationProvider;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.function.Function;

/**
 * Class containing a few helper methods for creating {@link AuthorizationProvider} for GitHub connections.
 */
public class AuthUtil {
    private static final String PKCS1_KEY_START = "-----BEGIN RSA PRIVATE KEY-----\n";
    private static final String PKCS1_KEY_END = "-----END RSA PRIVATE KEY-----";
    private static final String PKCS8_KEY_START = "-----BEGIN PRIVATE KEY-----\n";
    private static final String PKCS8_KEY_END = "-----END PRIVATE KEY-----";

    /**
     * Parse and convert the given key, if necessary, to the PKCS8 format.
     *
     * @param input the key to parse
     * @return a byte array containing the converted key
     * @throws IOException if the key could not be converted
     */
    public static byte[] parsePKCS8(String input) throws IOException {
        input = input.trim().replace("\r\n", "\n");
        if (input.startsWith(PKCS8_KEY_START)) {
            input = input.replace(PKCS8_KEY_START, "").replace(PKCS8_KEY_END, "").replaceAll("\\s", "");
            return Base64.getDecoder().decode(input);
        } else {
            input = input.replace(PKCS1_KEY_START, "").replace(PKCS1_KEY_END, "").replaceAll("\\s", "");
            final byte[] pkcs1Encoded = Base64.getDecoder().decode(input);
            final AlgorithmIdentifier algId = new AlgorithmIdentifier(PKCSObjectIdentifiers.rsaEncryption, DERNull.INSTANCE);
            final PrivateKeyInfo privateKeyInfo = new PrivateKeyInfo(algId, ASN1Sequence.getInstance(pkcs1Encoded));
            return privateKeyInfo.getEncoded();
        }
    }

    /**
     * Creates an {@link AuthorizationProvider} that connects to a GitHub application.
     *
     * @param appId the ID of the app
     * @param key   the key of the app, in {@link #parsePKCS8(String) PKCS8} format
     * @param owner the application installation owner
     * @return the authorization provider
     */
    public static AuthorizationProvider githubApp(String appId, byte[] key, Function<GHApp, GHAppInstallation> owner) throws NoSuchAlgorithmException, InvalidKeySpecException {
        final PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(key));
        return new AuthorizationProvider() {
            @Override
            public String getEncodedAuthorization() throws IOException {
                return "Bearer " + jwt();
            }

            private Jwt jwt = null;

            public String jwt() throws IOException {
                final Instant now = Instant.now();
                if (jwt == null) {
                    this.jwt = createJwt();
                } else if (now.isAfter(jwt.expirationDate())) {
                    this.jwt = createJwt();
                }
                return jwt.jwt();
            }

            private Jwt createJwt() throws IOException {
                final GitHub gitHub = new GitHubBuilder()
                        .withJwtToken(refreshJWT(appId, privateKey))
                        .build();

                final GHAppInstallationToken token = owner.apply(gitHub.getApp()).createToken().create();
                return new Jwt(token.getExpiresAt().toInstant(), token.getToken());
            }
        };
    }

    private static String refreshJWT(String appId, PrivateKey privateKey) {
        final Instant now = Instant.now();
        final Instant exp = now.plus(Duration.ofMinutes(10));
        final JwtBuilder builder = Jwts.builder()
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(exp))
                .setIssuer(appId)
                .signWith(privateKey, SignatureAlgorithm.RS256);
        return builder.compact();
    }

    public record Jwt(Instant expirationDate, String jwt) {
    }
}
