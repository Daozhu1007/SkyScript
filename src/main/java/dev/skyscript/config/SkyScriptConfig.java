package dev.skyscript.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.skyscript.script.PosCond;
import dev.skyscript.script.Script;
import dev.skyscript.script.Step;
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

    /** 清洗加载的方案：把旧版损坏的中文"忽略"op 归一到 "ignore"，并清掉 ignore 条件（避免摘要显示"未设"/运行时误判） */
    private static void sanitizeScript(Script s) {
        if (s == null || s.steps == null) return;
        for (Step st : s.steps) {
            sanitizeStep(st);
        }
    }

    private static void sanitizeStep(Step st) {
        if (st == null) return;
        if (st.cond != null) {
            st.cond.removeIf(pc -> pc == null || "忽略".equals(pc.op) || "ignore".equals(pc.op));
            for (PosCond pc : st.cond) {
                if (pc != null && "忽略".equals(pc.op)) pc.op = "ignore";
            }
        }
        if ("loop".equals(st.type) && st.body != null) {
            for (Step b : st.body) sanitizeStep(b);
        }
    }

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
                                if (s != null && s.name != null) {
                                    sanitizeScript(s);
                                    list.add(s);
                                }
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

    /** 改名：保存到新文件，并删除旧文件（改名前后名字相同则只保存） */
    public static void renameScript(Script script, String oldName) {
        saveScript(script);
        if (oldName != null && !oldName.equals(script.name)) {
            deleteScript(oldName);
        }
    }

    /**
     * 复制方案：按「原名 Copy / Copy 2 / Copy 3 …」取唯一名，深拷贝后立即落盘。
     * 逻辑名和清洗后的文件名都查重，绝不覆盖已有方案；不改动活动方案。
     */
    public static Script duplicateScript(Script source) {
        if (source == null) return null;
        String base = source.name == null ? "unnamed" : source.name;
        List<Script> existing = listScripts();
        String name = base + " Copy";
        for (int i = 2; ; i++) {
            if (!nameTaken(name, existing)) break;
            name = base + " Copy " + i;
        }
        Script dup = source.copy();
        dup.name = name;
        saveScript(dup);
        return dup;
    }

    /** 名字是否已占用：既查现有方案的逻辑名，也查清洗后的文件名（不同名字可能映射到同一文件） */
    private static boolean nameTaken(String name, List<Script> existing) {
        for (Script s : existing) {
            if (s.name.equals(name)) return true;
        }
        return Files.exists(scriptsDir().resolve(sanitize(name) + ".json"));
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
