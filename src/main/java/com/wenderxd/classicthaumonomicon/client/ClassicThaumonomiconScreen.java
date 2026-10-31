package com.wenderxd.classicthaumonomicon.client;

import com.leclowndu93150.thaumaturge.TTIds;
import com.leclowndu93150.thaumaturge.api.capability.PlayerResearch;
import com.leclowndu93150.thaumaturge.api.capability.PlayerResearchAccess;
import com.leclowndu93150.thaumaturge.api.capability.ResearchFlag;
import com.leclowndu93150.thaumaturge.api.research.IResearchCategory;
import com.leclowndu93150.thaumaturge.api.research.IResearchEntry;
import com.leclowndu93150.thaumaturge.api.research.IResearchStage;
import com.leclowndu93150.thaumaturge.api.research.ResearchEntryMeta;
import com.leclowndu93150.thaumaturge.api.research.ResearchParent;
import com.leclowndu93150.thaumaturge.api.research.ResearchRequirement;
import com.leclowndu93150.thaumaturge.api.research.ResearchUnlockConditions;
import com.leclowndu93150.thaumaturge.client.render.research.EntryIconRenderer;
import com.leclowndu93150.thaumaturge.client.screen.TTScreenTextures;
import com.leclowndu93150.thaumaturge.client.screen.research.EntryDetailScreen;
import com.leclowndu93150.thaumaturge.network.ServerboundClearResearchFlagsPayload;
import com.leclowndu93150.thaumaturge.network.ServerboundUnlockResearchPayload;
import com.leclowndu93150.thaumaturge.registry.TTSounds;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.wenderxd.classicthaumonomicon.ClassicConfig;
import com.wenderxd.classicthaumonomicon.ClassicThaumonomicon;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.PngInfo;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * Thaumaturge's research map, drawn like the Thaumcraft 4 Thaumonomicon.
 *
 * <p>The map is a fixed book-sized pane in the middle of the screen: a wooden frame with carved
 * runes around a window onto a nebula that scrolls slower than the research tree. Categories are
 * tabs down the side of the pane. Entries sit on plates (square for a category's main line,
 * hexagonal for side research, round for milestones, parchment for hidden discoveries) joined by
 * tendrils. Finished research is lit, research that can be started next pulses, and research out
 * of reach is dim with its name in a script the player can't read yet.
 *
 * <p>Only the drawing is different. What an entry is, whether it can be seen or started and what
 * clicking it does all come from Thaumaturge's API and payloads, read the same way its own map
 * reads them, so progression is unchanged. Two Thaumaturge features that Thaumcraft 4 didn't have
 * are added: the search, as a tab at the bottom of the left column, and categories from other
 * mods, which continue the tab columns.
 */
public final class ClassicThaumonomiconScreen extends Screen {
    // Frame sheet layout, from Thaumaturge's Legacy (art/gui/thaumonomicon.py).
    private static final ResourceLocation FRAME = ClassicThaumonomicon.id("textures/gui/book_frame.png");
    private static final ResourceLocation NEBULA = ClassicThaumonomicon.id("textures/gui/book_nebula.png");
    private static final ResourceLocation SPARKLE = ClassicThaumonomicon.id("textures/gui/sparkle.png");
    private static final ResourceLocation FORBIDDEN = ClassicThaumonomicon.id("textures/gui/forbidden.png");
    private static final int SHEET = 256;
    private static final int WHITE = 0xFFFFFFFF;

    private static final int PANE_WIDTH = 256;
    private static final int PANE_HEIGHT = 230;
    /** The window the map is seen through, relative to the pane's top left. */
    private static final int MAP_X = 16;
    private static final int MAP_Y = 17;
    private static final int MAP_WIDTH = 224;
    private static final int MAP_HEIGHT = 196;

    /** One grid step between entries, the entry itself, and the plate drawn round it. */
    private static final int GRID = 24;
    private static final int NODE = 22;
    private static final int PLATE = 26;
    private static final int PLATE_V = 230;
    private static final int PLATE_SQUARE_U = 0;
    private static final int PLATE_SPECIAL_U = 26;
    private static final int PLATE_ROUND_U = 54;
    private static final int PLATE_HIDDEN_U = 86;
    private static final int PLATE_HEX_U = 110;
    private static final int PLATE_HIDDEN_HEX_U = 230;
    private static final int ICON = 16;
    private static final int ICON_INSET = 3;

    /** Tabs: selected, unselected, and the shade laid over an unselected tab's icon. */
    private static final int TAB = 24;
    private static final int TAB_V = 232;
    private static final int TAB_SELECTED_U = 152;
    private static final int TAB_UNSELECTED_U = 176;
    private static final int TAB_SHADE_U = 200;
    private static final int TABS_PER_COLUMN = 9;
    /** The left column's last slot holds the search tab, so categories fill the eight above it. */
    private static final int SEARCH_SLOT = TABS_PER_COLUMN - 1;
    private static final int SEARCH_ICON_U = 160;
    private static final int SEARCH_ICON_V = 16;

    /**
     * How far the backdrop slides as the map crosses its whole extent, in the sheet's 256-unit
     * space. The window shows a quarter-scale region of it, 112 by 98 units, drawn twice the size,
     * so with these spans the region always stays on the sheet.
     */
    private static final float BACKGROUND_SPAN_X = 144.0F;
    private static final float BACKGROUND_SPAN_Y = 158.0F;

    private static final float LOCKED_BRIGHTNESS = 0.3F;
    private static final float LOCKED_ICON_BRIGHTNESS = 0.2F;
    private static final int LOCKED_ITEM_SHADE = 0x99000000;
    private static final long PULSE_PERIOD_MS = 600L;

    private static final int CONNECTOR_DONE = 0x1A1A1A;
    private static final int CONNECTOR_SIBLING_DONE = 0x1A1A33;
    private static final int CONNECTOR_NEXT = 0x00FF00;
    private static final int CONNECTOR_FAR = 0x0000FF;

    /** The warp stain: frames of a tainted node's aura, tinted Thaumcraft 4's 0x440055 at two-thirds. */
    private static final int FORBIDDEN_FRAMES = 32;
    private static final int FORBIDDEN_CELL = 64;
    private static final int FORBIDDEN_SIZE = 80;
    private static final int FORBIDDEN_TINT = 0xA8440055;
    private static final long FORBIDDEN_FRAME_MS = 50L;

    private static final int SPARKLE_FRAMES = 16;
    private static final long SPARKLE_FRAME_MS = 50L;

    /** The hover box: flat black background, the name at full size and the rest at half size below. */
    private static final int TOOLTIP_BACKGROUND = 0xC0000000;
    private static final int TOOLTIP_HEAD = 12;
    private static final int TOOLTIP_LINE = 6;
    /** In front of item icons, which draw at z 150. Vanilla tooltips use the same depth. */
    private static final float TOOLTIP_Z = 400.0F;
    private static final int NAME = 0xFFFFFFFF;
    private static final int NAME_SPECIAL = 0xFFFFFF80;
    private static final int NAME_LOCKED = 0xFF808080;
    private static final int NAME_LOCKED_SPECIAL = 0xFF808040;
    private static final int STAGE = 0xFF55FFFF;
    private static final int NOT_BEGUN = 0xFF87D1AB;
    private static final int MISSING = 0xFFDC141C;
    private static final int MISSING_PARENT = 0xFFFFFF55;
    private static final int WARP = 0xFFAA55FF;
    private static final int ID_COLOUR = 0xFF555555;
    private static final int MAX_WARP_LEVEL = 5;

