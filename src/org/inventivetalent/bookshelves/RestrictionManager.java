package org.inventivetalent.bookshelves;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RestrictionManager {

    private final boolean whitelist;

    private final List<Material> materials = new ArrayList<>();

    public RestrictionManager() {
        this.whitelist = Bookshelves.instance.getConfig().getBoolean("restrictions.whitelist");

        Bookshelves.instance.getConfig().getStringList("restrictions.materials").forEach(
                materialName -> {
                    Optional<Material> parsedMaterial = Optional.ofNullable(Material.getMaterial(materialName));

                    if (parsedMaterial.isPresent()) {
                        materials.add(parsedMaterial.get());
                    } else {
                        Bookshelves.instance.getLogger().warning(String.format("Unable to parse material \"%s\". Material will be ignored.", materialName));
                    }
                }
        );

        Bookshelves.instance.getLogger().info(String.format("Loaded %s material-restrictions.", materials.size()));
    }

    public boolean isRestricted(Material material) {
        return whitelist ? materials.contains(material) : !materials.contains(material);
    }

}