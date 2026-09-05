package dev.shvquu.betternpcs.api.skin;

import dev.shvquu.betternpcs.api.npc.property.NpcSkin;
import java.util.Objects;
import java.util.UUID;

/**
 * A description of where a skin should come from, before it has been fetched.
 *
 * <p>Kept separate from {@link NpcSkin} because the two answer different questions. This type says
 * <em>"the skin of the player Notch"</em>, which is stable, human-readable and safe to write into a
 * configuration file. {@link NpcSkin} holds the several hundred bytes of base64 that resolving that
 * request produced, which is what actually goes on the wire.
 *
 * <p>Both are persisted: the source so that {@code /npc reload} can re-resolve a skin whose owner
 * has since changed it, the resolved value so that a restart does not have to reach Mojang at all.
 *
 * <p>This interface is sealed. Extensions that fetch skins from elsewhere register a
 * {@link SkinProvider} rather than adding a source type — the set of ways to <em>name</em> a skin is
 * small and fixed, while the set of places to fetch one from is not.
 *
 * @since 1.0.0
 */
public sealed interface SkinSource {

    /**
     * Which kind of source this is, for serialisation and for {@code switch} statements that need a
     * plain value rather than a pattern.
     *
     * @since 1.0.0
     */
    enum Kind {
        /** A player name; see {@link ByName}. */
        NAME,
        /** A player unique id; see {@link ByUniqueId}. */
        UUID,
        /** A literal texture value; see {@link ByTexture}. */
        TEXTURE
    }

    /**
     * Returns which kind of source this is.
     *
     * @return the kind
     */
    Kind kind();

    /**
     * Returns the single value that identifies this source, for storage and for display.
     *
     * <p>Together with {@link #kind()} this round-trips through {@link #of(Kind, String)}.
     *
     * @return the player name, the unique id, or the texture value
     */
    String value();

    /**
     * Returns whether resolving this source may need a network call.
     *
     * <p>Callers on the main thread use this to decide whether they can resolve inline or must go
     * through {@link SkinService#resolve(SkinSource)} and continue asynchronously.
     *
     * @return {@code true} if resolution may block
     */
    boolean requiresLookup();

    /**
     * Returns a source naming a player by their current name.
     *
     * <p>Names are not stable — a player may change theirs, and another player may take the old one.
     * A source built this way therefore resolves to whoever holds the name at the time it is
     * fetched. Use {@link #uniqueId(UUID)} when the identity has to stay fixed.
     *
     * @param name the player name
     * @return the source
     * @throws NullPointerException     if {@code name} is {@code null}
     * @throws IllegalArgumentException if {@code name} is blank or longer than 16 characters
     */
    static SkinSource playerName(String name) {
        return new ByName(name);
    }

    /**
     * Returns a source naming a player by their unique id.
     *
     * @param uniqueId the player's unique id
     * @return the source
     * @throws NullPointerException if {@code uniqueId} is {@code null}
     */
    static SkinSource uniqueId(UUID uniqueId) {
        return new ByUniqueId(uniqueId);
    }

    /**
     * Returns a source holding an already-known texture, which needs no lookup.
     *
     * @param skin the resolved skin
     * @return the source
     * @throws NullPointerException if {@code skin} is {@code null}
     */
    static SkinSource texture(NpcSkin skin) {
        return new ByTexture(skin);
    }

    /**
     * Rebuilds a source from its serialised form.
     *
     * <p>The texture kind is not supported here, because a texture needs both a value and a
     * signature and therefore does not fit in a single string. Deserialise those with
     * {@link #texture(NpcSkin)}.
     *
     * @param kind  the kind, from {@link #kind()}
     * @param value the value, from {@link #value()}
     * @return the source
     * @throws NullPointerException     if either argument is {@code null}
     * @throws IllegalArgumentException if {@code kind} is {@link Kind#TEXTURE}, or {@code value} is
     *                                  not valid for {@code kind}
     */
    static SkinSource of(Kind kind, String value) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(value, "value");
        return switch (kind) {
            case NAME -> playerName(value);
            case UUID -> {
                try {
                    yield uniqueId(java.util.UUID.fromString(value.trim()));
                } catch (IllegalArgumentException malformed) {
                    throw new IllegalArgumentException("Not a unique id: '" + value + "'", malformed);
                }
            }
            case TEXTURE -> throw new IllegalArgumentException(
                    "A texture source carries a signature as well and cannot be rebuilt from one string");
        };
    }

    /**
     * A skin named by the current owner of a player name.
     *
     * @param value the player name
     * @since 1.0.0
     */
    record ByName(String value) implements SkinSource {

        /**
         * Creates the source.
         *
         * @param value the player name
         * @throws NullPointerException     if {@code value} is {@code null}
         * @throws IllegalArgumentException if {@code value} is blank or longer than 16 characters
         */
        public ByName {
            Objects.requireNonNull(value, "value");
            value = value.trim();
            if (value.isEmpty() || value.length() > 16) {
                throw new IllegalArgumentException(
                        "A player name is 1 to 16 characters, was '" + value + "'");
            }
        }

        @Override
        public Kind kind() {
            return Kind.NAME;
        }

        @Override
        public boolean requiresLookup() {
            return true;
        }
    }

    /**
     * A skin named by a player's unique id.
     *
     * @param uniqueId the player's unique id
     * @since 1.0.0
     */
    record ByUniqueId(UUID uniqueId) implements SkinSource {

        /**
         * Creates the source.
         *
         * @param uniqueId the player's unique id
         * @throws NullPointerException if {@code uniqueId} is {@code null}
         */
        public ByUniqueId {
            Objects.requireNonNull(uniqueId, "uniqueId");
        }

        @Override
        public Kind kind() {
            return Kind.UUID;
        }

        @Override
        public String value() {
            return uniqueId.toString();
        }

        @Override
        public boolean requiresLookup() {
            return true;
        }
    }

    /**
     * An already-resolved skin, wrapped so that it can be used wherever a source is expected.
     *
     * @param skin the resolved skin
     * @since 1.0.0
     */
    record ByTexture(NpcSkin skin) implements SkinSource {

        /**
         * Creates the source.
         *
         * @param skin the resolved skin
         * @throws NullPointerException if {@code skin} is {@code null}
         */
        public ByTexture {
            Objects.requireNonNull(skin, "skin");
        }

        @Override
        public Kind kind() {
            return Kind.TEXTURE;
        }

        @Override
        public String value() {
            return skin.value();
        }

        @Override
        public boolean requiresLookup() {
            return false;
        }
    }
}
