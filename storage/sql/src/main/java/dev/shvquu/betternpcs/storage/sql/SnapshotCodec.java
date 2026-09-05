package dev.shvquu.betternpcs.storage.sql;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.shvquu.betternpcs.api.action.ActionDefinition;
import dev.shvquu.betternpcs.api.interaction.InteractionType;
import dev.shvquu.betternpcs.api.npc.NpcSnapshot;
import dev.shvquu.betternpcs.api.npc.NpcType;
import dev.shvquu.betternpcs.api.npc.property.Billboard;
import dev.shvquu.betternpcs.api.npc.property.HologramSettings;
import dev.shvquu.betternpcs.api.npc.property.LookMode;
import dev.shvquu.betternpcs.api.npc.property.LookSettings;
import dev.shvquu.betternpcs.api.npc.property.NametagSettings;
import dev.shvquu.betternpcs.api.npc.property.NpcAppearance;
import dev.shvquu.betternpcs.api.npc.property.NpcEquipment;
import dev.shvquu.betternpcs.api.npc.property.NpcPosition;
import dev.shvquu.betternpcs.api.npc.property.NpcSkin;
import dev.shvquu.betternpcs.api.npc.property.NpcVisibility;
import dev.shvquu.betternpcs.api.npc.property.TextAlignment;
import dev.shvquu.betternpcs.api.npc.property.TextDisplayStyle;
import dev.shvquu.betternpcs.api.skin.SkinSource;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Converts the parts of an {@link NpcSnapshot} that do not deserve their own columns to and from
 * JSON.
 *
 * <h2>Why a JSON column rather than fifty columns</h2>
 *
 * <p>Identity and position are real columns because they are the only things ever queried or
 * indexed. Everything else — appearance flags, look settings, two text styles, equipment, actions,
 * metadata — is written as one JSON document. Fifty columns would mean a schema migration for every
 * new NPC property, on four database engines, and NPCs are always read and written whole anyway.
 *
 * <h2>Tolerance</h2>
 *
 * <p>Every read falls back to a default rather than failing. A row written by an older version is
 * missing whatever the newer one added, and a row written by a <em>newer</em> version may contain
 * things this one does not understand — a server owner who downgrades should get their NPCs back,
 * not an exception. The one exception is a value that is present but nonsensical, which is reported:
 * silently replacing it would hide corruption.
 *
 * @since 1.0.0
 */
public final class SnapshotCodec {

    private SnapshotCodec() {
        throw new AssertionError("No instances");
    }

    // ---------------------------------------------------------------------------------------------
    // Writing
    // ---------------------------------------------------------------------------------------------

    /**
     * Encodes everything about a snapshot that is not stored in its own column.
     *
     * @param snapshot the snapshot to encode
     * @return the JSON document
     * @throws NullPointerException if {@code snapshot} is {@code null}
     */
    public static String encode(NpcSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");

        JsonObject root = new JsonObject();
        snapshot.displayName().ifPresent(name -> root.addProperty("displayName", name));
        snapshot.skinSource().ifPresent(source -> root.add("skinSource", encodeSkinSource(source)));
        snapshot.resolvedSkin().ifPresent(skin -> root.add("skin", encodeSkin(skin)));

        root.add("equipment", encodeEquipment(snapshot.equipment()));
        root.add("appearance", encodeAppearance(snapshot.appearance()));
        root.add("visibility", encodeVisibility(snapshot.visibility()));
        root.add("look", encodeLook(snapshot.look()));
        root.add("nametag", encodeNametag(snapshot.nametag()));
        root.add("hologram", encodeHologram(snapshot.hologram()));
        root.add("actions", encodeActions(snapshot.actions()));
        root.add("metadata", encodeMetadata(snapshot.metadata()));

        return root.toString();
    }

    private static JsonObject encodeSkinSource(SkinSource source) {
        JsonObject json = new JsonObject();
        json.addProperty("kind", source.kind().name());
        json.addProperty("value", source.value());
        if (source instanceof SkinSource.ByTexture texture) {
            texture.skin().signature().ifPresent(signature -> json.addProperty("signature", signature));
        }
        return json;
    }

