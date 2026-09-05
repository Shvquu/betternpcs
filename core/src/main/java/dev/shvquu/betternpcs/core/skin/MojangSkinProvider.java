package dev.shvquu.betternpcs.core.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.shvquu.betternpcs.api.npc.property.NpcSkin;
import dev.shvquu.betternpcs.api.skin.SkinProvider;
import dev.shvquu.betternpcs.api.skin.SkinSource;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Fetches skins from Mojang's public profile endpoints.
 *
 * <p>A name is resolved in two steps — name to unique id, then unique id to profile — because only
 * the second endpoint returns the signed texture property, and only a signed texture renders on a
 * vanilla client.
 *
 * <h2>Being a good citizen</h2>
 *
 * <p>These endpoints are rate limited and Mojang does not document the exact limits. Three things
 * here matter for staying under them: every request carries a timeout so a stalled connection cannot
 * hold a slot open indefinitely, {@link DefaultSkinService} collapses concurrent requests for the
 * same skin into one, and {@link SkinCache} keeps the answer so a restart does not ask again.
 *
 * <p>A 204 or 404 means "no such player", which is an empty result rather than a failure — the next
 * provider should get its turn. Anything else is a failure, so that a rate limit or an outage is
 * visible in the log rather than being silently indistinguishable from an unknown name.
 *
 * @since 1.0.0
 */
public final class MojangSkinProvider implements SkinProvider {

    /** The id this provider registers under. */
    public static final String ID = "mojang";

    private static final URI NAME_LOOKUP = URI.create("https://api.mojang.com/users/profiles/minecraft/");
    private static final URI PROFILE_LOOKUP =
            URI.create("https://sessionserver.mojang.com/session/minecraft/profile/");

    private final HttpClient http;
    private final Duration timeout;
    private final Executor executor;

    /**
     * Creates the provider.
     *
     * @param timeout  how long a single request may take
     * @param executor where the response handling runs; never the main server thread
     * @throws NullPointerException     if either argument is {@code null}
     * @throws IllegalArgumentException if {@code timeout} is not positive
     */
    public MojangSkinProvider(Duration timeout, Executor executor) {
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.executor = Objects.requireNonNull(executor, "executor");
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("The skin request timeout must be positive");
        }
        this.http = HttpClient.newBuilder()
                .connectTimeout(timeout)
                // Mojang redirects between its endpoints; following them is required, but only for
                // the same protocol, so a redirect to plain HTTP cannot downgrade the connection.
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(executor)
                .build();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int priority() {
        return MOJANG_PRIORITY;
    }

    @Override
    public boolean supports(SkinSource source) {
        // A texture source is already resolved, and never reaches a provider at all.
        return source.kind() == SkinSource.Kind.NAME || source.kind() == SkinSource.Kind.UUID;
    }

    @Override
    public CompletableFuture<Optional<NpcSkin>> fetch(SkinSource source) {
        Objects.requireNonNull(source, "source");
        return switch (source.kind()) {
            case NAME -> resolveName(source.value())
                    .thenComposeAsync(id -> id.map(this::fetchProfile)
                            .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty())), executor);
            case UUID -> fetchProfile(UUID.fromString(source.value()));
            case TEXTURE -> CompletableFuture.completedFuture(Optional.empty());
        };
    }

    private CompletableFuture<Optional<UUID>> resolveName(String name) {
        return send(NAME_LOOKUP.resolve(encode(name))).thenApply(body ->
                body.flatMap(json -> readString(json, "id")).map(MojangSkinProvider::parseUndashedUuid));
    }

    private CompletableFuture<Optional<NpcSkin>> fetchProfile(UUID uniqueId) {
        // unsigned=false is what makes Mojang include the signature. Without it the texture comes
        // back unsigned and the client renders the default skin, which looks like the plugin simply
        // not working.
        URI uri = PROFILE_LOOKUP.resolve(uniqueId.toString().replace("-", "") + "?unsigned=false");
        return send(uri).thenApply(body -> body.flatMap(MojangSkinProvider::readTextures));
    }

    private CompletableFuture<Optional<JsonObject>> send(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", "application/json")
                // Identifies the plugin in Mojang's logs, which is what they ask of API consumers.
                .header("User-Agent", "BetterNPCs")
                .GET()
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int status = response.statusCode();
                    if (status == 204 || status == 404) {
                        // No such player. Not a failure — the next provider may know them.
                        return Optional.empty();
                    }
                    if (status != 200) {
                        throw new IllegalStateException(
                                "Mojang answered " + status + " for " + uri.getHost()
                                        + (status == 429 ? " (rate limited)" : ""));
                    }
                    try {
                        JsonElement parsed = JsonParser.parseString(response.body());
                        return parsed.isJsonObject()
                                ? Optional.of(parsed.getAsJsonObject())
                                : Optional.empty();
                    } catch (JsonParseException malformed) {
                        throw new IllegalStateException(
                                "Mojang returned a response that is not JSON", malformed);
                    }
                });
    }

    private static Optional<NpcSkin> readTextures(JsonObject profile) {
        if (!profile.has("properties") || !profile.get("properties").isJsonArray()) {
            return Optional.empty();
        }
        for (JsonElement element : profile.getAsJsonArray("properties")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject property = element.getAsJsonObject();
            if (!"textures".equals(readString(property, "name").orElse(null))) {
                continue;
            }
            Optional<String> value = readString(property, "value");
            if (value.isEmpty()) {
                continue;
            }
            return Optional.of(NpcSkin.ofNullable(
                    value.get(), readString(property, "signature").orElse(null)));
        }
        return Optional.empty();
    }

    private static Optional<String> readString(JsonObject object, String member) {
        JsonElement element = object.get(member);
        if (element == null || !element.isJsonPrimitive()) {
            return Optional.empty();
        }
        return Optional.of(element.getAsString());
    }

    private static UUID parseUndashedUuid(String undashed) {
        // Mojang returns ids without hyphens, which UUID.fromString will not accept.
        String dashed = undashed.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5");
        return UUID.fromString(dashed);
    }

    private static String encode(String pathSegment) {
        return java.net.URLEncoder.encode(pathSegment, java.nio.charset.StandardCharsets.UTF_8);
    }
}
