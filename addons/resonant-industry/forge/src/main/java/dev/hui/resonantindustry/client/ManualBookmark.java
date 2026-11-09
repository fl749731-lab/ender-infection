package dev.hui.resonantindustry.client;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Local reading preferences; independent of worlds, players and server data. */
record ManualBookmark(String chapter, int layer, boolean instructions, int scroll, float yaw, float pitch, float zoom) {
    static final ManualBookmark DEFAULT = new ManualBookmark("intro", -1, false, 0, 145, 25, 1);
    private static final System.Logger LOGGER = System.getLogger("resonant_industry.manual");
    private static final int MAX_BYTES = 4096;

    ManualBookmark {
        if (chapter == null || !chapter.matches("[a-z0-9_]{1,64}")) chapter = "intro";
        layer = Math.max(-1, Math.min(255, layer));
        scroll = Math.max(0, Math.min(100_000, scroll));
        yaw = Float.isFinite(yaw) ? (yaw % 360 + 360) % 360 : 145;
        pitch = Float.isFinite(pitch) ? Math.max(5, Math.min(80, pitch)) : 25;
        zoom = Float.isFinite(zoom) ? Math.max(.6f, Math.min(1.5f, zoom)) : 1;
    }

    static Path file(Path gameDirectory) {
        return gameDirectory.resolve("config/resonant-industry-manual.properties");
    }

    static ManualBookmark load(Path gameDirectory) {
        Path path = file(gameDirectory);
        try {
            if (!Files.exists(path)) return DEFAULT;
            byte[] bytes;
            try (var stream = Files.newInputStream(path)) { bytes = stream.readNBytes(MAX_BYTES + 1); }
            if (bytes.length > MAX_BYTES) throw new IOException("Manual bookmark exceeds " + MAX_BYTES + " bytes");
            var values = new Properties();
            values.load(new StringReader(new String(bytes, StandardCharsets.UTF_8)));
            if (!"1".equals(values.getProperty("version"))) return DEFAULT;
            return new ManualBookmark(values.getProperty("chapter", "intro"), integer(values, "layer", -1),
                    Boolean.parseBoolean(values.getProperty("instructions", "false")), integer(values, "scroll", 0),
                    decimal(values, "yaw", 145), decimal(values, "pitch", 25), decimal(values, "zoom", 1));
        } catch (IOException | IllegalArgumentException | SecurityException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not read the industry manual bookmark; using the first page", error);
            return DEFAULT;
        }
    }

    boolean save(Path gameDirectory) {
        Path path = file(gameDirectory);
        Path temporary = null;
        try {
            Files.createDirectories(path.getParent());
            temporary = Files.createTempFile(path.getParent(), "resonant-industry-manual-", ".tmp");
            var values = new Properties();
            values.setProperty("version", "1");
            values.setProperty("chapter", chapter);
            values.setProperty("layer", Integer.toString(layer));
            values.setProperty("instructions", Boolean.toString(instructions));
            values.setProperty("scroll", Integer.toString(scroll));
            values.setProperty("yaw", Float.toString(yaw));
            values.setProperty("pitch", Float.toString(pitch));
            values.setProperty("zoom", Float.toString(zoom));
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                values.store(writer, "Resonant Industry manual reading position");
            }
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | SecurityException error) {
            LOGGER.log(System.Logger.Level.WARNING, "Could not save the industry manual bookmark; keeping the current session position", error);
            return false;
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException | SecurityException ignored) { }
        }
    }

    private static int integer(Properties values, String key, int fallback) {
        try { return Integer.parseInt(values.getProperty(key, Integer.toString(fallback))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static float decimal(Properties values, String key, float fallback) {
        try { return Float.parseFloat(values.getProperty(key, Float.toString(fallback))); }
        catch (NumberFormatException ignored) { return fallback; }
    }
}
