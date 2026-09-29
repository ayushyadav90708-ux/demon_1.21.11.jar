package com.heaven;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Identifier;

/** Small status panel in the top-left corner. */
public class HudManager implements HudElement {
    private final HeavenClient heaven;

    public HudManager(HeavenClient heaven) {
        this.heaven = heaven;
    }

    public static void register(HeavenClient heaven) {
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS,
                Identifier.of(HeavenClient.MOD_ID, "hud"), new HudManager(heaven));
    }

    @Override
    public void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.options.hudHidden) {
            return;
        }
        TaskManager tasks = heaven.tasks();
        boolean active = tasks.isActive();

        List<String> lines = new ArrayList<>();
        lines.add("HEAVEN");
        lines.add("Task: " + (active ? tasks.type().label : (heaven.find().isWaiting() ? "Finding" : "Idle")));
        lines.add("Target: " + (active ? tasks.targetText() : (heaven.find().isWaiting()
                ? heaven.find().waitingFor() : "-")));
        if (tasks.type() == TaskManager.TaskType.TRAVELLING) {
            lines.add("Destination: " + tasks.destinationText());
        }
        lines.add("Status: " + (active ? "Active (" + tasks.detailText() + ")" : "Idle"));
        lines.add("Food: " + heaven.food().status());
        lines.add("Combat: " + heaven.combat().status());

        TextRenderer tr = client.textRenderer;
        int x = 6;
        int y = 6;
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, tr.getWidth(line));
        }
        int lineHeight = 10;
        context.fill(x - 3, y - 3, x + width + 3, y + lines.size() * lineHeight + 1, 0x90000000);
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? 0xFF55FFFF : (active ? 0xFFFFFFFF : 0xFFAAAAAA);
            context.drawTextWithShadow(tr, lines.get(i), x, y + i * lineHeight, color);
        }
    }
}
