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
import space.ranzeplay.containeritemfinder.service.ChestSearchService;
import net.minecraft.text.Text;

public class ContainerItemFinderCommand {
    private final ChestSearchService searchService;

    public ContainerItemFinderCommand(ChestSearchService searchService) {
        this.searchService = searchService;
    }

    private void executeSearch(ServerCommandSource source, ServerWorld world, Vec3d pos, int range, Item item, int count) {
        new Thread(() -> {
            source.sendMessage(Text.literal("Searching for items..."));
            Text result = searchService.searchChests(source, world, pos, range, item, count);
            source.sendMessage(result);
        }).start();
    }

    public void register() {
        Main.getLogger().info("Registering commands");
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("cif")
                    .then(CommandManager.literal("cancel")
                        .executes(context -> {
                            Text result = searchService.cancelSearch(context.getSource());
                            context.getSource().sendMessage(result);
                            return 1;
                        }))
                    .then(CommandManager.argument("range", IntegerArgumentType.integer(1))
                        .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                            .executes(context -> {
                                int range = IntegerArgumentType.getInteger(context, "range");
                                var item = ItemStackArgumentType.getItemStackArgument(context, "item");
                                var source = context.getSource();
                                var world = source.getWorld();
                                var pos = source.getPosition();
                                
                                executeSearch(source, world, pos, range, item.getItem(), -1);
                                return 1;
                            })
                            .then(CommandManager.argument("count", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    int range = IntegerArgumentType.getInteger(context, "range");
                                    var item = ItemStackArgumentType.getItemStackArgument(context, "item");
                                    int count = IntegerArgumentType.getInteger(context, "count");
                                    var source = context.getSource();
                                    var world = source.getWorld();
                                    var pos = source.getPosition();
                                    
                                    executeSearch(source, world, pos, range, item.getItem(), count);
                                    return 1;
                                })
                            )
                        )
                    )
            );
        });
    }
} 