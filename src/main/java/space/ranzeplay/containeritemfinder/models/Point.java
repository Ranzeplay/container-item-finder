package space.ranzeplay.containeritemfinder.models;

import lombok.*;
import net.minecraft.util.math.BlockPos;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Point {
    private int x;
    private int y;
    private int z;

    public BlockPos toBlockPos() {
        return new BlockPos(x, y, z);
    }
}
