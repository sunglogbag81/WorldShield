package com.sunglogbag81.worldshield;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class WorldShieldPlugin extends JavaPlugin implements Listener, TabExecutor {
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, String> currentRegion = new HashMap<>();
    private final Map<Flag, Boolean> globalFlags = new EnumMap<>(Flag.class);
    private RegionManager regionManager;
    private String prefix;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadAll();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("worldshield").setExecutor(this);
        getCommand("worldshield").setTabCompleter(this);
    }

    private void loadAll() {
        reloadConfig();
        prefix = color(getConfig().getString("messages.prefix", "&6[WorldShield]&r "));
        globalFlags.clear();
        for (Flag flag : Flag.values()) {
            globalFlags.put(flag, getConfig().getBoolean("global." + flag.key(), true));
        }
        globalFlags.putIfAbsent(Flag.KEEP_INVENTORY, false);
        regionManager = new RegionManager(getDataFolder());
        regionManager.load();
    }

    private boolean allowed(Location location, Flag flag) {
        return regionManager.regionFlag(location, flag).orElse(globalFlags.getOrDefault(flag, true));
    }

    @EventHandler(ignoreCancelled = true)
    public void onWand(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.hasPermission("worldshield.admin")) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.WOODEN_AXE || event.getClickedBlock() == null) return;
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new Selection());
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            selection.setPos1(event.getClickedBlock());
            msg(player, "1번 지점 설정: " + formatBlock(event.getClickedBlock()));
        } else {
            selection.setPos2(event.getClickedBlock());
            msg(player, "2번 지점 설정: " + formatBlock(event.getClickedBlock()));
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        Player victim = asPlayer(event.getEntity());
        Player attacker = asPlayer(event.getDamager());
        if (victim == null || attacker == null) return;
        if (!allowed(victim.getLocation(), Flag.PVP)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!allowed(event.getBlockPlaced().getLocation(), Flag.BLOCK_PLACE)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!allowed(event.getBlock().getLocation(), Flag.BLOCK_BREAK)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> !allowed(block.getLocation(), Flag.EXPLOSION_BLOCK_DAMAGE));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> !allowed(block.getLocation(), Flag.EXPLOSION_BLOCK_DAMAGE));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (!allowed(event.getEntity().getLocation(), Flag.KEEP_INVENTORY)) return;
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || sameBlock(event.getFrom(), event.getTo())) return;
        Player player = event.getPlayer();
        Optional<Region> region = regionManager.highestRegion(event.getTo());
        String key = region.map(value -> value.world() + ":" + value.name()).orElse("");
        if (key.equals(currentRegion.get(player.getUniqueId()))) return;
        currentRegion.put(player.getUniqueId(), key);
        region.filter(Region::titleEnabled).ifPresent(value -> player.showTitle(Title.title(
                component(value.title()), component(value.subtitle()),
                Title.Times.times(Duration.ofMillis(value.fadeIn() * 50L), Duration.ofMillis(value.stay() * 50L), Duration.ofMillis(value.fadeOut() * 50L)))));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("worldshield.admin")) {
            msg(sender, ChatColor.RED + "권한이 없습니다.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) return help(sender);
        if (args[0].equalsIgnoreCase("reload")) {
            loadAll();
            msg(sender, "설정을 다시 불러왔습니다.");
            return true;
        }
        if (args[0].equalsIgnoreCase("wand")) return giveWand(sender);
        if (args[0].equalsIgnoreCase("pos1") || args[0].equalsIgnoreCase("pos2")) return setPos(sender, args[0]);
        if (args[0].equalsIgnoreCase("region")) return regionCommand(sender, args);
        if (args[0].equalsIgnoreCase("flag")) return flagCommand(sender, args);
        if (args[0].equalsIgnoreCase("title")) return titleCommand(sender, args);
        return help(sender);
    }

    private boolean help(CommandSender sender) {
        msg(sender, "/ws wand - 나무 도끼를 받습니다.");
        msg(sender, "/ws region create <name> - 선택 영역으로 구역 생성");
        msg(sender, "/ws region delete <name> - 현재 월드 구역 삭제");
        msg(sender, "/ws region list [world] - 구역 목록");
        msg(sender, "/ws flag global <flag> <true|false> - 전체 월드 설정");
        msg(sender, "/ws flag region <name> <flag> <true|false|unset> [world] - 구역 설정");
        msg(sender, "/ws title <name> <title|subtitle> <text...> [--world <world>] - 입장 타이틀 설정");
        return true;
    }

    private boolean giveWand(CommandSender sender) {
        if (!(sender instanceof Player player)) return true;
        player.getInventory().addItem(new ItemStack(Material.WOODEN_AXE));
        msg(player, "나무 도끼 지급 완료. 좌클릭=pos1, 우클릭=pos2");
        return true;
    }

    private boolean setPos(CommandSender sender, String pos) {
        if (!(sender instanceof Player player)) return true;
        Block block = player.getTargetBlockExact(100);
        if (block == null) {
            msg(player, ChatColor.RED + "바라보는 블록이 없습니다.");
            return true;
        }
        Selection selection = selections.computeIfAbsent(player.getUniqueId(), ignored -> new Selection());
        if (pos.equalsIgnoreCase("pos1")) selection.setPos1(block); else selection.setPos2(block);
        msg(player, pos + " 설정: " + formatBlock(block));
        return true;
    }

    private boolean regionCommand(CommandSender sender, String[] args) {
        if (args.length < 2) return help(sender);
        String world = sender instanceof Player player ? player.getWorld().getName() : Bukkit.getWorlds().get(0).getName();
        if (args[1].equalsIgnoreCase("create") && args.length >= 3 && sender instanceof Player player) {
            Selection selection = selections.get(player.getUniqueId());
            if (selection == null || !selection.isComplete()) {
                msg(player, ChatColor.RED + "먼저 나무 도끼로 같은 월드의 두 지점을 선택하세요.");
                return true;
            }
            Region region = selection.toRegion(args[2]);
            try {
                regionManager.save(region);
                msg(player, "구역 생성: " + region.name());
            } catch (IOException e) {
                msg(player, ChatColor.RED + "구역 저장 실패: " + e.getMessage());
            }
            return true;
        }
        if (args[1].equalsIgnoreCase("delete") && args.length >= 3) {
            msg(sender, regionManager.delete(world, args[2]) ? "구역 삭제 완료." : ChatColor.RED + "구역을 찾지 못했습니다.");
            return true;
        }
        if (args[1].equalsIgnoreCase("list")) {
            if (args.length >= 3) world = args[2];
            List<String> names = regionManager.all(world).stream().map(Region::name).toList();
            msg(sender, world + " 구역: " + (names.isEmpty() ? "없음" : String.join(", ", names)));
            return true;
        }
        if (args[1].equalsIgnoreCase("info") && args.length >= 3) {
            if (args.length >= 4) world = args[3];
            Optional<Region> region = regionManager.get(world, args[2]);
            msg(sender, region.map(value -> value.name() + " flags=" + value.flags()).orElse(ChatColor.RED + "구역 없음"));
            return true;
        }
        return help(sender);
    }

    private boolean flagCommand(CommandSender sender, String[] args) {
        if (args.length < 4) return help(sender);
        Optional<Flag> flag = Flag.fromKey(args[2].equalsIgnoreCase("global") ? args[2] : args.length >= 4 ? args[3] : "");
        if (args[1].equalsIgnoreCase("global")) {
            flag = Flag.fromKey(args[2]);
            if (flag.isEmpty() || args.length < 4) return invalidFlag(sender);
            boolean value = Boolean.parseBoolean(args[3]);
            getConfig().set("global." + flag.get().key(), value);
            saveConfig();
            globalFlags.put(flag.get(), value);
            msg(sender, "global " + flag.get().key() + " = " + value);
            return true;
        }
        if (args[1].equalsIgnoreCase("region") && args.length >= 5) {
            String world = args.length >= 6 ? args[5] : sender instanceof Player player ? player.getWorld().getName() : Bukkit.getWorlds().get(0).getName();
            flag = Flag.fromKey(args[3]);
            if (flag.isEmpty()) return invalidFlag(sender);
            Optional<Region> region = regionManager.get(world, args[2]);
            if (region.isEmpty()) {
                msg(sender, ChatColor.RED + "구역을 찾지 못했습니다.");
                return true;
            }
            Boolean value = args[4].equalsIgnoreCase("unset") ? null : Boolean.parseBoolean(args[4]);
            region.get().setFlag(flag.get(), value);
            try {
                regionManager.save(region.get());
                msg(sender, "region " + region.get().name() + " " + flag.get().key() + " = " + (value == null ? "unset" : value));
            } catch (IOException e) {
                msg(sender, ChatColor.RED + "저장 실패: " + e.getMessage());
            }
            return true;
        }
        return help(sender);
    }

    private boolean titleCommand(CommandSender sender, String[] args) {
        if (args.length < 4) return help(sender);
        String world = sender instanceof Player player ? player.getWorld().getName() : Bukkit.getWorlds().get(0).getName();
        List<String> words = new ArrayList<>(Arrays.asList(args).subList(3, args.length));
        int worldFlag = words.indexOf("--world");
        if (worldFlag >= 0 && words.size() > worldFlag + 1) {
            world = words.get(worldFlag + 1);
            words = words.subList(0, worldFlag);
        }
        Optional<Region> region = regionManager.get(world, args[1]);
        if (region.isEmpty()) {
            msg(sender, ChatColor.RED + "구역을 찾지 못했습니다.");
            return true;
        }
        if (!args[2].equalsIgnoreCase("title") && !args[2].equalsIgnoreCase("subtitle")) return help(sender);
        region.get().setTitlePart(args[2], String.join(" ", words));
        try {
            regionManager.save(region.get());
            msg(sender, "타이틀 저장 완료.");
        } catch (IOException e) {
            msg(sender, ChatColor.RED + "저장 실패: " + e.getMessage());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("help", "wand", "pos1", "pos2", "region", "flag", "title", "reload");
        if (args.length == 2 && args[0].equalsIgnoreCase("region")) return List.of("create", "delete", "list", "info");
        if (args.length == 2 && args[0].equalsIgnoreCase("flag")) return List.of("global", "region");
        if ((args.length == 3 && args[1].equalsIgnoreCase("global")) || (args.length == 4 && args[1].equalsIgnoreCase("region"))) {
            return Arrays.stream(Flag.values()).map(Flag::key).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("title")) return sender instanceof Player player ? regionManager.all(player.getWorld().getName()).stream().map(Region::name).toList() : List.of();
        if (args.length == 3 && args[0].equalsIgnoreCase("title")) return List.of("title", "subtitle");
        return List.of();
    }

    private boolean invalidFlag(CommandSender sender) {
        msg(sender, ChatColor.RED + "알 수 없는 플래그. 사용 가능: " + String.join(", ", Arrays.stream(Flag.values()).map(Flag::key).toList()));
        return true;
    }

    private Player asPlayer(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof org.bukkit.projectiles.ProjectileSource source && source instanceof Player player) return player;
        if (entity instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private boolean sameBlock(Location first, Location second) {
        return first.getWorld() == second.getWorld() && first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY() && first.getBlockZ() == second.getBlockZ();
    }

    private Component component(String text) {
        return legacy.deserialize(text == null ? "" : text);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private void msg(CommandSender sender, String text) {
        sender.sendMessage(prefix + color(text));
    }

    private String formatBlock(Block block) {
        return block.getWorld().getName() + " " + block.getX() + ", " + block.getY() + ", " + block.getZ();
    }
}
