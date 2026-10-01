package com.modswitcher;

import java.nio.file.Path;

/**
 * One row in the list.
 *
 * @param id            mod id (or file name for jars that are currently disabled)
 * @param name          display name
 * @param version       version string, may be empty
 * @param jar           the jar file this row refers to, or null if it cannot be toggled
 * @param loaded        true if the mod is loaded in this session
 * @param blockedReason null if the mod can be toggled, otherwise why it is locked
 */
public record ModEntry(String id, String name, String version, Path jar, boolean loaded, String blockedReason) {
    public boolean toggleable() {
        return blockedReason == null && jar != null;
    }
}
