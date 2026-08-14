package dev.skyscript.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.skyscript.script.Script;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 配置读写：config/sky_script/settings.json + scripts/*.json
 */
public final class SkyScriptConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Settings settings = new Settings();

    private SkyScriptConfig() {
    }

    private static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("sky_script");
    }

    private static Path settingsPath() {
        return dir().resolve("settings.json");
    }

    private static Path scriptsDir() {
        return dir().resolve("scripts");
    }

    public static Settings get() {
        return settings;
    }

    public static void load() {
        try {
            if (Files.exists(settingsPath())) {
                settings = GSON.fromJson(Files.readString(settingsPath()), Settings.class);
            }
        } catch (Exception ignored) {
        }
        if (settings == null) settings = new Settings();
        settings.applyDefaults();
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(dir());
            Files.writeString(settingsPath(), GSON.toJson(settings));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // ---------- 方案 ----------

    public static List<Script> listScripts() {
        List<Script> list = new ArrayList<>();
        try {
            Path d = scriptsDir();
            if (!Files.isDirectory(d)) return list;
            try (var stream = Files.list(d)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".json"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .forEach(p -> {
                            try {
                                Script s = GSON.fromJson(Files.readString(p), Script.class);
                                if (s != null && s.name != null) list.add(s);
                            } catch (Exception ignored) {
                            }
                        });
            }
        } catch (IOException ignored) {
        }
        return list;
    }

    public static Script loadScript(String name) {
        if (name == null || name.isEmpty()) return null;
        for (Script s : listScripts()) {
            if (s.name.equals(name)) return s;
        }
        return null;
    }

    public static void saveScript(Script script) {
        try {
            Files.createDirectories(scriptsDir());
            Files.writeString(scriptsDir().resolve(sanitize(script.name) + ".json"), GSON.toJson(script));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void deleteScript(String name) {
        try {
            Files.deleteIfExists(scriptsDir().resolve(sanitize(name) + ".json"));
        } catch (IOException ignored) {
        }
    }

    /** 活动方案：只有用户在编辑器里显式设为「活动」才返回；未设置返回 null（不再自动选第一个）。 */
    public static Script getActiveScript() {
        if (settings.activeScript == null || settings.activeScript.isEmpty()) return null;
        return loadScript(settings.activeScript);
    }

    private static String sanitize(String name) {
        return name == null ? "unnamed" : name.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }
}
