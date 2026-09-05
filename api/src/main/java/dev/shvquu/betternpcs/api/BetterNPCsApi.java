package dev.shvquu.betternpcs.api;

import dev.shvquu.betternpcs.api.action.ActionRegistry;
import dev.shvquu.betternpcs.api.extension.ExtensionManager;
import dev.shvquu.betternpcs.api.npc.NpcManager;
import dev.shvquu.betternpcs.api.skin.SkinService;
import net.kyori.adventure.text.Component;

/**
 * The root of the BetterNPCs API.
 *
 * <p>Obtained from {@link BetterNPCs#get()} once BetterNPCs has enabled, or from Bukkit's services
 * manager, where it is registered under this interface.
 *
 * <p>Everything reachable from here is safe for third-party plugins to depend on under the promise
 * described by {@link ApiVersion}. Nothing outside the {@code dev.shvquu.betternpcs.api} package is.
 *
 * @since 1.0.0
 */
public interface BetterNPCsApi {

    /**
     * Returns the API version this implementation provides.
     *
     * <p>Check it at startup if the plugin uses anything added after 1.0.0:
     *
     * <pre>{@code
     * if (!api.apiVersion().isCompatibleWith(1, 2)) {
     *     getLogger().severe("BetterNPCs 1.2 or newer is required");
     *     getServer().getPluginManager().disablePlugin(this);
     *     return;
     * }
     * }</pre>
     *
     * @return the API version
     */
    ApiVersion apiVersion();

    /**
     * Returns the version of the BetterNPCs plugin itself.
     *
     * <p>For diagnostics. Version checks belong on {@link #apiVersion()}, which is what actually
     * describes compatibility.
     *
     * @return the plugin version
     */
    String pluginVersion();

    /**
     * Returns the Minecraft version BetterNPCs detected, normalised.
     *
     * @return the version, for example {@code 1.21.4} or {@code 26.2}
     */
    String minecraftVersion();

    /**
     * Returns the registry of every NPC.
     *
     * @return the NPC manager
     */
    NpcManager npcManager();

    /**
     * Returns the skin resolution service.
     *
     * @return the skin service
     */
    SkinService skinService();

    /**
     * Returns the registry of action handlers.
     *
     * @return the action registry
     */
    ActionRegistry actionRegistry();

    /**
     * Returns the extension manager.
     *
     * @return the extension manager
     */
    ExtensionManager extensionManager();

    /**
     * Renders MiniMessage source, expanding BetterNPCs' own placeholders and, when it is installed,
     * PlaceholderAPI's.
     *
     * <p>Offered so that an extension formats text exactly the way the rest of the plugin does,
     * including the tag set and the escaping rules — placeholder values are inserted as literal text
     * rather than re-parsed, so a hostile player name cannot inject formatting or a click event.
     *
     * @param miniMessage the MiniMessage source
     * @param player      the player to resolve placeholders for, or {@code null} for none
     * @return the rendered component
     * @throws NullPointerException if {@code miniMessage} is {@code null}
     */
    Component render(String miniMessage, org.bukkit.entity.Player player);

    /**
     * Returns whether PlaceholderAPI was found and hooked.
     *
     * @return {@code true} if PlaceholderAPI placeholders resolve
     */
    boolean isPlaceholderApiAvailable();
}
