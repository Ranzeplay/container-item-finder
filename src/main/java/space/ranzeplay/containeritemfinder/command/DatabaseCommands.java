package space.ranzeplay.containeritemfinder.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.Item;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import space.ranzeplay.containeritemfinder.Main;

public class DatabaseCommands {
    public void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    CommandManager.literal("dif")
                            .then(CommandManager.literal("search")
                                    .then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                                            .then(CommandManager.argument("count", IntegerArgumentType.integer(1))
                                                    .then(CommandManager.argument("range", IntegerArgumentType.integer(1))
                                                            .executes(this::executeSearch))
                                                    .executes(this::executeSearch)
                                            )
                                            .executes(this::executeSearch)
                                    )
                            )
            );
        });
    }

    private int executeSearch(CommandContext<ServerCommandSource> context) {
        Item item = ItemStackArgumentType.getItemStackArgument(context, "item").getItem();
        Integer count = null;
        Integer range = null;

        try {
            count = context.getArgument("count", Integer.class);
        } catch (IllegalArgumentException ignored) {
        }

        try {
            range = context.getArgument("range", Integer.class);
        } catch (IllegalArgumentException ignored) {
        }

        Main.getTrackingService().searchTrackingItem(context.getSource(), context.getSource().getWorld(), context.getSource().getPosition(), range, item, count);

        return 1;
    }
}
