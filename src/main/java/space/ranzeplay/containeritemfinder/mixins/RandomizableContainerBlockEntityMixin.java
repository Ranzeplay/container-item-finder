package space.ranzeplay.containeritemfinder.mixins;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.ranzeplay.containeritemfinder.Main;
import space.ranzeplay.containeritemfinder.models.Location;

import java.util.Objects;

@Mixin(RandomizableContainerBlockEntity.class)
public class RandomizableContainerBlockEntityMixin extends BlockEntityMixin {
    @Inject(method = "setItem", at = @At("HEAD"))
    private void onSetItem(int slot, ItemStack stack, CallbackInfo ci) {
        var self = (RandomizableContainerBlockEntity) (Object) this;
        if ((self instanceof ShulkerBoxBlockEntity || self instanceof ChestBlockEntity) && Main.getTrackingService() != null) {
            Main.getTrackingService().queueScan(new Location(Objects.requireNonNull(self.getLevel()), self.getBlockPos()));
        }
    }
}
