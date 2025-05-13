package space.ranzeplay.containeritemfinder.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import space.ranzeplay.containeritemfinder.Main;
import space.ranzeplay.containeritemfinder.service.ContainerIndexService;
import net.minecraft.text.Text;

public class ContainerIndexCommand {
    private final ContainerIndexService indexService;

    public ContainerIndexCommand(ContainerIndexService indexService) {
        this.indexService = indexService;
    }

    public void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("cif")
                    .then(CommandManager.literal("index")
                        .then(CommandManager.argument("range", IntegerArgumentType.integer(1))
                            .executes(context -> {
                                int range = IntegerArgumentType.getInteger(context, "range");
                                var source = context.getSource();
                                var world = source.getWorld();
                                var pos = source.getPosition();
                                
                                new Thread(() -> {
                                    source.sendMessage(Text.literal("Indexing containers..."));
                                    Text result = indexService.indexContainers(source, world, pos, range);
                                    source.sendMessage(result);
                                }).start();
                                return 1;
                            })
                        )
                    )
            );
        });
    }
} 