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
 * Gives Thaumaturge's Thaumonomicon its Thaumcraft 4 look back.
 *
 * <h2>How it takes over</h2>
 *
 * <p>Thaumaturge opens its book from three places - the item, the keybind and a server packet - and
 * every one of them goes through {@link ThaumonomiconBrowserScreen#reopen()}. Rather than patch any
 * of them, this listens for the moment a screen is about to open and, when it is Thaumaturge's
 * research map, hands Minecraft the classic one instead. The same hook catches the way back from an
 * entry's pages: {@code reopen()} can open straight onto the last-read entry with Thaumaturge's map
 * behind it, and when that entry is closed the map it returns to is swapped here too.
 *
 * <p>Everything past the map - the pages, recipes and stages - is still Thaumaturge's own screen.
 * Its open book is already the classic two-page spread on parchment, so it is left alone.
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
