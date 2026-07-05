/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.event.events;

import org.bleachhack.event.Event;

public class EventWorldRender extends Event {

	protected float partialTicks;

	// 1.21.11's WorldRenderer.render(...) no longer takes/builds a single top-level MatrixStack (see
	// task #4 notes) - nothing subscribed to getMatrices() anyway, so it's dropped rather than faked.
	public static class Pre extends EventWorldRender {

		public Pre(float partialTicks) {
			this.partialTicks = partialTicks;
		}

	}

	public static class Post extends EventWorldRender {

		public Post(float partialTicks) {
			this.partialTicks = partialTicks;
		}

	}

	public float getPartialTicks() {
		return partialTicks;
	}
}
