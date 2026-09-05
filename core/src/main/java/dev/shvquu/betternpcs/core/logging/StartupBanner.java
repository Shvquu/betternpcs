package dev.shvquu.betternpcs.core.logging;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds the block of text BetterNPCs writes to the console when it starts.
 *
 * <p>The banner is not decoration. It is the first thing anyone is asked for when a server owner
 * reports a problem, so every line in it answers a question that would otherwise cost a round trip:
 * which build, which API, which Minecraft version, which server software, which Java, which storage
 * backend, which language, and whether debug logging is on.
 *
 * <p>Rendering is a pure function of {@link Details} returning lines, rather than something that
 * logs directly. That is what makes the alignment testable — a box whose borders do not line up
 * looks broken, and it is very easy to break by adding one field.
 *
 * @since 1.0.0
 */
public final class StartupBanner {

    /** The character used to draw the wordmark when the console can render it. */
    private static final String BLOCK = "█";

    private static final List<String> WORDMARK_UNICODE = List.of(
            "██████╗ ███╗   ██╗██████╗  ██████╗",
            "██╔══██╗████╗  ██║██╔══██╗██╔════╝",
            "██████╔╝██╔██╗ ██║██████╔╝██║     ",
            "██╔══██╗██║╚██╗██║██╔═══╝ ██║     ",
            "██████╔╝██║ ╚████║██║     ╚██████╗",
            "╚═════╝ ╚═╝  ╚═══╝╚═╝      ╚═════╝");

    private static final List<String> WORDMARK_ASCII = List.of(
            "  ___   _  _  ___   ___ ",
            " | _ ) | \\| || _ \\ / __|",
            " | _ \\ | .` ||  _/| (__ ",
            " |___/ |_|\\_||_|   \\___|");

    private static final char[] BORDER_UNICODE = {'╔', '═', '╗', '║', '╚', '╝'};
    private static final char[] BORDER_ASCII = {'+', '-', '+', '|', '+', '+'};

    private StartupBanner() {
        throw new AssertionError("No instances");
    }

    /**
     * What the banner reports.
     *
     * @param pluginName       the plugin's name, so that a rebranded fork still identifies itself
     * @param pluginVersion    the plugin version
     * @param apiVersion       the API version dependent plugins compile against
     * @param minecraftVersion the detected Minecraft version
     * @param serverSoftware   the server implementation and its build
     * @param javaVersion      the running Java version
     * @param storage          the configured storage backend, with no credentials
     * @param language         the configured default language
     * @param adapter          a description of the loaded version adapter
     * @param debug            whether debug logging is on
     * @since 1.0.0
     */
    public record Details(
            String pluginName,
            String pluginVersion,
            String apiVersion,
            String minecraftVersion,
            String serverSoftware,
            String javaVersion,
            String storage,
            String language,
            String adapter,
            boolean debug) {

        /**
         * Creates the details.
         *
         * @param pluginName       the plugin's name
         * @param pluginVersion    the plugin version
         * @param apiVersion       the API version
         * @param minecraftVersion the detected Minecraft version
         * @param serverSoftware   the server implementation
         * @param javaVersion      the running Java version
         * @param storage          the configured storage backend
         * @param language         the configured default language
         * @param adapter          the loaded version adapter
         * @param debug            whether debug logging is on
         * @throws NullPointerException if any argument is {@code null}
         */
        public Details {
            Objects.requireNonNull(pluginName, "pluginName");
            Objects.requireNonNull(pluginVersion, "pluginVersion");
            Objects.requireNonNull(apiVersion, "apiVersion");
            Objects.requireNonNull(minecraftVersion, "minecraftVersion");
            Objects.requireNonNull(serverSoftware, "serverSoftware");
            Objects.requireNonNull(javaVersion, "javaVersion");
            Objects.requireNonNull(storage, "storage");
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(adapter, "adapter");
        }
    }