    /** Research still out of reach is named in the enchanting table's script, as the old book did. */
    private static final ResourceLocation ARCANE_FONT = ResourceLocation.withDefaultNamespace("alt");

    private static final int SEARCH_BOX_INSET = 4;
    private static final int SEARCH_BOX_WIDTH = 120;
    private static final int SEARCH_BOX_HEIGHT = 12;
    private static final int SEARCH_FIRST_ROW = 22;
    private static final int SEARCH_ROW = 10;
    private static final int SEARCH_ICON_X = 6;
    private static final int SEARCH_TEXT_X = 18;
    private static final int SEARCH_RECIPE_U = 224;
    private static final int SEARCH_RECIPE_V = 48;
    private static final int SEARCH_WASH = 0xB0000000;
    private static final int SEARCH_CATEGORY = 0xFFDDAAAA;
    private static final int SEARCH_ENTRY = 0xFFDDDDDD;
    private static final int SEARCH_RECIPE = 0xFFAAAADD;
    private static final int SEARCH_CATEGORY_HOVER = 0xFFFFCCCC;
    private static final int SEARCH_ENTRY_HOVER = 0xFFFFFFFF;
    private static final int SEARCH_RECIPE_HOVER = 0xFFCCCCFF;
    private static final int SEARCH_OVERFLOW = 0xFFAAAAAA;

    private static final float CLACK_VOLUME = 0.4F;
    private static final float PAGE_VOLUME = 0.66F;

    /** Kept between openings so the book reopens where it was left. */
    private static @Nullable ResourceLocation persistedCategory;
    private static double persistedMapX = Double.NaN;
    private static double persistedMapY = Double.NaN;
    private static boolean persistedSearching;
    private static String persistedQuery = "";

    private final List<Holder.Reference<IResearchCategory>> allCategories = new ArrayList<>();
    private final List<Holder.Reference<IResearchCategory>> tabCategories = new ArrayList<>();
    private final Map<ResourceLocation, EntryNode> nodesById = new HashMap<>();
    private final Map<Holder.Reference<IResearchCategory>, List<EntryNode>> nodesByCategory = new HashMap<>();
    private final Map<ResourceLocation, Boolean> visibility = new HashMap<>();
    private final List<SearchResult> searchResults = new ArrayList<>();
    private final Map<ResourceLocation, Optional<PngInfo>> iconSizes = new HashMap<>();
    private final Tendrils tendrils = new Tendrils();

    private @Nullable Holder.Reference<IResearchCategory> activeCategory;
    private double mapX;
    private double mapY;
    private int minMapX;
    private int minMapY;
    private int maxMapX;
    private int maxMapY;
    private boolean dragging;
    private int rightColumnScroll;
    private boolean searching;
    private @Nullable EditBox searchField;
    private @Nullable EntryNode hovered;
    private int tickCount;

    public ClassicThaumonomiconScreen() {
        super(Component.translatable("item.thaumaturge.thaumonomicon"));
    }

    // Setup

    @Override
    protected void init() {
        super.init();
        if (minecraft.player == null) {
            return;
        }
        loadRegistryData();
        visibility.clear();
        searchField = new EditBox(font, mapLeft() + SEARCH_BOX_INSET, mapTop() + SEARCH_BOX_INSET, SEARCH_BOX_WIDTH, SEARCH_BOX_HEIGHT,
                Component.translatable("gui.thaumaturge.thaumonomicon.search"));
        searchField.setBordered(true);
        searchField.setMaxLength(15);
        searchField.setTextColor(0xFFFFFFFF);
        searchField.setResponder(this::onSearchChanged);
        searchField.setVisible(false);
        addRenderableWidget(searchField);

        PlayerResearch knowledge = knowledge();
        refreshTabs(knowledge);
        Holder.Reference<IResearchCategory> remembered = null;
        for (Holder.Reference<IResearchCategory> ref : tabCategories) {
            if (ref.key().location().equals(persistedCategory)) {
                remembered = ref;
            }
        }
        if (remembered != null && !Double.isNaN(persistedMapX)) {
            activeCategory = remembered;
            updateBounds(knowledge);
            mapX = persistedMapX;
            mapY = persistedMapY;
            clampMap();
        } else {
            selectCategory(remembered != null ? remembered : tabCategories.isEmpty() ? null : tabCategories.get(0), knowledge);
        }
        if (persistedSearching) {
            setSearching(true);
            searchField.setValue(persistedQuery);
        }
    }

    private void loadRegistryData() {
        allCategories.clear();
        nodesById.clear();
        nodesByCategory.clear();
        List<Holder.Reference<IResearchCategory>> own = new ArrayList<>();
        List<Holder.Reference<IResearchCategory>> others = new ArrayList<>();
        minecraft.player.registryAccess().lookup(IResearchCategory.REGISTRY_KEY).ifPresent(lookup -> lookup.listElements().forEach(ref -> {
            (ref.key().location().getNamespace().equals(TTIds.MODID) ? own : others).add(ref);
        }));
        Comparator<Holder.Reference<IResearchCategory>> order = Comparator.<Holder.Reference<IResearchCategory>>comparingInt(ref -> ref.value().index())
                .thenComparing(ref -> ref.key().location().toString());
        own.sort(order);
        others.sort(order);
        allCategories.addAll(own);
        allCategories.addAll(others);
        minecraft.player.registryAccess().lookup(IResearchEntry.REGISTRY_KEY).ifPresent(lookup -> lookup.listElements().forEach(holder -> {
            IResearchEntry entry = holder.value();
            if (!(entry.category() instanceof Holder.Reference<IResearchCategory> category)) {
                return;
            }
            EntryNode node = new EntryNode(holder.key().location(), entry, holder, category);
            nodesById.put(node.id, node);
            nodesByCategory.computeIfAbsent(category, key -> new ArrayList<>()).add(node);
        }));
    }

    /** The categories that get a tab: every one the player has opened up, Thaumaturge's first. */
    private void refreshTabs(PlayerResearch knowledge) {
        tabCategories.clear();
        for (Holder.Reference<IResearchCategory> ref : allCategories) {
            if (isCategoryUnlocked(knowledge, ref)) {
                tabCategories.add(ref);
            }
        }
        rightColumnScroll = Mth.clamp(rightColumnScroll, 0, maxRightColumnScroll());
    }

    private void selectCategory(@Nullable Holder.Reference<IResearchCategory> category, PlayerResearch knowledge) {
        activeCategory = category;
        updateBounds(knowledge);
        mapX = (minMapX + maxMapX) / 2.0;
        mapY = (minMapY + maxMapY) / 2.0;
    }

    /**
     * Drag limits. The map stops when the outermost research in each direction reaches the middle
     * of the window, so nothing can be dragged out of sight.
     */
    private void updateBounds(PlayerResearch knowledge) {
        int minCol = Integer.MAX_VALUE;
        int maxCol = Integer.MIN_VALUE;
        int minRow = Integer.MAX_VALUE;
        int maxRow = Integer.MIN_VALUE;
        for (EntryNode node : activeNodes()) {
            if (!isVisible(knowledge, node)) {
                continue;
            }
            minCol = Math.min(minCol, node.entry.column());
            maxCol = Math.max(maxCol, node.entry.column());
            minRow = Math.min(minRow, node.entry.row());
            maxRow = Math.max(maxRow, node.entry.row());
        }
        if (minCol == Integer.MAX_VALUE) {
            minCol = maxCol = minRow = maxRow = 0;
        }
        minMapX = minCol * GRID + NODE / 2 - MAP_WIDTH / 2;
        maxMapX = maxCol * GRID + NODE / 2 - MAP_WIDTH / 2;
        minMapY = minRow * GRID + NODE / 2 - MAP_HEIGHT / 2;
        maxMapY = maxRow * GRID + NODE / 2 - MAP_HEIGHT / 2;
    }

