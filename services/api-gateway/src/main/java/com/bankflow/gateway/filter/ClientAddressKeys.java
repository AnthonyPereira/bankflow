package com.bankflow.gateway.filter;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.HexFormat;

/**
 * Calcule la clé de quota d'un client à partir de son adresse IP. Une adresse IPv4 compte seule.
 * Une adresse IPv6 compte avec tout son préfixe /64 : un fournisseur attribue au moins un /64 à
 * chaque abonné, soit 2^64 adresses qu'un client pourrait faire tourner pour échapper au quota.
 */
final class ClientAddressKeys {

    private static final int IPV6_PREFIX_BYTES = 8;

    private ClientAddressKeys() {}

    static String resolveKey(String address) {
        if (address == null || address.isBlank()) {
            return "inconnue";
        }
        var literal = stripDecorations(address.strip());
        if (literal.indexOf(':') < 0) {
            return literal;
        }
        try {
            // ofLiteral ne fait jamais de résolution DNS ; ::ffff:a.b.c.d revient en IPv4
            var parsed = InetAddress.ofLiteral(literal);
            if (parsed instanceof Inet6Address) {
                var prefix = new byte[IPV6_PREFIX_BYTES];
                System.arraycopy(parsed.getAddress(), 0, prefix, 0, IPV6_PREFIX_BYTES);
                return HexFormat.of().formatHex(prefix) + "::/64";
            }
            return parsed.getHostAddress();
        } catch (IllegalArgumentException exception) {
            // Adresse illisible : elle garde son propre compteur, sans faire échouer la requête
            return address;
        }
    }

    /** Retire les crochets ({@code [::1]}) et l'identifiant de zone ({@code fe80::1%eth0}). */
    private static String stripDecorations(String address) {
        var literal = address;
        if (literal.startsWith("[") && literal.endsWith("]")) {
            literal = literal.substring(1, literal.length() - 1);
        }
        var zone = literal.indexOf('%');
        return zone >= 0 ? literal.substring(0, zone) : literal;
    }
}