    /**
     * Builds the banner, choosing a drawing style the console can actually render.
     *
     * @param details what to report
     * @return the lines to log, without trailing newlines
     * @throws NullPointerException if {@code details} is {@code null}
     */
    public static List<String> render(Details details) {
        return render(details, consoleSupportsBoxDrawing());
    }

    /**
     * Builds the banner in a chosen drawing style.
     *
     * @param details what to report
     * @param unicode {@code true} to draw with box-drawing and block characters, {@code false} to
     *                use plain ASCII
     * @return the lines to log, without trailing newlines
     * @throws NullPointerException if {@code details} is {@code null}
     */
    public static List<String> render(Details details, boolean unicode) {
        Objects.requireNonNull(details, "details");

        List<String> wordmark = unicode ? WORDMARK_UNICODE : WORDMARK_ASCII;
        char[] border = unicode ? BORDER_UNICODE : BORDER_ASCII;

        Map<String, String> fields = fields(details);
        int labelWidth = fields.keySet().stream().mapToInt(String::length).max().orElse(0);

        List<String> body = new ArrayList<>();
        body.addAll(wordmark);
        body.add("");
        body.add(details.pluginName() + " " + details.pluginVersion());
        body.add("");
        fields.forEach((label, value) -> body.add(pad(label + ":", labelWidth + 1) + "  " + value));

        // The box is sized from its contents rather than from a constant, so adding a field can
        // never leave the borders misaligned.
        int contentWidth = body.stream().mapToInt(String::length).max().orElse(0);
        int innerWidth = contentWidth + 4;

        List<String> lines = new ArrayList<>(body.size() + 4);
        lines.add(border[0] + repeat(border[1], innerWidth) + border[2]);
        lines.add(framed("", innerWidth, border[3]));
        for (String line : body) {
            lines.add(framed(line, innerWidth, border[3]));
        }
        lines.add(framed("", innerWidth, border[3]));
        lines.add(border[4] + repeat(border[1], innerWidth) + border[5]);
        return List.copyOf(lines);
    }

    private static Map<String, String> fields(Details details) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("API", details.apiVersion());
        fields.put("Minecraft", details.minecraftVersion());
        fields.put("Server", details.serverSoftware());
        fields.put("Adapter", details.adapter());
        fields.put("Java", details.javaVersion());
        fields.put("Storage", details.storage());
        fields.put("Language", details.language());
        fields.put("Debug", details.debug() ? "on" : "off");
        return fields;
    }

    private static String framed(String content, int innerWidth, char vertical) {
        return vertical + "  " + pad(content, innerWidth - 4) + "  " + vertical;
    }

    private static String pad(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        return value + " ".repeat(width - value.length());
    }

    private static String repeat(char character, int count) {
        return String.valueOf(character).repeat(count);
    }

    /**
     * Returns whether the console's encoding can represent the box-drawing characters.
     *
     * <p>Worth checking rather than assuming. A Windows console still commonly runs in a legacy code
     * page, and a banner that renders as a wall of question marks is a worse first impression than a
     * plain ASCII one. Java exposes the console encoding as {@code stdout.encoding}; where it does
     * not, the platform's native encoding is the next best guess.
     *
     * @return {@code true} if the block and box-drawing characters are safe to print
     */
    public static boolean consoleSupportsBoxDrawing() {
        for (String property : List.of("stdout.encoding", "native.encoding", "file.encoding")) {
            String name = System.getProperty(property);
            if (name == null || name.isBlank()) {
                continue;
            }
            try {
                return Charset.forName(name).newEncoder().canEncode(BLOCK);
            } catch (IllegalCharsetNameException | UnsupportedCharsetException | UnsupportedOperationException
                    unknownEncoding) {
                // An encoding Java cannot name is one we should not gamble a wall of question marks
                // on. Fall through to the next property, and to ASCII if none of them answer.
            }
        }
        return false;
    }
}
