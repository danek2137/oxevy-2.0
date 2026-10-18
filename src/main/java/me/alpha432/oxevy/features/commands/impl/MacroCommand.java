package me.alpha432.oxevy.features.commands.impl;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import me.alpha432.oxevy.Oxevy;
import me.alpha432.oxevy.features.commands.Command;
import me.alpha432.oxevy.features.modules.client.MacroModule;
import me.alpha432.oxevy.manager.CommandManager;
import org.lwjgl.glfw.GLFW;

public class MacroCommand extends Command {
    public MacroCommand() {
        super("macro", "Manage keybind macros", "macros");
    }

    @Override
    public void createArgumentBuilder(LiteralArgumentBuilder<CommandManager> builder) {
        builder.then(literal("add")
                .then(argument("name", StringArgumentType.string())
                        .then(argument("type", StringArgumentType.string())
                                .then(argument("value", StringArgumentType.greedyString())
                                        .then(argument("keybind", IntegerArgumentType.integer())
                                                .executes(this::addMacro))))));

        builder.then(literal("remove")
                .then(argument("name", StringArgumentType.string())
                        .executes(this::removeMacro)));

        builder.then(literal("list")
                .executes(this::listMacros));

        builder.then(literal("toggle")
                .then(argument("name", StringArgumentType.string())
                        .executes(this::toggleMacro)));

        builder.then(literal("bind")
                .then(argument("name", StringArgumentType.string())
                        .then(argument("key", IntegerArgumentType.integer())
                                .executes(this::setBind))));

        builder.then(literal("help")
                .executes(this::showHelp));
    }

    private int addMacro(CommandContext<CommandManager> ctx) {
        String name = ctx.getArgument("name", String.class);
        String typeStr = ctx.getArgument("type", String.class).toUpperCase();
        String value = ctx.getArgument("value", String.class);
        int keybind = ctx.getArgument("keybind", Integer.class);

        MacroModule.MacroType type;
        try {
            type = MacroModule.MacroType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            Command.sendMessage("{red}Invalid macro type. Use: COMMAND, GUI, or MESSAGE");
            return fail();
        }

        MacroModule macroModule = Oxevy.moduleManager.getModuleByClass(MacroModule.class);
        if (macroModule == null) {
            Command.sendMessage("{red}Macro module not loaded");
            return fail();
        }

        if (macroModule.addMacro(name, type, value, keybind)) {
            Command.sendMessage("{green}Added macro: {white}{}{green} ({}) bound to {}",
                    name, type.name().toLowerCase(), GLFW.glfwGetKeyName(keybind, 0));
        } else {
            Command.sendMessage("{red}Macro '{}' already exists", name);
        }
        return success();
    }

    private int removeMacro(CommandContext<CommandManager> ctx) {
        String name = ctx.getArgument("name", String.class);

        MacroModule macroModule = Oxevy.moduleManager.getModuleByClass(MacroModule.class);
        if (macroModule == null) {
            Command.sendMessage("{red}Macro module not loaded");
            return fail();
        }

        if (macroModule.removeMacro(name)) {
            Command.sendMessage("{green}Removed macro: {}", name);
        } else {
            Command.sendMessage("{red}Macro '{}' not found", name);
        }
        return success();
    }

    private int listMacros(CommandContext<CommandManager> ctx) {
        MacroModule macroModule = Oxevy.moduleManager.getModuleByClass(MacroModule.class);
        if (macroModule == null) {
            Command.sendMessage("{red}Macro module not loaded");
            return fail();
        }

        var macros = macroModule.getAllMacros();
        if (macros.isEmpty()) {
            Command.sendMessage("{yellow}No macros configured");
            return success();
        }

        Command.sendMessage("{green}=== Macros ({}) ===", macros.size());
        for (MacroModule.Macro macro : macros) {
            String keyName = macro.keybind <= 0 ? "None" : GLFW.glfwGetKeyName(macro.keybind, 0);
            String status = macro.enabled ? "{green}ON" : "{red}OFF";
            Command.sendMessage("  {white}{}: {}{green} [{}] bound to {}",
                    macro.name, status, macro.type.name(), keyName);
        }
        return success();
    }

    private int toggleMacro(CommandContext<CommandManager> ctx) {
        String name = ctx.getArgument("name", String.class);

        MacroModule macroModule = Oxevy.moduleManager.getModuleByClass(MacroModule.class);
        if (macroModule == null) {
            Command.sendMessage("{red}Macro module not loaded");
            return fail();
        }

        if (macroModule.toggleMacro(name)) {
            MacroModule.Macro macro = macroModule.getMacro(name);
            Command.sendMessage("{green}Macro '{}' is now {}", name, macro.enabled ? "{green}enabled" : "{red}disabled");
        } else {
            Command.sendMessage("{red}Macro '{}' not found", name);
        }
        return success();
    }

    private int setBind(CommandContext<CommandManager> ctx) {
        String name = ctx.getArgument("name", String.class);
        int keybind = ctx.getArgument("key", Integer.class);

        MacroModule macroModule = Oxevy.moduleManager.getModuleByClass(MacroModule.class);
        if (macroModule == null) {
            Command.sendMessage("{red}Macro module not loaded");
            return fail();
        }

        if (macroModule.setMacroKeybind(name, keybind)) {
            String keyName = keybind <= 0 ? "None" : GLFW.glfwGetKeyName(keybind, 0);
            Command.sendMessage("{green}Set keybind for '{}' to {}", name, keyName);
        } else {
            Command.sendMessage("{red}Macro '{}' not found", name);
        }
        return success();
    }

    private int showHelp(CommandContext<CommandManager> ctx) {
        Command.sendMessage("{green}=== Macro Commands ===");
        Command.sendMessage("{white}.macro add <name> <COMMAND|GUI|MESSAGE> <value> <keybind>");
        Command.sendMessage("{white}.macro remove <name>");
        Command.sendMessage("{white}.macro list");
        Command.sendMessage("{white}.macro toggle <name>");
        Command.sendMessage("{white}.macro bind <name> <keybind>");
        Command.sendMessage("{white}.macro help");
        Command.sendMessage("{gray}Keybind: Use GLFW key codes (e.g., 290 for F1, 291 for F2)");
        Command.sendMessage("{gray}Types: COMMAND (executes .cmd), GUI (clickgui/mainmenu/settings), MESSAGE (sends chat)");
        return success();
    }
}