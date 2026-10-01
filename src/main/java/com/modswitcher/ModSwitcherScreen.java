package com.modswitcher;

import java.util.List;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/** Paged list of mods with queue-for-next-launch enable/disable buttons. */
public class ModSwitcherScreen extends Screen {
    private static final int ROW_HEIGHT = 30;
    private static final int ROW_WIDTH = 320;
    private static final int LIST_TOP = 70;

    private final Screen parent;
    private boolean showDisabledView;
    private boolean showLibraries;
    private int page;
    private int perPage = 4;
    private List<ModEntry> entries = List.of();

    public ModSwitcherScreen(Screen parent) {
        super(Text.literal("Mod Switcher"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        entries = showDisabledView ? ModScanner.disabledMods() : ModScanner.loadedMods(showLibraries);
        perPage = MathHelper.clamp((height - LIST_TOP - 62) / ROW_HEIGHT, 2, 8);
        int pages = pageCount();
        page = MathHelper.clamp(page, 0, pages - 1);

        int left = width / 2 - ROW_WIDTH / 2;
        int first = page * perPage;
        int last = Math.min(entries.size(), first + perPage);
        for (int i = first; i < last; i++) {
            ModEntry entry = entries.get(i);
            int y = LIST_TOP + (i - first) * ROW_HEIGHT;
            PendingChanges.Action pending = PendingChanges.get(entry.jar());
            String label;
            if (!entry.toggleable()) {
                label = "Locked";
            } else if (pending != null) {
                label = "Undo";
            } else {
                label = entry.loaded() ? "Disable" : "Enable";
            }
            ButtonWidget toggle = ButtonWidget.builder(Text.literal(label), b -> {
                PendingChanges.toggle(entry.jar(), entry.loaded());
                clearAndInit();
            }).dimensions(left + ROW_WIDTH - 80, y, 80, 20).build();
            if (!entry.toggleable()) {
                toggle.active = false;
                if (entry.blockedReason() != null) {
                    toggle.setTooltip(Tooltip.of(Text.literal(entry.blockedReason())));
                }
            }
            addDrawableChild(toggle);
        }

        int navY = height - 52;
        ButtonWidget prev = ButtonWidget.builder(Text.literal("<"), b -> {
            page--;
            clearAndInit();
        }).dimensions(width / 2 - 70, navY, 40, 20).build();
        prev.active = page > 0;
        addDrawableChild(prev);

        ButtonWidget next = ButtonWidget.builder(Text.literal(">"), b -> {
            page++;
            clearAndInit();
        }).dimensions(width / 2 + 30, navY, 40, 20).build();
        next.active = page < pages - 1;
        addDrawableChild(next);

        if (PendingChanges.hasAny()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Quit Game"), b -> client.scheduleStop())
                    .dimensions(left, navY, 110, 20)
                    .tooltip(Tooltip.of(Text.literal("Closes Minecraft now so the queued changes are applied. Start it again afterwards.")))
                    .build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Clear Changes"), b -> {
                PendingChanges.clear();
                clearAndInit();
            }).dimensions(left + ROW_WIDTH - 110, navY, 110, 20).build());
        }

        int bottomY = height - 28;
        addDrawableChild(ButtonWidget.builder(Text.literal(showDisabledView ? "View: Disabled" : "View: Loaded"), b -> {
            showDisabledView = !showDisabledView;
            page = 0;
            clearAndInit();
        }).dimensions(left, bottomY, 104, 20)
                .tooltip(Tooltip.of(Text.literal("Switch between mods loaded now and jars already disabled in the mods folder.")))
                .build());

        if (!showDisabledView) {
            addDrawableChild(ButtonWidget.builder(Text.literal(showLibraries ? "Libraries: Shown" : "Libraries: Hidden"), b -> {
                showLibraries = !showLibraries;
                page = 0;
                clearAndInit();
            }).dimensions(left + 108, bottomY, 104, 20)
                    .tooltip(Tooltip.of(Text.literal("Show built-in and bundled library mods (they are always locked).")))
                    .build());
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(left + ROW_WIDTH - 104, bottomY, 104, 20).build());
    }

    private int pageCount() {
        return Math.max(1, (entries.size() + perPage - 1) / perPage);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Restart required: changes only take effect after you close and relaunch Minecraft."),
                width / 2, 25, 0xFFFFAA00);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Fabric cannot safely unload mods mid-session, so nothing changes in this game."),
                width / 2, 37, 0xFFFFAA00);
        if (PendingChanges.hasAny()) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.literal(PendingChanges.count() + " change(s) queued - applied when the game closes."),
                    width / 2, 52, 0xFF55FF55);
        }

        int left = width / 2 - ROW_WIDTH / 2;
        int first = page * perPage;
        int last = Math.min(entries.size(), first + perPage);
        if (entries.isEmpty()) {
            String empty = showDisabledView
                    ? "No disabled mods (*.jar.disabled) in the mods folder."
                    : "No mods to show.";
            context.drawCenteredTextWithShadow(textRenderer, Text.literal(empty), width / 2, LIST_TOP + 20, 0xFFAAAAAA);
        }
        for (int i = first; i < last; i++) {
            ModEntry entry = entries.get(i);
            int y = LIST_TOP + (i - first) * ROW_HEIGHT;
            String name = textRenderer.trimToWidth(entry.name(), ROW_WIDTH - 90);
            context.drawTextWithShadow(textRenderer, name, left, y, 0xFFFFFFFF);

            PendingChanges.Action pending = PendingChanges.get(entry.jar());
            String sub;
            int color;
            if (pending == PendingChanges.Action.DISABLE) {
                sub = "Will be disabled after restart";
                color = 0xFFFFAA00;
            } else if (pending == PendingChanges.Action.ENABLE) {
                sub = "Will be enabled after restart";
                color = 0xFFFFAA00;
            } else {
                String version = entry.version().isEmpty() ? "" : "  v" + entry.version();
                sub = entry.id() + version;
                color = 0xFFAAAAAA;
            }
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(sub, ROW_WIDTH - 90), left, y + 11, color);
        }

        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal((page + 1) + " / " + pageCount()), width / 2, height - 46, 0xFFFFFFFF);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
