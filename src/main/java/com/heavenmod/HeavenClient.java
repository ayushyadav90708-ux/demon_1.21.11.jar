package com.heavenmod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/**
 * Heaven 1.1.0. Uses # commands intercepted locally so they are not sent to chat.
 * This implementation deliberately avoids through-wall/X-ray mining and automated PvP.
 */
public final class HeavenClient implements ClientModInitializer {
    private static final HeavenTask TASK = new HeavenTask();

    @Override public void onInitializeClient() {
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            if (!message.startsWith("#")) return true;
            TASK.command(message.substring(1).trim());
            return false;
        });
        ClientTickEvents.END_CLIENT_TICK.register(TASK::tick);
    }

    static final class HeavenTask {
        enum Mode { IDLE, MINE, TRAVEL, COMBAT }
        private Mode mode = Mode.IDLE;
        private Mode resumeMode = Mode.IDLE;
        private String mineId;
        private Vec3d destination;
        private HostileEntity threat;
        private int cooldown;

        void command(String raw) {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c.player == null) return;
            String[] p = raw.split("\\s+");
            if (p.length == 0) return;
            switch (p[0].toLowerCase(Locale.ROOT)) {
                case "mine" -> { if (p.length < 2) { msg("Usage: #mine <block>"); return; }
                    Identifier id = Identifier.tryParse(p[1].contains(":") ? p[1] : "minecraft:" + p[1]);
                    if (id == null || !Registries.BLOCK.containsId(id)) { msg("Unknown block: " + p[1]); return; }
                    mineId = id.toString(); mode = Mode.MINE; msg("Mining: " + mineId); }
                case "takeme" -> { if (p.length != 4) { msg("Usage: #takeme <x> <y> <z>"); return; }
                    try { destination = new Vec3d(Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3])); mode = Mode.TRAVEL; msg("Travelling to " + p[1] + " " + p[2] + " " + p[3]); }
                    catch (NumberFormatException e) { msg("Invalid coordinates."); } }
                case "find" -> msg("#find received: " + raw.substring(Math.min(raw.length(), 5)).trim() + ". Search integration can be extended with vanilla locator data.");
                case "fight" -> msg("Player PvP automation is not enabled in this build. Heaven can defend against hostile mobs.");
                case "stop" -> stop();
                case "status" -> msg("Mode=" + mode + (mineId != null ? " target=" + mineId : "") + (destination != null ? " destination=" + destination : ""));
                default -> msg("Unknown command. Try #mine, #find, #takeme, #stop, #status.");
            }
        }

        void tick(MinecraftClient c) {
            if (c.player == null || c.world == null) return;
            if (cooldown > 0) cooldown--;
            autoEat(c);
            HostileEntity h = nearestThreat(c);
            if (h != null && (mode == Mode.MINE || mode == Mode.TRAVEL || mode == Mode.IDLE) && h.squaredDistanceTo(c.player) < 16.0) {
                if (mode != Mode.COMBAT) { resumeMode = mode; mode = Mode.COMBAT; msg("Threat detected: pausing current task."); }
                threat = h;
            }
            if (mode == Mode.COMBAT) combatStep(c);
            else if (mode == Mode.TRAVEL) travelStep(c.player);
            else if (mode == Mode.MINE) mineStep(c);
        }

        private void travelStep(ClientPlayerEntity p) {
            if (destination == null) { stop(); return; }
            Vec3d d = destination.subtract(p.getPos());
            if (d.horizontalLength() < 1.8 && Math.abs(d.y) < 2.5) { msg("Destination reached."); stop(); return; }
            float yaw = (float)(Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
            p.setYaw(yaw); p.setHeadYaw(yaw);
            if (p.isOnGround() && Math.abs(d.y) > 0.5) p.jump();
        }

        private void mineStep(MinecraftClient c) {
            if (mineId == null || cooldown > 0) return;
            BlockPos origin = c.player.getBlockPos();
            BlockPos best = null; double bestD = Double.MAX_VALUE;
            for (int dx=-5; dx<=5; dx++) for (int dy=-3; dy<=3; dy++) for (int dz=-5; dz<=5; dz++) {
                BlockPos pos = origin.add(dx,dy,dz);
                BlockState s = c.world.getBlockState(pos);
                if (!Registries.BLOCK.getId(s.getBlock()).toString().equals(mineId)) continue;
                if (!c.world.getBlockState(pos).isAir() && visible(c,pos)) {
                    double d = pos.getSquaredDistance(c.player.getPos());
                    if (d < bestD) { bestD=d; best=pos; }
                }
            }
            if (best == null) return;
            lookAt(c.player, Vec3d.ofCenter(best));
            c.options.attackKey.setPressed(true);
            cooldown=8;
        }

        private void combatStep(MinecraftClient c) {
            if (threat == null || !threat.isAlive() || threat.squaredDistanceTo(c.player) > 25.0) {
                c.options.attackKey.setPressed(false); threat=null; mode=resumeMode; msg("Threat cleared; resuming " + mode + "."); return;
            }
            lookAt(c.player, threat.getEyePos());
            if (threat.squaredDistanceTo(c.player) < 9.0) c.options.attackKey.setPressed(true);
        }

        private boolean visible(MinecraftClient c, BlockPos p) {
            var hit = c.world.raycast(new net.minecraft.world.RaycastContext(c.player.getEyePos(), Vec3d.ofCenter(p), net.minecraft.world.RaycastContext.ShapeType.OUTLINE, net.minecraft.world.RaycastContext.FluidHandling.NONE, c.player));
            return hit.getBlockPos().equals(p);
        }

        private HostileEntity nearestThreat(MinecraftClient c) {
            HostileEntity best=null; double d=Double.MAX_VALUE;
            for (HostileEntity e : c.world.getEntitiesByClass(HostileEntity.class, c.player.getBoundingBox().expand(4.0), x -> x.isAlive())) {
                double x=e.squaredDistanceTo(c.player); if (x<d) {d=x; best=e;}
            }
            return best;
        }

        private void autoEat(MinecraftClient c) {
            if (c.player.getHungerManager().getFoodLevel() > 8 || c.player.isUsingItem()) { if (!c.player.isUsingItem()) c.options.useKey.setPressed(false); return; }
            for (int i=0;i<9;i++) if (c.player.getInventory().getStack(i).getItem().getFoodComponent()!=null) { c.player.getInventory().setSelectedSlot(i); c.options.useKey.setPressed(true); return; }
        }

        private void lookAt(ClientPlayerEntity p, Vec3d target) {
            Vec3d d=target.subtract(p.getEyePos()); double h=Math.sqrt(d.x*d.x+d.z*d.z);
            p.setYaw((float)(Math.toDegrees(Math.atan2(d.z,d.x))-90)); p.setPitch((float)-Math.toDegrees(Math.atan2(d.y,h))); p.setHeadYaw(p.getYaw());
        }

        private void stop() {
            mode=Mode.IDLE; resumeMode=Mode.IDLE; mineId=null; destination=null; threat=null;
            MinecraftClient c=MinecraftClient.getInstance(); if(c.player!=null){c.options.attackKey.setPressed(false);c.options.useKey.setPressed(false);}
            msg("Stopped.");
        }
        private void msg(String s){ MinecraftClient c=MinecraftClient.getInstance(); if(c.player!=null)c.player.sendMessage(Text.literal("§b[Heaven] §f"+s),false); }
    }
}
