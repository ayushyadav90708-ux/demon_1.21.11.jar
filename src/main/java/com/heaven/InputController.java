package com.heaven;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;

/**
 * Central place that turns Heaven's movement decisions into ordinary key presses,
 * exactly like a player holding the keys. Nothing here touches packets.
 */
public final class InputController {
    private boolean forward;
    private boolean back;
    private boolean left;
    private boolean right;
    private boolean jump;
    private boolean sneak;
    private boolean sprint;
    private boolean use;
    private boolean controlling;

    public void begin() {
        forward = false;
        back = false;
        left = false;
        right = false;
        jump = false;
        sneak = false;
        sprint = false;
        use = false;
    }

    public void forward(boolean v) { forward = v; }
    public void back(boolean v) { back = v; }
    public void left(boolean v) { left = v; }
    public void right(boolean v) { right = v; }
    public void jump(boolean v) { jump = v; }
    public void sneak(boolean v) { sneak = v; }
    public void sprint(boolean v) { sprint = v; }
    public void use(boolean v) { use = v; }

    public void apply(MinecraftClient client) {
        GameOptions o = client.options;
        o.forwardKey.setPressed(forward);
        o.backKey.setPressed(back);
        o.leftKey.setPressed(left);
        o.rightKey.setPressed(right);
        o.jumpKey.setPressed(jump);
        o.sneakKey.setPressed(sneak);
        o.sprintKey.setPressed(sprint);
        o.useKey.setPressed(use);
        controlling = true;
    }

    /** Let go of every key Heaven was holding (only once, so real key presses are not disturbed). */
    public void release(MinecraftClient client) {
        if (!controlling) {
            return;
        }
        begin();
        GameOptions o = client.options;
        o.forwardKey.setPressed(false);
        o.backKey.setPressed(false);
        o.leftKey.setPressed(false);
        o.rightKey.setPressed(false);
        o.jumpKey.setPressed(false);
        o.sneakKey.setPressed(false);
        o.sprintKey.setPressed(false);
        o.useKey.setPressed(false);
        controlling = false;
    }
}
