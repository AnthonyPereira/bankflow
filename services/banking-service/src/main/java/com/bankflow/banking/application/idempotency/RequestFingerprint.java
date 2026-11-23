package com.bankflow.banking.application.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.json.JsonMapper;

/**
 * Empreinte d'une demande : SHA-256 de « méthode chemin » suivi du corps JSON canonique (champs
 * triés, valeurs déjà normalisées par l'appelant, par exemple un montant à 2 décimales).
 */
public final class RequestFingerprint {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private RequestFingerprint() {}

    public static String createHash(String method, String path, Map<String, ?> body) {
        var canonical = JSON.writeValueAsString(new TreeMap<>(body));
        var text = method + " " + path + "\n" + canonical;
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
