package space.ranzeplay.containeritemfinder.models;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

@Getter
@EqualsAndHashCode(callSuper = true)
public class Location extends Point {
    public Location(Level level, BlockPos pos) {
        super(pos.getX(), pos.getY(), pos.getZ());
        this.level = level.dimension().identifier().toString();
    }

    private final String level;
}
