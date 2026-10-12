package studio.dreamys.prometheus.util;

import java.nio.file.Path;
import java.nio.file.Paths;

public class PathUtil {
    public static Path fromHomeFolder(String... paths) {
        return Paths.get(System.getProperty("user.home"), paths);
    }

    //returns the fallback if the env variable is unset or empty
    public static Path fromEnvOr(String env, Path fallback) {
        String path = System.getenv(env);
        if (path == null || path.isEmpty()) return fallback;
        return Paths.get(path);
    }
}
