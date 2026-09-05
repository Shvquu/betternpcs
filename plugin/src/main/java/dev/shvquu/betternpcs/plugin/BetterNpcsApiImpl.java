package dev.shvquu.betternpcs.plugin;

import dev.shvquu.betternpcs.api.ApiVersion;
import dev.shvquu.betternpcs.api.BetterNPCsApi;
import dev.shvquu.betternpcs.api.action.ActionRegistry;
import dev.shvquu.betternpcs.api.extension.ExtensionManager;
import dev.shvquu.betternpcs.api.npc.NpcManager;
import dev.shvquu.betternpcs.api.skin.SkinService;
import dev.shvquu.betternpcs.core.i18n.MessageService;
import dev.shvquu.betternpcs.core.npc.NpcPlaceholders;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * The published {@link BetterNPCsApi}.
 *
 * <p>A thin façade over the engine. Everything it exposes already exists; the value of the separate
 * type is that it is the only thing third-party plugins see, so the engine can be rearranged without
 * breaking them.
 *
 * @since 1.0.0
 */
final class BetterNpcsApiImpl implements BetterNPCsApi {

    private final String pluginVersion;
    private final String minecraftVersion;
    private final NpcManager npcManager;
    private final SkinService skinService;
    private final ActionRegistry actionRegistry;
    private final ExtensionManager extensionManager;
    private final MessageService messages;
    private final boolean placeholderApi;

    /**
     * Creates the façade.
     *
     * @param pluginVersion    the plugin version
     * @param minecraftVersion the detected Minecraft version
     * @param npcManager       the NPC manager
     * @param skinService      the skin service
     * @param actionRegistry   the action registry
     * @param extensionManager the extension manager
     * @param messages         renders text
     * @param placeholderApi   whether PlaceholderAPI was found
     * @throws NullPointerException if any reference argument is {@code null}
     */
    BetterNpcsApiImpl(
            String pluginVersion,
            String minecraftVersion,
            NpcManager npcManager,
            SkinService skinService,
            ActionRegistry actionRegistry,
            ExtensionManager extensionManager,
            MessageService messages,
            boolean placeholderApi) {

        this.pluginVersion = Objects.requireNonNull(pluginVersion, "pluginVersion");
        this.minecraftVersion = Objects.requireNonNull(minecraftVersion, "minecraftVersion");
        this.npcManager = Objects.requireNonNull(npcManager, "npcManager");
        this.skinService = Objects.requireNonNull(skinService, "skinService");
        this.actionRegistry = Objects.requireNonNull(actionRegistry, "actionRegistry");
        this.extensionManager = Objects.requireNonNull(extensionManager, "extensionManager");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.placeholderApi = placeholderApi;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.CURRENT;
    }

    @Override
    public String pluginVersion() {
        return pluginVersion;
    }

    @Override
    public String minecraftVersion() {
        return minecraftVersion;
    }

    @Override
    public NpcManager npcManager() {
        return npcManager;
    }

    @Override
    public SkinService skinService() {
        return skinService;
    }

    @Override
    public ActionRegistry actionRegistry() {
        return actionRegistry;
    }

    @Override
    public ExtensionManager extensionManager() {
        return extensionManager;
    }

    @Override
    public Component render(String miniMessage, Player player) {
        Objects.requireNonNull(miniMessage, "miniMessage");
        // Routed through the same service the rest of the plugin uses, so an extension gets the same
        // tag set and — more importantly — the same escaping of placeholder values.
        return messages.renderRaw(player, miniMessage, NpcPlaceholders.tags(null, player));
    }

    @Override
    public boolean isPlaceholderApiAvailable() {
        return placeholderApi;
    }
}
