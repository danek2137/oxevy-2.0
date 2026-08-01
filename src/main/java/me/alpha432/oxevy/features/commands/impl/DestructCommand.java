package me.alpha432.oxevy.features.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.commands.Command;
import me.alpha432.oxevy.features.settings.Bind;
import me.alpha432.oxevy.manager.CommandManager;

import static me.alpha432.oxevy.util.traits.Util.EVENT_BUS;

public class DestructCommand extends Command {
    public DestructCommand() {
        super("destruct");
        setDescription("Removes all client traces from memory");
    }

    @Override
    public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
        builder.executes(ctx -> {
            if (Oxevy.destroyed) {
                Command.sendMessage("{gray}Oxevy is already destroyed.");
                return success();
            }

            Oxevy.moduleManager.getModules().forEach(m -> {
                if (m.isEnabled()) m.disable();
                m.bind.setValue(Bind.none());
                EVENT_BUS.unregister(m);
            });

            Oxevy.friendManager.getFriends().clear();
            Oxevy.targetManager.setTarget(null);

            Oxevy.destroyed = true;

            Command.sendMessage("{red}Oxevy traces removed from memory.");
            return success();
        });
    }
}
