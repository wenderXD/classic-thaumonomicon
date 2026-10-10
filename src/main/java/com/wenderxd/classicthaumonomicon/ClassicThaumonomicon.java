package com.wenderxd.classicthaumonomicon;

import com.leclowndu93150.thaumaturge.client.screen.research.ThaumonomiconBrowserScreen;
import com.wenderxd.classicthaumonomicon.client.ClassicThaumonomiconScreen;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Swaps Thaumaturge's research map for the Thaumcraft 4 style one.
 *
 * <p>Thaumaturge opens its book from the item, the keybind and a server packet, and all three go
 * through {@link ThaumonomiconBrowserScreen#reopen()}. Instead of patching each of them, this
 * listens for a screen that is about to open and replaces it when it is Thaumaturge's research
 * map. That also covers coming back from an entry's pages: {@code reopen()} can open straight onto
 * the last-read entry with Thaumaturge's map behind it, and the map is swapped when that entry is
 * closed.
 *
 * <p>The pages, recipes and stages past the map are still Thaumaturge's own screen.
 */
@Mod(value = ClassicThaumonomicon.MOD_ID, dist = Dist.CLIENT)
public final class ClassicThaumonomicon {
    public static final String MOD_ID = "classic_thaumonomicon";

    public ClassicThaumonomicon(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClassicConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        NeoForge.EVENT_BUS.addListener(ClassicThaumonomicon::onScreenOpening);
        if (Boolean.getBoolean(MOD_ID + ".visualCheck")) {
            com.wenderxd.classicthaumonomicon.client.BookVisualCheck.register();
        }
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof ThaumonomiconBrowserScreen && ClassicConfig.ENABLED.get()) {
            event.setNewScreen(new ClassicThaumonomiconScreen());
        }
    }
}
