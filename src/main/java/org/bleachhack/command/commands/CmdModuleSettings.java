/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.command.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;

import org.bleachhack.command.Command;
import org.bleachhack.command.CommandCategory;
import org.bleachhack.command.CommandManager;
import org.bleachhack.command.exception.CmdSyntaxException;
import org.bleachhack.module.Module;
import org.bleachhack.module.ModuleManager;
import org.bleachhack.setting.module.ModuleSetting;
import org.bleachhack.setting.module.SettingMode;
import org.bleachhack.setting.module.SettingSlider;
import org.bleachhack.setting.module.SettingToggle;
import org.bleachhack.util.BleachLogger;

// One command class, one alias per module - lets "!<moduleName> reset" and
// "!<moduleName> <settingName> <value>" work for every module without hand-writing a command
// class per module. Aliases are built from whatever's in ModuleManager at load time, which runs
// after modules are loaded (see BleachHack.postInit). Modules that already have their own dedicated
// command (Notebot, Spammer, EntityMenu, ClickGui, ...) are skipped here - see moduleAliases() - and
// that command calls applyArgs() itself instead, so there's only ever one command per module name.
// This class must be last in bleachhack.commands.json so every other command is already loaded by
// the time moduleAliases() checks for existing aliases.
public class CmdModuleSettings extends Command {

	private static final String DISPLAY_SYNTAX = "<module> reset | <module> <setting> <value>";

	public CmdModuleSettings() {
		super("modulesettings", "Reset a module's settings, or change one by name (e.g. \"$criticals mode Packet\", \"$criticals reset\").",
				DISPLAY_SYNTAX, CommandCategory.MODULES, moduleAliases());
	}

	private static String[] moduleAliases() {
		Set<String> reserved = new HashSet<>();
		for (Command c : CommandManager.getCommands()) {
			for (String a : c.getAliases()) {
				reserved.add(a.toLowerCase(Locale.ENGLISH));
			}
		}

		List<String> names = new ArrayList<>();
		for (Module m : ModuleManager.getModules()) {
			if (!reserved.contains(m.getName().toLowerCase(Locale.ENGLISH))) {
				names.add(m.getName());
			}
		}
		return names.toArray(new String[0]);
	}

	// CommandManager feeds getSyntax() straight into the tab-complete tree, and that tree can only
	// match literal words, not the "<module>" placeholder used for the human-readable syntax above.
	// So build one literal branch per real module name (lowercase, since the tree's top-level match
	// is case-sensitive and every other literal keyword in this command system is lowercase) purely
	// to drive suggestions. getHelpTooltip() below keeps showing the readable placeholder version.
	@Override
	public String getSyntax() {
		Set<String> owned = new HashSet<>();
		for (String a : getAliases()) {
			owned.add(a.toLowerCase(Locale.ENGLISH));
		}

		StringBuilder sb = new StringBuilder();
		for (Module m : ModuleManager.getModules()) {
			String name = m.getName().toLowerCase(Locale.ENGLISH);
			if (!owned.contains(name)) {
				continue;
			}

			if (sb.length() > 0) {
				sb.append(" | ");
			}
			sb.append(name).append(" reset | ").append(name).append(" <setting> <value>");
		}
		return sb.toString();
	}

	@Override
	public Text getHelpTooltip() {
		return Text.literal("§7Category: " + getCategory() + "\n")
				.append("Aliases: §f" + getPrefix() + String.join(" §7/§f " + getPrefix(), getAliases()) + "\n").styled(s -> s.withColor(BleachLogger.INFO_COLOR))
				.append("Usage: §f" + DISPLAY_SYNTAX + "\n").styled(s -> s.withColor(BleachLogger.INFO_COLOR))
				.append("Description: §f" + getDescription()).styled(s -> s.withColor(BleachLogger.INFO_COLOR));
	}

