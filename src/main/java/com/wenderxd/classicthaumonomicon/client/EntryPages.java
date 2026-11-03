package com.wenderxd.classicthaumonomicon.client;

import com.leclowndu93150.thaumaturge.client.screen.research.EntryDetailScreen;
import com.wenderxd.classicthaumonomicon.ClassicConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Going back from an entry's pages. Thaumaturge's return button sits above the book, and right
 * click does nothing while that button is on. Here right click presses the button, and the button
 * sits at the bottom middle of the book.
 *
 * <p>Thaumaturge keeps its back arrow in that same spot, for a recipe opened from another recipe.
 * While the arrow is up the button is hidden so the click reaches the arrow. The paper of an open
 * recipe draws in front of the screen's widgets and covers both, so with one open the button is
 * drawn again on top of the paper. It uses the same sprite as the arrow.
 *
 * <p>Versions of Thaumaturge without the button already go back on right click, and so does its
 * config with the button turned off. With no button on the screen nothing here does anything.
 */
public final class EntryPages {
    /** The block Thaumaturge lays its pages out from, and its back arrow inside that block. */
    private static final int BOOK_WIDTH = 256;
    private static final int BOOK_HEIGHT = 181;
    private static final int ARROW_X = 118;
    private static final int ARROW_Y = 190;
    private static final int ARROW_HEIGHT = 12;

    /** The paper of an open recipe draws at z 200 and its items at z 350. */
    private static final float OVER_PAPER_Z = 400.0F;
    private static final Tooltip BACK = Tooltip.create(Component.translatable("gui.thaumaturge.thaumonomicon.back"));

    private static final @Nullable Field HISTORY = historyField();
    private static final @Nullable Method INSERT_OPEN = insertOpenMethod();

    private EntryPages() {}

    public static void onRenderPre(ScreenEvent.Render.Pre event) {
        Screen screen = event.getScreen();
        AbstractButton button = returnButton(screen);
        if (button == null) {
            return;
        }
        button.setPosition((screen.width - BOOK_WIDTH) / 2 + ARROW_X, (screen.height - BOOK_HEIGHT) / 2 + ARROW_Y);
        button.setHeight(ARROW_HEIGHT);
        button.visible = !backArrowShown(screen);
    }

    public static void onRenderPost(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        AbstractButton button = returnButton(screen);
        if (button == null || !insertOpen(screen)) {
            return;
        }
        boolean clickable = button.visible;
        Tooltip tooltip = button.getTooltip();
        button.visible = true;
        if (!clickable) {
            button.setTooltip(BACK);
        }
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, OVER_PAPER_Z);
        button.render(graphics, event.getMouseX(), event.getMouseY(), event.getPartialTick());
        graphics.pose().popPose();
        button.setTooltip(tooltip);
        button.visible = clickable;
    }

    public static void onMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return;
        }
        AbstractButton button = returnButton(event.getScreen());
        if (button != null) {
            button.onPress();
            event.setCanceled(true);
        }
    }

    private static @Nullable AbstractButton returnButton(Screen screen) {
        if (!(screen instanceof EntryDetailScreen) || !ClassicConfig.ENABLED.get()) {
            return null;
        }
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractButton button) {
                return button;
            }
        }
        return null;
    }

    private static boolean backArrowShown(Screen screen) {
        try {
            return HISTORY != null && HISTORY.get(screen) instanceof Collection<?> history && !history.isEmpty();
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    private static boolean insertOpen(Screen screen) {
        try {
            return INSERT_OPEN != null && (boolean) INSERT_OPEN.invoke(screen);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static @Nullable Field historyField() {
        try {
            Field field = EntryDetailScreen.class.getDeclaredField("history");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static @Nullable Method insertOpenMethod() {
        try {
            Method method = EntryDetailScreen.class.getDeclaredMethod("insertOpen");
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
