package space.ranzeplay.containeritemfinder.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import space.ranzeplay.containeritemfinder.Main;

final class CommandDiagnostics {
    private static final Component FAILURE_MESSAGE =
            Component.literal("Command failed. Check the server console for details.");

    private CommandDiagnostics() {
    }

    static Command<CommandSourceStack> logged(Command<CommandSourceStack> command) {
        return context -> {
            try {
                return command.run(context);
            } catch (CommandSyntaxException exception) {
                // Brigadier already reports expected command errors to the sender.
                throw exception;
            } catch (Exception exception) {
                logFailure(context, exception);
                throw new SimpleCommandExceptionType(FAILURE_MESSAGE).create();
            }
        };
    }

    static void runAsync(CommandContext<CommandSourceStack> context, Runnable task) {
        new Thread(() -> {
            try {
                task.run();
            } catch (Exception exception) {
                logFailure(context, exception);
                context.getSource().getServer().execute(() ->
                        context.getSource().sendFailure(FAILURE_MESSAGE));
            }
        }, "cif-command").start();
    }

    private static void logFailure(CommandContext<CommandSourceStack> context, Exception exception) {
        var source = context.getSource();
        Main.getLogger().error("Failed to execute command '{}' from '{}' in {} at {}",
                context.getInput(), source.getTextName(), source.getLevel().dimension(),
                source.getPosition(), exception);
    }
}