	@Override
	public void onCommand(String alias, String[] args) throws Exception {
		Module module = null;
		for (Module m : ModuleManager.getModules()) {
			if (m.getName().equalsIgnoreCase(alias)) {
				module = m;
				break;
			}
		}

		if (module == null) {
			throw new CmdSyntaxException();
		}

		applyArgs(module, args);
	}

	// Shared with commands for modules that already have a dedicated command (Notebot, Spammer,
	// EntityMenu, ClickGui, ...) so "reset"/"<setting> <value>" works there too instead of only
	// through this class's own per-module aliases.
	public static void applyArgs(Module module, String[] args) throws CmdSyntaxException {
		if (args.length == 0) {
			throw new CmdSyntaxException();
		}

		if (args[0].equalsIgnoreCase("reset")) {
			for (ModuleSetting<?> setting : module.getSettings()) {
				resetRecursive(setting);
			}

			BleachLogger.info("Reset all settings on §f" + module.getName() + "§r.");
			return;
		}

		if (args.length < 2) {
			throw new CmdSyntaxException();
		}

		ModuleSetting<?> setting = findSetting(module.getSettings(), args[0]);
		if (setting == null) {
			throw new CmdSyntaxException("No setting named \"" + args[0] + "\" on " + module.getName() + ".");
		}

		String value = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
		String result = applyValue(setting, value);
		if (result == null) {
			throw new CmdSyntaxException("Couldn't set \"" + setting.getName() + "\" to \"" + value + "\".");
		}

		BleachLogger.info("Set §f" + setting.getName() + "§r to §f" + result + "§r.");
	}

	private static void resetRecursive(ModuleSetting<?> setting) {
		setting.resetValue();

		if (setting instanceof SettingToggle toggle) {
			for (ModuleSetting<?> child : toggle.getChildren()) {
				resetRecursive(child);
			}
		}
	}

	// Chat commands split on spaces, so a setting/value name with a space in it (e.g. "XP Bottles")
	// can never be typed as-is - accept it dash-separated instead ("xp-bottles") on top of the real name.
	private static boolean sameName(String actual, String typed) {
		return actual.equalsIgnoreCase(typed) || actual.replace(' ', '-').equalsIgnoreCase(typed);
	}

	private static ModuleSetting<?> findSetting(List<ModuleSetting<?>> settings, String name) {
		for (ModuleSetting<?> setting : settings) {
			if (sameName(setting.getName(), name)) {
				return setting;
			}

			if (setting instanceof SettingToggle toggle) {
				ModuleSetting<?> found = findSetting(toggle.getChildren(), name);
				if (found != null) {
					return found;
				}
			}
		}

		return null;
	}

	// Only the common, plain-value setting types are supported from chat - Color/BlockList/
	// ItemList/Keybind settings need more structured input than a single command argument can
	// give them cleanly, so they're left to the ClickGui.
	private static String applyValue(ModuleSetting<?> setting, String value) {
		if (setting instanceof SettingToggle toggle) {
			boolean state = value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on") || value.equals("1");
			boolean off = value.equalsIgnoreCase("false") || value.equalsIgnoreCase("off") || value.equals("0");
			if (!state && !off) {
				return null;
			}

			toggle.setValue(state);
			return state ? "on" : "off";
		}

		if (setting instanceof SettingMode mode) {
			for (int i = 0; i < mode.modes.length; i++) {
				if (sameName(mode.modes[i], value)) {
					mode.setValue(i);
					return mode.modes[i];
				}
			}

			try {
				int index = Integer.parseInt(value);
				if (index >= 0 && index < mode.modes.length) {
					mode.setValue(index);
					return mode.modes[index];
				}
			} catch (NumberFormatException ignored) {}

			return null;
		}

		if (setting instanceof SettingSlider slider) {
			try {
				double parsed = Double.parseDouble(value);
				double clamped = Math.max(slider.min, Math.min(slider.max, parsed));
				slider.setValue(clamped);
				return String.valueOf(slider.getValue());
			} catch (NumberFormatException e) {
				return null;
			}
		}

		return null;
	}

}
