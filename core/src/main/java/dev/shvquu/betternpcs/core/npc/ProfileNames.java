package dev.shvquu.betternpcs.core.npc;

import dev.shvquu.betternpcs.core.render.NpcView;
import java.util.Objects;
import java.util.UUID;

/**
 * Derives the game profile name a player NPC is spawned with.
 *
 * <p>Minecraft's profile name is not the name players see — that is drawn separately as a text
 * display — but it still has to exist and be well formed. A name the client cannot decode does not
 * merely fail to render the NPC: it drops the connection with a protocol error, which looks to a
 * server owner like the plugin crashing their players.
 *
 * <p>Two rules therefore matter more than looking pretty. The result is at most
 * {@value NpcView#MAX_PROFILE_NAME_LENGTH} characters, and it contains only characters vanilla
 * accepts in a name. Anything else about it is cosmetic — it briefly appears in the tab list before
 * the entry is withdrawn, and it shows up in packet dumps, so a recognisable name is worth having
 * where the NPC's own name allows one.
 *
 * @since 1.0.0
 */
public final class ProfileNames {

    private ProfileNames() {
        throw new AssertionError("No instances");
    }

    /**
     * Returns a profile name for an NPC.
     *
     * <p>Uses the NPC's own name where it survives sanitising, so that a packet dump or a moment's
     * tab list flicker shows something recognisable. Falls back to characters from the unique id
     * when it does not, which is the case for an NPC named entirely in a non-Latin script.
     *
     * @param npcName  the NPC's name
     * @param uniqueId the NPC's unique id, used as the fallback and to keep names distinct
     * @return a name of 1 to {@value NpcView#MAX_PROFILE_NAME_LENGTH} valid characters
     * @throws NullPointerException if either argument is {@code null}
     */
    public static String forNpc(String npcName, UUID uniqueId) {
        Objects.requireNonNull(npcName, "npcName");
        Objects.requireNonNull(uniqueId, "uniqueId");

        StringBuilder sanitised = new StringBuilder(NpcView.MAX_PROFILE_NAME_LENGTH);
        for (int i = 0; i < npcName.length() && sanitised.length() < NpcView.MAX_PROFILE_NAME_LENGTH; i++) {
            char character = npcName.charAt(i);
            if (isAllowed(character)) {
                sanitised.append(character);
            }
        }

        if (sanitised.isEmpty()) {
            // Nothing usable survived — an NPC named entirely in Cyrillic or Japanese, for instance.
            // The unique id is always available and always valid.
            return fromUniqueId(uniqueId);
        }
        return sanitised.toString();
    }

    /**
     * Returns a profile name derived from a unique id alone.
     *
     * @param uniqueId the unique id
     * @return a name of exactly {@value NpcView#MAX_PROFILE_NAME_LENGTH} valid characters
     * @throws NullPointerException if {@code uniqueId} is {@code null}
     */
    public static String fromUniqueId(UUID uniqueId) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        // The hyphen-free form is hex, every character of which vanilla accepts.
        String hex = uniqueId.toString().replace("-", "");
        return hex.substring(0, NpcView.MAX_PROFILE_NAME_LENGTH);
    }

    private static boolean isAllowed(char character) {
        return (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9')
                || character == '_';
    }

    /**
     * Returns whether a string is usable as a profile name as it stands.
     *
     * @param candidate the string to check
     * @return {@code true} if it needs no sanitising
     */
    public static boolean isValid(String candidate) {
        if (candidate == null
                || candidate.isEmpty()
                || candidate.length() > NpcView.MAX_PROFILE_NAME_LENGTH) {
            return false;
        }
        for (int i = 0; i < candidate.length(); i++) {
            if (!isAllowed(candidate.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
