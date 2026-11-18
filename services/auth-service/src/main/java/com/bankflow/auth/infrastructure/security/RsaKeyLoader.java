package com.bankflow.auth.infrastructure.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;

/** Charge ou génère la clé RSA qui signe les JWT. */
final class RsaKeyLoader {

    private static final int MINIMUM_KEY_SIZE = 2048;

    private RsaKeyLoader() {}

    /** Lit une clé privée PKCS#8 encodée en Base64 et en déduit la clé publique. */
    static RSAKey fromBase64(String encodedPrivateKey, String keyId) {
        try {
            var keyFactory = KeyFactory.getInstance("RSA");
            var der = Base64.getDecoder().decode(encodedPrivateKey.strip());
            if (!(keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der))
                    instanceof RSAPrivateCrtKey privateKey)) {
                throw new IllegalStateException("JWT_PRIVATE_KEY doit être une clé RSA complète.");
            }
            if (privateKey.getModulus().bitLength() < MINIMUM_KEY_SIZE) {
                throw new IllegalStateException("JWT_PRIVATE_KEY doit faire au moins 2048 bits.");
            }
            var publicKey =
                    (RSAPublicKey)
                            keyFactory.generatePublic(
                                    new RSAPublicKeySpec(
                                            privateKey.getModulus(),
                                            privateKey.getPublicExponent()));
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(keyId)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_PRIVATE_KEY est illisible.", exception);
        }
    }

    static RSAKey generate(String keyId) {
        try {
            return new RSAKeyGenerator(MINIMUM_KEY_SIZE)
                    .keyID(keyId)
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .generate();
        } catch (JOSEException exception) {
            throw new IllegalStateException("Impossible de générer la clé RSA.", exception);
        }
    }
}
