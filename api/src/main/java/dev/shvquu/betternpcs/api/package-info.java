/**
 * The root of the BetterNPCs public API.
 *
 * <p>{@link dev.shvquu.betternpcs.api.BetterNPCs} is the entry point;
 * {@link dev.shvquu.betternpcs.api.BetterNPCsApi} is the service it hands out.
 *
 * <p>Everything under {@code dev.shvquu.betternpcs.api} is covered by the compatibility promise in
 * {@link dev.shvquu.betternpcs.api.ApiVersion}, except members annotated
 * {@link dev.shvquu.betternpcs.api.Internal}. Nothing outside it is: the {@code core},
 * {@code storage} and {@code nms} packages are implementation detail and change without notice.
 *
 * @since 1.0.0
 */
package dev.shvquu.betternpcs.api;
