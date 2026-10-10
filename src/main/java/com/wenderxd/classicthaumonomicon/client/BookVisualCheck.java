package com.wenderxd.classicthaumonomicon.client;

import com.leclowndu93150.thaumaturge.client.screen.research.EntryDetailScreen;
import com.leclowndu93150.thaumaturge.client.screen.research.ThaumonomiconBrowserScreen;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * Dev-only screenshot run for the book. {@code gradlew runBookVisualCheck} writes the pictures to
 * {@code runs/book-check/screenshots}.
 *
 * <p>It makes a new creative world, opens the book the way Thaumaturge does (so the screen swap is
 * tested too), grants research in steps with Thaumaturge's own commands and screenshots each
 * state: a new player's book, every category with everything learned, a hover box, the search and
 * an entry's pages. Then it quits. The jar leaves this class out.
 */
public final class BookVisualCheck {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String WORLD = "Book Check";

    private static final List<Step> SCRIPT = new ArrayList<>();
    private static boolean worldRequested;
    private static int step;
    private static int wait;

    private BookVisualCheck() {}

    public static void register() {
        deleteOldWorld();
        buildScript();
        NeoForge.EVENT_BUS.addListener(BookVisualCheck::onTick);
    }

    private static void buildScript() {
        // Wait for the world to load, the research registries to sync and the starting research to arrive.
        pause(200);
        run(mc -> mc.setScreen(ThaumonomiconBrowserScreen.reopen()));
        pause(30);
        run(mc -> check(mc.screen instanceof ClassicThaumonomiconScreen, "the book opened as the classic map"));
        shot("01_new_player");
        hoverEntry(ClassicThaumonomiconScreen.Probe.LOCKED);
        shot("02_locked_hover");
        hoverEntry(ClassicThaumonomiconScreen.Probe.AVAILABLE);
        shot("03_available_hover");

        run(mc -> mc.setScreen(null));
        command("thaumaturge research @s category thaumaturge:basics");
        pause(40);
        run(mc -> mc.setScreen(ThaumonomiconBrowserScreen.reopen()));
        pause(30);
        shot("04_basics_complete");

        run(mc -> mc.setScreen(null));
        command("thaumaturge research @s everything");
        pause(60);
        run(mc -> mc.setScreen(ThaumonomiconBrowserScreen.reopen()));
        pause(30);
        for (int tab = 0; tab < 7; tab++) {
            final int index = tab;
            run(mc -> clickTab(mc, index));
            pause(25);
            shot("05_category_" + index);
        }
        run(mc -> clickTab(mc, 0));
        pause(10);
        hoverEntry(ClassicThaumonomiconScreen.Probe.WARP);
        shot("06_warp_hover");
        run(mc -> moveMouse(mc, (mc.screen.width - 256) / 2.0 - 12, (mc.screen.height - 230) / 2.0 + 24 + 12));
        pause(5);
        shot("07_tab_hover");

        run(BookVisualCheck::clickSearchTab);
        pause(5);
        run(mc -> {
            for (char c : "wand".toCharArray()) {
                mc.screen.charTyped(new CharacterEvent(c));
            }
        });
        pause(10);
        shot("08_search");
        run(BookVisualCheck::clickSearchTab);
        pause(5);

        run(mc -> {
            if (mc.screen instanceof ClassicThaumonomiconScreen book) {
                int[] at = book.focus(ClassicThaumonomiconScreen.Probe.COMPLETE);
                if (at != null) {
                    clickAt(mc, at[0], at[1]);
                }
            }
        });
        pause(20);
        run(mc -> check(mc.screen instanceof EntryDetailScreen, "clicking an entry opened its pages"));
        shot("09_entry");
        run(mc -> mc.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_BACKSPACE, 0, 0)));
        pause(10);
        run(mc -> check(mc.screen instanceof ClassicThaumonomiconScreen, "going back from an entry lands on the classic map"));

        run(mc -> clickAt(mc, mc.screen.width / 2.0, mc.screen.height / 2.0));
        pause(10);
        run(mc -> mc.setScreen(null));
        pause(5);
        run(mc -> mc.setScreen(ThaumonomiconBrowserScreen.reopen()));
        pause(10);
        run(mc -> check(mc.screen instanceof EntryDetailScreen || mc.screen instanceof ClassicThaumonomiconScreen, "reopening the book shows the last entry or the classic map"));
        run(mc -> LOGGER.info("[BookVisualCheck] reopened onto {}", mc.screen == null ? "nothing" : mc.screen.getClass().getSimpleName()));
        run(mc -> {
            if (mc.screen instanceof EntryDetailScreen) {
                mc.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_BACKSPACE, 0, 0));
            }
        });
        pause(10);
        run(mc -> check(mc.screen instanceof ClassicThaumonomiconScreen, "the map behind a reopened entry is the classic one"));
        run(Minecraft::stop);
    }

    /** Centres the map on an entry of the given kind and puts the cursor on it. */
    private static void hoverEntry(ClassicThaumonomiconScreen.Probe probe) {
        run(mc -> {
            if (!(mc.screen instanceof ClassicThaumonomiconScreen book)) {
                return;
            }
            int[] at = book.focus(probe);
            if (at == null) {
                LOGGER.warn("[BookVisualCheck] no {} entry in view to hover", probe);
                return;
            }
            moveMouse(mc, at[0], at[1]);
        });
        pause(5);
    }

    private static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!worldRequested && mc.screen instanceof TitleScreen) {
                worldRequested = true;
                LOGGER.info("[BookVisualCheck] creating world '{}'", WORLD);
                LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261008L, false, false), WorldPresets::createNormalWorldDimensions, new TitleScreen());
            }
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (step >= SCRIPT.size()) {
            return;
        }
        Step current = SCRIPT.get(step++);
        try {
            current.action.accept(mc);
        } catch (RuntimeException e) {
            LOGGER.error("[BookVisualCheck] step {} failed", step - 1, e);
        }
        wait = current.pauseAfter;
    }

    private static void pause(int ticks) {
        SCRIPT.add(new Step(mc -> {}, ticks));
    }

    private static void run(Consumer<Minecraft> action) {
        SCRIPT.add(new Step(action, 1));
    }

    private static void command(String command) {
        run(mc -> mc.player.connection.sendCommand(command));
    }

    private static void shot(String name) {
        SCRIPT.add(new Step(mc -> Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), 1, message -> LOGGER.info("[BookVisualCheck] {}", message.getString())), 5));
    }

    /** Moves the cursor to a point in GUI coordinates. Later frames draw with it there. */
    private static void moveMouse(Minecraft mc, double guiX, double guiY) {
        double scale = mc.getWindow().getGuiScale();
        try {
            Field xpos = MouseHandler.class.getDeclaredField("xpos");
            Field ypos = MouseHandler.class.getDeclaredField("ypos");
            xpos.setAccessible(true);
            ypos.setAccessible(true);
            xpos.setDouble(mc.mouseHandler, guiX * scale + scale / 2.0);
            ypos.setDouble(mc.mouseHandler, guiY * scale + scale / 2.0);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("[BookVisualCheck] could not move the cursor", e);
        }
    }

    private static void clickTab(Minecraft mc, int index) {
        Screen screen = mc.screen;
        if (screen == null) {
            return;
        }
        double paneLeft = (screen.width - 256) / 2.0;
        double paneTop = (screen.height - 230) / 2.0;
        clickAt(mc, paneLeft - 12, paneTop + index * 24 + 12);
    }

    private static void clickSearchTab(Minecraft mc) {
        Screen screen = mc.screen;
        if (screen == null) {
            return;
        }
        clickAt(mc, (screen.width - 256) / 2.0 - 12, (screen.height - 230) / 2.0 + 8 * 24 + 12);
    }

    private static void clickAt(Minecraft mc, double x, double y) {
        Screen screen = mc.screen;
        if (screen == null) {
            return;
        }
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(0, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    private static void check(boolean ok, String what) {
        if (ok) {
            LOGGER.info("[BookVisualCheck] PASS: {}", what);
        } else {
            LOGGER.error("[BookVisualCheck] FAIL: {}", what);
        }
    }

    private static void deleteOldWorld() {
        Path world = Path.of("saves", WORLD);
        if (!Files.exists(world)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(world)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[BookVisualCheck] could not delete the old world", e);
        }
    }

    private record Step(Consumer<Minecraft> action, int pauseAfter) {
    }
}
