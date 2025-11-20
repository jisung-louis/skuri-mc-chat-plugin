package com.jisung.skurimcchat;

import org.bukkit.entity.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;




public class EntityDeathMessageFormatter {

    public static String format(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        // 1) 주민 처리 (Villager)
        if (entity instanceof Villager villager) {
            String victimName = extractVillagerName(villager);
            String killerName = extractKiller(event);

            if (killerName != null) {
                return victimName + "이(가) " + killerName + "에게 살해되었습니다.";
            } else {
                return victimName + "이(가) 사망했습니다.";
            }
        }

        // 2) 길들여진 엔티티 처리 (Wolf, Cat, Horse 등)
        if (entity instanceof Tameable tameable && tameable.isTamed()) {
            String victimName = extractTamedEntityName(tameable);
            String killerName = extractKiller(event);

            if (killerName != null) {
                return victimName + "가 " + killerName + "에게 살해되었습니다.";
            } else {
                return victimName + "가 사망했습니다.";
            }
        }

        // 기본값 (처리하지 않는 엔티티)
        return null;
    }

    // -----------------------------------------
    // 주민 이름 생성
    // -----------------------------------------
    private static String extractVillagerName(Villager villager) {
        // 이름표가 있다면 우선 사용
        if (villager.customName() != null) {
            return villager.customName().toString();
        }

        // 직업 기반 한국어 이름
        String job = translateProfession(villager.getProfession());
        return job + " 주민";
    }

    private static String translateProfession(Villager.Profession prof ) {

        if (prof == Villager.Profession.FARMER) return "농부";
        if (prof == Villager.Profession.LIBRARIAN) return "사서";
        if (prof == Villager.Profession.CLERIC) return "성직자";
        if (prof == Villager.Profession.ARMORER) return "갑옷 제작자";
        if (prof == Villager.Profession.BUTCHER) return "도축업자";
        if (prof == Villager.Profession.CARTOGRAPHER) return "지도제작자";
        if (prof == Villager.Profession.FISHERMAN) return "어부";
        if (prof == Villager.Profession.FLETCHER) return "화살 제작자";
        if (prof == Villager.Profession.LEATHERWORKER) return "가죽공";
        if (prof == Villager.Profession.MASON) return "석공";
        if (prof == Villager.Profession.SHEPHERD) return "목동";
        if (prof == Villager.Profession.TOOLSMITH) return "도구 제작자";
        if (prof == Villager.Profession.WEAPONSMITH) return "무기 제작자";

        return "무직";
    }

    // -----------------------------------------
    // 길들여진 엔티티 이름 생성
    // -----------------------------------------
    private static String extractTamedEntityName(Tameable tameable) {
        Entity entity = (Entity) tameable;

        String typeKo = translateTamedType(entity);

        String ownerName = "알 수 없음";
        if (tameable.getOwner() instanceof Player owner) {
            ownerName = owner.getName();
        }

        // 이름표 있으면 → "<owner>의 개 '뽀삐'"
        if (entity.customName() != null) {
            return ownerName + "의 " + typeKo + " '" + entity.getName() + "'";
        }

        // 이름표 없으면 → "<owner>의 개"
        return ownerName + "의 " + typeKo;
    }

    private static String translateTamedType(Entity entity) {
        return switch (entity.getType()) {
            case WOLF -> "개";
            case CAT -> "고양이";
            case HORSE -> "말";
            case DONKEY -> "당나귀";
            case MULE -> "노새";
            case LLAMA -> "라마";
            case PARROT -> "앵무새";
            default -> "반려동물";
        };
    }

    // -----------------------------------------
    // 사망 가해자 추출
    // -----------------------------------------
    private static String extractKiller(EntityDeathEvent event) {
        EntityDamageEvent dmg = event.getEntity().getLastDamageCause();

        if (dmg instanceof EntityDamageByEntityEvent ent) {
            Entity damager = ent.getDamager();

            // 몹에게 죽은 경우 → Zombie, Skeleton 등
            if (damager instanceof LivingEntity living) {
                return living.getName();
            }

            // 화살/투사체
            if (damager instanceof Projectile projectile) {
                if (projectile.getShooter() instanceof LivingEntity shooter) {
                    return shooter.getName();
                }
            }
        }

        return null;
    }
}
