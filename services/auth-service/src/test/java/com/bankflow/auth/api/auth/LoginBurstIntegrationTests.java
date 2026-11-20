package com.bankflow.auth.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.auth.AuthApiTestSupport;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Vérifie qu'aucune transaction ni aucune connexion n'est tenue pendant le hachage du mot de passe,
 * sans dépendre de la vitesse du processeur.
 *
 * <p>Le contexte Spring est propre à cette classe : la source de données trace les connexions
 * tenues par chaque thread, et l'encodeur contrôle, à chaque vérification de mot de passe, que le
 * thread courant n'en tient aucune. Il s'endort ensuite avant de déléguer au vrai encodeur : le
 * sommeil libère le thread porteur, contrairement à bcrypt, et rend chaque vérification longue sur
 * toutes les machines. Si un verrou ou une connexion restait tenu pendant le hachage, la violation
 * serait enregistrée et la rafale saturerait le pool pendant de longues secondes.
 */
@Import(LoginBurstIntegrationTests.TrackingConfiguration.class)
class LoginBurstIntegrationTests extends AuthApiTestSupport {

    private static final int BURST_SIZE = 40;
    private static final Duration HASH_DELAY = Duration.ofSeconds(2);

    private static final Map<Thread, AtomicInteger> HELD_CONNECTIONS = new ConcurrentHashMap<>();
    private static final List<String> VIOLATIONS = new CopyOnWriteArrayList<>();
    private static volatile CountDownLatch hashStarted = new CountDownLatch(1);

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void uneRafaleDeConnexionsSurUnMemeCompteNeSaturePasLePoolDeConnexions() throws Exception {
        var target = generateUniqueEmail();
        register(target, VALID_PASSWORD);
        var other = generateUniqueEmail();
        register(other, VALID_PASSWORD);
        var token = extractAccessToken(login(other, VALID_PASSWORD));
        VIOLATIONS.clear();
        hashStarted = new CountDownLatch(1);
        var start = new CountDownLatch(1);
        var attempts = new ArrayList<Future<Integer>>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int attempt = 0; attempt < BURST_SIZE; attempt++) {
                attempts.add(
                        executor.submit(() -> {
                            start.await();
                            return login(target, "mauvais-mot-de-passe").statusCode();
                        }));
            }
            var me =
                    executor.submit(() -> {
                        // /auth/me est appelé pendant que la rafale vérifie des mots de passe
                        assertThat(hashStarted.await(30, TimeUnit.SECONDS)).isTrue();
                        var begin = System.nanoTime();
                        var response =
                                send(
                                        buildRequest("/auth/me")
                                                .header("Authorization", "Bearer " + token));
                        return new MeResult(
                                response.statusCode(), Duration.ofNanos(System.nanoTime() - begin));
                    });
            start.countDown();

            var meResult = me.get(120, TimeUnit.SECONDS);
            var statuses = new ArrayList<Integer>();
            for (var attempt : attempts) {
                statuses.add(attempt.get(120, TimeUnit.SECONDS));
            }

            // Vérifiée d'abord : c'est la cause directe d'une saturation du pool
            assertThat(VIOLATIONS).isEmpty();
            assertThat(meResult.status()).isEqualTo(200);
            // Une vérification dure au moins HASH_DELAY : /auth/me n'a attendu aucune d'elles
            assertThat(meResult.latency()).isLessThan(HASH_DELAY);
            assertThat(statuses).containsOnly(401);
        }

        assertThat(countFailedAttempts(target)).isEqualTo(5);
    }

    private int countFailedAttempts(String email) {
        return jdbcTemplate.queryForObject(
                "SELECT failed_login_attempts FROM users WHERE email = ?", Integer.class, email);
    }

    private HttpResponse<String> login(String email, String password) throws Exception {
        return postJson("/auth/login", buildCredentials(email, password));
    }

    private static String extractAccessToken(HttpResponse<String> response) {
        return parseJson(response).get("accessToken").asString();
    }

    private static void recordViolationIfResourcesHeld() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            VIOLATIONS.add("transaction active pendant le hachage");
        }
        var held = HELD_CONNECTIONS.get(Thread.currentThread());
        if (held != null && held.get() > 0) {
            VIOLATIONS.add(held.get() + " connexion(s) tenue(s) pendant le hachage");
        }
    }

    private record MeResult(int status, Duration latency) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class TrackingConfiguration {

        @Bean("trackingBeanPostProcessor")
        static BeanPostProcessor createTrackingBeanPostProcessor() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) {
                    if (bean instanceof PasswordEncoder encoder) {
                        return new CheckingPasswordEncoder(encoder);
                    }
                    if (bean instanceof DataSource dataSource
                            && !(bean instanceof ConnectionTrackingDataSource)) {
                        return new ConnectionTrackingDataSource(dataSource);
                    }
                    return bean;
                }
            };
        }
    }

    /** Contrôle l'absence de transaction et de connexion, puis rend la vérification longue. */
    private static final class CheckingPasswordEncoder implements PasswordEncoder {

        private final PasswordEncoder delegate;

        private CheckingPasswordEncoder(PasswordEncoder delegate) {
            this.delegate = delegate;
        }

        @Override
        public String encode(CharSequence rawPassword) {
            return delegate.encode(rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            recordViolationIfResourcesHeld();
            hashStarted.countDown();
            try {
                Thread.sleep(HASH_DELAY);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return delegate.matches(rawPassword, encodedPassword);
        }

        @Override
        public boolean upgradeEncoding(String encodedPassword) {
            return delegate.upgradeEncoding(encodedPassword);
        }
    }

    /** Compte les connexions ouvertes et pas encore rendues au pool, thread par thread. */
    private static final class ConnectionTrackingDataSource extends DelegatingDataSource {

        private ConnectionTrackingDataSource(DataSource target) {
            super(target);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return trackConnection(super.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return trackConnection(super.getConnection(username, password));
        }

        private static Connection trackConnection(Connection connection) {
            var held =
                    HELD_CONNECTIONS.computeIfAbsent(
                            Thread.currentThread(), thread -> new AtomicInteger());
            held.incrementAndGet();
            var closed = new AtomicBoolean();
            return (Connection)
                    Proxy.newProxyInstance(
                            Connection.class.getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("close")
                                        && closed.compareAndSet(false, true)) {
                                    held.decrementAndGet();
                                }
                                try {
                                    return method.invoke(connection, args);
                                } catch (InvocationTargetException exception) {
                                    throw exception.getCause();
                                }
                            });
        }
    }
}
