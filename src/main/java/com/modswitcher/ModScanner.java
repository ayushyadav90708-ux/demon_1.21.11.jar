package com.modswitcher;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.ModOrigin;

/** Collects the mods to show. */
public final class ModScanner {
    private static final String DISABLED_SUFFIX = ".jar.disabled";

    private ModScanner() {}

    public static Path modsDir() {
        return FabricLoader.getInstance().getGameDir().resolve("mods");
    }

    private static ModContainer topLevel(ModContainer c) {
        ModContainer cur = c;
        while (true) {
            Optional<ModContainer> parent = cur.getContainingMod();
            if (parent.isEmpty()) {
                return cur;
            }
            cur = parent.get();
        }
    }

    private static Path singleJar(ModContainer c) {
        ModOrigin origin = c.getOrigin();
        if (origin.getKind() != ModOrigin.Kind.PATH) {
            return null;
        }
        List<Path> paths = origin.getPaths();
        if (paths.size() != 1) {
            return null;
        }
        Path p = paths.get(0).toAbsolutePath().normalize();
        if (!Files.isRegularFile(p) || !p.getFileName().toString().endsWith(".jar")) {
            return null;
        }
        if (!p.getParent().equals(modsDir().toAbsolutePath().normalize())) {
            return null;
        }
        return p;
    }

    /** Names of loaded mods that still need {@code target} (ignoring mods that are queued to be disabled). */
    private static List<String> requiredBy(ModContainer target, Set<Path> pendingDisabled) {
        Set<String> ids = new HashSet<>();
        ids.add(target.getMetadata().getId());
        ids.addAll(target.getMetadata().getProvides());
        List<String> out = new ArrayList<>();
        for (ModContainer other : FabricLoader.getInstance().getAllMods()) {
            ModContainer top = topLevel(other);
            if (top == topLevel(target)) {
                continue;
            }
            Path topJar = singleJar(top);
            if (topJar != null && pendingDisabled.contains(topJar)) {
                continue;
            }
            for (ModDependency dep : other.getMetadata().getDependencies()) {
                if (dep.getKind() == ModDependency.Kind.DEPENDS && ids.contains(dep.getModId())) {
                    String name = top.getMetadata().getName();
                    if (!out.contains(name)) {
                        out.add(name);
                    }
                }
            }
        }
        return out;
    }

    /** Mods (queued for disabling) that {@code c} or its bundled modules depend on. */
    private static List<String> queuedDependencies(ModContainer c, Set<Path> pendingDisabled) {
        List<String> out = new ArrayList<>();
        for (ModContainer part : FabricLoader.getInstance().getAllMods()) {
            if (topLevel(part) != c) {
                continue;
            }
            for (ModDependency dep : part.getMetadata().getDependencies()) {
                if (dep.getKind() != ModDependency.Kind.DEPENDS) {
                    continue;
                }
                for (ModContainer candidate : FabricLoader.getInstance().getAllMods()) {
                    ModContainer top = topLevel(candidate);
                    if (top == c) {
                        continue;
                    }
                    Path topJar = singleJar(top);
                    if (topJar == null || !pendingDisabled.contains(topJar)) {
                        continue;
                    }
                    ModMetadata cm = candidate.getMetadata();
                    if (cm.getId().equals(dep.getModId()) || cm.getProvides().contains(dep.getModId())) {
                        String name = top.getMetadata().getName();
                        if (!out.contains(name)) {
                            out.add(name);
                        }
                    }
                }
            }
        }
        return out;
    }

    public static List<ModEntry> loadedMods(boolean showLibraries) {
        Set<Path> pendingDisabled = PendingChanges.pendingDisabled();
        List<ModEntry> out = new ArrayList<>();
        for (ModContainer c : FabricLoader.getInstance().getAllMods()) {
            ModMetadata md = c.getMetadata();
            boolean nested = c.getContainingMod().isPresent();
            boolean builtin = "builtin".equals(md.getType());
            if (!showLibraries && (nested || builtin)) {
                continue;
            }
            String reason = null;
            Path jar = null;
            if (nested) {
                reason = "Bundled inside " + topLevel(c).getMetadata().getName();
            } else if (builtin) {
                reason = "Built into the game or Fabric";
            } else if (md.getId().equals("modswitcher")) {
                reason = "Mod Switcher cannot disable itself";
            } else {
                jar = singleJar(c);
                if (jar == null) {
                    reason = "Not a plain jar in the mods folder";
                } else {
                    if (pendingDisabled.contains(jar)) {
                        // Undoing this would bring back a mod that needs something already queued for removal.
                        List<String> missing = queuedDependencies(c, pendingDisabled);
                        if (!missing.isEmpty()) {
                            reason = "Needs " + String.join(", ", missing) + ", which is queued to be disabled. Undo that first.";
                        }
                    } else {
                        List<String> needed = requiredBy(c, pendingDisabled);
                        if (!needed.isEmpty()) {
                            reason = "Required by: " + String.join(", ", needed) + ". Disable those first.";
                        }
                    }
                }
            }
            out.add(new ModEntry(md.getId(), md.getName(), md.getVersion().getFriendlyString(), jar, true, reason));
        }
        out.sort(Comparator.comparing(e -> e.name().toLowerCase()));
        return out;
    }

    public static List<ModEntry> disabledMods() {
        List<ModEntry> out = new ArrayList<>();
        Path dir = modsDir();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(DISABLED_SUFFIX)).forEach(p -> {
                String file = p.getFileName().toString();
                String base = file.substring(0, file.length() - ".disabled".length());
                String id = base;
                String name = base;
                String version = "";
                try (ZipFile zip = new ZipFile(p.toFile())) {
                    ZipEntry entry = zip.getEntry("fabric.mod.json");
                    if (entry != null) {
                        JsonObject json = JsonParser.parseReader(
                                new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)).getAsJsonObject();
                        if (json.has("id")) {
                            id = json.get("id").getAsString();
                        }
                        name = json.has("name") ? json.get("name").getAsString() : id;
                        if (json.has("version")) {
                            version = json.get("version").getAsString();
                        }
                    }
                } catch (Exception ignored) {
                    // unreadable metadata: fall back to the file name
                }
                out.add(new ModEntry(id, name, version, p.toAbsolutePath().normalize(), false, null));
            });
        } catch (Exception ignored) {
            // mods folder unreadable: show an empty list
        }
        out.sort(Comparator.comparing(e -> e.name().toLowerCase()));
        return out;
    }
}
