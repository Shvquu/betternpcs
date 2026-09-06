package dev.shvquu.betternpcs.core.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Exercises the checker against a real HTTP server on an ephemeral local port.
 *
 * <p>The JDK's own {@link HttpServer} rather than a mocked client: the interesting behaviour is in
 * how real status codes and real response bodies are handled, and a mock would only assert that the
 * code calls the methods it was written to call.
 */
class UpdateCheckerTest {

    private static final String REPOSITORY = "Shvquu/betternpcs";
    private static final PluginVersion CURRENT = PluginVersion.parse("1.0.0").orElseThrow();

    private HttpServer server;
    private ExecutorService executor;
    private final AtomicReference<Response> response = new AtomicReference<>();
    private final List<String> requestedPaths = new CopyOnWriteArrayList<>();
    private final List<String> requestedAccept = new CopyOnWriteArrayList<>();

    private record Response(int status, String body, Duration delay) {

        static Response of(int status, String body) {
            return new Response(status, body, Duration.ZERO);
        }
    }

    @BeforeEach
    void startServer() throws IOException {
        executor = Executors.newFixedThreadPool(2);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(executor);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestedPaths.add(exchange.getRequestURI().getPath());
        requestedAccept.add(String.valueOf(exchange.getRequestHeaders().getFirst("Accept")));

        Response configured = response.get();
        if (!configured.delay().isZero()) {
            try {
                Thread.sleep(configured.delay().toMillis());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        byte[] body = configured.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(configured.status(), body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private UpdateChecker checker() {
        return checker(Duration.ofSeconds(5));
    }

    private UpdateChecker checker(Duration timeout) {
        Logger logger = Logger.getLogger("UpdateCheckerTest");
        // Failures are expected in several of these tests; the stack traces would bury the
        // assertions.
        logger.setLevel(Level.OFF);

        URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        Executor httpExecutor = executor;
        return new UpdateChecker(base, REPOSITORY, timeout, httpExecutor, logger, true);
    }

    private static String releaseJson(String tag, String url) {
        return "{\"tag_name\":\"" + tag + "\",\"html_url\":\"" + url + "\"}";
    }

    private Optional<UpdateChecker.Release> check(PluginVersion current) {
        return checker().check(current).join();
    }

    // ---------------------------------------------------------------------------------------------

    @Nested
    @DisplayName("finding an update")
    class FindingAnUpdate {

        @Test
        void reportsANewerRelease() {
            response.set(Response.of(200, releaseJson("v1.1.0", "https://example.invalid/r/1.1.0")));

            Optional<UpdateChecker.Release> found = check(CURRENT);

            assertThat(found).isPresent();
            assertThat(found.get().version()).isEqualTo(PluginVersion.parse("1.1.0").orElseThrow());
            assertThat(found.get().url()).isEqualTo("https://example.invalid/r/1.1.0");
        }

        @Test
        void offersAReleaseToSomeoneRunningItsSnapshot() {
            response.set(Response.of(200, releaseJson("v1.0.0", "https://example.invalid/r/1.0.0")));

            assertThat(check(PluginVersion.parse("1.0.0-SNAPSHOT").orElseThrow())).isPresent();
        }

        @Test
        void asksTheRightEndpoint() {
            response.set(Response.of(200, releaseJson("v1.1.0", "https://example.invalid")));

            check(CURRENT);

            assertThat(requestedPaths).containsExactly("/repos/Shvquu/betternpcs/releases/latest");
            assertThat(requestedAccept).containsExactly("application/vnd.github+json");
        }

        @Test
        void fallsBackToTheReleasesPageWhenNoUrlIsGiven() {
            response.set(Response.of(200, "{\"tag_name\":\"v2.0.0\"}"));

            assertThat(check(CURRENT))
                    .hasValueSatisfying(release ->
                            assertThat(release.url())
                                    .isEqualTo("https://github.com/Shvquu/betternpcs/releases/latest"));
        }
    }

    @Nested
    @DisplayName("no update")
    class NoUpdate {

        @Test
        void staysQuietWhenTheLatestReleaseIsTheRunningOne() {
            response.set(Response.of(200, releaseJson("v1.0.0", "https://example.invalid")));

            assertThat(check(CURRENT)).isEmpty();
        }

        @Test
        void staysQuietWhenTheLatestReleaseIsOlder() {
            response.set(Response.of(200, releaseJson("v0.9.0", "https://example.invalid")));

            assertThat(check(CURRENT)).isEmpty();
        }

        @Test
        void treatsAMissingReleaseAsNoUpdate() {
            // What GitHub answers for a repository that has not published a release yet, which is
            // this project's own current state. It is the answer, not a failure.
            response.set(Response.of(404, "{\"message\":\"Not Found\"}"));

            assertThat(check(CURRENT)).isEmpty();
        }
    }

    @Nested
    @DisplayName("failure")
    class Failure {

        @Test
        void neverFailsTheFutureOnARateLimit() {
            response.set(Response.of(403, "{\"message\":\"API rate limit exceeded\"}"));

            // An optional convenience check must not surface as a failed future the caller then has
            // to handle on a background thread during startup.
            assertThat(check(CURRENT)).isEmpty();
        }

        @Test
        void neverFailsTheFutureOnAServerError() {
            response.set(Response.of(500, "boom"));

            assertThat(check(CURRENT)).isEmpty();
        }

        @Test
        void neverFailsTheFutureOnMalformedJson() {
            response.set(Response.of(200, "{not json"));

            assertThat(check(CURRENT)).isEmpty();
        }

        @Test
        void neverFailsTheFutureOnAnUnreadableTag() {
            response.set(Response.of(200, releaseJson("nightly", "https://example.invalid")));

            assertThat(check(CURRENT)).isEmpty();
        }

        @Test
        void neverFailsTheFutureOnATimeout() {
            response.set(new Response(200,
                    releaseJson("v9.0.0", "https://example.invalid"), Duration.ofSeconds(2)));

            assertThat(checker(Duration.ofMillis(250)).check(CURRENT).join()).isEmpty();
        }

        @Test
        void neverFailsTheFutureWhenTheHostIsUnreachable() {
            Logger logger = Logger.getLogger("UpdateCheckerTest");
            logger.setLevel(Level.OFF);

            // Port 1 on the loopback interface: nothing listens there, so the connection is refused
            // immediately rather than the test waiting on a timeout.
            UpdateChecker unreachable = new UpdateChecker(
                    URI.create("http://127.0.0.1:1/"),
                    REPOSITORY,
                    Duration.ofSeconds(2),
                    executor,
                    logger,
                    true);

            assertThat(unreachable.check(CURRENT).join()).isEmpty();
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        void refusesSomethingThatIsNotAnOwnerAndName() {
            assertThatThrownBy(() -> new UpdateChecker(
                    "not-a-repository", Duration.ofSeconds(5), executor, Logger.getGlobal(), false))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("owner/name");
        }

        @Test
        void refusesANonPositiveTimeout() {
            assertThatThrownBy(() -> new UpdateChecker(
                    REPOSITORY, Duration.ZERO, executor, Logger.getGlobal(), false))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
