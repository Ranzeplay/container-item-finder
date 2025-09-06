package space.ranzeplay.containeritemfinder.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.Item;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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
                            .then(CommandManager.literal("stats")
                                    .executes(this::executeStats))
                            .then(CommandManager.literal("rescan")
                                    .executes(this::rescan))
            );
        });
    }

    private int rescan(CommandContext<ServerCommandSource> context) {
        if(context.getSource().hasPermissionLevel(2)) {
            if(Main.getTrackingService().isScanning()) {
                context.getSource().sendMessage(Text.translatable("info.cif.db.rescan.busy").formatted(Formatting.RED));
            } else {
                context.getSource().sendMessage(Text.translatable("info.cif.db.rescan.start").formatted(Formatting.GREEN));
                Main.getTrackingService().tryScan(context.getSource().getServer());
            }
        } else {
            context.getSource().sendMessage(Text.translatable("info.cif.db.rescan.noperm").formatted(Formatting.RED));
        }

        return 1;
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

    private int executeStats(CommandContext<ServerCommandSource> context) {
        final var source = context.getSource();
        if (Main.getTrackingService().getLatestStatistics() == null) {
            source.sendMessage(Text.translatable("info.cif.stat.n_a").formatted(Formatting.RED));
        } else {
            source.sendMessage(Main.getTrackingService().getLatestStatistics().toText());
        }
        if (Main.getTrackingService().isScanning()) {
            source.sendMessage(Text.translatable("info.cif.stat.scanner.pre").append(Text.translatable("info.cif.stat.scanner.active").formatted(Formatting.GREEN)));
        } else {
            source.sendMessage(Text.translatable("info.cif.stat.scanner.pre").append(Text.translatable("info.cif.stat.scanner.inactive").formatted(Formatting.YELLOW)));
        }

        return 1;
    }
}