    private void clampMap() {
        mapX = Mth.clamp(mapX, minMapX, maxMapX);
        mapY = Mth.clamp(mapY, minMapY, maxMapY);
    }

    private void persistState() {
        persistedMapX = mapX;
        persistedMapY = mapY;
        persistedSearching = searching;
        persistedQuery = searching && searchField != null ? searchField.getValue() : "";
        persistedCategory = activeCategory == null ? null : activeCategory.key().location();
    }

    @Override
    public void onClose() {
        persistState();
        super.onClose();
    }

    @Override
    public void tick() {
        super.tick();
        tickCount++;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // What the player knows

    private PlayerResearch knowledge() {
        return PlayerResearchAccess.of(minecraft.player);
    }

    private List<EntryNode> activeNodes() {
        return activeCategory == null ? List.of() : nodesByCategory.getOrDefault(activeCategory, List.of());
    }

    private static boolean isCategoryUnlocked(PlayerResearch knowledge, Holder.Reference<IResearchCategory> ref) {
        Optional<ResourceLocation> gate = ref.value().requiredResearch();
        return gate.isEmpty() || knowledge.hasCompleted(gate.get());
    }

    /**
     * Whether an entry is on the map at all, by Thaumaturge's rule: known entries always, a hidden
     * entry only once it could be started, and never an entry whose parents are off the map.
     * Recomputed every frame, since knowledge can change while the book is open.
     */
    private boolean isVisible(PlayerResearch knowledge, EntryNode node) {
        if (knowledge.knows(node.id)) {
            return true;
        }
        Boolean known = visibility.get(node.id);
        if (known != null) {
            return known;
        }
        // Mark it visible while its parents are checked. This also breaks cycles.
        visibility.put(node.id, Boolean.TRUE);
        boolean visible = computeVisible(knowledge, node);
        visibility.put(node.id, visible);
        return visible;
    }

    private boolean computeVisible(PlayerResearch knowledge, EntryNode node) {
        boolean hidden = node.entry.hasMeta(ResearchEntryMeta.HIDDEN);
        if (hidden && (node.entry.parents().isEmpty() || !canUnlock(knowledge, node))) {
            return false;
        }
        for (ResearchParent parent : node.entry.parents()) {
            EntryNode parentNode = nodesById.get(parent.id());
            if (parentNode != null && !isVisible(knowledge, parentNode)) {
                return false;
            }
        }
        return true;
    }

    private boolean canUnlock(PlayerResearch knowledge, EntryNode node) {
        for (ResearchParent parent : node.entry.parents()) {
            if (!parent.isSatisfiedBy(knowledge)) {
                return false;
            }
        }
        return knowledge.knows(node.id) || ResearchUnlockConditions.passes(minecraft.player, knowledge, node.id);
    }

    private static boolean hasWarp(IResearchEntry entry) {
        return maxWarp(entry) > 0;
    }

    private static int maxWarp(IResearchEntry entry) {
        int warp = 0;
        for (IResearchStage stage : entry.stages()) {
            warp = Math.max(warp, stage.warp());
        }
        return warp;
    }

    private boolean categoryHasNews(PlayerResearch knowledge, Holder.Reference<IResearchCategory> ref) {
        for (EntryNode node : nodesByCategory.getOrDefault(ref, List.of())) {
            if (knowledge.knows(node.id) && (knowledge.hasFlag(node.id, ResearchFlag.RESEARCH) || knowledge.hasFlag(node.id, ResearchFlag.PAGE))) {
                return true;
            }
        }
        return false;
    }

    private int categoryCompletion(PlayerResearch knowledge, Holder.Reference<IResearchCategory> ref) {
        int total = 0;
        int known = 0;
        for (EntryNode node : nodesByCategory.getOrDefault(ref, List.of())) {
            if (node.entry.hasMeta(ResearchEntryMeta.AUTOUNLOCK)) {
                continue;
            }
            total++;
            if (knowledge.knows(node.id)) {
                known++;
            }
        }
        return total == 0 ? 0 : (int) (known * 100.0F / total);
    }

    // Layout

    private int paneLeft() {
        return (width - PANE_WIDTH) / 2;
    }

    private int paneTop() {
        return (height - PANE_HEIGHT) / 2;
    }

    private int mapLeft() {
        return paneLeft() + MAP_X;
    }

    private int mapTop() {
        return paneTop() + MAP_Y;
    }

    private boolean inMap(double mouseX, double mouseY) {
        return mouseX >= mapLeft() && mouseX < mapLeft() + MAP_WIDTH && mouseY >= mapTop() && mouseY < mapTop() + MAP_HEIGHT;
    }

    private int leftColumnSize() {
        return Math.min(tabCategories.size(), SEARCH_SLOT);
    }

    private int rightColumnSize() {
        return Math.min(tabCategories.size() - leftColumnSize(), TABS_PER_COLUMN);
    }

    private int maxRightColumnScroll() {
        return Math.max(0, tabCategories.size() - leftColumnSize() - TABS_PER_COLUMN);
    }

    /** A category's tab: in the left column, or mirrored in the right once the left is full. */
    private @Nullable Tab tabAt(int index) {
        int left = leftColumnSize();
        if (index < left) {
            return new Tab(tabCategories.get(index), paneLeft() - TAB, paneTop() + index * TAB, false);
        }
        int slot = index - left - rightColumnScroll;
        if (slot < 0 || slot >= TABS_PER_COLUMN || index >= tabCategories.size()) {
            return null;
        }
        return new Tab(tabCategories.get(index), paneLeft() + PANE_WIDTH, paneTop() + slot * TAB, true);
    }

    private @Nullable Tab hoveredTab(double mouseX, double mouseY) {
        for (int index = 0; index < tabCategories.size(); index++) {
            Tab tab = tabAt(index);
            if (tab != null && tab.contains(mouseX, mouseY)) {
                return tab;
            }
        }
        return null;
    }

    private int searchTabX() {
        return paneLeft() - TAB;
    }

    private int searchTabY() {
        return paneTop() + SEARCH_SLOT * TAB;
    }

    private boolean overSearchTab(double mouseX, double mouseY) {
        return mouseX >= searchTabX() && mouseX < searchTabX() + TAB && mouseY >= searchTabY() && mouseY < searchTabY() + TAB;
    }

    private boolean overRightColumn(double mouseX, double mouseY) {
        int x = paneLeft() + PANE_WIDTH;
        return mouseX >= x && mouseX < x + TAB && mouseY >= paneTop() && mouseY < paneTop() + TABS_PER_COLUMN * TAB;
    }

    // Drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.player == null || searchField == null) {
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        PlayerResearch knowledge = knowledge();
        visibility.clear();
        refreshTabs(knowledge);
        if (activeCategory != null && !tabCategories.contains(activeCategory)) {
            selectCategory(tabCategories.isEmpty() ? null : tabCategories.get(0), knowledge);
        }
        updateBounds(knowledge);
        clampMap();

        // The book, through renderBackground, then the search box.
        super.render(graphics, mouseX, mouseY, partialTick);

        Tab tab = hoveredTab(mouseX, mouseY);
        if (tab != null) {
            drawTabTooltip(graphics, knowledge, tab.category, mouseX, mouseY);
        } else if (overSearchTab(mouseX, mouseY)) {
            drawTooltip(graphics, Component.translatable("gui.thaumaturge.thaumonomicon.search"), NAME, List.of(), mouseX, mouseY);
        } else if (hovered != null && !dragging) {
            drawEntryTooltip(graphics, knowledge, hovered, mouseX, mouseY);
        }
    }

