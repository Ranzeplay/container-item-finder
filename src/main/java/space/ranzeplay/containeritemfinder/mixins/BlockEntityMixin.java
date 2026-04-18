package space.ranzeplay.containeritemfinder.mixins;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(BlockEntity.class)
public class BlockEntityMixin {
    @Shadow
    @SuppressWarnings("unused")
    protected Level level;

    @Final
    @Shadow
    @SuppressWarnings("unused")
    protected BlockPos worldPosition;
}
