package space.ranzeplay.containeritemfinder.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import space.ranzeplay.containeritemfinder.service.ContainerSearchService;
import com.mojang.brigadier.context.CommandContext;

public class ContainerSearchCommand {
    private final ContainerSearchService searchService;

    public ContainerSearchCommand(ContainerSearchService searchService) {
        this.searchService = searchService;
    }

    private void executeSearch(CommandSourceStack source, Level world, Vec3 pos, int range, Item item, int count) {
        new Thread(() -> {
            source.sendSystemMessage(Component.translatable("info.cif.status.searching"));
            Component result = searchService.searchChests(source, world, pos, range, item, count);
            source.sendSystemMessage(result);
        }).start();
    }

    private int executeCommand(CommandContext<CommandSourceStack> context, int count) {
        int range = IntegerArgumentType.getInteger(context, "range");
        var item = ItemArgument.getItem(context, "item");
        var source = context.getSource();
        var world = source.getLevel();
        var pos = source.getPosition();
        
        executeSearch(source, world, pos, range, item.item().value(), count);
        return 1;
    }

    public void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    LiteralArgumentBuilder.<CommandSourceStack>literal("cif")
                    .then(LiteralArgumentBuilder.<CommandSourceStack>literal("search")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("range", IntegerArgumentType.integer(1))
                            .then(RequiredArgumentBuilder.<CommandSourceStack, ItemInput>argument("item", ItemArgument.item(registryAccess))
                                .executes(context -> executeCommand(context, -1))
                                .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("count", IntegerArgumentType.integer(1))
                                    .executes(context -> executeCommand(context, IntegerArgumentType.getInteger(context, "count")))
                                )
                            )
                        )
                    )
            );
        });
    }
}
