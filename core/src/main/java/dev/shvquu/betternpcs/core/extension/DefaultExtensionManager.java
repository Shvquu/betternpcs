package dev.shvquu.betternpcs.core.extension;

import dev.shvquu.betternpcs.api.BetterNPCsApi;
import dev.shvquu.betternpcs.api.extension.ExtensionManager;
import dev.shvquu.betternpcs.api.extension.NpcExtension;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The default {@link ExtensionManager}.
 *
 * <p>Handles the one thing that makes an extension system worth having over a list of callbacks:
 * ordering. Plugins load in whatever order the server decides, so an extension may well register
 * before the one it depends on exists. Rather than failing, it waits — and registering the
 * dependency later enables it and everything that was waiting for it, in dependency order.
 *
 * <p>All methods must be called on the main server thread.
 *
 * @since 1.0.0
 */
public final class DefaultExtensionManager implements ExtensionManager {

    private record Registration(NpcExtension extension, State state) {
    }

    private final Map<String, Registration> registrations = new LinkedHashMap<>();
    private final Supplier<BetterNPCsApi> api;
    private final Logger logger;

    /**
     * Creates the manager.
     *
     * @param api    supplies the API handed to extensions; a supplier because extensions are
     *               registered after the API exists but the manager is built before it
     * @param logger where extension failures are reported
     * @throws NullPointerException if either argument is {@code null}
     */
    public DefaultExtensionManager(Supplier<BetterNPCsApi> api, Logger logger) {
        this.api = Objects.requireNonNull(api, "api");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @Override
    public void register(NpcExtension extension) {
        Objects.requireNonNull(extension, "extension");
        String id = Objects.requireNonNull(extension.id(), "extension.id");

        if (registrations.containsKey(id)) {
            throw new IllegalStateException("An extension with the id '" + id + "' is already registered");
        }
        registrations.put(id, new Registration(extension, State.PENDING));

        // Registering one extension can enable several: this one, and anything that was waiting for
        // it. Resolving the whole set is simpler than working out which subset became eligible.
        enablePending();
    }

    private void enablePending() {
        boolean progress = true;
        while (progress) {
            progress = false;
            for (Map.Entry<String, Registration> entry : new ArrayList<>(registrations.entrySet())) {
                Registration registration = entry.getValue();
                if (registration.state() != State.PENDING || !dependenciesSatisfied(registration)) {
                    continue;
                }
                enable(entry.getKey(), registration.extension());
                // One extension enabling may satisfy another's dependencies, so the set is walked
                // again rather than assumed complete.
                progress = true;
            }
        }
    }

    private boolean dependenciesSatisfied(Registration registration) {
        for (String dependency : registration.extension().dependencies()) {
            Registration required = registrations.get(dependency);
            if (required == null || required.state() != State.ENABLED) {
                return false;
            }
        }
        return true;
    }

    private void enable(String id, NpcExtension extension) {
        try {
            extension.onEnable(api.get());
            registrations.put(id, new Registration(extension, State.ENABLED));
            logger.info(() -> "Enabled the extension '" + id + "' (" + extension.version() + ").");
        } catch (RuntimeException failure) {
            registrations.put(id, new Registration(extension, State.FAILED));
            // Logged and contained. An extension that cannot start is that extension's problem;
            // taking BetterNPCs down with it would take every other extension too.
            logger.log(Level.WARNING, failure,
                    () -> "The extension '" + id + "' failed to enable and has been disabled.");
        }
    }

    @Override
    public boolean unregister(String id) {
        Objects.requireNonNull(id, "id");
        Registration registration = registrations.get(id);
        if (registration == null) {
            return false;
        }

        // Dependents first, and they become pending rather than being removed, so re-registering
        // this extension restores the whole chain.
        for (String dependent : dependentsOf(id)) {
            disable(dependent, State.PENDING);
        }
        disable(id, null);
        registrations.remove(id);
        return true;
    }

    private List<String> dependentsOf(String id) {
        List<String> dependents = new ArrayList<>();
        registrations.forEach((candidateId, registration) -> {
            if (registration.extension().dependencies().contains(id)) {
                dependents.add(candidateId);
            }
        });
        return dependents;
    }

    private void disable(String id, State newState) {
        Registration registration = registrations.get(id);
        if (registration == null || registration.state() != State.ENABLED) {
            return;
        }
        try {
            registration.extension().onDisable();
        } catch (RuntimeException failure) {
            logger.log(Level.WARNING, failure,
                    () -> "The extension '" + id + "' failed while shutting down.");
        }
        if (newState != null) {
            registrations.put(id, new Registration(registration.extension(), newState));
        }
    }

    /**
     * Disables every enabled extension, in reverse dependency order.
     *
     * <p>Called during plugin shutdown. Reverse order matters: an extension must still be able to
     * use whatever it depends on while it is cleaning up.
     */
    public void disableAll() {
        List<String> order = new ArrayList<>(registrations.keySet());
        java.util.Collections.reverse(order);
        order.forEach(id -> disable(id, State.DISABLED));
    }

    @Override
    public Optional<NpcExtension> find(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(registrations.get(id)).map(Registration::extension);
    }

    @Override
    public Optional<State> state(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(registrations.get(id)).map(Registration::state);
    }

    @Override
    public Collection<NpcExtension> extensions() {
        return registrations.values().stream().map(Registration::extension).toList();
    }

    @Override
    public Set<String> unsatisfiedDependencies() {
        Set<String> missing = new LinkedHashSet<>();
        registrations.values().forEach(registration ->
                registration.extension().dependencies().stream()
                        .filter(dependency -> !registrations.containsKey(dependency))
                        .forEach(missing::add));
        return Set.copyOf(missing);
    }

    /**
     * Reports extensions still waiting for something that never arrived.
     *
     * <p>Called at the end of startup. Without it, a forgotten plugin presents as an extension
     * silently doing nothing, which is very hard to diagnose from the outside.
     */
    public void reportUnsatisfied() {
        Set<String> missing = unsatisfiedDependencies();
        if (missing.isEmpty()) {
            return;
        }
        logger.warning(() -> "These extensions are waiting for something that is not installed: "
                + String.join(", ", missing));
    }
}
