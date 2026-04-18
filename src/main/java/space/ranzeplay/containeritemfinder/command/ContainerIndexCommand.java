package space.ranzeplay.containeritemfinder.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import space.ranzeplay.containeritemfinder.service.ContainerIndexService;

public class ContainerIndexCommand {
    private final ContainerIndexService indexService;

    public ContainerIndexCommand(ContainerIndexService indexService) {
        this.indexService = indexService;
    }

    public void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("cif")
                    .then(LiteralArgumentBuilder.<CommandSourceStack>literal("index")
                            .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("range", IntegerArgumentType.integer(1))
                            .executes(context -> {
                                int range = IntegerArgumentType.getInteger(context, "range");
                                var source = context.getSource();
                                var world = source.getLevel();
                                var pos = source.getPosition();
                                
                                new Thread(() -> {
                                    source.sendSystemMessage(Component.translatable("info.cif.status.indexing"));
                                    Component result = indexService.indexContainers(source, world, pos, range);
                                    source.sendSystemMessage(result);
                                }).start();
                                return 1;
                            })
                        )
                    )
            );
        });
    }
}
