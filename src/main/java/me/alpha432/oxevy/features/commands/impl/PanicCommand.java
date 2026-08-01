package me.alpha432.oxevy.features.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.Feature;
import me.alpha432.oxevy.features.commands.Command;
import me.alpha432.oxevy.manager.CommandManager;

public class PanicCommand extends Command {
    public PanicCommand() {
        super("panic");
        setDescription("Disables all modules");
    }

    @Override
    public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
        builder.executes(ctx -> {
            Oxevy.moduleManager.getModules().stream()
                    .filter(Feature::isEnabled)
                    .forEach(m -> m.disable());
            return success("Panic! All modules disabled.");
        });
    }
}
