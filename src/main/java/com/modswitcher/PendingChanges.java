package com.modswitcher;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Queue of enable/disable requests. Nothing is touched while the game runs: Fabric cannot unload mods,
 * and the jars are in use. When the game exits, each queued jar is renamed to or from "*.jar.disabled".
 * Windows keeps loaded jars locked until the process is gone, so there a tiny helper script finishes the rename.
 */
public final class PendingChanges {
    public enum Action { DISABLE, ENABLE }

    private static final Logger LOGGER = LoggerFactory.getLogger("Mod Switcher");
    private static final String SUFFIX = ".disabled";
    private static final Map<Path, Action> PENDING = new LinkedHashMap<>();
    private static boolean hookRegistered;

    private PendingChanges() {}

    public static synchronized Action get(Path jar) {
        return jar == null ? null : PENDING.get(jar);
    }

    public static synchronized void toggle(Path jar, boolean currentlyLoaded) {
        if (jar == null) {
            return;
        }
        Action wanted = currentlyLoaded ? Action.DISABLE : Action.ENABLE;
        if (PENDING.get(jar) == wanted) {
            PENDING.remove(jar);
        } else {
            PENDING.put(jar, wanted);
        }
        registerHook();
    }

    public static synchronized void clear() {
        PENDING.clear();
    }

    public static synchronized int count() {
        return PENDING.size();
    }

    public static synchronized boolean hasAny() {
        return !PENDING.isEmpty();
    }

    /** Jars that are queued to be disabled. */
    public static synchronized Set<Path> pendingDisabled() {
        Set<Path> out = new HashSet<>();
        for (Map.Entry<Path, Action> e : PENDING.entrySet()) {
            if (e.getValue() == Action.DISABLE) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    private static void registerHook() {
        if (hookRegistered) {
            return;
        }
        hookRegistered = true;
        Runtime.getRuntime().addShutdownHook(new Thread(PendingChanges::applyAll, "ModSwitcher-apply"));
    }

    private static Path targetFor(Path from, Action action) {
        String name = from.getFileName().toString();
        if (action == Action.DISABLE) {
            return from.resolveSibling(name + SUFFIX);
        }
        if (name.endsWith(SUFFIX)) {
            return from.resolveSibling(name.substring(0, name.length() - SUFFIX.length()));
        }
        return null;
    }

    private static void applyAll() {
        Map<Path, Action> snapshot;
        synchronized (PendingChanges.class) {
            snapshot = new LinkedHashMap<>(PENDING);
        }
        if (snapshot.isEmpty()) {
            return;
        }
        List<Path[]> failed = new ArrayList<>();
        for (Map.Entry<Path, Action> e : snapshot.entrySet()) {
            Path from = e.getKey();
            Path to = targetFor(from, e.getValue());
            if (to == null || !Files.exists(from) || Files.exists(to)) {
                LOGGER.warn("Skipping {} (missing source or target already exists)", from);
                continue;
            }
            try {
                Files.move(from, to);
                LOGGER.info("{} -> {}", from.getFileName(), to.getFileName());
            } catch (IOException ex) {
                failed.add(new Path[]{from, to});
            }
        }
        if (!failed.isEmpty() && System.getProperty("os.name", "").toLowerCase().contains("win")) {
            launchWindowsHelper(failed);
        } else if (!failed.isEmpty()) {
            LOGGER.error("Could not rename {} mod file(s)", failed.size());
        }
    }

    /** Waits for the game to exit (jars unlock), then renames, retrying for about a minute. */
    private static void launchWindowsHelper(List<Path[]> renames) {
        try {
            StringBuilder bat = new StringBuilder();
            bat.append("@echo off\r\n").append("setlocal enabledelayedexpansion\r\n");
            bat.append("ping -n 4 127.0.0.1 >nul\r\n");
            int i = 0;
            for (Path[] r : renames) {
                String from = r[0].toAbsolutePath().toString();
                String to = r[1].toAbsolutePath().toString();
                bat.append("set tries=0\r\n");
                bat.append(":retry").append(i).append("\r\n");
                bat.append("move /y \"").append(from).append("\" \"").append(to).append("\" >nul 2>&1\r\n");
                bat.append("if exist \"").append(from).append("\" (\r\n");
                bat.append("  set /a tries+=1\r\n");
                bat.append("  if !tries! lss 20 (\r\n");
                bat.append("    ping -n 3 127.0.0.1 >nul\r\n");
                bat.append("    goto retry").append(i).append("\r\n");
                bat.append("  )\r\n");
                bat.append(")\r\n");
                i++;
            }
            bat.append("del \"%~f0\"\r\n");
            Path script = Files.createTempFile("modswitcher-", ".bat");
            Files.write(script, bat.toString().getBytes(StandardCharsets.UTF_8));
            new ProcessBuilder("cmd.exe", "/c", "start", "", "/min", script.toAbsolutePath().toString()).start();
        } catch (Exception ex) {
            LOGGER.error("Could not start the rename helper", ex);
        }
    }
}