    /**
     * A plain dark overlay instead of a blur, like Thaumcraft 4, with the book on it. The book is
     * the screen's background so that widgets draw over it.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        if (minecraft.player != null && searchField != null) {
            drawBook(graphics, knowledge(), mouseX, mouseY, partialTick);
        }
    }

    private void drawBook(GuiGraphics graphics, PlayerResearch knowledge, int mouseX, int mouseY, float partialTick) {
        int left = paneLeft();
        int top = paneTop();
        int mapLeft = mapLeft();
        int mapTop = mapTop();
        int scrollX = Mth.floor(mapX);
        int scrollY = Mth.floor(mapY);
        hovered = null;

        // Clip the map to the window, or entries on the rim and tendrils would draw over the frame
        // and outside the book.
        graphics.enableScissor(mapLeft, mapTop, mapLeft + MAP_WIDTH, mapTop + MAP_HEIGHT);
        drawBackdrop(graphics, mapLeft, mapTop, scrollX, scrollY);
        if (searching) {
            graphics.fill(mapLeft, mapTop, mapLeft + MAP_WIDTH, mapTop + MAP_HEIGHT, SEARCH_WASH);
            drawSearchResults(graphics, mouseX, mouseY);
        } else {
            float time = minecraft.player.tickCount + partialTick;
            drawConnections(graphics, knowledge, mapLeft, mapTop, scrollX, scrollY, time);
            drawEntries(graphics, knowledge, mapLeft, mapTop, scrollX, scrollY, mouseX, mouseY);
        }
        graphics.disableScissor();

        drawTabs(graphics, knowledge);
        blit(graphics, FRAME, left, top, 0.0F, 0.0F, PANE_WIDTH, PANE_HEIGHT, SHEET, SHEET);
    }

    /**
     * The background behind the tree. It moves by a fixed span however large the category is, which
     * gives the parallax.
     */
    private void drawBackdrop(GuiGraphics graphics, int mapLeft, int mapTop, int scrollX, int scrollY) {
        if (activeCategory == null) {
            blit(graphics, NEBULA, mapLeft, mapTop, 0.0F, 0.0F, MAP_WIDTH, MAP_HEIGHT, MAP_WIDTH / 2, MAP_HEIGHT / 2, SHEET, SHEET);
            return;
        }
        float acrossX = maxMapX > minMapX ? (float) (scrollX - minMapX) / (maxMapX - minMapX) : 0.5F;
        float acrossY = maxMapY > minMapY ? (float) (scrollY - minMapY) / (maxMapY - minMapY) : 0.5F;
        float u = Mth.clamp(acrossX, 0.0F, 1.0F) * BACKGROUND_SPAN_X;
        float v = Mth.clamp(acrossY, 0.0F, 1.0F) * BACKGROUND_SPAN_Y;
        blit(graphics, backdropFor(activeCategory), mapLeft, mapTop, u, v, MAP_WIDTH, MAP_HEIGHT, MAP_WIDTH / 2, MAP_HEIGHT / 2, SHEET, SHEET);
    }

    /**
     * Thaumcraft 4 used the same violet nebula for every category except Eldritch, which had a
     * darker one. Thaumaturge's Eldritch background is used for that. Categories from other mods
     * keep their own.
     */
    private static ResourceLocation backdropFor(Holder.Reference<IResearchCategory> category) {
        ResourceLocation id = category.key().location();
        boolean classic = ClassicConfig.CLASSIC_BACKGROUNDS.get() && id.getNamespace().equals(TTIds.MODID) && !id.getPath().equals("eldritch");
        return classic ? NEBULA : category.value().background();
    }

    /**
     * The tendrils. Each runs from an entry toward the research it follows from: dark and still
     * once the entry is done, green and waving when it is next, blue when it is further off.
     * Siblings, which Thaumaturge draws as decoration, are joined the same way in a darker blue.
     */
    private void drawConnections(GuiGraphics graphics, PlayerResearch knowledge, int mapLeft, int mapTop, int scrollX, int scrollY, float time) {
        for (EntryNode source : activeNodes()) {
            if (!isVisible(knowledge, source)) {
                continue;
            }
            boolean sourceDone = knowledge.hasCompleted(source.id);
            boolean reverse = source.entry.hasMeta(ResearchEntryMeta.REVERSE);
            for (ResearchParent parent : source.entry.parents()) {
                EntryNode parentNode = nodesById.get(parent.id());
                if (parent.inherit() || parentNode == null || !parentNode.category.equals(activeCategory) || parentNode.entry.siblings().contains(source.id)) {
                    continue;
                }
                if (sourceDone) {
                    link(source, parentNode, mapLeft, mapTop, scrollX, scrollY, CONNECTOR_DONE, false, reverse, time);
                } else if (parent.isSatisfiedBy(knowledge)) {
                    link(source, parentNode, mapLeft, mapTop, scrollX, scrollY, CONNECTOR_NEXT, true, reverse, time);
                } else if (isVisible(knowledge, parentNode)) {
                    link(source, parentNode, mapLeft, mapTop, scrollX, scrollY, CONNECTOR_FAR, true, reverse, time);
                }
            }
            for (ResourceLocation siblingId : source.entry.siblings()) {
                EntryNode sibling = nodesById.get(siblingId);
                if (sibling == null || !sibling.category.equals(activeCategory)) {
                    continue;
                }
                if (sourceDone) {
                    link(source, sibling, mapLeft, mapTop, scrollX, scrollY, CONNECTOR_SIBLING_DONE, false, reverse, time);
                } else if (knowledge.hasCompleted(siblingId)) {
                    link(source, sibling, mapLeft, mapTop, scrollX, scrollY, CONNECTOR_NEXT, true, reverse, time);
                } else if (isVisible(knowledge, sibling)) {
                    link(source, sibling, mapLeft, mapTop, scrollX, scrollY, CONNECTOR_FAR, true, reverse, time);
                }
            }
        }
        tendrils.submit(graphics);
    }

    private void link(EntryNode from, EntryNode to, int mapLeft, int mapTop, int scrollX, int scrollY, int rgb, boolean wiggle, boolean reverse, float time) {
        float x1 = mapLeft + from.entry.column() * GRID - scrollX + NODE / 2.0F;
        float y1 = mapTop + from.entry.row() * GRID - scrollY + NODE / 2.0F;
        float x2 = mapLeft + to.entry.column() * GRID - scrollX + NODE / 2.0F;
        float y2 = mapTop + to.entry.row() * GRID - scrollY + NODE / 2.0F;
        if (reverse) {
            tendrils.add(x2, y2, x1, y1, rgb, wiggle, time);
        } else {
            tendrils.add(x1, y1, x2, y2, rgb, wiggle, time);
        }
    }

