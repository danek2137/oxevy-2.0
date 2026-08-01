package me.alpha432.oxevy.features.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.commands.Command;
import me.alpha432.oxevy.manager.CommandManager;

import java.util.List;
import java.util.StringJoiner;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public class ConfigCommand extends Command {
    public ConfigCommand() {
        super("config", "cfg");
        setDescription("Manages config profiles (save/load/list/delete)");
    }

    @Override
    public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
        builder.then(literal("list")
                        .executes(ctx -> {
                            List<String> configs = Oxevy.configManager.listConfigs();
                            StringJoiner joiner = new StringJoiner(", ");
                            String current = Oxevy.configManager.getCurrentConfig();
                            for (String c : configs) {
                                if (c.equals(current)) {
                                    joiner.add("{green}" + c + "{reset}");
                                } else {
                                    joiner.add(c);
                                }
                            }
                            Command.sendMessage("{gray}Available configs: %s", joiner);
                            Command.sendMessage("{gray}Current: {green}%s", current);
                            return success();
                        }))
                .then(literal("save")
                        .then(argument("name", word())
                                .executes(ctx -> {
                                    String name = getString(ctx, "name");
                                    if (Oxevy.configManager.saveProfile(name)) {
                                        Command.sendMessage("{green}Saved config profile: {gray}%s", name);
                                    } else {
                                        Command.sendMessage("{red}Failed to save config profile: {gray}%s", name);
                                    }
                                    return success();
                                })))
                .then(literal("load")
                        .then(argument("name", word())
                                .executes(ctx -> {
                                    String name = getString(ctx, "name");
                                    if (Oxevy.configManager.loadProfile(name)) {
                                        Command.sendMessage("{green}Loaded config profile: {gray}%s", name);
                                    } else {
                                        Command.sendMessage("{red}Failed to load config profile: {gray}%s", name);
                                    }
                                    return success();
                                })))
                .then(literal("delete")
                        .then(argument("name", word())
                                .executes(ctx -> {
                                    String name = getString(ctx, "name");
                                    if ("default".equalsIgnoreCase(name)) {
                                        Command.sendMessage("{red}Cannot delete the default profile");
                                        return success();
                                    }
                                    if (Oxevy.configManager.deleteProfile(name)) {
                                        Command.sendMessage("{green}Deleted config profile: {gray}%s", name);
                                    } else {
                                        Command.sendMessage("{red}Failed to delete config profile: {gray}%s", name);
                                    }
                                    return success();
                                })));
    }
}
