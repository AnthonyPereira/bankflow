package com.bankflow.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClientAddressKeysTests {

    @Test
    void uneAdresseIpv4CompteSeule() {
        assertThat(ClientAddressKeys.resolveKey("203.0.113.10")).isEqualTo("203.0.113.10");
        assertThat(ClientAddressKeys.resolveKey("203.0.113.11"))
                .isNotEqualTo(ClientAddressKeys.resolveKey("203.0.113.10"));
    }

    @Test
    void deuxAdressesDuMemePrefixe64PartagentLaMemeCle() {
        var first = ClientAddressKeys.resolveKey("2001:db8:1:2::1");
        var second = ClientAddressKeys.resolveKey("2001:0db8:0001:0002:ffff:ffff:ffff:ffff");

        assertThat(first).isEqualTo(second).isEqualTo("20010db800010002::/64");
    }

    @Test
    void deuxPrefixes64DifferentsOntDesClesDifferentes() {
        assertThat(ClientAddressKeys.resolveKey("2001:db8:1:2::1"))
                .isNotEqualTo(ClientAddressKeys.resolveKey("2001:db8:1:3::1"));
    }

    @Test
    void uneAdresseIpv4MappeeEnIpv6CompteCommeIpv4() {
        assertThat(ClientAddressKeys.resolveKey("::ffff:203.0.113.10")).isEqualTo("203.0.113.10");
        assertThat(ClientAddressKeys.resolveKey("::ffff:203.0.113.11")).isEqualTo("203.0.113.11");
    }

    @Test
    void lesCrochetsEtLaZoneSontIgnores() {
        var expected = ClientAddressKeys.resolveKey("fe80::1");

        assertThat(ClientAddressKeys.resolveKey("fe80::2%eth0")).isEqualTo(expected);
        assertThat(ClientAddressKeys.resolveKey("[fe80::3]")).isEqualTo(expected);
    }

    @Test
    void uneAdresseIllisibleNeLevePasDException() {
        assertThat(ClientAddressKeys.resolveKey("pas:une:adresse")).isEqualTo("pas:une:adresse");
        assertThat(ClientAddressKeys.resolveKey("1:2:3:4:5:6:7:8:9"))
                .isEqualTo("1:2:3:4:5:6:7:8:9");
        assertThat(ClientAddressKeys.resolveKey(null)).isEqualTo("inconnue");
        assertThat(ClientAddressKeys.resolveKey(" ")).isEqualTo("inconnue");
    }
}
