package studio.dreamys.prometheus.util;

import com.lunarclient.common.v1.Uuid;
import com.lunarclient.websocket.cosmetic.v2.Outfit;
import com.lunarclient.websocket.cosmetic.v2.OutfitTree;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileUtils {
    public static final Logger logger = Logger.getLogger("Prometheus");

    //lunar has no badge equipped when the id is -1
    private static final int NO_BADGE = -1;

    //old saves were relative to the working directory, which changes with how lunar is launched
    private static final Path legacyPath = Paths.get("prometheus", "saved").toAbsolutePath();

    private static final Path basePath = OS.getConfigFolder().resolve("lunar");
    private static final Path badgePath = basePath.resolve("badge.bin");
    private static final Path legacyOutfitPath = basePath.resolve("outfit.bin");
    private static final Path outfitsPath = basePath.resolve("outfits.bin");
    private static final Path outfitTreePath = basePath.resolve("outfit_tree.bin");
    private static final Path emotesPath = basePath.resolve("emotes.bin");
    private static final Path spraysPath = basePath.resolve("sprays.bin");

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

        migrateLegacyFiles();
    }

    private static void migrateLegacyFiles() {
        if (!Files.isDirectory(legacyPath) || legacyPath.equals(basePath)) {
            return;
        }

        //copy over anything we don't have yet, the legacy folder is left untouched
        for (String name : new String[]{"badge.bin", "outfit.bin", "emotes.bin", "sprays.bin"}) {
            Path source = legacyPath.resolve(name);
            Path target = basePath.resolve(name);

            if (Files.exists(source) && !Files.exists(target)) {
                try {
                    Files.copy(source, target);
                    logger.log(Level.INFO, "Migrated " + source + " to " + target);
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Failed to migrate " + source, e);
                }
            }
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
        int badgeId = NO_BADGE;

        try {
            byte[] bytes = Files.readAllBytes(badgePath);

            if (bytes.length == 1) {
                //legacy files hold a single unsigned byte, where -1 was stored as 0xFF
                int legacyId = bytes[0] & 0xFF;
                badgeId = legacyId == 0xFF ? NO_BADGE : legacyId;
            } else {
                badgeId = ByteBuffer.wrap(bytes).getInt();
            }
        } catch (Throwable e) {
            logger.log(Level.WARNING, "Failed to deserialize badge from file.", e);
        }

        return badgeId;
    }

    public static Uuid randomUuid() {
        UUID uuid = UUID.randomUUID();
        return Uuid.newBuilder()
                .setHigh64(uuid.getMostSignificantBits())
                .setLow64(uuid.getLeastSignificantBits())
                .build();
    }

    public static void writeOutfits(List<Outfit> outfits) {
        try (OutputStream os = Files.newOutputStream(outfitsPath)) {
            for (Outfit outfit : outfits) {
                outfit.writeDelimitedTo(os);
            }
        } catch (Throwable e) {
            logger.log(Level.SEVERE, "Failed to serialize outfit list to file.", e);
        }
    }

    public static List<Outfit> readOutfits() {
        List<Outfit> outfits = new ArrayList<>();

        if (Files.exists(outfitsPath)) {
            try (InputStream is = Files.newInputStream(outfitsPath)) {
                while (is.available() > 0) {
                    Outfit outfit = Outfit.parseDelimitedFrom(is);
                    if (outfit != null) {
                        outfits.add(outfit);
                    }
                }
            } catch (Throwable e) {
                logger.log(Level.WARNING, "Failed to deserialize outfit list from file.", e);
            }
        } else if (Files.exists(legacyOutfitPath)) {
            //legacy saves only hold a single outfit
            try (InputStream is = Files.newInputStream(legacyOutfitPath)) {
                outfits.add(Outfit.newBuilder()
                        .setName("Prometheus")
                        .setFavorite(true)
                        .mergeFrom(Outfit.parseFrom(is))
                        .build());
            } catch (Throwable e) {
                logger.log(Level.WARNING, "Failed to deserialize legacy outfit from file.", e);
            }
        }

        //lunar keys outfits by id, so every outfit needs a stable one
        boolean changed = false;
        for (int i = 0; i < outfits.size(); i++) {
            if (!outfits.get(i).hasId()) {
                outfits.set(i, outfits.get(i).toBuilder().setId(randomUuid()).build());
                changed = true;
            }
        }

        //create default outfit
        if (outfits.isEmpty()) {
            outfits.add(Outfit.newBuilder()
                    .setId(randomUuid())
                    .setName("Prometheus")
                    .setFavorite(true)
                    .build());
            changed = true;
        }

        if (changed) {
            writeOutfits(outfits);
        }

        return outfits;
    }

    public static void writeOutfitTree(OutfitTree outfitTree) {
        try (OutputStream os = Files.newOutputStream(outfitTreePath)) {
            outfitTree.writeTo(os);
        } catch (Throwable e) {
            logger.log(Level.SEVERE, "Failed to serialize outfit tree to file.", e);
        }
    }

    public static OutfitTree readOutfitTree(List<Outfit> outfits) {
        OutfitTree outfitTree = OutfitTree.getDefaultInstance();

        if (Files.exists(outfitTreePath)) {
            try (InputStream is = Files.newInputStream(outfitTreePath)) {
                outfitTree = OutfitTree.parseFrom(is);
            } catch (Throwable e) {
                logger.log(Level.WARNING, "Failed to deserialize outfit tree from file.", e);
            }
        }

        return fixOutfitTree(outfitTree, outfits);
    }

    //drop references to outfits that no longer exist, and fall back to the first outfit as default
    public static OutfitTree fixOutfitTree(OutfitTree outfitTree, List<Outfit> outfits) {
        Set<Uuid> ids = new HashSet<>();
        for (Outfit outfit : outfits) {
            ids.add(outfit.getId());
        }

        OutfitTree.Builder builder = OutfitTree.newBuilder();
        for (OutfitTree.ConditionalOutfit conditionalOutfit : outfitTree.getConditionalOutfitsList()) {
            if (ids.contains(conditionalOutfit.getOutfitId())) {
                builder.addConditionalOutfits(conditionalOutfit);
            }
        }

        if (outfitTree.hasDefaultOutfitId() && ids.contains(outfitTree.getDefaultOutfitId())) {
            builder.setDefaultOutfitId(outfitTree.getDefaultOutfitId());
        } else if (!outfits.isEmpty()) {
            builder.setDefaultOutfitId(outfits.get(0).getId());
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
