package dev.shvquu.betternpcs.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type or member that is technically public but is not part of the supported API.
 *
 * <p>Annotated elements exist only so that the BetterNPCs implementation modules — which live in
 * different jars and therefore cannot use package-private access — can reach them. They may change
 * or disappear in any release, including a patch release, and the semantic versioning promise in
 * {@link ApiVersion} does not cover them.
 *
 * <p>Third-party plugins must not call annotated members.
 *
 * @since 1.0.0
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface Internal {
}
