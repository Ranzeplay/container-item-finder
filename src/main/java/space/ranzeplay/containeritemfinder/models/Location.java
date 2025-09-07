package space.ranzeplay.containeritemfinder.models;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

@Getter
@EqualsAndHashCode(callSuper = true)
public class Location extends Point {
    public Location(World world, BlockPos pos) {
        super(pos.getX(), pos.getY(), pos.getZ());
        this.world = world.getRegistryKey().getValue().toString();
    }

    private final String world;
}