    private void drawEntries(GuiGraphics graphics, PlayerResearch knowledge, int mapLeft, int mapTop, int scrollX, int scrollY, int mouseX, int mouseY) {
        boolean mouseInMap = inMap(mouseX, mouseY);
        for (EntryNode node : activeNodes()) {
            int x = node.entry.column() * GRID - scrollX;
            int y = node.entry.row() * GRID - scrollY;
            if (x < -GRID || y < -GRID || x > MAP_WIDTH || y > MAP_HEIGHT || !isVisible(knowledge, node)) {
                continue;
            }
            int drawX = mapLeft + x;
            int drawY = mapTop + y;
            boolean complete = knowledge.hasCompleted(node.id);
            boolean unlockable = canUnlock(knowledge, node);

            // Under the plate, so the stain spreads behind the entry and over its neighbours.
            if (hasWarp(node.entry)) {
                drawForbidden(graphics, drawX + NODE / 2, drawY + NODE / 2);
            }

            float brightness = complete ? 1.0F : unlockable ? pulse() : LOCKED_BRIGHTNESS;
            int tint = grey(brightness);
            blit(graphics, FRAME, drawX - 2, drawY - 2, plateU(node.entry), PLATE_V, PLATE, PLATE, SHEET, SHEET, tint);
            if (node.entry.hasMeta(ResearchEntryMeta.SPIKY)) {
                blit(graphics, FRAME, drawX - 2, drawY - 2, PLATE_SPECIAL_U, PLATE_V, PLATE, PLATE, SHEET, SHEET, tint);
            }

            if (knowledge.hasFlag(node.id, ResearchFlag.RESEARCH) || knowledge.hasFlag(node.id, ResearchFlag.PAGE)) {
                drawSparkle(graphics, drawX - 5, drawY - 5);
            }

            Object icon = EntryIconRenderer.resolveIcon(node.entry, tickCount);
            drawIcon(graphics, icon, drawX + ICON_INSET, drawY + ICON_INSET, unlockable ? brightness : LOCKED_ICON_BRIGHTNESS, !unlockable);

            if (mouseInMap && mouseX >= drawX && mouseX <= drawX + NODE && mouseY >= drawY && mouseY <= drawY + NODE) {
                hovered = node;
            }
        }
    }

    /**
     * Which plate an entry sits on. Round entries get the round plate. Otherwise Thaumaturge's
     * hexagonal entries get the pointed plate and the rest the square one, in stone, or in parchment
     * when the entry is a hidden discovery.
     */
    private static int plateU(IResearchEntry entry) {
        if (entry.hasMeta(ResearchEntryMeta.ROUND)) {
            return PLATE_ROUND_U;
        }
        boolean hidden = entry.hasMeta(ResearchEntryMeta.HIDDEN);
        if (entry.hasMeta(ResearchEntryMeta.HEX)) {
            return hidden ? PLATE_HIDDEN_HEX_U : PLATE_HEX_U;
        }
        return hidden ? PLATE_HIDDEN_U : PLATE_SQUARE_U;
    }

    /** The slow pulse on research that could be started next. */
    private static float pulse() {
        double phase = (System.currentTimeMillis() % PULSE_PERIOD_MS) / (double) PULSE_PERIOD_MS;
        return (float) Math.sin(phase * Math.PI * 2.0) * 0.25F + 0.75F;
    }

    private static int grey(float brightness) {
        int channel = Mth.clamp(Math.round(brightness * 255.0F), 0, 255);
        return 0xFF000000 | channel << 16 | channel << 8 | channel;
    }

    private void drawForbidden(GuiGraphics graphics, int centreX, int centreY) {
        // Counted backwards so the wisps curl inward.
        int frame = FORBIDDEN_FRAMES - 1 - (int) (System.currentTimeMillis() / FORBIDDEN_FRAME_MS % FORBIDDEN_FRAMES);
        blit(graphics, FORBIDDEN, centreX - FORBIDDEN_SIZE / 2, centreY - FORBIDDEN_SIZE / 2, (float) (frame * FORBIDDEN_CELL), 0.0F, FORBIDDEN_SIZE, FORBIDDEN_SIZE,
                FORBIDDEN_CELL, FORBIDDEN_CELL, FORBIDDEN_FRAMES * FORBIDDEN_CELL, FORBIDDEN_CELL, FORBIDDEN_TINT);
    }

    /** The new-research sparkle, cycling through its sixteen frames. */
    private static void drawSparkle(GuiGraphics graphics, int x, int y) {
        int frame = (int) (System.currentTimeMillis() / SPARKLE_FRAME_MS % SPARKLE_FRAMES);
        blit(graphics, SPARKLE, x, y, (float) (frame * ICON), 0.0F, ICON, ICON, SPARKLE_FRAMES * ICON, ICON);
    }

    /**
     * An entry's icon. Texture icons take the plate's brightness, so they pulse and dim with it.
     * Items can't be tinted, so one still out of reach gets a dark overlay. Focus icons go through
     * Thaumaturge's own renderer.
     */
    private void drawIcon(GuiGraphics graphics, Object icon, int x, int y, float brightness, boolean locked) {
        if (icon instanceof ResourceLocation texture) {
            drawTextureIcon(graphics, texture, x, y, grey(brightness));
            return;
        }
        if (icon instanceof ItemStack stack) {
            if (stack.isEmpty()) {
                return;
            }
            graphics.renderItem(stack, x, y);
            if (locked) {
                // The overlay render type skips the depth test. The item sits at z 150 and would hide a plain fill.
                graphics.fill(RenderType.guiOverlay(), x, y, x + ICON, y + ICON, LOCKED_ITEM_SHADE);
            }
            return;
        }
        EntryIconRenderer.drawResearchIcon(graphics, x, y, icon, locked);
    }

    /** A texture icon, stepping through its frames when it is a strip of them. */
    private void drawTextureIcon(GuiGraphics graphics, ResourceLocation texture, int x, int y, int tint) {
        Optional<PngInfo> size = iconSizes.computeIfAbsent(texture, this::readSize);
        if (size.isEmpty()) {
            blit(graphics, texture, x, y, 0.0F, 0.0F, ICON, ICON, ICON, ICON, ICON, ICON, tint);
            return;
        }
        int w = size.get().width();
        int h = size.get().height();
        long frameTime = System.currentTimeMillis() / 150L;
        if (h > w && h % w == 0) {
            int frame = (int) (frameTime % (h / w));
            blit(graphics, texture, x, y, 0.0F, (float) (frame * w), ICON, ICON, w, w, w, h, tint);
        } else if (w > h && w % h == 0) {
            int frame = (int) (frameTime % (w / h));
            blit(graphics, texture, x, y, (float) (frame * h), 0.0F, ICON, ICON, h, h, w, h, tint);
        } else {
            blit(graphics, texture, x, y, 0.0F, 0.0F, ICON, ICON, w, h, w, h, tint);
        }
    }

    /** A texture's size, from its PNG header. A loaded texture in 1.21.1 doesn't keep it. */
    private Optional<PngInfo> readSize(ResourceLocation texture) {
        try (InputStream stream = minecraft.getResourceManager().open(texture)) {
            return Optional.of(PngInfo.fromStream(stream));
        } catch (IOException missing) {
            return Optional.empty();
        }
    }

