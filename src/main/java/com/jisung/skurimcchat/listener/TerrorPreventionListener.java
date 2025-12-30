package com.jisung.skurimcchat.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class TerrorPreventionListener implements Listener {
    private final JavaPlugin plugin;

    public TerrorPreventionListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // 라이터 사용, 화염구 사용, 엔드 수정 설치
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null) return;

        Material type = item.getType();
        Player player = event.getPlayer();
        Location loc;

        if (type == Material.FLINT_AND_STEEL) {
            // 라이터 사용
            Block clickedBlock = event.getClickedBlock();
            loc = clickedBlock != null ? clickedBlock.getLocation() : player.getLocation();
            logTerrorAction(player, "라이터", "사용", loc);
        } else if (type == Material.FIRE_CHARGE) {
            // 화염구 사용
            Block clickedBlock = event.getClickedBlock();
            loc = clickedBlock != null ? clickedBlock.getLocation() : player.getLocation();
            logTerrorAction(player, "화염구", "사용", loc);
        } else if (type == Material.END_CRYSTAL && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // 엔드 수정 설치 (엔티티이므로 PlayerInteractEvent로 감지)
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null) {
                // 클릭한 블록의 위쪽에 설치되므로 방향을 고려
                BlockFace face = event.getBlockFace();
                loc = clickedBlock.getLocation().add(face.getDirection());
                logTerrorAction(player, "엔드 수정", "설치", loc);
            }
        }
    }

    // TNT 설치
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        Material type = event.getBlock().getType();
        Player player = event.getPlayer();
        Location loc = event.getBlock().getLocation();

        if (type == Material.TNT) {
            logTerrorAction(player, "TNT", "설치", loc);
        }
        // 엔드 수정은 엔티티이므로 BlockPlaceEvent가 아닌 PlayerInteractEvent나 EntitySpawnEvent로 처리
    }

    // TNT 수레 설치, 엔드 수정 설치 (EntitySpawnEvent로 감지 - PlayerInteractEvent에서 놓친 경우 대비)
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntitySpawn(EntitySpawnEvent event) {
        Location loc = event.getLocation();
        EntityType entityType = event.getEntityType();
        
        if (entityType == EntityType.TNT_MINECART && event.getEntity() instanceof org.bukkit.entity.minecart.ExplosiveMinecart) {
            // TNT 수레 설치 - 플레이어가 스폰시킨 경우만 감지 (자연 스폰 제외)
            // 주변 플레이어 중 가장 가까운 플레이어 찾기
            Player nearestPlayer = null;
            double nearestDistance = Double.MAX_VALUE;
            for (Player player : event.getEntity().getWorld().getPlayers()) {
                double distance = player.getLocation().distance(loc);
                if (distance < 10 && distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestPlayer = player;
                }
            }
            if (nearestPlayer != null) {
                logTerrorAction(nearestPlayer, "TNT 수레", "설치", loc);
            } else {
                // 플레이어를 찾을 수 없는 경우 위치만 기록
                plugin.getLogger().warning(String.format(
                    "[TERROR_LOG] TNT 수레가 %s %d,%d,%d에 설치되었습니다.",
                    loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
                ));
            }
        } else if (entityType == EntityType.END_CRYSTAL) {
            // 엔드 수정 설치 (PlayerInteractEvent에서 놓친 경우 대비)
            // 주변 플레이어 중 가장 가까운 플레이어 찾기
            Player nearestPlayer = null;
            double nearestDistance = Double.MAX_VALUE;
            for (Player player : event.getEntity().getWorld().getPlayers()) {
                double distance = player.getLocation().distance(loc);
                if (distance < 10 && distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestPlayer = player;
                }
            }
            if (nearestPlayer != null) {
                logTerrorAction(nearestPlayer, "엔드 수정", "설치", loc);
            } else {
                // 플레이어를 찾을 수 없는 경우 위치만 기록
                plugin.getLogger().warning(String.format(
                    "[TERROR_LOG] 엔드 수정이 %s %d,%d,%d에 설치되었습니다.",
                    loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
                ));
            }
        }
    }

    // 용암 설치
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.getBucket() == Material.LAVA_BUCKET) {
            logTerrorAction(event.getPlayer(), "용암", "설치", event.getBlock().getLocation());
        }
    }

    // TNT 점화, 크리퍼 점화
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockIgnite(BlockIgniteEvent event) {
        BlockIgniteEvent.IgniteCause cause = event.getCause();
        Entity ignitingEntity = event.getIgnitingEntity();
        Location loc = event.getBlock().getLocation();

        // 플레이어가 직접 점화한 경우
        if (ignitingEntity instanceof Player) {
            Player player = (Player) ignitingEntity;
            
            // TNT 점화 확인
            if (event.getBlock().getType() == Material.TNT) {
                if (cause == BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL || 
                    cause == BlockIgniteEvent.IgniteCause.FIREBALL ||
                    cause == BlockIgniteEvent.IgniteCause.SPREAD) {
                    logTerrorAction(player, "TNT", "점화", loc);
                }
            }
        }

        // 크리퍼 점화 (크리퍼가 원인인 경우)
        if (cause == BlockIgniteEvent.IgniteCause.EXPLOSION && 
            ignitingEntity != null && 
            ignitingEntity.getType() == EntityType.CREEPER) {
            // 크리퍼가 점화한 경우 - 위치만 기록
            plugin.getLogger().warning(String.format(
                "[TERROR_LOG] 크리퍼가 %s %d,%d,%d에서 블록을 점화했습니다.",
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
            ));
        }
    }

    // 엔드 수정 폭발, TNT 수레 폭발
    @EventHandler(priority = EventPriority.MONITOR)
    public void onExplode(EntityExplodeEvent event) {
        Entity entity = event.getEntity();
        Location loc = event.getLocation();

        if (entity.getType() == EntityType.END_CRYSTAL) {
            // 엔드 수정 폭발 - 위치만 기록
            plugin.getLogger().warning(String.format(
                "[TERROR_LOG] 엔드 수정이 %s %d,%d,%d에서 폭발했습니다.",
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
            ));
        } else if (entity.getType() == EntityType.TNT_MINECART) {
            // TNT 수레 폭발 - 위치만 기록
            plugin.getLogger().warning(String.format(
                "[TERROR_LOG] TNT 수레가 %s %d,%d,%d에서 폭발했습니다.",
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
            ));
        }
    }

    // 발사기로 화염구 발사
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDispense(BlockDispenseEvent event) {
        if (event.getItem().getType() == Material.FIRE_CHARGE) {
            Location loc = event.getBlock().getLocation();
            // 발사한 플레이어 정보는 직접적으로 없음 - 위치만 기록
            plugin.getLogger().warning(String.format(
                "[TERROR_LOG] 발사기가 %s %d,%d,%d에서 화염구를 발사했습니다.",
                loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()
            ));
        }
    }

    private void logTerrorAction(Player player, String item, String action, Location loc) {
        String logMsg = String.format(
            "[TERROR_LOG] %s(%s)님이 %s %d,%d,%d에서 %s을(를) %s했습니다.",
            player.getName(),
            player.getUniqueId().toString(),
            loc.getWorld().getName(),
            loc.getBlockX(),
            loc.getBlockY(),
            loc.getBlockZ(),
            item,
            action
        );
        plugin.getLogger().warning(logMsg);
    }
}

