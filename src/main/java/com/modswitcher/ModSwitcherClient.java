package com.modswitcher;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Adds a "Mod Switcher" button to the title screen and the pause menu. */
public class ModSwitcherClient implements ClientModInitializer {
    public static final String MOD_ID = "modswitcher";
    public static final Logger LOGGER = LoggerFactory.getLogger("Mod Switcher");

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof TitleScreen || screen instanceof GameMenuScreen) {
                ButtonWidget button = ButtonWidget.builder(Text.literal("Mod Switcher"),
                                b -> client.setScreen(new ModSwitcherScreen(screen)))
                        .dimensions(6, 6, 110, 20)
                        .build();
                Screens.getButtons(screen).add(button);
            }
        });
        LOGGER.info("Mod Switcher loaded");
    }
}
