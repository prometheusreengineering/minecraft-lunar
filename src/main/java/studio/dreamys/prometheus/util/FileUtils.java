package studio.dreamys.prometheus.util;

import com.lunarclient.websocket.cosmetic.v2.Outfit;
import com.lunarclient.websocket.emote.v1.EquippedEmote;
import com.lunarclient.websocket.spray.v1.EquippedSpray;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileUtils {
    public static final Logger logger = Logger.getLogger("Prometheus");

    private static Path basePath = Paths.get("prometheus", "saved");
    private static Path badgePath = basePath.resolve("badge.bin");
    private static Path outfitPath = basePath.resolve("outfit.bin");
    private static Path emotesPath = basePath.resolve("emotes.bin");
    private static Path spraysPath = basePath.resolve("sprays.bin");

    static {
        //overwrite lunar's logger settings
        Handler handlerObj = new ConsoleHandler();
        handlerObj.setLevel(Level.ALL);
        logger.addHandler(handlerObj);
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);

        try {
            //create directories
            Files.createDirectories(basePath);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to create base directory: " + basePath, e);
        }
    }

    public static void writeBadge(int badgeId) {
        try (DataOutputStream os = new DataOutputStream(Files.newOutputStream(badgePath))) {
            os.writeInt(badgeId);
        } catch (Throwable e) {
            logger.log(Level.SEVERE, "Failed to serialize badge to file.", e);
        }
    }

    public static int readBadge() {
        int badgeId = 0;

        try {
            byte[] bytes = Files.readAllBytes(badgePath);

            //legacy files hold a single unsigned byte
            badgeId = bytes.length == 1 ? bytes[0] & 0xFF : ByteBuffer.wrap(bytes).getInt();
        } catch (Throwable e) {
            logger.log(Level.WARNING, "Failed to deserialize badge from file.", e);
        }

        return badgeId;
    }

    public static void writeOutfit(Outfit outfit) {
        try (OutputStream os = Files.newOutputStream(outfitPath)) {
            outfit.writeTo(os);
        } catch (Throwable e) {
            logger.log(Level.SEVERE, "Failed to serialize outfit to file.", e);
        }
    }

    public static Outfit readOutfit() {
        //create default outfit
        Outfit.Builder builder = Outfit.newBuilder()
                .setName("Prometheus")
                .setFavorite(true);

        try (InputStream is = Files.newInputStream(outfitPath)) {
            Outfit outfit = Outfit.parseFrom(is);
            builder.mergeFrom(outfit);
        } catch (Throwable e) {
            logger.log(Level.WARNING, "Failed to deserialize outfit from file.", e);
        }

        return builder.build();
    }

    public static void writeEquippedEmotes(List<EquippedEmote> equippedEmotes) {
        try (OutputStream os = Files.newOutputStream(emotesPath)) {
            for (EquippedEmote emote : equippedEmotes) {
                emote.writeDelimitedTo(os);
            }
        } catch (Throwable e) {
            logger.log(Level.SEVERE, "Failed to serialize equipped emote list to file.", e);
        }
    }

    public static List<EquippedEmote> readEquippedEmotes() {
        List<EquippedEmote> equippedEmotes = new ArrayList<>();

        try (InputStream is = Files.newInputStream(emotesPath)) {
            while (is.available() > 0) {
                EquippedEmote emote = EquippedEmote.parseDelimitedFrom(is);
                if (emote != null) {
                    equippedEmotes.add(emote);
                }
            }
        } catch (Throwable e) {
            logger.log(Level.WARNING, "Failed to deserialize equipped emote list from file.", e);
        }

        return equippedEmotes;
    }

    public static void writeEquippedSprays(List<EquippedSpray> equippedSprays) {
        try (OutputStream os = Files.newOutputStream(spraysPath)) {
            for (EquippedSpray spray : equippedSprays) {
                spray.writeDelimitedTo(os);
            }
        } catch (Throwable e) {
            logger.log(Level.SEVERE, "Failed to serialize equipped spray list to file.", e);
        }
    }

    public static List<EquippedSpray> readEquippedSprays() {
        List<EquippedSpray> equippedSprays = new ArrayList<>();

        try (InputStream is = Files.newInputStream(spraysPath)) {
            while (is.available() > 0) {
                EquippedSpray spray = EquippedSpray.parseDelimitedFrom(is);
                if (spray != null) {
                    equippedSprays.add(spray);
                }
            }
        } catch (Throwable e) {
            logger.log(Level.WARNING, "Failed to deserialize equipped spray list from file.", e);
        }

        return equippedSprays;
    }
}
