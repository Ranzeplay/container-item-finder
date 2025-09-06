package space.ranzeplay.containeritemfinder.mixins;

import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.ranzeplay.containeritemfinder.Main;

@Mixin(ServerPlayerEntity.class)
public class ServerPlayerEntityMixin {
    @Inject(method = "onHandledScreenClosed" , at = @At("HEAD"))
    private void onHandledScreenClosed(CallbackInfo ci) {
        final var player = (ServerPlayerEntity)(Object)this;

        if(player.currentScreenHandler instanceof GenericContainerScreenHandler || player.currentScreenHandler instanceof ShulkerBoxScreenHandler) {
            if(Main.getTrackingService() != null) {
                Main.getTrackingService().queueScan(player.getPos(), player.getWorld(), 10);
            }
        }
    }
}
