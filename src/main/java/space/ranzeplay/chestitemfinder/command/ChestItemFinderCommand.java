package space.ranzeplay.chestitemfinder.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.ItemStackArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import space.ranzeplay.chestitemfinder.service.ChestSearchService;
import net.minecraft.text.Text;

public class ChestItemFinderCommand {
    private final ChestSearchService searchService;

    public ChestItemFinderCommand(ChestSearchService searchService) {
        this.searchService = searchService;
    }

    public void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                net.minecraft.server.command.CommandManager.literal("cif")
                    .then(net.minecraft.server.command.CommandManager.argument("range", IntegerArgumentType.integer(1))
                        .then(net.minecraft.server.command.CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                            .executes(context -> {
                                int range = IntegerArgumentType.getInteger(context, "range");
                                var item = ItemStackArgumentType.getItemStackArgument(context, "item");
                                var source = context.getSource();
                                var world = source.getWorld();
                                var pos = source.getPosition();
                                
                                // Schedule the search in a separate thread
                                source.getServer().execute(() -> {
                                    source.sendMessage(Text.literal("Searching for items..."));
                                    Text result = searchService.searchChests(world, pos, range, item.getItem(), -1);
                                    source.sendMessage(result);
                                });
                                
                                return 1;
                            })
                            .then(net.minecraft.server.command.CommandManager.argument("count", IntegerArgumentType.integer(1))
                                .executes(context -> {
                                    int range = IntegerArgumentType.getInteger(context, "range");
                                    var item = ItemStackArgumentType.getItemStackArgument(context, "item");
                                    int count = IntegerArgumentType.getInteger(context, "count");
                                    var source = context.getSource();
                                    var world = source.getWorld();
                                    var pos = source.getPosition();
                                    
                                    // Schedule the search in a separate thread
                                    source.getServer().execute(() -> {
                                        source.sendMessage(Text.literal("Searching for items..."));
                                        Text result = searchService.searchChests(world, pos, range, item.getItem(), count);
                                        source.sendMessage(result);
                                    });
                                    
                                    return 1;
                                })
                            )
                        )
                    )
            );
        });
    }
} 