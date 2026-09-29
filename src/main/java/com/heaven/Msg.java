package com.heaven;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Chat feedback helpers. */
public final class Msg {
    private Msg() {}

    public static void info(String message) {
        send(Text.literal("[Heaven] ").formatted(Formatting.AQUA)
                .append(Text.literal(message).formatted(Formatting.WHITE)));
    }

    public static void error(String message) {
        send(Text.literal("[Heaven] ").formatted(Formatting.AQUA)
                .append(Text.literal(message).formatted(Formatting.RED)));
    }

    private static void send(Text text) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.player != null) {
            client.player.sendMessage(text, false);
        }
    }
}
