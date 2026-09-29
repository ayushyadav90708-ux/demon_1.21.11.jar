package com.heaven;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

/** Registers /mine, /takeme, /find, /stop and /heaven. */
public final class CommandManager {
    private CommandManager() {}

    public static void register(HeavenClient heaven) {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            registerMine(dispatcher, heaven);
            registerTakeMe(dispatcher, heaven);
            registerFind(dispatcher, heaven);
            registerStop(dispatcher, heaven);
            registerHeaven(dispatcher, heaven);
        });
    }

    private static boolean inWorld() {
        MinecraftClient c = MinecraftClient.getInstance();
        return c.player != null && c.world != null;
    }

    private static int fail(CommandContext<FabricClientCommandSource> ctx, String message) {
        ctx.getSource().sendError(Text.literal("[Heaven] " + message));
        return 0;
    }

    private static int ok(CommandContext<FabricClientCommandSource> ctx, String message) {
        ctx.getSource().sendFeedback(Text.literal("[Heaven] ").formatted(Formatting.AQUA)
                .append(Text.literal(message).formatted(Formatting.WHITE)));
        return 1;
    }

    // ---- /mine <block> ----
    private static void registerMine(CommandDispatcher<FabricClientCommandSource> d, HeavenClient h) {
        d.register(ClientCommandManager.literal("mine")
                .executes(ctx -> fail(ctx, "Usage: /mine <block>, e.g. /mine diamond_ore"))
                .then(ClientCommandManager.argument("block", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            try {
                                if (!inWorld()) {
                                    return fail(ctx, "You need to be in a world.");
                                }
                                String raw = StringArgumentType.getString(ctx, "block").trim().toLowerCase(Locale.ROOT);
                                Identifier id = Identifier.tryParse(raw.contains(":") ? raw : "minecraft:" + raw);
                                if (id == null || !Registries.BLOCK.containsId(id)) {
                                    return fail(ctx, "Unknown block '" + raw + "'." + suggestBlocks(raw));
                                }
                                Block block = Registries.BLOCK.get(id);
                                if (block == Blocks.AIR) {
                                    return fail(ctx, "Pick a real block to mine.");
                                }
                                h.tasks().startMining(block);
                                return ok(ctx, "Mining " + id + ": I only target blocks I can actually see. /stop to cancel.");
                            } catch (Exception e) {
                                HeavenClient.LOGGER.error("/mine failed", e);
                                return fail(ctx, "Could not start mining: " + e.getMessage());
                            }
                        })));
    }

    private static String suggestBlocks(String raw) {
        if (raw.length() < 3) {
            return "";
        }
        String needle = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;
        List<String> hits = Registries.BLOCK.getIds().stream()
                .filter(i -> i.getPath().contains(needle))
                .map(Identifier::getPath)
                .sorted()
                .limit(6)
                .collect(Collectors.toList());
        return hits.isEmpty() ? "" : " Did you mean: " + String.join(", ", hits) + "?";
    }

    // ---- /takeme <x> <y> <z>  (or /takeme <x> <z>) ----
    private static void registerTakeMe(CommandDispatcher<FabricClientCommandSource> d, HeavenClient h) {
        d.register(ClientCommandManager.literal("takeme")
                .executes(ctx -> fail(ctx, "Usage: /takeme <x> <y> <z>   (or /takeme <x> <z> to ignore height)"))
                .then(ClientCommandManager.argument("x", DoubleArgumentType.doubleArg(-30000000, 30000000))
                        .then(ClientCommandManager.argument("y", DoubleArgumentType.doubleArg(-30000000, 30000000))
                                .executes(ctx -> travel(ctx, h,
                                        DoubleArgumentType.getDouble(ctx, "x"), 0,
                                        DoubleArgumentType.getDouble(ctx, "y"), true))
                                .then(ClientCommandManager.argument("z", DoubleArgumentType.doubleArg(-30000000, 30000000))
                                        .executes(ctx -> travel(ctx, h,
                                                DoubleArgumentType.getDouble(ctx, "x"),
                                                DoubleArgumentType.getDouble(ctx, "y"),
                                                DoubleArgumentType.getDouble(ctx, "z"), false))))));
    }

    private static int travel(CommandContext<FabricClientCommandSource> ctx, HeavenClient h,
                              double x, double y, double z, boolean horizontalOnly) {
        try {
            if (!inWorld()) {
                return fail(ctx, "You need to be in a world.");
            }
            int bx = (int) Math.floor(x);
            int bz = (int) Math.floor(z);
            int by = horizontalOnly ? MinecraftClient.getInstance().player.getBlockY() : (int) Math.floor(y);
            h.tasks().startTravel(bx, by, bz, horizontalOnly);
            return ok(ctx, "Travelling to " + h.tasks().targetText() + ". /stop to cancel.");
        } catch (Exception e) {
            HeavenClient.LOGGER.error("/takeme failed", e);
            return fail(ctx, "Could not start travelling: " + e.getMessage());
        }
    }

    // ---- /find <target> ----
    private static void registerFind(CommandDispatcher<FabricClientCommandSource> d, HeavenClient h) {
        d.register(ClientCommandManager.literal("find")
                .executes(ctx -> fail(ctx, "Usage: /find <biome or structure>, e.g. /find village"))
                .then(ClientCommandManager.argument("target", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            try {
                                if (!inWorld()) {
                                    return fail(ctx, "You need to be in a world.");
                                }
                                h.find().find(StringArgumentType.getString(ctx, "target"));
                                return 1;
                            } catch (Exception e) {
                                HeavenClient.LOGGER.error("/find failed", e);
                                return fail(ctx, "Search failed: " + e.getMessage());
                            }
                        })));
    }

    // ---- /stop ----
    private static void registerStop(CommandDispatcher<FabricClientCommandSource> d, HeavenClient h) {
        d.register(ClientCommandManager.literal("stop").executes(ctx -> {
            boolean was = h.tasks().isActive() || h.find().isWaiting();
            h.tasks().stop(null);
            h.find().cancel();
            return ok(ctx, was ? "Stopped." : "Nothing was running.");
        }));
    }

    // ---- /heaven ... ----
    private static void registerHeaven(CommandDispatcher<FabricClientCommandSource> d, HeavenClient h) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommandManager.literal("heaven")
                .executes(ctx -> help(ctx))
                .then(ClientCommandManager.literal("help").executes(ctx -> help(ctx)))
                .then(ClientCommandManager.literal("status").executes(ctx -> status(ctx, h)));

        root.then(toggle("food", h, (cfg, v) -> cfg.autoFood = v));
        root.then(toggle("defense", h, (cfg, v) -> cfg.autoDefense = v));
        root.then(toggle("boat", h, (cfg, v) -> cfg.boatAssist = v));
        root.then(toggle("elytra", h, (cfg, v) -> cfg.elytraAssist = v));
        d.register(root);
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> toggle(String name, HeavenClient h,
                                                                           BiConsumer<HeavenConfig, Boolean> setter) {
        return ClientCommandManager.literal(name)
                .then(ClientCommandManager.literal("on").executes(ctx -> {
                    setter.accept(h.config(), true);
                    return ok(ctx, name + " assistance enabled.");
                }))
                .then(ClientCommandManager.literal("off").executes(ctx -> {
                    setter.accept(h.config(), false);
                    return ok(ctx, name + " assistance disabled.");
                }));
    }

    private static int help(CommandContext<FabricClientCommandSource> ctx) {
        ok(ctx, "Heaven " + HeavenClient.VERSION + " commands:");
        String[] lines = {
                "/mine <block>  - walk to and mine visible blocks of that type",
                "/takeme <x> <y> <z>  (or <x> <z>)  - navigate to coordinates",
                "/find <biome|structure>  - locate a biome or structure",
                "/stop  - stop Heaven's current task",
                "/heaven status  - show current task and state",
                "/heaven food|defense|boat|elytra on|off  - toggle assistance"
        };
        for (String l : lines) {
            ctx.getSource().sendFeedback(Text.literal("  " + l).formatted(Formatting.GRAY));
        }
        return 1;
    }

    private static int status(CommandContext<FabricClientCommandSource> ctx, HeavenClient h) {
        TaskManager t = h.tasks();
        ok(ctx, "Task: " + (t.isActive() ? t.type().label : (h.find().isWaiting() ? "Finding" : "Idle"))
                + " | State: " + t.detailText());
        String[] lines = {
                "Target: " + t.targetText(),
                "Destination: " + t.destinationText(),
                "Food: " + h.food().status(),
                "Combat: " + h.combat().status(),
                "Boat: " + (h.config().boatAssist ? h.boat().stateText() : "Off")
                        + " | Elytra: " + (h.config().elytraAssist ? h.elytra().stateText() : "Off")
        };
        for (String l : lines) {
            ctx.getSource().sendFeedback(Text.literal("  " + l).formatted(Formatting.GRAY));
        }
        return 1;
    }
}
