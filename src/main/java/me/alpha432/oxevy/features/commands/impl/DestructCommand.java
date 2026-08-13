package me.alpha432.oxevy.features.commands.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.commands.Command;
import me.alpha432.oxevy.features.settings.Bind;
import me.alpha432.oxevy.manager.CommandManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static me.alpha432.oxevy.util.traits.Util.EVENT_BUS;

public class DestructCommand extends Command {
    public DestructCommand() {
        super("destruct");
        setDescription("Removes all client traces from memory and disk");
    }

    @Override
    public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
        builder.executes(ctx -> {
            destruct();
            return success();
        });
    }

    public static void destruct() {
        if (Oxevy.destroyed) {
            return;
        }

        Oxevy.moduleManager.getModules().forEach(m -> {
            if (m.isEnabled()) m.disable();
            m.bind.setValue(Bind.none());
            EVENT_BUS.unregister(m);
        });

        Oxevy.friendManager.getFriends().clear();
        Oxevy.targetManager.setTarget(null);

        deleteFolder();
        deleteModJar();

        Oxevy.destroyed = true;
    }

    private static void deleteFolder() {
        Path base = FabricLoader.getInstance().getGameDir().resolve("oxevy");
        if (!Files.exists(base)) return;
        try (var walk = Files.walk(base)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }

    private static void deleteModJar() {
        FabricLoader.getInstance().getModContainer("oxevy")
                .flatMap(c -> c.getOrigin().getPaths().stream().findFirst())
                .filter(Files::exists)
                .ifPresent(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
    }
}
