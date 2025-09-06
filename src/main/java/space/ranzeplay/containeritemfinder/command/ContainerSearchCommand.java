package space.ranzeplay.containeritemfinder.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.item.Item;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import space.ranzeplay.containeritemfinder.Main;
import space.ranzeplay.containeritemfinder.service.ContainerSearchService;
import net.minecraft.text.Text;
import com.mojang.brigadier.context.CommandContext;

public class ContainerSearchCommand {
    private final ContainerSearchService searchService;

    public ContainerSearchCommand(ContainerSearchService searchService) {
        this.searchService = searchService;
    }

    private void executeSearch(ServerCommandSource source, ServerWorld world, Vec3d pos, int range, Item item, int count) {
        new Thread(() -> {
            source.sendMessage(Text.translatable("info.cif.status.searching"));
            Text result = searchService.searchChests(source, world, pos, range, item, count);
            source.sendMessage(result);
        }).start();
    }

    private int executeCommand(CommandContext<ServerCommandSource> context, int count) {
        int range = IntegerArgumentType.getInteger(context, "range");
        var item = ItemStackArgumentType.getItemStackArgument(context, "item");
        var source = context.getSource();
        var world = source.getWorld();
        var pos = source.getPosition();
        
        executeSearch(source, world, pos, range, item.getItem(), count);
        return 1;
    }

    public void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("cif")
                    .then(CommandManager.literal("search")
                        .then(CommandManager.argument("range", IntegerArgumentType.integer(1))
                            .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                                .executes(context -> executeCommand(context, -1))
                                .then(CommandManager.argument("count", IntegerArgumentType.integer(1))
                                    .executes(context -> executeCommand(context, IntegerArgumentType.getInteger(context, "count")))
                                )
                            )
                        )
                    )
            );
        });
    }
}
