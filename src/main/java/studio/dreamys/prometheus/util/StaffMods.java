package studio.dreamys.prometheus.util;

import com.lunarclient.websocket.cosmetic.v2.CosmeticService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;

import static studio.dreamys.prometheus.util.FileUtils.logger;

/**
 * Lunar only makes staff mods (ex: Xray) available when a server sends Apollo's EnableStaffModsMessage, and takes them
 * away again on disconnect. The flag lives on an obfuscated class whose names change every build, so we find the class
 * by its structure at runtime and keep the flag set.
 */
public class StaffMods {
    //staff mod classes are tiny, this skips reading most of the jar
    private static final int MAX_CLASS_SIZE = 4096;
    private static final byte[] XRAY = "Xray".getBytes(StandardCharsets.UTF_8);
    private static final byte[] GET_ORDINAL = "getOrdinal".getBytes(StandardCharsets.UTF_8);

    private static boolean started;

    public static synchronized void unlock() {
        if (started) {
            return;
        }
        started = true;

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Prometheus - Staff Mods");
            thread.setDaemon(true);
            return thread;
        });

        executor.execute(() -> {
            List<StaffMod> staffMods = find();
            if (staffMods.isEmpty()) {
                logger.log(Level.WARNING, "Could not find Lunar's staff mods, they will stay locked.");
                executor.shutdown();
                return;
            }

            logger.log(Level.INFO, "Unlocking " + staffMods.size() + " staff mod(s).");

            //lunar locks them again on every disconnect, so keep re-applying
            executor.scheduleWithFixedDelay(() -> {
                for (StaffMod staffMod : staffMods) {
                    staffMod.makeAvailable();
                }
            }, 0, 1, TimeUnit.SECONDS);
        });
    }

    private static List<StaffMod> find() {
        List<StaffMod> staffMods = new ArrayList<>();
        ClassLoader classLoader = CosmeticService.class.getClassLoader();

        try (JarFile jar = openLunarJar(classLoader)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith("com/moonsworth/lunar/") || !name.endsWith(".class") || entry.getSize() > MAX_CLASS_SIZE) {
                    continue;
                }

                byte[] bytes;
                try (InputStream is = jar.getInputStream(entry)) {
                    bytes = readAll(is);
                }
                if (!contains(bytes, XRAY) || !contains(bytes, GET_ORDINAL)) {
                    continue;
                }

                String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
                staffMods.addAll(fromClass(Class.forName(className, false, classLoader)));
            }
        } catch (Throwable e) {
            logger.log(Level.SEVERE, "Failed to search for Lunar's staff mods.", e);
        }

        return staffMods;
    }

    /**
     * The staff mod class looks like: {@code public static final StaffMod XRAY = new StaffMod("Xray", 1);} with
     * {@code getName()}, {@code getOrdinal()} and an obfuscated boolean getter/setter for availability.
     */
    private static List<StaffMod> fromClass(Class<?> clazz) throws ReflectiveOperationException {
        List<StaffMod> staffMods = new ArrayList<>();

        try {
            if (clazz.getDeclaredMethod("getName").getReturnType() != String.class
                    || clazz.getDeclaredMethod("getOrdinal").getReturnType() != int.class) {
                return staffMods;
            }
        } catch (NoSuchMethodException e) {
            return staffMods;
        }

        Method getter = null;
        Method setter = null;
        for (Method method : clazz.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }

            Class<?>[] parameters = method.getParameterTypes();
            if (method.getReturnType() == boolean.class && parameters.length == 0) {
                if (getter != null) return staffMods;
                getter = method;
            } else if (method.getReturnType() == void.class && parameters.length == 1 && parameters[0] == boolean.class) {
                if (setter != null) return staffMods;
                setter = method;
            }
        }
        if (getter == null || setter == null) {
            return staffMods;
        }
        getter.setAccessible(true);
        setter.setAccessible(true);

        for (Field field : clazz.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (field.getType() == clazz && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
                field.setAccessible(true);
                staffMods.add(new StaffMod(field.get(null), getter, setter));
            }
        }

        return staffMods;
    }

    private static JarFile openLunarJar(ClassLoader classLoader) throws IOException {
        URL url = classLoader.getResource(CosmeticService.class.getName().replace('.', '/') + ".class");
        if (url == null) {
            throw new IOException("Could not locate Lunar's classes.");
        }

        URLConnection connection = url.openConnection();
        if (!(connection instanceof JarURLConnection)) {
            throw new IOException("Lunar's classes are not in a jar: " + url);
        }

        //our own copy, the cached one is shared with the class loader and must not be closed
        connection.setUseCaches(false);
        return ((JarURLConnection) connection).getJarFile();
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = is.read(buffer)) != -1) {
            os.write(buffer, 0, read);
        }
        return os.toByteArray();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    private static class StaffMod {
        private final Object instance;
        private final Method getter;
        private final Method setter;
        private boolean failed;

        StaffMod(Object instance, Method getter, Method setter) {
            this.instance = instance;
            this.getter = getter;
            this.setter = setter;
        }

        void makeAvailable() {
            if (failed) {
                return;
            }

            try {
                if (!(boolean) getter.invoke(instance)) {
                    setter.invoke(instance, true);
                }
            } catch (Throwable e) {
                //this runs every second, so only report it once
                failed = true;
                logger.log(Level.WARNING, "Failed to unlock staff mod.", e);
            }
        }
    }
}