    private static JsonObject encodeSkin(NpcSkin skin) {
        JsonObject json = new JsonObject();
        json.addProperty("value", skin.value());
        skin.signature().ifPresent(signature -> json.addProperty("signature", signature));
        return json;
    }

    private static JsonObject encodeEquipment(NpcEquipment equipment) {
        JsonObject json = new JsonObject();
        equipment.asMap().forEach((slot, item) ->
                // serializeAsBytes carries Minecraft's own data version, so an item written on one
                // Minecraft version is upgraded rather than misread when it is loaded on a newer one.
                json.addProperty(slot.name(), Base64.getEncoder().encodeToString(item.serializeAsBytes())));
        return json;
    }

    private static JsonObject encodeAppearance(NpcAppearance appearance) {
        JsonObject json = new JsonObject();
        json.addProperty("glowing", appearance.glowing());
        appearance.glowColor().ifPresent(color ->
                json.addProperty("glowColor", NamedTextColor.NAMES.key(color)));
        json.addProperty("sneaking", appearance.sneaking());
        json.addProperty("onFire", appearance.onFire());
        json.addProperty("invisible", appearance.invisible());
        json.addProperty("collidable", appearance.collidable());
        json.addProperty("gravity", appearance.gravity());
        json.addProperty("silent", appearance.silent());
        json.addProperty("invulnerable", appearance.invulnerable());
        json.addProperty("listedInTablist", appearance.listedInTablist());
        return json;
    }

    private static JsonObject encodeVisibility(NpcVisibility visibility) {
        JsonObject json = new JsonObject();
        json.addProperty("viewDistance", visibility.viewDistance());
        visibility.permission().ifPresent(node -> json.addProperty("permission", node));
        json.addProperty("visibleByDefault", visibility.visibleByDefault());
        return json;
    }

    private static JsonObject encodeLook(LookSettings look) {
        JsonObject json = new JsonObject();
        json.addProperty("mode", look.mode().name());
        look.target().ifPresent(target -> json.add("target", encodePosition(target)));
        json.addProperty("range", look.range());
        json.addProperty("rotationSpeed", look.rotationSpeed());
        json.addProperty("updateInterval", look.updateInterval());
        json.addProperty("headOnly", look.headOnly());
        return json;
    }

    private static JsonObject encodePosition(NpcPosition position) {
        JsonObject json = new JsonObject();
        json.addProperty("world", position.world());
        json.addProperty("x", position.x());
        json.addProperty("y", position.y());
        json.addProperty("z", position.z());
        json.addProperty("yaw", position.yaw());
        json.addProperty("pitch", position.pitch());
        return json;
    }

    private static JsonObject encodeNametag(NametagSettings nametag) {
        JsonObject json = new JsonObject();
        json.addProperty("visible", nametag.visible());
        nametag.text().ifPresent(text -> json.addProperty("text", text));
        json.addProperty("verticalOffset", nametag.verticalOffset());
        json.addProperty("viewDistance", nametag.viewDistance());
        nametag.permission().ifPresent(node -> json.addProperty("permission", node));
        json.add("style", encodeStyle(nametag.style()));
        return json;
    }

    private static JsonObject encodeHologram(HologramSettings hologram) {
        JsonObject json = new JsonObject();
        JsonArray lines = new JsonArray();
        hologram.lines().forEach(lines::add);
        json.add("lines", lines);
        json.addProperty("verticalOffset", hologram.verticalOffset());
        json.addProperty("viewDistance", hologram.viewDistance());
        json.add("style", encodeStyle(hologram.style()));
        return json;
    }

    private static JsonObject encodeStyle(TextDisplayStyle style) {
        JsonObject json = new JsonObject();
        json.addProperty("billboard", style.billboard().name());
        json.addProperty("alignment", style.alignment().name());
        json.addProperty("scale", style.scale());
        json.addProperty("lineHeight", style.lineHeight());
        style.backgroundColor().ifPresent(color -> json.addProperty("backgroundColor", color));
        json.addProperty("textOpacity", style.textOpacity());
        json.addProperty("seeThrough", style.seeThrough());
        json.addProperty("shadowed", style.shadowed());
        return json;
    }

