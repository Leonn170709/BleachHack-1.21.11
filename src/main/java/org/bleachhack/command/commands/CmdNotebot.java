/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.command.commands;

import org.bleachhack.command.Command;
import org.bleachhack.command.CommandCategory;
import org.bleachhack.gui.NotebotScreen;
import org.bleachhack.module.ModuleManager;
import org.bleachhack.util.BleachQueue;

public class CmdNotebot extends Command {

	public CmdNotebot() {
		super("notebot", "Shows the notebot gui, or change a setting (e.g. \"$notebot loop true\", \"$notebot reset\").", "notebot | notebot reset | notebot <setting> <value>", CommandCategory.MODULES);
	}

	@Override
	public void onCommand(String alias, String[] args) throws Exception {
		if (args.length == 0) {
			BleachQueue.add(() -> mc.setScreen(new NotebotScreen()));
			return;
		}

		CmdModuleSettings.applyArgs(ModuleManager.getModule("Notebot"), args);
	}

}
