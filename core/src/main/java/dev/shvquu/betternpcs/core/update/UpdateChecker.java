package dev.shvquu.betternpcs.core.update;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Asks GitHub whether a newer release exists.
 *
 * <h2>What it deliberately does not do</h2>
 *
 * <p>It never downloads anything and never replaces anything. A plugin that updates itself is a
 * plugin that can break a server while nobody is watching, so this returns a version number and a
 * link, and the decision stays with whoever runs the server.
 *
 * <p>It also runs exactly once, at startup. A recurring check would put a background HTTP request
 * on a schedule for information that changes a handful of times a year.
 *
 * <h2>Failure is quiet</h2>
 *
 * <p>A failed check is reported only when debug logging is on. Plenty of Minecraft servers have no
 * outbound internet access at all, and a warning on every start for an optional convenience feature
 * is noise that trains people to ignore the log.
 *
 * <p>A {@code 404} is treated as "no release yet" rather than as a failure — that is the honest
 * reading for a repository that has not published one.
 *
 * @since 1.0.0
 */
public final class UpdateChecker {

    /** GitHub's public API. */
    public static final URI GITHUB_API = URI.create("https://api.github.com/");

    private final URI apiBase;
    private final String repository;
    private final Duration timeout;
    private final Logger logger;
    private final boolean debug;
    private final HttpClient http;

    /**
     * A published release.
     *
     * @param version the release version, parsed from its tag
     * @param url     the page a human should be sent to
     * @since 1.0.0
     */
    public record Release(PluginVersion version, String url) {

        /**
         * Creates the release.
         *
         * @param version the version
         * @param url     the release page
         * @throws NullPointerException if either argument is {@code null}
         */
        public Release {
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(url, "url");
        }
    }

    /**
     * Creates a checker against GitHub's public API.
     *
     * @param repository the {@code owner/name} slug
     * @param timeout    how long the request may take
     * @param executor   where the request runs; never the main server thread
     * @param logger     where failures are reported
     * @param debug      whether to report failures at all
     * @throws NullPointerException     if any reference argument is {@code null}
     * @throws IllegalArgumentException if {@code repository} is not {@code owner/name}, or the
     *                                  timeout is not positive
     */
    public UpdateChecker(
            String repository, Duration timeout, Executor executor, Logger logger, boolean debug) {
        this(GITHUB_API, repository, timeout, executor, logger, debug);
    }

    /**
     * Creates a checker against a specific API base.
     *
     * <p>The base is injectable so that the tests can point it at a local HTTP server and exercise
     * the real request and response handling rather than a mock of it.
     *
     * @param apiBase    the API root, ending in a slash
     * @param repository the {@code owner/name} slug
     * @param timeout    how long the request may take
     * @param executor   where the request runs
     * @param logger     where failures are reported
     * @param debug      whether to report failures at all
     * @throws NullPointerException     if any reference argument is {@code null}
     * @throws IllegalArgumentException if {@code repository} is not {@code owner/name}, or the
     *                                  timeout is not positive
     */
    public UpdateChecker(
            URI apiBase,
            String repository,
            Duration timeout,
            Executor executor,
            Logger logger,
            boolean debug) {

        this.apiBase = Objects.requireNonNull(apiBase, "apiBase");
        this.repository = Objects.requireNonNull(repository, "repository").trim();
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.debug = debug;
        Objects.requireNonNull(executor, "executor");

        if (!this.repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException(
                    "A repository is 'owner/name', was '" + repository + "'");
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("The update check timeout must be positive");
        }

        this.http = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(executor)
                .build();
    }

    /**
     * Looks for a release newer than the given version.
     *
     * <p>Never completes exceptionally. Every failure mode — no network, a rate limit, a malformed
     * response, a repository with no releases — resolves to an empty result, because there is
     * nothing a caller could usefully do about any of them beyond what this already does.
     *
     * @param current the version currently running
     * @return a future completing with the newer release, or empty if there is none
     * @throws NullPointerException if {@code current} is {@code null}
     */
    public CompletableFuture<Optional<Release>> check(PluginVersion current) {
        Objects.requireNonNull(current, "current");

        URI uri = apiBase.resolve("repos/" + repository + "/releases/latest");

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                // The documented media type for GitHub's REST API; without it the response shape is
                // not guaranteed across API versions.
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "BetterNPCs")
                .GET()
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> read(response, current))
                .exceptionally(failure -> {
                    report("Could not reach GitHub", failure);
                    return Optional.empty();
                });
    }

    private Optional<Release> read(HttpResponse<String> response, PluginVersion current) {
        int status = response.statusCode();

        if (status == 404) {
            // No release has been published yet. Not a failure — it is simply the answer.
            report("no release has been published yet", null);
            return Optional.empty();
        }
        if (status != 200) {
            report("GitHub answered " + status + (status == 403 ? " (rate limited)" : ""), null);
            return Optional.empty();
        }

        JsonObject release;
        try {
            JsonElement parsed = JsonParser.parseString(response.body());
            if (!parsed.isJsonObject()) {
                report("GitHub returned something that is not a release object", null);
                return Optional.empty();
            }
            release = parsed.getAsJsonObject();
        } catch (JsonParseException malformed) {
            report("GitHub returned a response that is not JSON", malformed);
            return Optional.empty();
        }

        Optional<PluginVersion> latest = string(release, "tag_name").flatMap(PluginVersion::parse);
        if (latest.isEmpty()) {
            report("The latest release has no tag this build can read", null);
            return Optional.empty();
        }
        if (!current.isOlderThan(latest.get())) {
            // Logged so that, with debug on, the check is observable either way. Silence that could
            // equally mean "up to date" or "never ran" is not a useful diagnostic.
            report("up to date (latest release is " + latest.get() + ")", null);
            return Optional.empty();
        }

        String url = string(release, "html_url")
                .orElseGet(() -> "https://github.com/" + repository + "/releases/latest");
        return Optional.of(new Release(latest.get(), url));
    }

    private static Optional<String> string(JsonObject object, String member) {
        JsonElement element = object.get(member);
        if (element == null || !element.isJsonPrimitive()) {
            return Optional.empty();
        }
        return Optional.of(element.getAsString());
    }

    private void report(String what, Throwable failure) {
        if (!debug) {
            return;
        }
        logger.log(Level.INFO, failure, () -> "Update check: " + what + ".");
    }
}