    private static JsonObject encodeActions(Map<InteractionType, List<ActionDefinition>> actions) {
        JsonObject json = new JsonObject();
        actions.forEach((interaction, definitions) -> {
            JsonArray array = new JsonArray();
            definitions.forEach(definition -> array.add(definition.serialize()));
            json.add(interaction.name(), array);
        });
        return json;
    }

    private static JsonObject encodeMetadata(Map<String, String> metadata) {
        JsonObject json = new JsonObject();
        metadata.forEach(json::addProperty);
        return json;
    }

    // ---------------------------------------------------------------------------------------------
    // Reading
    // ---------------------------------------------------------------------------------------------

    /**
     * Rebuilds a snapshot from its columns and JSON document.
     *
     * @param uniqueId       the NPC's unique id
     * @param name           the NPC's name
     * @param typeId         the serialised {@link NpcType}
     * @param position       where the NPC stands
     * @param spawnByDefault whether the NPC spawns when its world loads
     * @param json           the document produced by {@link #encode(NpcSnapshot)}, may be blank
     * @return the snapshot
     * @throws NullPointerException     if any argument except {@code json} is {@code null}
     * @throws IllegalArgumentException if the type id or the JSON document is unusable
     */
    public static NpcSnapshot decode(
            UUID uniqueId,
            String name,
            String typeId,
            NpcPosition position,
            boolean spawnByDefault,
            String json) {

        NpcSnapshot.Builder builder =
                NpcSnapshot.builder(uniqueId, name, NpcType.fromId(typeId), position)
                        .spawnByDefault(spawnByDefault);

        JsonObject root = parse(json);
        if (root == null) {
            return builder.build();
        }

        string(root, "displayName").ifPresent(builder::displayName);
        object(root, "skinSource").map(SnapshotCodec::decodeSkinSource).ifPresent(builder::skinSource);
        object(root, "skin").map(SnapshotCodec::decodeSkin).ifPresent(builder::resolvedSkin);

        object(root, "equipment").ifPresent(equipment -> builder.equipment(decodeEquipment(equipment)));
        object(root, "appearance").ifPresent(appearance -> builder.appearance(decodeAppearance(appearance)));
        object(root, "visibility").ifPresent(visibility -> builder.visibility(decodeVisibility(visibility)));
        object(root, "look").ifPresent(look -> builder.look(decodeLook(look)));
        object(root, "nametag").ifPresent(nametag -> builder.nametag(decodeNametag(nametag)));
        object(root, "hologram").ifPresent(hologram -> builder.hologram(decodeHologram(hologram)));
        object(root, "actions").ifPresent(actions -> decodeActions(actions, builder));
        object(root, "metadata").ifPresent(metadata -> decodeMetadata(metadata, builder));

        return builder.build();
    }

