package com.heaven;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

/**
 * /find framework. It only uses information a normal client legitimately has:
 *  - biomes: sampled from chunks that are already loaded around the player;
 *  - structures/other biomes: asks the server through the vanilla /locate command
 *    (which the server permits or refuses like for any player) and reads the chat reply.
 */
public class FindManager {
    private static final Pattern COORDS = Pattern.compile("\\[(-?\\d+), (~|-?\\d+), (-?\\d+)\\]");
    private static final int WAIT_TICKS = 200;

    /** Friendly names -> vanilla /locate structure argument. */
    private static final Map<String, String> STRUCTURES = Map.ofEntries(
            Map.entry("village", "#minecraft:village"),
            Map.entry("mineshaft", "#minecraft:mineshaft"),
            Map.entry("shipwreck", "#minecraft:shipwreck"),
            Map.entry("ocean_ruin", "#minecraft:ocean_ruin"),
            Map.entry("ruined_portal", "#minecraft:ruined_portal"),
            Map.entry("stronghold", "minecraft:stronghold"),
            Map.entry("fortress", "minecraft:fortress"),
            Map.entry("bastion", "minecraft:bastion_remnant"),
            Map.entry("bastion_remnant", "minecraft:bastion_remnant"),
            Map.entry("ancient_city", "minecraft:ancient_city"),
            Map.entry("monument", "minecraft:monument"),
            Map.entry("ocean_monument", "minecraft:monument"),
            Map.entry("mansion", "minecraft:mansion"),
            Map.entry("woodland_mansion", "minecraft:mansion"),
            Map.entry("outpost", "minecraft:pillager_outpost"),
            Map.entry("pillager_outpost", "minecraft:pillager_outpost"),
            Map.entry("desert_pyramid", "minecraft:desert_pyramid"),
            Map.entry("jungle_pyramid", "minecraft:jungle_pyramid"),
            Map.entry("jungle_temple", "minecraft:jungle_pyramid"),
            Map.entry("igloo", "minecraft:igloo"),
            Map.entry("swamp_hut", "minecraft:swamp_hut"),
            Map.entry("buried_treasure", "minecraft:buried_treasure"),
            Map.entry("end_city", "minecraft:end_city"),
            Map.entry("trail_ruins", "minecraft:trail_ruins"),
            Map.entry("trial_chambers", "minecraft:trial_chambers"),
            Map.entry("nether_fossil", "minecraft:nether_fossil"));

    private final HeavenClient heaven;
    private boolean waiting;
    private int waitTicks;
    private String waitingFor = "";
    private String pendingCommand;

    public FindManager(HeavenClient heaven) {
        this.heaven = heaven;
    }

    public boolean isWaiting() { return waiting; }
    public String waitingFor() { return waitingFor; }

    public void cancel() {
        waiting = false;
        waitingFor = "";
    }

    /** Called from the message event for every incoming game chat line. */
    public void onGameMessage(Text message) {
        if (!waiting) {
            return;
        }
        Matcher m = COORDS.matcher(message.getString());
        if (!m.find()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity p = client.player;
        int x = Integer.parseInt(m.group(1));
        int z = Integer.parseInt(m.group(3));
        String y = m.group(2);
        waiting = false;
        if (p != null) {
            long dist = Math.round(Math.sqrt(Math.pow(x - p.getX(), 2) + Math.pow(z - p.getZ(), 2)));
            Msg.info("Found " + waitingFor + " at x=" + x + ", z=" + z + (y.equals("~") ? "" : ", y=" + y)
                    + " (~" + dist + " blocks). Travel there with /takeme " + x + " "
                    + (y.equals("~") ? "" : y + " ") + z);
        }
    }

    public void tick(MinecraftClient client) {
        if (pendingCommand != null && client.player != null) {
            client.player.networkHandler.sendChatCommand(pendingCommand);
            pendingCommand = null;
        }
        if (waiting && --waitTicks <= 0) {
            waiting = false;
            Msg.error("No location came back for " + waitingFor
                    + ". /locate may be unavailable here (needs permission), or nothing was found.");
        }
    }

    public void find(String rawTarget) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity p = client.player;
        ClientWorld w = client.world;
        if (p == null || w == null) {
            Msg.error("You need to be in a world to use /find.");
            return;
        }
        String query = rawTarget.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (query.isEmpty()) {
            Msg.error("Usage: /find <biome or structure>, e.g. /find village or /find cherry_grove");
            return;
        }
        String structureKey = query.startsWith("minecraft:") ? query.substring(10) : query;

        // 1) Biome in loaded chunks.
        Identifier biomeId = Identifier.tryParse(query.contains(":") ? query : "minecraft:" + query);
        Registry<Biome> biomes = w.getRegistryManager().getOrThrow(RegistryKeys.BIOME);
        if (biomeId != null && biomes.containsId(biomeId)) {
            BlockPos found = scanLoadedBiome(client, w, p, biomeId);
            if (found != null) {
                long dist = Math.round(Math.sqrt(p.squaredDistanceTo(found.getX() + 0.5, p.getY(), found.getZ() + 0.5)));
                Msg.info("Found " + biomeId + " at x=" + found.getX() + ", y=" + found.getY() + ", z="
                        + found.getZ() + " (~" + dist + " blocks). Travel there with /takeme "
                        + found.getX() + " " + found.getY() + " " + found.getZ());
            } else {
                Msg.info(biomeId + " is not inside your loaded chunks. Asking the server via /locate biome...");
                startLocate("locate biome " + biomeId, biomeId.toString());
            }
            return;
        }

        // 2) Structures: use /locate.
        String locateArg = STRUCTURES.get(structureKey);
        if (locateArg == null && structureKey.contains(":") ) {
            locateArg = structureKey;
        }
        if (locateArg != null) {
            startLocate("locate structure " + locateArg, structureKey);
            return;
        }

        Msg.error("Unknown target '" + rawTarget.trim() + "'. Try a biome id (plains, cherry_grove, desert...) "
                + "or a structure like: " + String.join(", ", Set.of("village", "stronghold", "mansion",
                "monument", "ancient_city", "fortress")) + ".");
    }

    private void startLocate(String command, String label) {
        waiting = true;
        waitTicks = WAIT_TICKS;
        waitingFor = label;
        pendingCommand = command;
    }

    private BlockPos scanLoadedBiome(MinecraftClient client, ClientWorld w, ClientPlayerEntity p, Identifier id) {
        int viewDistance = client.options.getViewDistance().getValue();
        int radius = Math.min(viewDistance * 16, 256);
        int[] ys = {p.getBlockY(), 63};
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx += 8) {
            for (int dz = -radius; dz <= radius; dz += 8) {
                for (int y : ys) {
                    BlockPos pos = new BlockPos(p.getBlockX() + dx, y, p.getBlockZ() + dz);
                    if (!WorldUtil.isLoaded(w, pos)) {
                        continue;
                    }
                    if (w.getBiome(pos).matchesId(id)) {
                        double d = (double) dx * dx + (double) dz * dz;
                        if (d < bestDist) {
                            bestDist = d;
                            best = pos;
                        }
                    }
                }
            }
        }
        return best;
    }
}
