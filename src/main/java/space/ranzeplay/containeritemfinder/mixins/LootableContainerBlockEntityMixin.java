package space.ranzeplay.containeritemfinder.mixins;

import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.ranzeplay.containeritemfinder.Main;

import java.sql.SQLException;

@Mixin(LootableContainerBlockEntity.class)
public class LootableContainerBlockEntityMixin extends BlockEntityMixin {
    @Inject(method = "setStack", at = @At("HEAD"))
    private void onSetStack(int slot, ItemStack stack, CallbackInfo ci) {
        var self = (LootableContainerBlockEntity)(Object)this;
        if ((self instanceof ShulkerBoxBlockEntity || self instanceof ChestBlockEntity) && Main.getTrackingService() != null) {
            try {
                Main.getTrackingService().scanOne(this.world, this.pos, true);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
