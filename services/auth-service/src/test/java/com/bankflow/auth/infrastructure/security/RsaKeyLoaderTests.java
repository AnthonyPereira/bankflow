package com.bankflow.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class RsaKeyLoaderTests {

    @Test
    void uneClePkcs8EnBase64EstChargeeAvecSaClePublique() throws Exception {
        var pair = generate(2048);
        var encoded = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());

        var key = RsaKeyLoader.fromBase64(encoded, "cle-1");

        assertThat(key.getKeyID()).isEqualTo("cle-1");
        assertThat(key.isPrivate()).isTrue();
        assertThat(key.toRSAPublicKey()).isEqualTo(pair.getPublic());
    }

    @Test
    void uneCleTropCourteEstRefusee() throws Exception {
        var encoded = Base64.getEncoder().encodeToString(generate(1024).getPrivate().getEncoded());

        assertThatThrownBy(() -> RsaKeyLoader.fromBase64(encoded, "cle-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2048");
    }

    @Test
    void uneCleIllisibleEstRefusee() {
        assertThatThrownBy(() -> RsaKeyLoader.fromBase64("pas-une-cle", "cle-1"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static java.security.KeyPair generate(int size) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(size);
        return generator.generateKeyPair();
    }
}
