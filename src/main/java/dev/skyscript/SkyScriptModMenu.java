package dev.skyscript;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.skyscript.screen.SkyScriptScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Mod Menu 集成：mod 列表里点 SkyScript 的「设置」→ 打开整合控制台。
 * 仅在安装了 Mod Menu 时生效（compileOnly 依赖，不影响没有 Mod Menu 的环境）。
 */
@Environment(EnvType.CLIENT)
public class SkyScriptModMenu implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new SkyScriptScreen();
    }
}
