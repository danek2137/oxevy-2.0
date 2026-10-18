package me.alpha432.oxevy.features.modules.render;

import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.event.impl.render.Render3DEvent;
import me.alpha432.oxevy.event.system.Subscribe;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.features.settings.Setting;
import me.alpha432.oxevy.util.render.RenderUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public class PlayerESPModule extends Module {
    
    public Setting<EspStyle> style = mode("Style", EspStyle.BOTH);
    public Setting<Boolean> showBoxes = bool("Boxes", true);
    public Setting<Boolean> showLines = bool("Lines", true);
    public Setting<BoxSize> boxSize = mode("BoxSize", BoxSize.ACCURATE);
    public Setting<Boolean> filterSleeping = bool("FilterSleeping", false);
    public Setting<Boolean> filterInvisible = bool("FilterInvisible", false);
    public Setting<Float> lineWidth = num("LineWidth", 1.5f, 0.5f, 3f);
    public Setting<Boolean> throughWalls = bool("ThroughWalls", true);
    
    private final List<Player> players = new ArrayList<>();
    
    public PlayerESPModule() {
        super("PlayerESP", "Highlights players with boxes and tracers", Category.RENDER);
    }
    
    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;
        
        players.clear();
        
        for (Player player : mc.level.players()) {
            if (player == mc.player) continue;
            if (player.isRemoved()) continue;
            if (me.alpha432.oxevy.util.entity.GhostPlayerEntity.isGhost(player)) continue;
            if (filterSleeping.getValue() && player.isSleeping()) continue;
            if (filterInvisible.getValue() && player.isInvisible()) continue;
            
            players.add(player);
        }
        
        double extraSize = switch (boxSize.getValue()) {
            case ACCURATE -> 0.0;
            case FANCY -> 0.02;
        };
        
        boolean hasBoxes = showBoxes.getValue() && style.getValue() != EspStyle.LINES;
        boolean hasLines = showLines.getValue() && style.getValue() != EspStyle.BOXES;
        
        if (hasBoxes) {
            for (Player player : players) {
                AABB box = getLerpedBox(player, event.getDelta());
                if (extraSize != 0) box = box.move(0, extraSize, 0).inflate(extraSize);
                
                int color = getColor(player);
                RenderUtil.drawOutlinedBoxes(event.getMatrix(), List.of(box), color, throughWalls.getValue(), lineWidth.getValue());
            }
        }
        
        if (hasLines) {
            Vec3 start = RenderUtil.getTracerOrigin();
            List<Vec3> ends = new ArrayList<>(players.size());
            List<Integer> colors = new ArrayList<>(players.size());
            
            for (Player player : players) {
                AABB box = getLerpedBox(player, event.getDelta());
                ends.add(box.getCenter());
                colors.add(getColor(player));
            }
            
            RenderUtil.drawTracersToPoints(event.getMatrix(), event.getDelta(), start, ends, colors, lineWidth.getValue(), throughWalls.getValue());
        }
    }
    
    private AABB getLerpedBox(Player player, float delta) {
        double x = Mth.lerp(delta, player.xo, player.getX());
        double y = Mth.lerp(delta, player.yo, player.getY());
        double z = Mth.lerp(delta, player.zo, player.getZ());
        
        double width = player.getBbWidth();
        double height = player.getBbHeight();
        
        // Offset to get proper bounding box (player position is center-bottom)
        return new AABB(
            x - width / 2,
            y,
            z - width / 2,
            x + width / 2,
            y + height,
            z + width / 2
        );
    }
    
    private int getColor(Player player) {
        if (Oxevy.friendManager.isFriend(player)) {
            return 0x8000FFFF; // Cyan for friends
        }
        
        float dist = mc.player.distanceTo(player);
        float f = Mth.clamp(dist / 30f, 0, 1);
        
        // Color gradient: Red (close) -> Yellow (medium) -> Green (far)
        int r = (int) (255 * (1 - f));
        int g = (int) (255 * f);
        int b = 0;
        int a = 0x80; // Alpha
        
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
    
    public enum EspStyle {
        BOXES,
        LINES,
        BOTH
    }
    
    public enum BoxSize {
        ACCURATE,
        FANCY
    }
}
