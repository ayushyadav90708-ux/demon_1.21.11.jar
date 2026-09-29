package com.heaven;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Heaven: client-side automation and navigation assistant. */
public class HeavenClient implements ClientModInitializer {
    public static final String MOD_ID = "heaven";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LoggerFactory.getLogger("Heaven");

    private static HeavenClient instance;

    private final HeavenConfig config = new HeavenConfig();
    private final InputController input = new InputController();
    private final NavigationManager navigation = new NavigationManager();
    private final FoodManager food = new FoodManager(this);
    private final CombatManager combat = new CombatManager(this);
    private final MiningManager mining = new MiningManager(this);
    private final BoatManager boat = new BoatManager(this);
    private final ElytraManager elytra = new ElytraManager(this);
    private final FindManager find = new FindManager(this);
    private final TaskManager tasks = new TaskManager(this);

    public static HeavenClient get() { return instance; }

    public HeavenConfig config() { return config; }
    public InputController input() { return input; }
    public NavigationManager navigation() { return navigation; }
    public FoodManager food() { return food; }
    public CombatManager combat() { return combat; }
    public MiningManager mining() { return mining; }
    public BoatManager boat() { return boat; }
    public ElytraManager elytra() { return elytra; }
    public FindManager find() { return find; }
    public TaskManager tasks() { return tasks; }

    @Override
    public void onInitializeClient() {
        instance = this;

        CommandManager.register(this);
        HudManager.register(this);

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                find.onGameMessage(message);
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            tasks.stop(null);
            find.cancel();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                find.tick(client);
                tasks.tick(client);
            } catch (Exception e) {
                LOGGER.error("Heaven task crashed, stopping it", e);
                tasks.stop(null);
                Msg.error("Heaven hit an unexpected error and stopped: " + e.getClass().getSimpleName());
            }
        });

        LOGGER.info("Heaven {} loaded", VERSION);
    }
}
