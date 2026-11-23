package com.bankflow.customer.api.customer;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.customer.CustomerApiTestSupport;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerApiIntegrationTests extends CustomerApiTestSupport {

    @Test
    void leProfilEstIntrouvableAvantSaCreationPuisLisible() throws Exception {
        var userId = UUID.randomUUID();
        var token = createToken(userId, ROLE_CLIENT);

        var before = get("/customers/me", token);
        assertThat(before.statusCode()).isEqualTo(404);
        assertThat(parseJson(before).get("code").asString()).isEqualTo("CUSTOMER_NOT_FOUND");
        assertThat(put("/customers/me", token, "{}").statusCode()).isEqualTo(404);

        var email = generateUniqueEmail();
        registrationService.registerUser(userId, email, false);

        var after = get("/customers/me", token);
        assertThat(after.statusCode()).isEqualTo(200);
        var body = parseJson(after);
        assertThat(body.get("userId").asString()).isEqualTo(userId.toString());
        assertThat(body.get("email").asString()).isEqualTo(email);
        assertThat(body.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(body.get("demo").asBoolean()).isFalse();
        assertThat(body.get("firstName").isNull()).isTrue();
        assertThat(body.get("createdAt").asString()).endsWith("Z");
    }

    @Test
    void leClientMetAJourSesCoordonneesEtLesChainesVidesDeviennentNull() throws Exception {
        var customer = createCustomer(generateUniqueEmail(), false);
        var token = createToken(customer.getUserId(), ROLE_CLIENT);

        var response =
                put(
                        "/customers/me",
                        token,
                        """
                        {"firstName":"  Alice ","lastName":"Martin","phone":"+33 6 12 34 56 78",
                         "address":"1 rue de la Paix"}\
                        """);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(parseJson(response).get("firstName").asString()).isEqualTo("Alice");
        assertThat(parseJson(response).get("phone").asString()).isEqualTo("+33 6 12 34 56 78");

        var cleared =
                parseJson(
                        put(
                                "/customers/me",
                                token,
                                """
                                {"firstName":"","lastName":"  ","phone":""}\
                                """));
        assertThat(cleared.get("firstName").isNull()).isTrue();
        assertThat(cleared.get("lastName").isNull()).isTrue();
        assertThat(cleared.get("phone").isNull()).isTrue();
        assertThat(cleared.get("address").isNull()).isTrue();
    }

    @Test
    void desDonneesInvalidesDonnent400AvecLeDetailParChamp() throws Exception {
        var customer = createCustomer(generateUniqueEmail(), false);
        var token = createToken(customer.getUserId(), ROLE_CLIENT);

        var response =
                put(
                        "/customers/me",
                        token,
                        "{\"phone\":\"abc\",\"firstName\":\"" + "x".repeat(101) + "\"}");

        assertThat(response.statusCode()).isEqualTo(400);
        var body = parseJson(response);
        assertThat(body.get("detail").asString()).isEqualTo("Les données envoyées sont invalides.");
        assertThat(body.get("errors").has("phone")).isTrue();
        assertThat(body.get("errors").has("firstName")).isTrue();
    }

    @Test
    void unClientNeVoitPasLeProfilDUnAutre() throws Exception {
        var alice = createCustomer(generateUniqueEmail(), false);
        var bob = createCustomer(generateUniqueEmail(), false);
        var bobToken = createToken(bob.getUserId(), ROLE_CLIENT);

        var denied = get("/customers/" + alice.getId(), bobToken);
        assertThat(denied.statusCode()).isEqualTo(404);
        assertThat(parseJson(denied).get("code").asString()).isEqualTo("CUSTOMER_NOT_FOUND");

        assertThat(get("/customers/" + bob.getId(), bobToken).statusCode()).isEqualTo(200);
        var adminToken = createToken(UUID.randomUUID(), ROLE_ADMIN);
        assertThat(get("/customers/" + alice.getId(), adminToken).statusCode()).isEqualTo(200);
    }

    @Test
    void unClientRecoit403SurLaListeEtSurLeChangementDeStatut() throws Exception {
        var customer = createCustomer(generateUniqueEmail(), false);
        var token = createToken(customer.getUserId(), ROLE_CLIENT);

        assertThat(get("/customers", token).statusCode()).isEqualTo(403);
        assertThat(
                        put(
                                        "/customers/" + customer.getId() + "/status",
                                        token,
                                        "{\"status\":\"SUSPENDED\"}")
                                .statusCode())
                .isEqualTo(403);
    }

    @Test
    void lAdministrateurPagineEtFiltre() throws Exception {
        var tag = "recherche" + UUID.randomUUID().toString().replace("-", "");
        var first = createCustomer(tag + "-a@bankflow.test", false);
        createCustomer(tag + "-b@bankflow.test", false);
        createCustomer(tag + "-c@bankflow.test", false);
        var adminToken = createToken(UUID.randomUUID(), ROLE_ADMIN);
        put("/customers/" + first.getId() + "/status", adminToken, "{\"status\":\"SUSPENDED\"}");

        var page0 = parseJson(get("/customers?size=2&q=" + tag.toUpperCase(), adminToken));
        assertThat(page0.get("totalElements").asLong()).isEqualTo(3);
        assertThat(page0.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page0.get("page").asInt()).isZero();
        assertThat(page0.get("size").asInt()).isEqualTo(2);
        assertThat(page0.get("content")).hasSize(2);
        // Les plus récents d'abord
        assertThat(page0.get("content").get(0).get("email").asString()).contains("-c@");

        var page1 = parseJson(get("/customers?size=2&page=1&q=" + tag, adminToken));
        assertThat(page1.get("content")).hasSize(1);

        var suspended = parseJson(get("/customers?status=SUSPENDED&q=" + tag, adminToken));
        assertThat(suspended.get("totalElements").asLong()).isEqualTo(1);
        assertThat(suspended.get("content").get(0).get("id").asString())
                .isEqualTo(first.getId().toString());
    }

    @Test
    void uneTailleDePageHorsLimitesDonne400() throws Exception {
        var adminToken = createToken(UUID.randomUUID(), ROLE_ADMIN);

        var tooBig = get("/customers?size=101", adminToken);
        assertThat(tooBig.statusCode()).isEqualTo(400);
        assertThat(parseJson(tooBig).get("errors").has("size")).isTrue();
        assertThat(get("/customers?size=0", adminToken).statusCode()).isEqualTo(400);
        assertThat(get("/customers?page=-1", adminToken).statusCode()).isEqualTo(400);
        assertThat(get("/customers?status=INCONNU", adminToken).statusCode()).isEqualTo(400);
    }

    @Test
    void suspendreUnClientDeDemonstrationDonne409() throws Exception {
        var demo = createCustomer(generateUniqueEmail(), true);
        var adminToken = createToken(UUID.randomUUID(), ROLE_ADMIN);

        var response =
                put(
                        "/customers/" + demo.getId() + "/status",
                        adminToken,
                        "{\"status\":\"SUSPENDED\"}");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(parseJson(response).get("code").asString())
                .isEqualTo("DEMO_CUSTOMER_CANNOT_BE_SUSPENDED");
        assertThat(customerRepository.findById(demo.getId()).orElseThrow().getStatus().name())
                .isEqualTo("ACTIVE");
    }

    @Test
    void laSuspensionPuisLaReactivationPublientCustomerUpdated() throws Exception {
        var customer = createCustomer(generateUniqueEmail(), false);
        var adminToken = createToken(UUID.randomUUID(), ROLE_ADMIN);
        var url = "/customers/" + customer.getId() + "/status";

        var suspended = put(url, adminToken, "{\"status\":\"SUSPENDED\"}");
        assertThat(suspended.statusCode()).isEqualTo(200);
        assertThat(parseJson(suspended).get("status").asString()).isEqualTo("SUSPENDED");

        var events =
                awaitEvents("bank.customers", customer.getId().toString(), "CustomerUpdated", 1);
        var envelope = events.get(0);
        assertThat(envelope.get("producer").asString()).isEqualTo("customer-service");
        assertThat(envelope.get("eventVersion").asInt()).isEqualTo(1);
        var payload = envelope.get("payload");
        assertThat(payload.get("customerId").asString()).isEqualTo(customer.getId().toString());
        assertThat(payload.get("userId").asString()).isEqualTo(customer.getUserId().toString());
        assertThat(payload.get("email").asString()).isEqualTo(customer.getEmail());
        assertThat(payload.get("status").asString()).isEqualTo("SUSPENDED");
        assertThat(payload.properties()).hasSize(4);

        // Même statut redemandé : rien n'est republié
        assertThat(put(url, adminToken, "{\"status\":\"SUSPENDED\"}").statusCode()).isEqualTo(200);
        assertThat(put(url, adminToken, "{\"status\":\"ACTIVE\"}").statusCode()).isEqualTo(200);
        var all = awaitEvents("bank.customers", customer.getId().toString(), "CustomerUpdated", 2);
        assertThat(all.get(1).get("payload").get("status").asString()).isEqualTo("ACTIVE");
        assertThat(
                        collectEventsDuring(
                                "bank.customers",
                                customer.getId().toString(),
                                "CustomerUpdated",
                                Duration.ofSeconds(2)))
                .hasSize(2);
    }

    @Test
    void unStatutInvalideDonne400EtUnClientInconnuDonne404() throws Exception {
        var customer = createCustomer(generateUniqueEmail(), false);
        var adminToken = createToken(UUID.randomUUID(), ROLE_ADMIN);

        assertThat(
                        put(
                                        "/customers/" + customer.getId() + "/status",
                                        adminToken,
                                        "{\"status\":\"INCONNU\"}")
                                .statusCode())
                .isEqualTo(400);
        assertThat(put("/customers/" + customer.getId() + "/status", adminToken, "{}").statusCode())
                .isEqualTo(400);
        assertThat(
                        put(
                                        "/customers/" + UUID.randomUUID() + "/status",
                                        adminToken,
                                        "{\"status\":\"SUSPENDED\"}")
                                .statusCode())
                .isEqualTo(404);
    }
}
