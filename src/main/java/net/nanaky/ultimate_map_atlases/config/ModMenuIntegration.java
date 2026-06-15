package net.nanaky.ultimate_map_atlases.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import net.nanaky.ultimate_map_atlases.config.UltimateMapAtlasesConfigScreen.MapAtlasesConfigScreen;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return MapAtlasesConfigScreen::new;
    }
}