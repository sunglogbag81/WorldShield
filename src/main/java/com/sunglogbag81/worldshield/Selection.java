package com.sunglogbag81.worldshield;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

public final class Selection {
    private Location pos1;
    private Location pos2;
    private final List<Region.PolygonPoint> polygonPoints = new ArrayList<>();

    public void setPos1(Block block) {
        this.pos1 = block.getLocation();
    }

    public void setPos2(Block block) {
        this.pos2 = block.getLocation();
    }

    public Location pos1() { return pos1; }
    public Location pos2() { return pos2; }

    public boolean isComplete() {
        return pos1 != null && pos2 != null && pos1.getWorld() != null && pos1.getWorld().equals(pos2.getWorld());
    }

    public boolean hasPolygonPoints() {
        return !polygonPoints.isEmpty();
    }

    public List<Region.PolygonPoint> polygonPoints() {
        return List.copyOf(polygonPoints);
    }

    public void addPolygonPoint(Block block) {
        if (!isComplete()) {
            throw new IllegalStateException("먼저 나무 도끼로 같은 월드의 minY/maxY 두 지점을 선택하세요.");
        }
        if (block.getWorld() == null || !block.getWorld().equals(pos1.getWorld())) {
            throw new IllegalArgumentException("꼭짓점은 선택한 Y 범위와 같은 월드에서 찍어야 합니다.");
        }
        Region.PolygonPoint point = new Region.PolygonPoint(block.getX(), block.getZ());
        if (!polygonPoints.isEmpty() && polygonPoints.get(polygonPoints.size() - 1).equals(point)) {
            throw new IllegalArgumentException("방금 찍은 꼭짓점과 같은 위치입니다.");
        }
        polygonPoints.add(point);
    }

    public Region.PolygonPoint undoPolygonPoint() {
        if (polygonPoints.isEmpty()) return null;
        return polygonPoints.remove(polygonPoints.size() - 1);
    }

    public void clearPolygonPoints() {
        polygonPoints.clear();
    }

    public int minY() {
        if (!isComplete()) throw new IllegalStateException("Selection is incomplete or crosses worlds");
        return Math.min(pos1.getBlockY(), pos2.getBlockY());
    }

    public int maxY() {
        if (!isComplete()) throw new IllegalStateException("Selection is incomplete or crosses worlds");
        return Math.max(pos1.getBlockY(), pos2.getBlockY());
    }

    public Region toRegion(String name) {
        if (!isComplete()) {
            throw new IllegalStateException("Selection is incomplete or crosses worlds");
        }
        World world = pos1.getWorld();
        return new Region(name, world.getName(), Math.min(pos1.getBlockX(), pos2.getBlockX()), Math.min(pos1.getBlockY(), pos2.getBlockY()),
                Math.min(pos1.getBlockZ(), pos2.getBlockZ()), Math.max(pos1.getBlockX(), pos2.getBlockX()),
                Math.max(pos1.getBlockY(), pos2.getBlockY()), Math.max(pos1.getBlockZ(), pos2.getBlockZ()));
    }

    public Region toPolygonRegion(String name) {
        if (!isComplete()) {
            throw new IllegalStateException("먼저 나무 도끼로 같은 월드의 minY/maxY 두 지점을 선택하세요.");
        }
        if (polygonPoints.size() < 3) {
            throw new IllegalStateException("polygon 구역은 막대기로 꼭짓점을 3개 이상 찍어야 합니다.");
        }
        World world = pos1.getWorld();
        return Region.polygon(name, world.getName(), minY(), maxY(), polygonPoints);
    }
}
