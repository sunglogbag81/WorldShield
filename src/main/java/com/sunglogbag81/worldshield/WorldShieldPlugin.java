package com.sunglogbag81.worldshield;

import io.papermc.paper.event.entity.EntityMoveEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class WorldShieldPlugin extends JavaPlugin implements Listener, TabExecutor {
    private static final String GUI_TITLE_PREFIX = ChatColor.DARK_GREEN + "WorldShield ";
    private static final long FIRE_TRACE_TTL_MILLIS = 5 * 60 * 1000L;
    private static final int AUTO_BAN_LOOK_DISTANCE = 60;
    private static final int AUTO_BAN_LOOK_RADIUS = 10;
    private static final String AUTO_BAN_MESSAGE = "알고리즘에 의해 자동 차단되었습니다.\n관리자가 로그를 확인중입니다. 잠시만 기다려주세요.";
    private final LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, String> currentRegion = new HashMap<>();
    private final Map<UUID, Long> lastCombatDamage = new HashMap<>();
    private final Map<UUID, String> lastDeathRegion = new HashMap<>();
    private final Map<UUID, String> logoutRegions = new HashMap<>();
    private final Map<UUID, String> openFlagGuis = new HashMap<>();
    private final Map<BlockKey, FireTrace> fireTraces = new HashMap<>();
    private final Map<BlockKey, FireTrace> lavaTraces = new HashMap<>();
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
        getCommand("auto-fire-ban").setExecutor(this);
        getCommand("auto-fire-ban").setTabCompleter(this);
    }

    private void loadAll() {
        reloadConfig();
        prefix = color(getConfig().getString("messages.prefix", "&6[WorldShield]&r "));
        globalFlags.clear();
        for (Flag flag : Flag.values()) {
            globalFlags.put(flag, getConfig().getBoolean("global." + flag.key(), true));
        }
        globalFlags.put(Flag.KEEP_INVENTORY, getConfig().getBoolean("global." + Flag.KEEP_INVENTORY.key(), false));
        regionManager = new RegionManager(getDataFolder(), getLogger());
        regionManager.load();
        loadLogoutRegions();
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
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Entity attackerEntity = damagingEntity(event.getDamager());
        if (attackerEntity != null && isCrossRegionDamage(attackerEntity.getLocation(), event.getEntity().getLocation())) {
            event.setCancelled(true);
            return;
        }

        Player victim = asPlayer(event.getEntity());
        Player attacker = asPlayer(event.getDamager());
        if (victim == null) return;
        if (attacker != null) {
            if (!allowed(victim.getLocation(), Flag.PVP)) {
                event.setCancelled(true);
                return;
            }
            long now = System.currentTimeMillis();
            lastCombatDamage.put(victim.getUniqueId(), now);
            lastCombatDamage.put(attacker.getUniqueId(), now);
            return;
        }
        if (isMobAttack(event.getDamager()) && !allowed(victim.getLocation(), Flag.MOB_DAMAGE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Mob)) return;
        if (event.getTarget() instanceof Player player && !allowed(player.getLocation(), Flag.MOB_TARGET)) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobMove(EntityMoveEvent event) {
        if (!(event.getEntity() instanceof Mob)) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || sameBlock(from, to)) return;
        if (isEnteringBlockedForMob(from, to)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLavaBucket(PlayerBucketEmptyEvent event) {
        if (event.getBucket() != Material.LAVA_BUCKET) return;
        cleanupFireTraces();
        lavaTraces.put(BlockKey.of(event.getBlock()), FireTrace.of(event.getPlayer(), event.getBlock(), System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockIgnite(BlockIgniteEvent event) {
        cleanupFireTraces();
        long now = System.currentTimeMillis();
        Player player = event.getPlayer();
        if (player == null && event.getIgnitingEntity() != null) {
            player = asPlayer(event.getIgnitingEntity());
        }
        if (player != null) {
            fireTraces.put(BlockKey.of(event.getBlock()), FireTrace.of(player, event.getBlock(), now));
            return;
        }

        Block ignitingBlock = event.getIgnitingBlock();
        if (ignitingBlock != null) {
            FireTrace source = fireTraces.get(BlockKey.of(ignitingBlock));
            if (source != null && !source.expired(now)) {
                fireTraces.put(BlockKey.of(event.getBlock()), source.at(event.getBlock(), now));
                return;
            }
        }

        if (event.getCause() == BlockIgniteEvent.IgniteCause.LAVA) {
            findNearestTrace(lavaTraces, event.getBlock(), 8, now)
                    .ifPresent(trace -> fireTraces.put(BlockKey.of(event.getBlock()), trace.at(event.getBlock(), now)));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (!isFire(event.getNewState().getType())) return;
        cleanupFireTraces();
        long now = System.currentTimeMillis();
        Block target = event.getBlock();
        FireTrace source = fireTraces.get(BlockKey.of(event.getSource()));
        if (source == null || source.expired(now)) {
            source = findNearestTrace(fireTraces, event.getSource(), 2, now).orElse(null);
        }
        if (source != null) {
            fireTraces.put(BlockKey.of(target), source.at(target, now));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!allowed(event.getBlockPlaced().getLocation(), Flag.BLOCK_PLACE)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (bucketEmptyBlocked(event)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (bucketFillBlocked(event)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (!allowed(event.getPlayer().getLocation(), Flag.EQUIPMENT_DURABILITY)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobSpawn(CreatureSpawnEvent event) {
        if (!allowed(event.getLocation(), Flag.MOB_SPAWNING)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (!allowed(event.getPlayer().getLocation(), Flag.ITEM_DROP)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && !allowed(player.getLocation(), Flag.ITEM_PICKUP)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnderPearl(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                && (!allowed(event.getFrom(), Flag.ENDERPEARL) || !allowed(event.getTo(), Flag.ENDERPEARL))) {
            event.setCancelled(true);
            return;
        }
        clearMobTargetsOnProtectedEntry(event.getPlayer(), event.getFrom(), event.getTo());
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
        Player player = event.getEntity();
        regionManager.highestRegion(player.getLocation())
                .filter(Region::hasSpawn)
                .ifPresent(region -> lastDeathRegion.put(player.getUniqueId(), regionKey(region)));
        if (!allowed(player.getLocation(), Flag.KEEP_INVENTORY)) return;
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setKeepLevel(true);
        event.setDroppedExp(0);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        String key = lastDeathRegion.remove(event.getPlayer().getUniqueId());
        if (key == null) return;
        regionFromKey(key).flatMap(this::spawnOf).ifPresent(event::setRespawnLocation);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Optional<Region> region = regionManager.highestRegion(player.getLocation()).filter(Region::hasSpawn);
        if (region.isPresent()) {
            logoutRegions.put(player.getUniqueId(), regionKey(region.get()));
        } else {
            logoutRegions.remove(player.getUniqueId());
        }
        saveLogoutRegions();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String key = logoutRegions.remove(event.getPlayer().getUniqueId());
        if (key == null) return;
        saveLogoutRegions();
        Bukkit.getScheduler().runTask(this, () -> regionFromKey(key).flatMap(this::spawnOf)
                .ifPresent(location -> event.getPlayer().teleport(location)));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || sameBlock(event.getFrom(), event.getTo())) return;
        Player player = event.getPlayer();
        Optional<Region> fromRegion = regionManager.highestRegion(event.getFrom());
        Optional<Region> toRegion = regionManager.highestRegion(event.getTo());
        if (isBlockedByCombatExitDelay(player, fromRegion, toRegion)) {
            event.setCancelled(true);
            return;
        }
        clearMobTargetsOnProtectedEntry(player, event.getFrom(), event.getTo());
        Optional<Region> region = toRegion;
        String key = region.map(value -> value.world() + ":" + value.name()).orElse("");
        if (key.equals(currentRegion.get(player.getUniqueId()))) return;
        currentRegion.put(player.getUniqueId(), key);
        region.filter(value -> value.titleEnabled() && (player.getGameMode() != GameMode.SPECTATOR || value.titleSpectator())).ifPresent(value -> player.showTitle(Title.title(
                component(value.title()), component(value.subtitle()),
                Title.Times.times(Duration.ofMillis(value.fadeIn() * 50L), Duration.ofMillis(value.stay() * 50L), Duration.ofMillis(value.fadeOut() * 50L)))));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("worldshield.admin")) {
            msg(sender, ChatColor.RED + "권한이 없습니다.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("auto-fire-ban")) return autoFireBanCommand(sender);
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) return help(sender);
        if (args[0].equalsIgnoreCase("reload")) {
            loadAll();
            msg(sender, "설정을 다시 불러왔습니다.");
            return true;
        }
        if (args[0].equalsIgnoreCase("자동차단") || args[0].equalsIgnoreCase("autoban")) return autoFireBanCommand(sender);
        if (args[0].equalsIgnoreCase("wand")) return giveWand(sender);
        if (args[0].equalsIgnoreCase("pos1") || args[0].equalsIgnoreCase("pos2")) return setPos(sender, args[0]);
        if (args[0].equalsIgnoreCase("region")) return regionCommand(sender, args);
        if (args[0].equalsIgnoreCase("gui")) return guiCommand(sender, args);
        if (args[0].equalsIgnoreCase("flag")) return flagCommand(sender, args);
        if (args[0].equalsIgnoreCase("title")) return titleCommand(sender, args);
        if (args[0].equalsIgnoreCase("combat")) return combatCommand(sender, args);
        return help(sender);
    }

    private boolean help(CommandSender sender) {
        msg(sender, "/ws wand - 나무 도끼를 받습니다.");
        msg(sender, "/ws region create <name> - 선택 영역으로 구역 생성");
        msg(sender, "/ws region delete <name> - 현재 월드 구역 삭제");
        msg(sender, "/ws region list [world] - 구역 목록");
        msg(sender, "/ws region setspawn <name> [world] - 현재 위치를 해당 구역 사망/재접속 스폰으로 설정(구역 밖 가능)");
        msg(sender, "/ws gui <global|region> [world] - 전체/구역 플래그 GUI");
        msg(sender, "/ws flag global <flag> <true|false> - 전체 월드 설정");
        msg(sender, "/ws flag region <name> <flag> <true|false|unset> [world] - 구역 설정");
        msg(sender, "/ws title <name> <title|subtitle> <text...> [--world <world>] - 입장 타이틀 설정");
        msg(sender, "/ws title <name> spectator <true|false> [world] - 관전모드 입장 타이틀 표시 설정");
        msg(sender, "/ws combat <name> exit-delay <seconds> [world] - 전투 후 구역 이탈 제한 시간 설정");
        msg(sender, "/자동차단 - 바라보는 앞쪽 화재 근원자를 최근 5분 로그로 찾아 자동 차단");
        return true;
    }

    private boolean autoFireBanCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            msg(sender, ChatColor.RED + "플레이어만 사용할 수 있습니다.");
            return true;
        }
        cleanupFireTraces();
        Set<BlockKey> fires = findFiresInFront(player);
        if (fires.isEmpty()) {
            msg(sender, ChatColor.YELLOW + "바라보는 앞쪽에서 불을 찾지 못했습니다.");
            return true;
        }

        Map<UUID, FireTrace> suspects = new HashMap<>();
        long now = System.currentTimeMillis();
        for (BlockKey fire : fires) {
            FireTrace trace = fireTraces.get(fire);
            if (trace == null || trace.expired(now)) continue;
            Player suspect = Bukkit.getPlayer(trace.playerId());
            if (suspect != null && suspect.hasPermission("worldshield.autoban.exempt")) {
                continue;
            }
            suspects.putIfAbsent(trace.playerId(), trace);
        }

        if (suspects.isEmpty()) {
            msg(sender, ChatColor.YELLOW + "불은 발견했지만 최근 5분 내 근원 플레이어 로그가 없습니다.");
            return true;
        }

        List<String> names = new ArrayList<>();
        for (FireTrace trace : suspects.values()) {
            banFireSuspect(trace, player.getName());
            names.add(trace.playerName());
        }
        msg(sender, ChatColor.RED + "자동 차단 완료: " + String.join(", ", names));
        getLogger().warning("Auto fire ban by " + player.getName() + ": " + String.join(", ", names));
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
        if (args[1].equalsIgnoreCase("setspawn") && args.length >= 3 && sender instanceof Player player) {
            if (args.length >= 4) world = args[3];
            Optional<Region> region = regionManager.get(world, args[2]);
            if (region.isEmpty()) {
                msg(sender, ChatColor.RED + "구역을 찾지 못했습니다.");
                return true;
            }
            region.get().setSpawn(player.getLocation());
            try {
                regionManager.save(region.get());
                msg(sender, "구역 스폰 설정 완료: " + region.get().name());
            } catch (IOException e) {
                msg(sender, ChatColor.RED + "저장 실패: " + e.getMessage());
            }
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

    private boolean guiCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length < 2) return help(sender);
        if (args[1].equalsIgnoreCase("global")) {
            openFlagGui(player, "global");
            return true;
        }
        String world = args.length >= 3 ? args[2] : player.getWorld().getName();
        Optional<Region> region = regionManager.get(world, args[1]);
        if (region.isEmpty()) {
            msg(sender, ChatColor.RED + "구역을 찾지 못했습니다.");
            return true;
        }
        openFlagGui(player, regionKey(region.get()));
        return true;
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
        if (args[2].equalsIgnoreCase("spectator") && args.length >= 5) {
            world = args[4].equalsIgnoreCase("--world") && args.length >= 6 ? args[5] : args[4];
        }
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
        if (args[2].equalsIgnoreCase("spectator")) {
            region.get().setTitleSpectator(Boolean.parseBoolean(args[3]));
            try {
                regionManager.save(region.get());
                msg(sender, "관전모드 타이틀 표시 = " + Boolean.parseBoolean(args[3]));
            } catch (IOException e) {
                msg(sender, ChatColor.RED + "저장 실패: " + e.getMessage());
            }
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

    private boolean combatCommand(CommandSender sender, String[] args) {
        if (args.length < 4 || !args[2].equalsIgnoreCase("exit-delay")) return help(sender);
        String world = args.length >= 5 ? args[4] : sender instanceof Player player ? player.getWorld().getName() : Bukkit.getWorlds().get(0).getName();
        Optional<Region> region = regionManager.get(world, args[1]);
        if (region.isEmpty()) {
            msg(sender, ChatColor.RED + "구역을 찾지 못했습니다.");
            return true;
        }
        int seconds;
        try {
            seconds = Math.max(0, Integer.parseInt(args[3]));
        } catch (NumberFormatException e) {
            msg(sender, ChatColor.RED + "초 단위 숫자를 입력하세요.");
            return true;
        }
        region.get().setCombatExitDelaySeconds(seconds);
        try {
            regionManager.save(region.get());
            msg(sender, "전투 후 이탈 제한 시간이 " + seconds + "초로 설정되었습니다.");
        } catch (IOException e) {
            msg(sender, ChatColor.RED + "저장 실패: " + e.getMessage());
        }
        return true;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String target = openFlagGuis.get(player.getUniqueId());
        if (target == null || !event.getView().getTitle().startsWith(GUI_TITLE_PREFIX)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getSlot() < 0 || event.getSlot() >= Flag.values().length) return;
        Flag flag = Flag.values()[event.getSlot()];
        if (target.equals("global")) {
            boolean value = !globalFlags.getOrDefault(flag, true);
            globalFlags.put(flag, value);
            getConfig().set("global." + flag.key(), value);
            saveConfig();
        } else {
            Optional<Region> region = regionFromKey(target);
            if (region.isEmpty()) return;
            Boolean current = region.get().flags().get(flag);
            Boolean next = event.isRightClick() ? null : current == null ? true : !current;
            region.get().setFlag(flag, next);
            try {
                regionManager.save(region.get());
            } catch (IOException e) {
                msg(player, ChatColor.RED + "저장 실패: " + e.getMessage());
                return;
            }
        }
        openFlagGui(player, target);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("auto-fire-ban")) return List.of();
        if (args.length == 1) return List.of("help", "wand", "pos1", "pos2", "region", "gui", "flag", "title", "combat", "자동차단", "autoban", "reload");
        if (args.length == 2 && args[0].equalsIgnoreCase("region")) return List.of("create", "delete", "list", "info", "setspawn");
        if (args.length == 2 && args[0].equalsIgnoreCase("gui")) return sender instanceof Player player ? regionManager.all(player.getWorld().getName()).stream().map(Region::name).toList() : List.of("global");
        if (args.length == 2 && args[0].equalsIgnoreCase("flag")) return List.of("global", "region");
        if ((args.length == 3 && args[1].equalsIgnoreCase("global")) || (args.length == 4 && args[1].equalsIgnoreCase("region"))) {
            return Arrays.stream(Flag.values()).map(Flag::key).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("title")) return sender instanceof Player player ? regionManager.all(player.getWorld().getName()).stream().map(Region::name).toList() : List.of();
        if (args.length == 3 && args[0].equalsIgnoreCase("title")) return List.of("title", "subtitle", "spectator");
        if (args.length == 3 && args[0].equalsIgnoreCase("combat")) return List.of("exit-delay");
        return List.of();
    }

    private boolean invalidFlag(CommandSender sender) {
        msg(sender, ChatColor.RED + "알 수 없는 플래그. 사용 가능: " + String.join(", ", Arrays.stream(Flag.values()).map(Flag::key).toList()));
        return true;
    }

    private void openFlagGui(Player player, String target) {
        Inventory inventory = Bukkit.createInventory(null, 18, GUI_TITLE_PREFIX + target);
        for (int i = 0; i < Flag.values().length; i++) {
            Flag flag = Flag.values()[i];
            Boolean value = target.equals("global") ? globalFlags.getOrDefault(flag, true) : regionFromKey(target).map(region -> region.flags().get(flag)).orElse(null);
            inventory.setItem(i, flagItem(flag, value));
        }
        openFlagGuis.put(player.getUniqueId(), target);
        player.openInventory(inventory);
    }

    private ItemStack flagItem(Flag flag, Boolean value) {
        Material material = value == null ? Material.GRAY_DYE : value ? Material.LIME_DYE : Material.RED_DYE;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + flag.key() + ChatColor.GRAY + " = " + flagValueText(value));
            meta.setLore(List.of(ChatColor.GRAY + "좌클릭: ON/OFF 전환", ChatColor.GRAY + "우클릭: 구역값 unset(전체 설정 상속)"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String flagValueText(Boolean value) {
        if (value == null) return ChatColor.GRAY + "unset";
        return value ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF";
    }

    private Set<BlockKey> findFiresInFront(Player player) {
        Set<BlockKey> result = new HashSet<>();
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        int radiusSquared = AUTO_BAN_LOOK_RADIUS * AUTO_BAN_LOOK_RADIUS;
        World world = player.getWorld();

        for (int distance = 1; distance <= AUTO_BAN_LOOK_DISTANCE; distance++) {
            Location center = eye.clone().add(direction.clone().multiply(distance));
            int centerX = center.getBlockX();
            int centerY = center.getBlockY();
            int centerZ = center.getBlockZ();
            for (int dx = -AUTO_BAN_LOOK_RADIUS; dx <= AUTO_BAN_LOOK_RADIUS; dx++) {
                for (int dy = -AUTO_BAN_LOOK_RADIUS; dy <= AUTO_BAN_LOOK_RADIUS; dy++) {
                    for (int dz = -AUTO_BAN_LOOK_RADIUS; dz <= AUTO_BAN_LOOK_RADIUS; dz++) {
                        if (dx * dx + dy * dy + dz * dz > radiusSquared) continue;
                        Block block = world.getBlockAt(centerX + dx, centerY + dy, centerZ + dz);
                        if (isFire(block.getType())) result.add(BlockKey.of(block));
                    }
                }
            }
        }
        return result;
    }

    private void banFireSuspect(FireTrace trace, String source) {
        Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(trace.playerName(), AUTO_BAN_MESSAGE, (java.util.Date) null, source);
        Player online = Bukkit.getPlayer(trace.playerId());
        if (online != null) online.kickPlayer(AUTO_BAN_MESSAGE);
    }

    private Optional<FireTrace> findNearestTrace(Map<BlockKey, FireTrace> traces, Block block, int radius, long now) {
        BlockKey center = BlockKey.of(block);
        int radiusSquared = radius * radius;
        FireTrace best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Map.Entry<BlockKey, FireTrace> entry : traces.entrySet()) {
            BlockKey key = entry.getKey();
            FireTrace trace = entry.getValue();
            if (!key.world().equals(center.world()) || trace.expired(now)) continue;
            int distance = key.distanceSquared(center);
            if (distance <= radiusSquared && distance < bestDistance) {
                bestDistance = distance;
                best = trace;
            }
        }
        return Optional.ofNullable(best);
    }

    private void cleanupFireTraces() {
        long now = System.currentTimeMillis();
        fireTraces.entrySet().removeIf(entry -> entry.getValue().expired(now));
        lavaTraces.entrySet().removeIf(entry -> entry.getValue().expired(now));
    }

    private boolean isFire(Material material) {
        return material == Material.FIRE || material == Material.SOUL_FIRE;
    }

    private boolean bucketEmptyBlocked(PlayerBucketEmptyEvent event) {
        Block clicked = event.getBlockClicked();
        if (clicked != null && sameBlock(clicked.getLocation(), event.getBlock().getLocation()) && clicked.getBlockData() instanceof Waterlogged) {
            return !allowed(clicked.getLocation(), Flag.WATERLOGGING);
        }
        if (clicked == null) return !allowed(event.getBlock().getLocation(), Flag.BLOCK_PLACE);
        return !allowed(event.getBlock().getLocation(), Flag.BLOCK_PLACE)
                || !allowed(clicked.getRelative(event.getBlockFace()).getLocation(), Flag.BLOCK_PLACE);
    }

    private boolean bucketFillBlocked(PlayerBucketFillEvent event) {
        Block clicked = event.getBlockClicked();
        if (clicked != null && sameBlock(clicked.getLocation(), event.getBlock().getLocation()) && clicked.getBlockData() instanceof Waterlogged) {
            return !allowed(clicked.getLocation(), Flag.WATERLOGGING);
        }
        return !allowed(event.getBlock().getLocation(), Flag.BLOCK_BREAK);
    }

    private boolean isEnteringBlockedForMob(Location from, Location to) {
        Optional<Region> fromRegion = regionManager.highestRegion(from);
        Optional<Region> toRegion = regionManager.highestRegion(to);
        if (toRegion.isEmpty() || !isRegionChange(fromRegion, toRegion)) return false;
        return !allowed(to, Flag.MOB_ENTRY);
    }

    private boolean isRegionChange(Optional<Region> fromRegion, Optional<Region> toRegion) {
        if (fromRegion.isEmpty() && toRegion.isEmpty()) return false;
        if (fromRegion.isEmpty() || toRegion.isEmpty()) return true;
        Region from = fromRegion.get();
        Region to = toRegion.get();
        return !from.world().equals(to.world()) || !from.name().equals(to.name());
    }

    private void clearMobTargetsOnProtectedEntry(Player player, Location from, Location to) {
        if (to == null || !isRegionChange(regionManager.highestRegion(from), regionManager.highestRegion(to))) return;
        if (!allowed(to, Flag.MOB_TARGET)) clearMobTargets(player);
    }

    private void clearMobTargets(Player player) {
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof Mob mob && player.equals(mob.getTarget())) {
                mob.setTarget(null);
            }
        }
    }

    private boolean isBlockedByCombatExitDelay(Player player, Optional<Region> fromRegion, Optional<Region> toRegion) {
        if (fromRegion.isEmpty()) return false;
        Region region = fromRegion.get();
        if (region.combatExitDelaySeconds() <= 0) return false;
        if (toRegion.isPresent() && toRegion.get().world().equals(region.world()) && toRegion.get().name().equals(region.name())) {
            return false;
        }
        Long lastDamage = lastCombatDamage.get(player.getUniqueId());
        if (lastDamage == null) return false;
        long remainingMillis = region.combatExitDelaySeconds() * 1000L - (System.currentTimeMillis() - lastDamage);
        if (remainingMillis <= 0) return false;
        long remainingSeconds = Math.max(1, (remainingMillis + 999) / 1000);
        msg(player, ChatColor.RED + "전투 중에는 결투장을 나갈 수 없습니다. " + remainingSeconds + "초 후 다시 시도하세요.");
        return true;
    }

    private Optional<Region> regionFromKey(String key) {
        String[] parts = key.split(":", 2);
        if (parts.length != 2) return Optional.empty();
        return regionManager.get(parts[0], parts[1]);
    }

    private String regionKey(Region region) {
        return region.world() + ":" + region.name();
    }

    private Optional<Location> spawnOf(Region region) {
        World world = Bukkit.getWorld(region.spawnWorld());
        if (world == null || !region.hasSpawn()) return Optional.empty();
        return Optional.of(region.spawnLocation(world));
    }

    private File logoutFile() {
        return new File(getDataFolder(), "logout-regions.yml");
    }

    private void loadLogoutRegions() {
        logoutRegions.clear();
        File file = logoutFile();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String uuid : yaml.getKeys(false)) {
            try {
                logoutRegions.put(UUID.fromString(uuid), yaml.getString(uuid));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveLogoutRegions() {
        YamlConfiguration yaml = new YamlConfiguration();
        logoutRegions.forEach((uuid, region) -> yaml.set(uuid.toString(), region));
        try {
            yaml.save(logoutFile());
        } catch (IOException e) {
            getLogger().warning("Failed to save logout regions: " + e.getMessage());
        }
    }

    private boolean isCrossRegionDamage(Location attacker, Location victim) {
        Optional<Region> attackerRegion = regionManager.highestRegion(attacker);
        Optional<Region> victimRegion = regionManager.highestRegion(victim);
        return isRegionChange(attackerRegion, victimRegion);
    }

    private Entity damagingEntity(Entity entity) {
        if (entity instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return entity;
    }

    private Player asPlayer(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof org.bukkit.projectiles.ProjectileSource source && source instanceof Player player) return player;
        if (entity instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private boolean isMobAttack(Entity entity) {
        if (entity instanceof Mob) return true;
        if (entity instanceof org.bukkit.entity.Projectile projectile && projectile.getShooter() instanceof Mob) return true;
        return false;
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

    private record BlockKey(String world, int x, int y, int z) {
        static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        }

        int distanceSquared(BlockKey other) {
            int dx = x - other.x;
            int dy = y - other.y;
            int dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private record FireTrace(UUID playerId, String playerName, BlockKey source, BlockKey current, long lastSeen) {
        static FireTrace of(Player player, Block block, long now) {
            BlockKey key = BlockKey.of(block);
            return new FireTrace(player.getUniqueId(), player.getName(), key, key, now);
        }

        FireTrace at(Block block, long now) {
            return new FireTrace(playerId, playerName, source, BlockKey.of(block), now);
        }

        boolean expired(long now) {
            return now - lastSeen > FIRE_TRACE_TTL_MILLIS;
        }
    }
}
