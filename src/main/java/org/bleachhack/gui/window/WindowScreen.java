/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.gui.window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;

import org.bleachhack.gui.window.widget.WindowWidget;

import it.unimi.dsi.fastutil.ints.Int2IntMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2IntRBTreeMap;
import it.unimi.dsi.fastutil.ints.Int2IntSortedMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

public abstract class WindowScreen extends Screen {

	private List<Window> windows = new ArrayList<>();

	// <Layer, Window Index>
	private Int2IntSortedMap windowOrder = new Int2IntRBTreeMap(); 

	private List<WindowWidget> globalWidgets = new ArrayList<>();
	private boolean autoClose;

	public WindowScreen(Text title) {
		this(title, true);
	}

	public WindowScreen(Text title, boolean autoClose) {
		super(title);
		this.autoClose = autoClose;
	}

	public Window addWindow(Window window) {
		windows.add(window);
		windowOrder.put(windows.size() - 1, windows.size() - 1);
		return window;
	}

	public void removeWindow(int index) {
		if (index >= 0 && index < windows.size()) {
			int layer = getWindowLayer(index);

			windows.remove(index);
			windowOrder.remove(layer);
			for (Entry e: new Int2IntRBTreeMap(windowOrder).int2IntEntrySet()) {
				if (e.getIntKey() > layer) {
					windowOrder.remove(e.getIntKey());
					windowOrder.put(e.getIntKey() - 1, e.getIntValue());
				}
			}
		}
	}

	public Window getWindow(int i) {
		return windows.get(i);
	}

	public void clearWindows() {
		windows.clear();
		windowOrder.clear();
	}

	public List<Window> getWindows() {
		return windows;
	}

	protected IntCollection getWindowsBackToFront() {
		return windowOrder.values();
	}

	protected IntCollection getWindowsFrontToBack() {
		IntList w = new IntArrayList(getWindowsBackToFront());
		Collections.reverse(w);
		return w;
	}

	protected int getWindowLayer(int index) {
		return windowOrder.int2IntEntrySet().stream().filter(i -> i.getIntValue() == index).findFirst().get().getIntKey();
	}

	protected int getSelectedWindow() {
		for (int i = 0; i < windows.size(); i++) {
			if (!getWindow(i).closed && getWindow(i).selected) {
				return i;
			}
		}

		return -1;
	}

	public <T extends WindowWidget> T addGlobalWidget(T widget) {
		globalWidgets.add(widget);
		return widget;
	}

	public List<WindowWidget> getGlobalWidgets() {
		return globalWidgets;
	}
	
	@Override
	public void init() {
		super.init();
		
		globalWidgets.clear();
		clearWindows();
	}

	@Override
	public void render(DrawContext matrices, int mouseX, int mouseY, float delta) {
		super.render(matrices, mouseX, mouseY, delta);

		for (WindowWidget w : globalWidgets) {
			w.render(matrices, 0, 0, mouseX, mouseY);
		}

		int sel = getSelectedWindow();

		if (sel == -1) {
			for (int i: getWindowsFrontToBack()) {
				if (!getWindow(i).closed) {
					selectWindow(i);
					break;
				}
			}
		}

		boolean close = true;

		for (int w: getWindowsBackToFront()) {
			if (!getWindow(w).closed) {
				close = false;
				onRenderWindow(matrices, w, mouseX, mouseY);
			}
		}

		if (autoClose && close) this.close();
	}

	public void onRenderWindow(DrawContext matrices, int window, int mouseX, int mouseY) {
		if (!windows.get(window).closed) {
			windows.get(window).render(matrices, mouseX, mouseY);
		}
	}

	public void selectWindow(int window) {
		for (int i = 0; i < windows.size(); i++) {
			Window w = windows.get(i);

			if (i == window) {
				w.closed = false;
				w.selected = true;
				int layer = getWindowLayer(window);

				windowOrder.remove(layer);
				for (Entry e: new Int2IntRBTreeMap(windowOrder).int2IntEntrySet()) {
					if (e.getIntKey() > layer) {
						windowOrder.remove(e.getIntKey());
						windowOrder.put(e.getIntKey() - 1, e.getIntValue());
					}
				}

				windowOrder.put(windowOrder.size(), window);
			} else {
				w.selected = false;
			}
		}
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubleClick) {
		double mouseX = click.x();
		double mouseY = click.y();
		int button = click.button();

		/* Handle what window will be selected when clicking */
		for (int wi: getWindowsFrontToBack()) {
			Window w = getWindow(wi);

			if (mouseX >= w.x1 && mouseX <= w.x2 && mouseY >= w.y1 && mouseY <= w.y2 && !w.closed) {
				if (w.shouldClose((int) mouseX, (int) mouseY)) {
					w.closed = true;
					break;
				}

				if (!w.selected)
					selectWindow(wi);

				w.mouseClicked(mouseX, mouseY, button);
				break;
			}
		}

		try {
			for (WindowWidget w : globalWidgets) {
				w.mouseClicked(0, 0, (int) mouseX, (int) mouseY, button);
			}
		} catch (ConcurrentModificationException ignored) {}

		return super.mouseClicked(click, doubleClick);
	}

	@Override
	public boolean mouseReleased(Click click) {
		for (Window w : windows)
			w.mouseReleased(click.x(), click.y(), click.button());

		return super.mouseReleased(click);
	}

	@Override
	public void tick() {
		for (Window w : windows)
			w.tick();

		super.tick();
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		for (Window w : windows)
			w.keyPressed(input.key(), input.scancode(), input.modifiers());

		return super.keyPressed(input);
	}

	@Override
	public boolean charTyped(CharInput input) {
		for (Window w : windows)
			w.charTyped((char) input.codepoint(), input.modifiers());

		return super.charTyped(input);
	}
}