    /**
     * The tabs, drawn before the pane so its edge covers their inner ends. The open category's tab
     * sticks out further and is lit, the others are shaded. Tabs on the right are the left ones
     * mirrored.
     */
    private void drawTabs(GuiGraphics graphics, PlayerResearch knowledge) {
        for (int index = 0; index < tabCategories.size(); index++) {
            Tab tab = tabAt(index);
            if (tab == null) {
                continue;
            }
            boolean selected = tab.category.equals(activeCategory);
            drawTabBody(graphics, tab.x, tab.y, selected, tab.mirrored);
            int iconX = tab.x + (tab.mirrored ? (selected ? 5 : 3) : (selected ? 3 : 5));
            blit(graphics, tab.category.value().icon(), iconX, tab.y + 4, 0.0F, 0.0F, ICON, ICON, ICON, ICON, ICON, ICON);
            if (!selected) {
                drawTabSprite(graphics, tab.x, tab.y, TAB_SHADE_U, tab.mirrored);
            }
            if (categoryHasNews(knowledge, tab.category)) {
                drawSparkle(graphics, tab.mirrored ? tab.x + TAB - ICON + 3 : tab.x - 3, tab.y - 4);
            }
        }
        // The search tab, in the bottom slot of the left column.
        int x = searchTabX();
        int y = searchTabY();
        drawTabBody(graphics, x, y, searching, false);
        blit(graphics, TTScreenTextures.RESEARCH_BROWSER, x + (searching ? 3 : 5), y + 4, (float) SEARCH_ICON_U, (float) SEARCH_ICON_V, ICON, ICON, TTScreenTextures.TEX_SIZE,
                TTScreenTextures.TEX_SIZE);
        if (!searching) {
            drawTabSprite(graphics, x, y, TAB_SHADE_U, false);
        }
    }

    private static void drawTabBody(GuiGraphics graphics, int x, int y, boolean selected, boolean mirrored) {
        drawTabSprite(graphics, x, y, selected ? TAB_SELECTED_U : TAB_UNSELECTED_U, mirrored);
    }

    private static void drawTabSprite(GuiGraphics graphics, int x, int y, int u, boolean mirrored) {
        if (mirrored) {
            // A negative source width flips the sprite.
            blit(graphics, FRAME, x, y, (float) (u + TAB), (float) TAB_V, TAB, TAB, -TAB, TAB, SHEET, SHEET);
        } else {
            blit(graphics, FRAME, x, y, (float) u, (float) TAB_V, TAB, TAB, SHEET, SHEET);
        }
    }

