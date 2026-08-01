package me.alpha432.oxevy.features.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.commands.Command;
import me.alpha432.oxevy.features.modules.Module;
import me.alpha432.oxevy.manager.CommandManager;

import java.util.HashSet;
import java.util.Set;

public class TempDCommand extends Command {
    public TempDCommand() {
        super("tempd", "tempdisable");
        setDescription("Temporarily disables or re-enables the entire client");
    }

    @Override
    public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
        builder.executes(ctx -> {
            boolean state = Oxevy.tempDisabled;
            Command.sendMessage("{gray}Temporary disable is currently: %s",
                    state ? "{green}ON" : "{red}OFF");
            return success();
        }).then(literal("on")
                .executes(ctx -> {
                    if (Oxevy.tempDisabled) {
                        Command.sendMessage("{gray}Already temporarily disabled");
                        return success();
                    }
                    Oxevy.tempDisabledModules.clear();
                    for (Module m : Oxevy.moduleManager.getModules()) {
                        if (m.isEnabled()) {
                            Oxevy.tempDisabledModules.add(m.getName());
                            m.disable();
                        }
                    }
                    Oxevy.tempDisabled = true;
                    Command.sendMessage("{red}Client temporarily disabled. Use {gray}.tempd off{red} to restore.");
                    return success();
                }))
                .then(literal("off")
                        .executes(ctx -> {
                            if (!Oxevy.tempDisabled) {
                                Command.sendMessage("{gray}Client is not temporarily disabled");
                                return success();
                            }
                            Oxevy.tempDisabled = false;
                            for (Module m : Oxevy.moduleManager.getModules()) {
                                if (Oxevy.tempDisabledModules.contains(m.getName())) {
                                    m.enable();
                                }
                            }
                            Oxevy.tempDisabledModules.clear();
                            Command.sendMessage("{green}Client restored to previous state.");
                            return success();
                        }));
    }
}
