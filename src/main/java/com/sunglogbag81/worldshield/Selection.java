package com.sunglogbag81.worldshield;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class Selection {
    private Location pos1;
    private Location pos2;

    public void setPos1(Block block) {
        this.pos1 = block.getLocation();
    }

    public void setPos2(Block block) {
        this.pos2 = block.getLocation();
    }

    public boolean isComplete() {
        return pos1 != null && pos2 != null && pos1.getWorld() != null && pos1.getWorld().equals(pos2.getWorld());
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
}
