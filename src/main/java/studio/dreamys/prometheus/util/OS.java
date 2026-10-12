package studio.dreamys.prometheus.util;

import java.nio.file.Path;
import java.util.Locale;

public enum OS {
    Windows(PathUtil.fromEnvOr("APPDATA", PathUtil.fromHomeFolder("AppData", "Roaming")).resolve(".minecraft")),
    MacOS(PathUtil.fromHomeFolder("Library", "Application Support")),
    Linux(PathUtil.fromEnvOr("XDG_DATA_HOME", PathUtil.fromHomeFolder(".local", "share")));

    OS(Path configFolder) {
        this.configFolder = configFolder;
    }

    private final Path configFolder;
    private static final OS current;

    static {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            current = Windows;
        } else if (osName.contains("darwin") || osName.contains("mac")) {
            current = MacOS;
        } else {
            current = Linux;
        }
    }

    //absolute so it doesn't depend on the working directory lunar was launched from
    public static Path getConfigFolder() {
        return PathUtil.fromEnvOr("PROMETHEUS_FOLDER", current.configFolder.resolve("prometheus")).toAbsolutePath();
    }
}
