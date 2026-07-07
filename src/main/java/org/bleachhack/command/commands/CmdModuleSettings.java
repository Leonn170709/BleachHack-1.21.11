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
import java.util.List;

import org.bleachhack.command.Command;
import org.bleachhack.command.CommandCategory;
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
// after modules are loaded (see BleachHack.postInit).
public class CmdModuleSettings extends Command {

	public CmdModuleSettings() {
		super("modulesettings", "Reset a module's settings, or change one by name (e.g. \"$criticals mode Packet\", \"$criticals reset\").",
				"<module> reset | <module> <setting> <value>", CommandCategory.MODULES, moduleAliases());
	}

	private static String[] moduleAliases() {
		List<String> names = new ArrayList<>();
		for (Module m : ModuleManager.getModules()) {
			names.add(m.getName());
		}
		return names.toArray(new String[0]);
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

		if (module == null || args.length == 0) {
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

	private void resetRecursive(ModuleSetting<?> setting) {
		setting.resetValue();

		if (setting instanceof SettingToggle toggle) {
			for (ModuleSetting<?> child : toggle.getChildren()) {
				resetRecursive(child);
			}
		}
	}

	private ModuleSetting<?> findSetting(List<ModuleSetting<?>> settings, String name) {
		for (ModuleSetting<?> setting : settings) {
			if (setting.getName().equalsIgnoreCase(name)) {
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
	private String applyValue(ModuleSetting<?> setting, String value) {
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
				if (mode.modes[i].equalsIgnoreCase(value)) {
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
