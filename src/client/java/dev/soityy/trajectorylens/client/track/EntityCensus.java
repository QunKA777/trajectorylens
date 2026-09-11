package dev.soityy.trajectorylens.client.track;

import dev.soityy.trajectorylens.client.ui.Report;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.projectile.Projectile;

/**
 * Nearby entity census: counts by rough category plus the busiest entity types.
 * Useful to see mob-cap pressure, item pile-ups and projectile spam; the scan
 * runs once per second and only covers what this client can see.
 */
public final class EntityCensus {

    public static final double RADIUS = 64.0;

    private int hostiles;
    private int animals;
    private int mobs;
    private int items;
    private int xpOrbs;
    private int projectiles;
    private int total;
    private final List<String> topTypes = new ArrayList<>();

    public void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        var player = mc.player;
        if (level == null || player == null || player.tickCount % 20 != 0) {
            return;
        }
        int h = 0;
        int a = 0;
        int m = 0;
        int it = 0;
        int xp = 0;
        int pr = 0;
        Map<String, Integer> byType = new HashMap<>();
        for (Entity e : level.getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(RADIUS))) {
            if (!e.isAlive() || e == player) {
                continue;
            }
            if (e instanceof Monster) {
                h++;
            } else if (e instanceof Animal) {
                a++;
            } else if (e instanceof Mob) {
                m++;
            } else if (e instanceof ItemEntity) {
                it++;
            } else if (e instanceof ExperienceOrb) {
                xp++;
            } else if (e instanceof Projectile) {
                pr++;
            }
            String id = e.getType().toShortString();
            byType.merge(id, 1, Integer::sum);
        }
        this.hostiles = h;
        this.animals = a;
        this.mobs = m;
        this.items = it;
        this.xpOrbs = xp;
        this.projectiles = pr;
        this.total = h + a + m + it + xp + pr;
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(byType.entrySet());
        sorted.sort(Comparator.comparingInt((Map.Entry<String, Integer> en) -> -en.getValue()));
        this.topTypes.clear();
        for (int i = 0; i < Math.min(6, sorted.size()); i++) {
            this.topTypes.add(sorted.get(i).getKey() + "×" + sorted.get(i).getValue());
        }
    }

    public int total() {
        return this.total;
    }

    public List<String> topTypes() {
        return this.topTypes;
    }

    /** One-line summary for the panel. */
    public String compact() {
        return String.format("附近实体 %d: 敌对%d 动物%d 其他%d | 物品%d 经验%d 投掷物%d",
            this.total, this.hostiles, this.animals, this.mobs, this.items, this.xpOrbs, this.projectiles);
    }

    public String topLine() {
        return this.topTypes.isEmpty() ? "最多: (无)" : "最多: " + String.join(", ", this.topTypes);
    }

    /** Multi-line report for chat. */
    public List<String> report() {
        List<String> out = new ArrayList<>();
        out.add("[TrajectoryLens] " + this.compact());
        out.add("[TrajectoryLens] " + this.topLine());
        if (this.items > 200) {
            out.add("[TrajectoryLens] §e提示: 附近物品超过 200 个,大量掉落物会拖慢服务器/客户端渲染。");
        }
        if (this.hostiles > 60) {
            out.add("[TrajectoryLens] §e提示: 敌对生物 >60,接近常见怪物上限(70),刷怪塔可能被压制。");
        }
        return out;
    }
}
