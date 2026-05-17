package com.sunglogbag81.worldshield;

import org.bukkit.Location;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public final class RegionManager {
    private final File root;
    private final Logger logger;
    private final Map<String, Map<String, Region>> regions = new HashMap<>();

    public RegionManager(File dataFolder, Logger logger) {
        this.root = new File(dataFolder, "regions");
        this.logger = logger;
    }

    public void load() {
        regions.clear();
        if (!root.exists()) root.mkdirs();
        File[] worldDirs = root.listFiles(File::isDirectory);
        if (worldDirs == null) return;
        for (File worldDir : worldDirs) {
            File[] files = worldDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files == null) continue;
            for (File file : files) {
                try {
                    Region region = Region.load(file);
                    regions.computeIfAbsent(region.world(), ignored -> new HashMap<>()).put(region.name(), region);
                } catch (RuntimeException e) {
                    logger.warning("Skipping invalid region file " + file.getPath() + ": " + e.getMessage());
                }
            }
        }
    }

    public void save(Region region) throws IOException {
        File worldDir = new File(root, region.world());
        if (!worldDir.exists()) worldDir.mkdirs();
        region.save(new File(worldDir, region.name() + ".yml"));
        regions.computeIfAbsent(region.world(), ignored -> new HashMap<>()).put(region.name(), region);
    }

    public boolean delete(String world, String name) {
        Map<String, Region> byWorld = regions.get(world);
        if (byWorld == null || byWorld.remove(name.toLowerCase()) == null) return false;
        return new File(new File(root, world), name.toLowerCase() + ".yml").delete();
    }

    public Optional<Region> get(String world, String name) {
        return Optional.ofNullable(regions.getOrDefault(world, Map.of()).get(name.toLowerCase()));
    }

    public List<Region> all(String world) {
        return regions.getOrDefault(world, Map.of()).values().stream()
                .sorted(Comparator.comparing(Region::name)).toList();
    }

    public List<Region> at(Location location) {
        List<Region> result = new ArrayList<>();
        if (location.getWorld() == null) return result;
        for (Region region : regions.getOrDefault(location.getWorld().getName(), Map.of()).values()) {
            if (region.contains(location)) result.add(region);
        }
        result.sort(Comparator.comparingInt(Region::priority).reversed().thenComparing(Region::name));
        return result;
    }

    public Optional<Boolean> regionFlag(Location location, Flag flag) {
        return at(location).stream().map(region -> region.flags().get(flag)).filter(value -> value != null).findFirst();
    }

    public Optional<Region> highestRegion(Location location) {
        return at(location).stream().findFirst();
    }
}
