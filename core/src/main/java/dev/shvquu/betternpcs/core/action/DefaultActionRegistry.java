package dev.shvquu.betternpcs.core.action;

import dev.shvquu.betternpcs.api.action.ActionDefinition;
import dev.shvquu.betternpcs.api.action.ActionRegistry;
import dev.shvquu.betternpcs.api.action.NpcActionHandler;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The default {@link ActionRegistry}.
 *
 * <p>Holds one entry per id <em>and</em> per alias, pointing at the same handler, so a lookup is a
 * single map read regardless of how a handler is addressed. That matters because a lookup happens
 * for every action of every interaction.
 *
 * <p>Thread safe for reads. Registration is expected on the main thread during enable.
 *
 * @since 1.0.0
 */
public final class DefaultActionRegistry implements ActionRegistry {

    private final Map<String, NpcActionHandler> byName = new ConcurrentHashMap<>();

    @Override
    public void register(NpcActionHandler handler) {
        Objects.requireNonNull(handler, "handler");

        Set<String> names = new LinkedHashSet<>();
        names.add(validName(handler.id(), "id"));
        for (String alias : handler.aliases()) {
            names.add(validName(alias, "alias"));
        }

        // Claim every name before publishing any of them, so a conflict on the third alias cannot
        // leave the first two pointing at a handler that was never registered.
        for (String name : names) {
            NpcActionHandler existing = byName.get(name);
            if (existing != null && existing != handler) {
                throw new IllegalStateException(
                        "The action name '" + name + "' is already taken by " + existing.id()
                                + ". An extension silently replacing a built-in action such as "
                                + "'console' would be a serious security surprise, so this is refused.");
            }
        }
        names.forEach(name -> byName.put(name, handler));
    }

    private static String validName(String name, String what) {
        Objects.requireNonNull(name, what);
        String normalised = name.trim().toLowerCase(Locale.ROOT);
        if (normalised.isEmpty()) {
            throw new IllegalArgumentException("An action " + what + " must not be blank");
        }
        for (int i = 0; i < normalised.length(); i++) {
            char character = normalised.charAt(i);
            if (character == ':' || Character.isWhitespace(character)) {
                // ActionDefinition splits on the first colon, so a name containing one could never
                // be addressed from a configuration file at all.
                throw new IllegalArgumentException(
                        "An action " + what + " must not contain ':' or whitespace, was '" + name + "'");
            }
        }
        return normalised;
    }

    @Override
    public boolean unregister(String id) {
        Objects.requireNonNull(id, "id");
        NpcActionHandler handler = byName.get(id.trim().toLowerCase(Locale.ROOT));
        if (handler == null) {
            return false;
        }
        // Removed by value so that every alias of this handler goes with it, and so that a name
        // another handler has since claimed is left alone.
        return byName.values().removeIf(candidate -> candidate == handler);
    }

    @Override
    public Optional<NpcActionHandler> find(String idOrAlias) {
        Objects.requireNonNull(idOrAlias, "idOrAlias");
        return Optional.ofNullable(byName.get(idOrAlias.trim().toLowerCase(Locale.ROOT)));
    }

    @Override
    public Collection<NpcActionHandler> handlers() {
        // Distinct by identity: a handler with three aliases appears three times in the map.
        return List.copyOf(new LinkedHashSet<>(byName.values()));
    }

    @Override
    public void validate(ActionDefinition definition) {
        Objects.requireNonNull(definition, "definition");

        NpcActionHandler handler = find(definition.type()).orElseThrow(() ->
                new IllegalArgumentException(
                        "There is no action called '" + definition.type() + "'. Known: " + knownNames()));

        try {
            handler.validate(definition.argument());
        } catch (IllegalArgumentException rejected) {
            throw new IllegalArgumentException(
                    "The '" + definition.type() + "' action rejected its argument: "
                            + rejected.getMessage(), rejected);
        }
    }

    /**
     * Returns every registered id and alias, sorted, for an error message that tells the user what
     * they could have written instead.
     *
     * @return a comma-separated list
     */
    public String knownNames() {
        return byName.keySet().stream().sorted().collect(java.util.stream.Collectors.joining(", "));
    }
}
