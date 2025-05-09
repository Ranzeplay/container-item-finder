package space.ranzeplay.containeritemfinder.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.server.command.CommandManager;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import space.ranzeplay.containeritemfinder.service.ChestSearchService;
import net.minecraft.text.Text;

public class ContainerItemFinderCommand {
    private final ChestSearchService searchService;

    public ContainerItemFinderCommand(ChestSearchService searchService) {
        this.searchService = searchService;
    }

    public void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("cif")
                    .then(CommandManager.argument("range", IntegerArgumentType.integer(1))
                        .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
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
                            .then(CommandManager.argument("count", IntegerArgumentType.integer(1))
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