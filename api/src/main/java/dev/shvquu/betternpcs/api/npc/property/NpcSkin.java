package dev.shvquu.betternpcs.api.npc.property;

import java.util.Objects;
import java.util.Optional;

/**
 * A resolved player skin: the base64 texture property Mojang issues, with its optional signature.
 *
 * <p>This is the final form a skin takes — a {@link dev.shvquu.betternpcs.api.skin.SkinSource} is
 * what gets resolved <em>into</em> one of these, possibly by an HTTP call. Storing the resolved
 * value means a restart does not have to hit Mojang's session servers again.
 *
 * <p>The signature matters more than it looks. Vanilla clients only render a skin whose texture
 * property carries Mojang's signature; an unsigned skin shows the default Steve or Alex model. An
 * unsigned skin is still worth keeping, because servers running in offline mode with a proxy that
 * forwards profile properties may accept one, so this type represents it rather than rejecting it.
 *
 * <p>Instances are immutable and safe to share across threads.
 *
 * @since 1.0.0
 */
public final class NpcSkin {

    private final String value;
    private final String signature;

    private NpcSkin(String value, String signature) {
        this.value = value;
        this.signature = signature;
    }

    /**
     * Creates a signed skin.
     *
     * @param value     the base64 {@code textures} property value
     * @param signature Mojang's signature for {@code value}
     * @return the signed skin
     * @throws NullPointerException     if either argument is {@code null}
     * @throws IllegalArgumentException if either argument is blank
     */
    public static NpcSkin of(String value, String signature) {
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(signature, "signature");
        requireNotBlank(value, "value");
        requireNotBlank(signature, "signature");
        return new NpcSkin(value, signature);
    }

    /**
     * Creates an unsigned skin.
     *
     * <p>Vanilla clients will not render this. Use it only when a proxy or authentication setup is
     * known to supply the signature separately.
     *
     * @param value the base64 {@code textures} property value
     * @return the unsigned skin
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public static NpcSkin unsigned(String value) {
        Objects.requireNonNull(value, "value");
        requireNotBlank(value, "value");
        return new NpcSkin(value, null);
    }

    /**
     * Creates a skin from a value and a signature that may be absent.
     *
     * <p>Convenience for deserialisation, where the signature is simply a column that may be null.
     *
     * @param value     the base64 {@code textures} property value
     * @param signature the signature, or {@code null} or blank for an unsigned skin
     * @return the skin
     * @throws NullPointerException     if {@code value} is {@code null}
     * @throws IllegalArgumentException if {@code value} is blank
     */
    public static NpcSkin ofNullable(String value, String signature) {
        return signature == null || signature.isBlank() ? unsigned(value) : of(value, signature);
    }

    private static void requireNotBlank(String candidate, String name) {
        if (candidate.isBlank()) {
            throw new IllegalArgumentException("Skin " + name + " must not be blank");
        }
    }

    /**
     * Returns the base64 encoded {@code textures} property value.
     *
     * @return the texture value, never blank
     */
    public String value() {
        return value;
    }

    /**
     * Returns Mojang's signature for {@link #value()}.
     *
     * @return the signature, or empty if this skin is unsigned
     */
    public Optional<String> signature() {
        return Optional.ofNullable(signature);
    }

    /**
     * Returns whether this skin carries a signature and will therefore render on a vanilla client.
     *
     * @return {@code true} if a signature is present
     */
    public boolean isSigned() {
        return signature != null;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof NpcSkin skin
                && value.equals(skin.value)
                && Objects.equals(signature, skin.signature);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, signature);
    }

    /**
     * Returns a description that deliberately omits the texture payload.
     *
     * <p>The value is several hundred characters of base64 and the signature is longer still;
     * printing either turns a log line into noise, so only the length and signing state appear.
     *
     * @return a short description
     */
    @Override
    public String toString() {
        return "NpcSkin[" + value.length() + " chars, " + (isSigned() ? "signed" : "unsigned") + "]";
    }
}
