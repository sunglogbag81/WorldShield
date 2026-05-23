package com.sunglogbag81.worldshield;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Region {
    public enum ShapeType {
        CUBOID,
        POLYGON
    }

    public record PolygonPoint(int x, int z) {
    }

    private final String name;
    private final String world;
    private final ShapeType shape;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final List<PolygonPoint> polygonPoints;
    private int priority;
    private final Map<Flag, Boolean> flags = new EnumMap<>(Flag.class);
    private boolean titleEnabled = true;
    private boolean titleSpectator = true;
    private String title = "&c결투장에 입장했습니다.";
    private String subtitle = "&7이 구역은 PVP가 허용되며, 인벤세이브가 적용됩니다.";
    private int fadeIn = 10;
    private int stay = 40;
    private int fadeOut = 10;
    private int combatExitDelaySeconds = 0;
    private String spawnWorld;
    private Double spawnX;
    private Double spawnY;
    private Double spawnZ;
    private Float spawnYaw;
    private Float spawnPitch;

    public Region(String name, String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this(name, world, ShapeType.CUBOID, minX, minY, minZ, maxX, maxY, maxZ, List.of());
    }

    public static Region polygon(String name, String world, int minY, int maxY, List<PolygonPoint> points) {
        if (points.size() < 3) {
            throw new IllegalArgumentException("Polygon regions require at least 3 points");
        }
        int minX = points.stream().mapToInt(PolygonPoint::x).min().orElse(0);
        int maxX = points.stream().mapToInt(PolygonPoint::x).max().orElse(0);
        int minZ = points.stream().mapToInt(PolygonPoint::z).min().orElse(0);
        int maxZ = points.stream().mapToInt(PolygonPoint::z).max().orElse(0);
        return new Region(name, world, ShapeType.POLYGON, minX, Math.min(minY, maxY), minZ, maxX, Math.max(minY, maxY), maxZ, points);
    }

    private Region(String name, String world, ShapeType shape, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, List<PolygonPoint> polygonPoints) {
        this.name = name.toLowerCase();
        this.world = world;
        this.shape = shape;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.polygonPoints = List.copyOf(polygonPoints);
    }

    public String name() { return name; }
    public String world() { return world; }
    public ShapeType shape() { return shape; }
    public int minY() { return minY; }
    public int maxY() { return maxY; }
    public List<PolygonPoint> polygonPoints() { return polygonPoints; }
    public int priority() { return priority; }
    public Map<Flag, Boolean> flags() { return flags; }
    public boolean titleEnabled() { return titleEnabled; }
    public boolean titleSpectator() { return titleSpectator; }
    public String title() { return title; }
    public String subtitle() { return subtitle; }
    public int fadeIn() { return fadeIn; }
    public int stay() { return stay; }
    public int fadeOut() { return fadeOut; }
    public int combatExitDelaySeconds() { return combatExitDelaySeconds; }
    public String spawnWorld() { return spawnWorld == null ? world : spawnWorld; }
    public boolean hasSpawn() { return spawnX != null && spawnY != null && spawnZ != null; }

    public Location spawnLocation(org.bukkit.World bukkitWorld) {
        if (!hasSpawn()) return null;
        return new Location(bukkitWorld, spawnX, spawnY, spawnZ, spawnYaw == null ? 0f : spawnYaw, spawnPitch == null ? 0f : spawnPitch);
    }

    public void setSpawn(Location location) {
        this.spawnWorld = location.getWorld() == null ? world : location.getWorld().getName();
        this.spawnX = location.getX();
        this.spawnY = location.getY();
        this.spawnZ = location.getZ();
        this.spawnYaw = location.getYaw();
        this.spawnPitch = location.getPitch();
    }

    public void setCombatExitDelaySeconds(int seconds) {
        this.combatExitDelaySeconds = Math.max(0, seconds);
    }

    public void setFlag(Flag flag, Boolean value) {
        if (value == null) flags.remove(flag); else flags.put(flag, value);
    }

    public void setTitlePart(String part, String text) {
        titleEnabled = true;
        if (part.equalsIgnoreCase("title")) title = text;
        if (part.equalsIgnoreCase("subtitle")) subtitle = text;
    }

    public void setTitleSpectator(boolean value) {
        titleSpectator = value;
    }

    public boolean contains(Location location) {
        if (location.getWorld() == null || !world.equals(location.getWorld().getName())) return false;
        if (location.getBlockY() < minY || location.getBlockY() > maxY) return false;
        if (location.getBlockX() < minX || location.getBlockX() > maxX || location.getBlockZ() < minZ || location.getBlockZ() > maxZ) return false;
        if (shape == ShapeType.CUBOID) return true;
        return containsPolygon(location.getBlockX() + 0.5, location.getBlockZ() + 0.5);
    }

    private boolean containsPolygon(double x, double z) {
        boolean inside = false;
        for (int i = 0, j = polygonPoints.size() - 1; i < polygonPoints.size(); j = i++) {
            PolygonPoint pi = polygonPoints.get(i);
            PolygonPoint pj = polygonPoints.get(j);
            double zi = pi.z() + 0.5;
            double zj = pj.z() + 0.5;
            double xi = pi.x() + 0.5;
            double xj = pj.x() + 0.5;
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    public void save(File file) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world", world);
        yaml.set("name", name);
        yaml.set("shape", shape.name().toLowerCase(Locale.ROOT));
        yaml.set("priority", priority);
        yaml.set("min.x", minX); yaml.set("min.y", minY); yaml.set("min.z", minZ);
        yaml.set("max.x", maxX); yaml.set("max.y", maxY); yaml.set("max.z", maxZ);
        if (shape == ShapeType.POLYGON) {
            List<Map<String, Integer>> points = new ArrayList<>();
            for (PolygonPoint point : polygonPoints) {
                points.add(Map.of("x", point.x(), "z", point.z()));
            }
            yaml.set("points", points);
        }
        for (Flag flag : Flag.values()) {
            yaml.set("flags." + flag.key(), flags.get(flag));
        }
        yaml.set("title.enabled", titleEnabled);
        yaml.set("title.spectator", titleSpectator);
        yaml.set("title.title", title);
        yaml.set("title.subtitle", subtitle);
        yaml.set("title.fade-in", fadeIn);
        yaml.set("title.stay", stay);
        yaml.set("title.fade-out", fadeOut);
        yaml.set("combat.exit-delay-seconds", combatExitDelaySeconds);
        yaml.set("spawn.world", hasSpawn() ? spawnWorld() : null);
        yaml.set("spawn.x", spawnX);
        yaml.set("spawn.y", spawnY);
        yaml.set("spawn.z", spawnZ);
        yaml.set("spawn.yaw", spawnYaw);
        yaml.set("spawn.pitch", spawnPitch);
        yaml.save(file);
    }

    public static Region load(File file) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ShapeType shape = parseShape(yaml.getString("shape", "cuboid"));
        String name = yaml.getString("name", file.getName().replaceFirst("\\.yml$", "")).toLowerCase();
        String world = yaml.getString("world", file.getParentFile().getName());
        Region region;
        if (shape == ShapeType.POLYGON) {
            region = Region.polygon(name, world, yaml.getInt("min.y"), yaml.getInt("max.y"), loadPoints(yaml));
        } else {
            region = new Region(name, world,
                    yaml.getInt("min.x"), yaml.getInt("min.y"), yaml.getInt("min.z"),
                    yaml.getInt("max.x"), yaml.getInt("max.y"), yaml.getInt("max.z"));
        }
        region.priority = yaml.getInt("priority", 0);
        for (Flag flag : Flag.values()) {
            if (yaml.contains("flags." + flag.key())) {
                region.flags.put(flag, yaml.getBoolean("flags." + flag.key()));
            }
        }
        region.titleEnabled = yaml.getBoolean("title.enabled", false);
        region.titleSpectator = yaml.getBoolean("title.spectator", true);
        region.title = yaml.getString("title.title", "");
        region.subtitle = yaml.getString("title.subtitle", "");
        region.fadeIn = yaml.getInt("title.fade-in", 10);
        region.stay = yaml.getInt("title.stay", 40);
        region.fadeOut = yaml.getInt("title.fade-out", 10);
        region.combatExitDelaySeconds = yaml.getInt("combat.exit-delay-seconds", 0);
        if (yaml.contains("spawn.x") && yaml.contains("spawn.y") && yaml.contains("spawn.z")) {
            region.spawnWorld = yaml.getString("spawn.world", region.world);
            region.spawnX = yaml.getDouble("spawn.x");
            region.spawnY = yaml.getDouble("spawn.y");
            region.spawnZ = yaml.getDouble("spawn.z");
            region.spawnYaw = (float) yaml.getDouble("spawn.yaw", 0);
            region.spawnPitch = (float) yaml.getDouble("spawn.pitch", 0);
        }
        return region;
    }

    private static ShapeType parseShape(String raw) {
        try {
            return ShapeType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ShapeType.CUBOID;
        }
    }

    private static List<PolygonPoint> loadPoints(YamlConfiguration yaml) {
        List<PolygonPoint> points = new ArrayList<>();
        List<Map<?, ?>> list = yaml.getMapList("points");
        for (Map<?, ?> map : list) {
            Object x = map.get("x");
            Object z = map.get("z");
            if (x instanceof Number xNumber && z instanceof Number zNumber) {
                points.add(new PolygonPoint(xNumber.intValue(), zNumber.intValue()));
            }
        }
        if (points.isEmpty()) {
            ConfigurationSection section = yaml.getConfigurationSection("points");
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    points.add(new PolygonPoint(section.getInt(key + ".x"), section.getInt(key + ".z")));
                }
            }
        }
        return points;
    }
}