    private static JsonObject parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(json);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException malformed) {
            // Reported rather than swallowed: a row that will not parse is corruption, and quietly
            // loading a default NPC over the top of it would destroy whatever was recoverable.
            throw new IllegalArgumentException("The stored NPC data is not valid JSON", malformed);
        }
    }

    private static SkinSource decodeSkinSource(JsonObject json) {
        SkinSource.Kind kind = enumValue(json, "kind", SkinSource.Kind.class, SkinSource.Kind.NAME);
        String value = string(json, "value").orElse("");
        if (value.isBlank()) {
            return null;
        }
        if (kind == SkinSource.Kind.TEXTURE) {
            return SkinSource.texture(
                    NpcSkin.ofNullable(value, string(json, "signature").orElse(null)));
        }
        return SkinSource.of(kind, value);
    }

    private static NpcSkin decodeSkin(JsonObject json) {
        return string(json, "value")
                .map(value -> NpcSkin.ofNullable(value, string(json, "signature").orElse(null)))
                .orElse(null);
    }

    private static NpcEquipment decodeEquipment(JsonObject json) {
        NpcEquipment.Builder builder = NpcEquipment.builder();
        for (String slotName : json.keySet()) {
            EquipmentSlot slot;
            try {
                slot = EquipmentSlot.valueOf(slotName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException notOnThisVersion) {
                // A slot a newer Minecraft version added. Skipping it loses one item; refusing would
                // lose the whole NPC.
                continue;
            }
            string(json, slotName).ifPresent(encoded -> {
                try {
                    builder.set(slot, ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)));
                } catch (RuntimeException unreadable) {
                    // Same reasoning: one unreadable item must not cost the NPC.
                }
            });
        }
        return builder.build();
    }

    private static NpcAppearance decodeAppearance(JsonObject json) {
        NpcAppearance defaults = NpcAppearance.DEFAULT;
        return NpcAppearance.builder()
                .glowing(bool(json, "glowing", defaults.glowing()))
                .glowColor(string(json, "glowColor").map(NamedTextColor.NAMES::value).orElse(null))
                .sneaking(bool(json, "sneaking", defaults.sneaking()))
                .onFire(bool(json, "onFire", defaults.onFire()))
                .invisible(bool(json, "invisible", defaults.invisible()))
                .collidable(bool(json, "collidable", defaults.collidable()))
                .gravity(bool(json, "gravity", defaults.gravity()))
                .silent(bool(json, "silent", defaults.silent()))
                .invulnerable(bool(json, "invulnerable", defaults.invulnerable()))
                .listedInTablist(bool(json, "listedInTablist", defaults.listedInTablist()))
                .build();
    }

    private static NpcVisibility decodeVisibility(JsonObject json) {
        return NpcVisibility.DEFAULT
                .withViewDistance(number(json, "viewDistance", NpcVisibility.SERVER_VIEW_DISTANCE))
                .withPermission(string(json, "permission").orElse(null))
                .withVisibleByDefault(bool(json, "visibleByDefault", true));
    }

    private static LookSettings decodeLook(JsonObject json) {
        LookSettings defaults = LookSettings.NONE;
        LookMode mode = enumValue(json, "mode", LookMode.class, LookMode.NONE);

        LookSettings.Builder builder = LookSettings.builder()
                .mode(mode)
                .range(number(json, "range", defaults.range()))
                .rotationSpeed((float) number(json, "rotationSpeed", defaults.rotationSpeed()))
                .updateInterval((int) number(json, "updateInterval", defaults.updateInterval()))
                .headOnly(bool(json, "headOnly", defaults.headOnly()));

        object(json, "target").map(SnapshotCodec::decodePosition).ifPresent(builder::target);

        if (mode == LookMode.LOOK_AT_LOCATION && object(json, "target").isEmpty()) {
            // The builder would refuse this combination. A row in that state is corrupt, and the
            // least destructive reading is an NPC that simply does not turn.
            builder.mode(LookMode.NONE);
        }
        return builder.build();
    }

    private static NpcPosition decodePosition(JsonObject json) {
        return new NpcPosition(
                string(json, "world").orElseThrow(() ->
                        new IllegalArgumentException("A stored position has no world")),
                number(json, "x", 0),
                number(json, "y", 0),
                number(json, "z", 0),
                (float) number(json, "yaw", 0),
                (float) number(json, "pitch", 0));
    }

    private static NametagSettings decodeNametag(JsonObject json) {
        NametagSettings defaults = NametagSettings.DEFAULT;
        return NametagSettings.builder()
                .visible(bool(json, "visible", defaults.visible()))
                .text(string(json, "text").orElse(null))
                .verticalOffset(number(json, "verticalOffset", defaults.verticalOffset()))
                .viewDistance(number(json, "viewDistance", NametagSettings.INHERIT_VIEW_DISTANCE))
                .permission(string(json, "permission").orElse(null))
                .style(object(json, "style").map(SnapshotCodec::decodeStyle).orElse(defaults.style()))
                .build();
    }

    private static HologramSettings decodeHologram(JsonObject json) {
        HologramSettings.Builder builder = HologramSettings.builder()
                .verticalOffset(number(json, "verticalOffset", 0))
                .viewDistance(number(json, "viewDistance", HologramSettings.INHERIT_VIEW_DISTANCE))
                .style(object(json, "style").map(SnapshotCodec::decodeStyle)
                        .orElse(TextDisplayStyle.DEFAULT));

        JsonElement lines = json.get("lines");
        if (lines != null && lines.isJsonArray()) {
            List<String> values = new ArrayList<>();
            lines.getAsJsonArray().forEach(line -> {
                if (line.isJsonPrimitive()) {
                    values.add(line.getAsString());
                }
            });
            builder.lines(values);
        }
        return builder.build();
    }

    private static TextDisplayStyle decodeStyle(JsonObject json) {
        TextDisplayStyle defaults = TextDisplayStyle.DEFAULT;
        TextDisplayStyle.Builder builder = TextDisplayStyle.builder()
                .billboard(enumValue(json, "billboard", Billboard.class, defaults.billboard()))
                .alignment(enumValue(json, "alignment", TextAlignment.class, defaults.alignment()))
                .scale((float) number(json, "scale", defaults.scale()))
                .lineHeight(number(json, "lineHeight", defaults.lineHeight()))
                .textOpacity((int) number(json, "textOpacity", defaults.textOpacity()))
                .seeThrough(bool(json, "seeThrough", defaults.seeThrough()))
                .shadowed(bool(json, "shadowed", defaults.shadowed()));

        JsonElement background = json.get("backgroundColor");
        if (background != null && background.isJsonPrimitive()) {
            builder.backgroundColor(background.getAsInt());
        }
        return builder.build();
    }

    private static void decodeActions(JsonObject json, NpcSnapshot.Builder builder) {
        for (String interactionName : json.keySet()) {
            InteractionType interaction;
            try {
                interaction = InteractionType.valueOf(interactionName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException unknown) {
                continue;
            }
            JsonElement lines = json.get(interactionName);
            if (lines == null || !lines.isJsonArray()) {
                continue;
            }
            List<ActionDefinition> definitions = new ArrayList<>();
            for (JsonElement line : lines.getAsJsonArray()) {
                if (!line.isJsonPrimitive()) {
                    continue;
                }
                try {
                    definitions.add(ActionDefinition.parse(line.getAsString()));
                } catch (IllegalArgumentException malformed) {
                    // One unreadable action line must not cost the NPC its other actions.
                }
            }
            if (!definitions.isEmpty()) {
                builder.actions(interaction, definitions);
            }
        }
    }

    private static void decodeMetadata(JsonObject json, NpcSnapshot.Builder builder) {
        for (String key : json.keySet()) {
            string(json, key).ifPresent(value -> builder.metadata(key, value));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Tolerant readers
    // ---------------------------------------------------------------------------------------------

    private static java.util.Optional<String> string(JsonObject json, String member) {
        JsonElement element = json.get(member);
        if (element == null || !element.isJsonPrimitive()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(element.getAsString());
    }

    private static java.util.Optional<JsonObject> object(JsonObject json, String member) {
        JsonElement element = json.get(member);
        if (element == null || !element.isJsonObject()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(element.getAsJsonObject());
    }

    private static boolean bool(JsonObject json, String member, boolean fallback) {
        JsonElement element = json.get(member);
        return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : fallback;
    }

    private static double number(JsonObject json, String member, double fallback) {
        JsonElement element = json.get(member);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsDouble();
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    private static <E extends Enum<E>> E enumValue(
            JsonObject json, String member, Class<E> type, E fallback) {
        return string(json, member)
                .map(name -> {
                    try {
                        return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException unknown) {
                        // A constant a newer version added. Falling back keeps the NPC loadable.
                        return fallback;
                    }
                })
                .orElse(fallback);
    }
}
