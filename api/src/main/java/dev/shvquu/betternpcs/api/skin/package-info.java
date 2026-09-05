/**
 * Skin resolution: naming a skin, fetching it, and plugging in another place to fetch it from.
 *
 * <p>{@link dev.shvquu.betternpcs.api.skin.SkinSource} names one,
 * {@link dev.shvquu.betternpcs.api.skin.SkinService} resolves it into a
 * {@link dev.shvquu.betternpcs.api.npc.property.NpcSkin}, and
 * {@link dev.shvquu.betternpcs.api.skin.SkinProvider} is the extension point for a server with its
 * own skin database.
 *
 * @since 1.0.0
 */
package dev.shvquu.betternpcs.api.skin;
