package me.alpha432.oxevy.features.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.commands.Command;
import me.alpha432.oxevy.manager.CommandManager;

import java.util.List;
import java.util.StringJoiner;

import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;

public class OxevyUsersCommand extends Command {
    public OxevyUsersCommand() {
        super("oxevyuser", "oux", "moduser");
        setDescription("Manages known Oxevy users (shows the O watermark on their name tags)");
    }

    @Override
    public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
        builder.then(literal("list")
                        .executes((ctx) -> {
                            List<String> users = Oxevy.oxevyUsersManager.getUsers();
                            if (users.isEmpty()) {
                                return success("No known Oxevy users");
                            }
                            StringJoiner joiner = new StringJoiner(",");
                            users.forEach(joiner::add);
                            return success("Oxevy users (%s): %s", users.size(), joiner);
                        }))
                .then(literal("clear")
                        .executes((ctx) -> {
                            Oxevy.oxevyUsersManager.getUsers().clear();
                            return success("Cleared Oxevy users list");
                        }))
                .then(literal("add")
                        .then(argument("username", word())
                                .executes((ctx) -> {
                                    String username = getString(ctx, "username");
                                    if (Oxevy.oxevyUsersManager.isKnown(username)) {
                                        return success("{green} %s {reset} is already in your Oxevy users list.", username);
                                    }
                                    Oxevy.oxevyUsersManager.add(username);
                                    return success("Added {green} %s {reset} to your Oxevy users list", username);
                                })))
                .then(literal("remove")
                        .then(argument("username", word())
                                .executes((ctx) -> {
                                    String username = getString(ctx, "username");
                                    if (!Oxevy.oxevyUsersManager.isKnown(username)) {
                                        return success("{green} %s {reset} is not in your Oxevy users list.", username);
                                    }
                                    Oxevy.oxevyUsersManager.remove(username);
                                    return success("Removed {green} %s {reset} from your Oxevy users list", username);
                                })));
    }
}