    private static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        blit(graphics, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight, WHITE);
    }

    private static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight, int colour) {
        blit(graphics, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight, colour);
    }

    private static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, float u, float v, int width, int height, int regionWidth, int regionHeight, int textureWidth,
            int textureHeight) {
        blit(graphics, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, WHITE);
    }

    /**
     * Draws a region of a texture at {@code width} by {@code height}, tinted by {@code colour}. A
     * negative region width flips it. GuiGraphics' own blit in 1.21.1 can't tint and doesn't blend,
     * and the frame's inner shadow and the warp haze need both.
     *
     * <p>The depth test is off, so each texture lands over everything drawn before it. Items sit at
     * z 150, and with the test on an item at the edge of the window would show over the frame.
     */
    private static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, float u, float v, int width, int height, int regionWidth, int regionHeight, int textureWidth,
            int textureHeight, int colour) {
        float u0 = u / textureWidth;
        float u1 = (u + regionWidth) / textureWidth;
        float v0 = v / textureHeight;
        float v1 = (v + regionHeight) / textureHeight;
        Matrix4f pose = graphics.pose().last().pose();
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(pose, x, y, 0.0F).setUv(u0, v0).setColor(colour);
        buffer.addVertex(pose, x, y + height, 0.0F).setUv(u0, v1).setColor(colour);
        buffer.addVertex(pose, x + width, y + height, 0.0F).setUv(u1, v1).setColor(colour);
        buffer.addVertex(pose, x + width, y, 0.0F).setUv(u1, v0).setColor(colour);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // Tooltips

    private void drawEntryTooltip(GuiGraphics graphics, PlayerResearch knowledge, EntryNode node, int mouseX, int mouseY) {
        boolean complete = knowledge.hasCompleted(node.id);
        boolean unlockable = canUnlock(knowledge, node);
        boolean special = node.entry.hasMeta(ResearchEntryMeta.SPIKY);
        Component name = Component.translatable(node.entry.nameKey());
        if (!unlockable && !knowledge.knows(node.id)) {
            name = arcaneName(name.getString());
        }
        int nameColour = unlockable ? (special ? NAME_SPECIAL : NAME) : (special ? NAME_LOCKED_SPECIAL : NAME_LOCKED);

        List<Line> lines = new ArrayList<>();
        if (unlockable) {
            if (!complete && !node.entry.stages().isEmpty()) {
                int stage = knowledge.stageOf(node.id);
                if (stage >= 0) {
                    lines.add(new Line(Component.translatable("tooltip.thaumaturge.research.stage").append(" " + (stage + 1) + "/" + node.entry.stages().size()), STAGE));
                } else {
                    lines.add(new Line(Component.translatable("tooltip.thaumaturge.research.not_begun"), NOT_BEGUN));
                }
            }
            int warp = maxWarp(node.entry);
            if (warp > 0) {
                Component level = Component.translatable("gui.thaumaturge.thaumonomicon.warp_level." + Math.min(warp, MAX_WARP_LEVEL));
                lines.add(new Line(Component.translatable("gui.thaumaturge.thaumonomicon.warp_warning", level).getString().trim(), WARP));
            }
        } else {
            lines.add(new Line(Component.translatable("tooltip.thaumaturge.research.missing"), MISSING));
            for (ResearchParent parent : node.entry.parents()) {
                if (parent.isSatisfiedBy(knowledge)) {
                    continue;
                }
                EntryNode parentNode = nodesById.get(parent.id());
                Component parentName = parentNode == null ? Component.literal("?") : Component.translatable(parentNode.entry.nameKey());
                lines.add(new Line(Component.literal(" - ").append(parentName), MISSING_PARENT));
            }
            for (Component message : ResearchUnlockConditions.lockedMessages(minecraft.player, knowledge, node.id)) {
                lines.add(new Line(Component.literal(" - ").append(message), MISSING_PARENT));
            }
        }
        if (knowledge.hasFlag(node.id, ResearchFlag.RESEARCH)) {
            lines.add(new Line(Component.translatable("tooltip.thaumaturge.research.new_research"), NAME));
        }
        if (knowledge.hasFlag(node.id, ResearchFlag.PAGE)) {
            lines.add(new Line(Component.translatable("tooltip.thaumaturge.research.new_page"), NAME));
        }
        if (minecraft.options.advancedItemTooltips) {
            lines.add(new Line(Component.literal(node.id.toString()), ID_COLOUR));
        }
        drawTooltip(graphics, name, nameColour, lines, mouseX, mouseY);
    }

    private void drawTabTooltip(GuiGraphics graphics, PlayerResearch knowledge, Holder.Reference<IResearchCategory> category, int mouseX, int mouseY) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(Component.literal(categoryCompletion(knowledge, category) + "%"), SEARCH_OVERFLOW));
        boolean newResearch = false;
        boolean newPage = false;
        for (EntryNode node : nodesByCategory.getOrDefault(category, List.of())) {
            if (knowledge.knows(node.id)) {
                newResearch |= knowledge.hasFlag(node.id, ResearchFlag.RESEARCH);
                newPage |= knowledge.hasFlag(node.id, ResearchFlag.PAGE);
            }
        }
        if (newResearch) {
            lines.add(new Line(Component.translatable("tooltip.thaumaturge.research.new_research"), NAME));
        }
        if (newPage) {
            lines.add(new Line(Component.translatable("tooltip.thaumaturge.research.new_page"), NAME));
        }
        drawTooltip(graphics, categoryName(category), NAME, lines, mouseX, mouseY);
    }

    /**
     * The Thaumcraft 4 hover box: no border, a flat black background, the first line at full size
     * and every other line at half size below it. Half-size lines are measured at half width, so a
     * long sentence under a short name doesn't widen the box to its full-size width.
     */
    private void drawTooltip(GuiGraphics graphics, Component title, int titleColour, List<Line> lines, int mouseX, int mouseY) {
        int boxWidth = font.width(title);
        for (Line line : lines) {
            boxWidth = Math.max(boxWidth, (font.width(line.text) + 1) / 2);
        }
        int boxHeight = lines.isEmpty() ? font.lineHeight - 1 : TOOLTIP_HEAD + (lines.size() - 1) * TOOLTIP_LINE + font.lineHeight / 2;
        int x = mouseX + 6;
        int y = mouseY - 4;
        // Keep it on screen: flip to the cursor's other side near the right edge, lift it near the bottom.
        if (x + boxWidth + 3 > width) {
            x = Math.max(3, mouseX - 6 - boxWidth);
        }
        if (y + boxHeight + 3 > height) {
            y = Math.max(3, height - 3 - boxHeight);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, TOOLTIP_Z);
        graphics.fill(x - 3, y - 3, x + boxWidth + 3, y + boxHeight + 3, TOOLTIP_BACKGROUND);
        graphics.drawString(font, title, x, y, titleColour, true);
        for (int i = 0; i < lines.size(); i++) {
            graphics.pose().pushPose();
            graphics.pose().translate(x, y + TOOLTIP_HEAD + i * TOOLTIP_LINE, 0.0F);
            graphics.pose().scale(0.5F, 0.5F, 1.0F);
            graphics.drawString(font, lines.get(i).text, 0, 0, lines.get(i).colour, true);
            graphics.pose().popPose();
        }
        graphics.pose().popPose();
    }

    /**
     * The name in the enchanting table's script. Vanilla's alt font only has A to Z, and any other
     * character draws as a missing-glyph box, so other letters are swapped for one it has (accented
     * letters lose the accent, other alphabets are mapped by code point) and anything that isn't a
     * letter stays in the normal font.
     */
    private static Component arcaneName(String name) {
        MutableComponent result = Component.empty();
        StringBuilder run = new StringBuilder();
        boolean runIsScript = false;
        int i = 0;
        while (i < name.length()) {
            int c = name.codePointAt(i);
            i += Character.charCount(c);
            boolean script = Character.isLetter(c);
            if (script != runIsScript) {
                appendRun(result, run, runIsScript);
                runIsScript = script;
            }
            run.appendCodePoint(script ? scriptLetter(c) : c);
        }
        appendRun(result, run, runIsScript);
        return result;
    }

    private static void appendRun(MutableComponent result, StringBuilder run, boolean script) {
        if (run.isEmpty()) {
            return;
        }
        MutableComponent part = Component.literal(run.toString());
        result.append(script ? part.withStyle(style -> style.withFont(ARCANE_FONT)) : part);
        run.setLength(0);
    }

    private static int scriptLetter(int c) {
        if (c < 128) {
            return c;
        }
        int base = Normalizer.normalize(Character.toString(c), Normalizer.Form.NFD).codePointAt(0);
        if (base < 128 && Character.isLetter(base)) {
            return base;
        }
        int letter = 'a' + c % 26;
        return Character.isUpperCase(c) ? Character.toUpperCase(letter) : letter;
    }

    private static Component categoryName(Holder.Reference<IResearchCategory> ref) {
        ResourceLocation id = ref.key().location();
        return Component.translatable("research_category." + id.getNamespace() + "." + id.getPath());
    }

    // Search

    private void setSearching(boolean on) {
        searching = on;
        dragging = false;
        searchResults.clear();
        if (searchField == null) {
            return;
        }
        searchField.setVisible(on);
        searchField.setValue("");
        searchField.setFocused(on);
        if (on) {
            setFocused(searchField);
        } else if (getFocused() == searchField) {
            setFocused(null);
        }
    }

    /** Thaumaturge's search: open categories by name, known research by name, and what it teaches to craft. */
    private void onSearchChanged(String query) {
        searchResults.clear();
        if (query == null || query.isEmpty() || minecraft.player == null) {
            return;
        }
        PlayerResearch knowledge = knowledge();
        String needle = query.toLowerCase(Locale.ROOT);
        for (Holder.Reference<IResearchCategory> ref : allCategories) {
            if (!isCategoryUnlocked(knowledge, ref)) {
                continue;
            }
            String name = categoryName(ref).getString();
            if (name.toLowerCase(Locale.ROOT).contains(needle)) {
                searchResults.add(new SearchResult(name, SearchResult.Kind.CATEGORY, null, ref));
            }
        }
        for (ResourceLocation known : knowledge.knownEntries()) {
            EntryNode node = nodesById.get(known);
            if (node == null) {
                continue;
            }
            String entryName = Component.translatable(node.entry.nameKey()).getString();
            if (entryName.toLowerCase(Locale.ROOT).contains(needle)) {
                searchResults.add(new SearchResult(entryName, SearchResult.Kind.ENTRY, node, null));
            }
            int stageCount = node.entry.stages().size();
            int stageIndex = Math.min(stageCount - 1, knowledge.stageOf(known) + 2);
            if (stageIndex < 0 || stageIndex >= stageCount) {
                continue;
            }
            for (ResearchRequirement requirement : node.entry.stages().get(stageIndex).craft()) {
                Optional<Holder<Item>> first = requirement.items().stream().findFirst();
                if (first.isEmpty()) {
                    continue;
                }
                ItemStack stack = new ItemStack(first.get());
                String itemName = stack.getHoverName().getString();
                if (!stack.isEmpty() && itemName.toLowerCase(Locale.ROOT).contains(needle)) {
                    searchResults.add(new SearchResult(itemName, SearchResult.Kind.RECIPE, node, null));
                }
            }
        }
        searchResults.sort(Comparator.comparing(SearchResult::name));
    }

    private int searchRowsShown() {
        return (MAP_HEIGHT - SEARCH_FIRST_ROW) / SEARCH_ROW - 1;
    }

    private void drawSearchResults(GuiGraphics graphics, int mouseX, int mouseY) {
        int rows = searchRowsShown();
        for (int i = 0; i < searchResults.size() && i < rows; i++) {
            SearchResult result = searchResults.get(i);
            int rowY = mapTop() + SEARCH_FIRST_ROW + i * SEARCH_ROW;
            boolean hover = searchRowHit(mouseX, mouseY, i);
            int iconX = mapLeft() + SEARCH_ICON_X;
            graphics.pose().pushPose();
            graphics.pose().translate(iconX, rowY, 0.0F);
            graphics.pose().scale(0.5F, 0.5F, 1.0F);
            switch (result.kind) {
                case RECIPE -> blit(graphics, TTScreenTextures.RESEARCH_BROWSER, 0, 0, (float) SEARCH_RECIPE_U, (float) SEARCH_RECIPE_V, ICON, ICON, TTScreenTextures.TEX_SIZE,
                        TTScreenTextures.TEX_SIZE);
                case ENTRY -> {
                    if (result.node != null) {
                        EntryIconRenderer.drawResearchIcon(graphics, 0, 0, EntryIconRenderer.resolveIcon(result.node.entry, tickCount), false);
                    }
                }
                case CATEGORY -> {
                    if (result.category != null) {
                        blit(graphics, result.category.value().icon(), 0, 0, 0.0F, 0.0F, ICON, ICON, ICON, ICON, ICON, ICON);
                    }
                }
            }
            graphics.pose().popPose();
            graphics.drawString(font, result.name, mapLeft() + SEARCH_TEXT_X, rowY, result.colour(hover), false);
        }
        if (searchResults.size() > rows) {
            graphics.drawString(font, Component.translatable("gui.thaumaturge.thaumonomicon.search_more"), mapLeft() + SEARCH_ICON_X, mapTop() + SEARCH_FIRST_ROW + rows * SEARCH_ROW + 2, SEARCH_OVERFLOW, false);
        }
    }

    private boolean searchRowHit(double mouseX, double mouseY, int row) {
        int rowY = mapTop() + SEARCH_FIRST_ROW + row * SEARCH_ROW;
        return mouseX >= mapLeft() + SEARCH_ICON_X && mouseX < mapLeft() + MAP_WIDTH - SEARCH_ICON_X && mouseY >= rowY && mouseY < rowY + font.lineHeight;
    }

    private @Nullable SearchResult searchResultAt(double mouseX, double mouseY) {
        int rows = searchRowsShown();
        for (int i = 0; i < searchResults.size() && i < rows; i++) {
            if (searchRowHit(mouseX, mouseY, i)) {
                return searchResults.get(i);
            }
        }
        return null;
    }

    // Input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || minecraft.player == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        PlayerResearch knowledge = knowledge();
        if (overSearchTab(mouseX, mouseY)) {
            playSound(TTSounds.CLACK.get(), CLACK_VOLUME);
            setSearching(!searching);
            return true;
        }
        Tab tab = hoveredTab(mouseX, mouseY);
        if (tab != null) {
            if (!tab.category.equals(activeCategory) || searching) {
                playSound(TTSounds.CLACK.get(), CLACK_VOLUME);
                setSearching(false);
                if (!tab.category.equals(activeCategory)) {
                    selectCategory(tab.category, knowledge);
                }
            }
            return true;
        }
        if (searching) {
            SearchResult result = searchResultAt(mouseX, mouseY);
            if (result != null) {
                if (result.kind == SearchResult.Kind.CATEGORY && result.category != null) {
                    playSound(TTSounds.CLACK.get(), CLACK_VOLUME);
                    setSearching(false);
                    selectCategory(result.category, knowledge);
                } else if (result.node != null) {
                    openEntry(result.node);
                }
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        EntryNode node = entryAt(knowledge, mouseX, mouseY);
        if (node != null) {
            if (knowledge.knows(node.id)) {
                knowledge.removeFlag(node.id, ResearchFlag.RESEARCH);
                knowledge.removeFlag(node.id, ResearchFlag.PAGE);
                PacketDistributor.sendToServer(new ServerboundClearResearchFlagsPayload(node.id, List.of(ResearchFlag.RESEARCH, ResearchFlag.PAGE)));
                int stage = knowledge.stageOf(node.id);
                if (stage > 0 && stage >= node.entry.stages().size() - 1) {
                    PacketDistributor.sendToServer(new ServerboundUnlockResearchPayload(node.id));
                }
                openEntry(node);
            } else if (canUnlock(knowledge, node)) {
                PacketDistributor.sendToServer(new ServerboundUnlockResearchPayload(node.id));
                openEntry(node);
            }
            return true;
        }
        if (inMap(mouseX, mouseY)) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** The entry under a point of the window. Where plates overlap, the one drawn last (on top). */
    private @Nullable EntryNode entryAt(PlayerResearch knowledge, double mouseX, double mouseY) {
        if (!inMap(mouseX, mouseY)) {
            return null;
        }
        int scrollX = Mth.floor(mapX);
        int scrollY = Mth.floor(mapY);
        EntryNode found = null;
        for (EntryNode node : activeNodes()) {
            int x = mapLeft() + node.entry.column() * GRID - scrollX;
            int y = mapTop() + node.entry.row() * GRID - scrollY;
            if (mouseX >= x && mouseX <= x + NODE && mouseY >= y && mouseY <= y + NODE && isVisible(knowledge, node)) {
                found = node;
            }
        }
        return found;
    }

    /** The kinds of entry {@link BookVisualCheck} asks to be shown. */
    enum Probe {
        LOCKED, AVAILABLE, COMPLETE, WARP
    }

    /**
     * For the visual check: centres the map on the first visible entry of the open category in the
     * given state and returns that entry's centre on screen, or null when there is none.
     */
    @Nullable int[] focus(Probe wanted) {
        PlayerResearch knowledge = knowledge();
        for (EntryNode node : activeNodes()) {
            if (!isVisible(knowledge, node)) {
                continue;
            }
            boolean complete = knowledge.hasCompleted(node.id);
            boolean unlockable = canUnlock(knowledge, node);
            boolean match = switch (wanted) {
                case LOCKED -> !unlockable;
                case AVAILABLE -> unlockable && !complete;
                case COMPLETE -> complete;
                case WARP -> hasWarp(node.entry);
            };
            if (match) {
                mapX = node.entry.column() * GRID + NODE / 2 - MAP_WIDTH / 2;
                mapY = node.entry.row() * GRID + NODE / 2 - MAP_HEIGHT / 2;
                clampMap();
                return new int[] {mapLeft() + node.entry.column() * GRID - Mth.floor(mapX) + NODE / 2, mapTop() + node.entry.row() * GRID - Mth.floor(mapY) + NODE / 2};
            }
        }
        return null;
    }

    private void openEntry(EntryNode node) {
        persistState();
        playSound(TTSounds.PAGE.get(), PAGE_VOLUME);
        minecraft.setScreen(new EntryDetailScreen(node.holder, node.id, this));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging) {
            mapX -= dx;
            mapY -= dy;
            clampMap();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (overRightColumn(mouseX, mouseY) && maxRightColumnScroll() > 0) {
            rightColumnScroll = Mth.clamp(rightColumnScroll + (scrollY < 0 ? 1 : -1), 0, maxRightColumnScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (searching && searchField != null && searchField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (searching && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            setSearching(false);
            return true;
        }
        if (minecraft.options.keyInventory.matches(keyCode, scanCode) && !(searching && searchField != null && searchField.isFocused())) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void playSound(SoundEvent sound, float volume) {
        if (minecraft.player != null) {
            minecraft.player.playSound(sound, volume, 1.0F);
        }
    }

    // Records

    private record EntryNode(ResourceLocation id, IResearchEntry entry, Holder<IResearchEntry> holder, Holder.Reference<IResearchCategory> category) {
    }

    private record Tab(Holder.Reference<IResearchCategory> category, int x, int y, boolean mirrored) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + TAB && mouseY >= y && mouseY < y + TAB;
        }
    }

    private record Line(Component text, int colour) {
        Line(String text, int colour) {
            this(Component.literal(text), colour);
        }
    }

    private record SearchResult(String name, Kind kind, @Nullable EntryNode node, @Nullable Holder.Reference<IResearchCategory> category) {
        enum Kind {
            CATEGORY, ENTRY, RECIPE
        }

        int colour(boolean hover) {
            return switch (kind) {
                case CATEGORY -> hover ? SEARCH_CATEGORY_HOVER : SEARCH_CATEGORY;
                case ENTRY -> hover ? SEARCH_ENTRY_HOVER : SEARCH_ENTRY;
                case RECIPE -> hover ? SEARCH_RECIPE_HOVER : SEARCH_RECIPE;
            };
        }
    }
}
