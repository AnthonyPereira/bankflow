package com.bankflow.auth.infrastructure.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JwtConfigTests {

    private final JwtConfig config = new JwtConfig();

    @Test
    void chaqueCleEphemereRecoitUnKidUnique() {
        var environment = new MockEnvironment();

        var first = config.createJwtSigningKey(createProperties(""), environment);
        var second = config.createJwtSigningKey(createProperties(""), environment);

        assertThat(first.getKeyID()).startsWith("bankflow-1-");
        assertThat(second.getKeyID()).startsWith("bankflow-1-");
        assertThat(first.getKeyID()).isNotEqualTo(second.getKeyID());
    }

    @Test
    void uneCleFournieGardeLeKidConfigure() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var encoded =
                Base64.getEncoder()
                        .encodeToString(generator.generateKeyPair().getPrivate().getEncoded());

        var key = config.createJwtSigningKey(createProperties(encoded), new MockEnvironment());

        assertThat(key.getKeyID()).isEqualTo("bankflow-1");
    }

    @Test
    void prodSimExigeUneCleFournie() {
        var environment = new MockEnvironment();
        environment.setActiveProfiles("prod-sim");

        assertThatThrownBy(() -> config.createJwtSigningKey(createProperties(""), environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_PRIVATE_KEY");
    }

    private static JwtProperties createProperties(String privateKey) {
        return new JwtProperties(
                "https://bankflow.local/auth",
                "bankflow-api",
                Duration.ofMinutes(15),
                privateKey,
                "bankflow-1");
    }
}
