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

import net.minecraft.block.entity.BlockEntity;

public class EventBlockEntityRender extends Event {

	public static class Single extends EventBlockEntityRender {

		protected BlockEntity blockEntity;

		public BlockEntity getBlockEntity() {
			return blockEntity;
		}

		// 1.21.11: block-entity rendering moved to the RenderState/OrderedRenderCommandQueue
		// architecture (see MixinBlockEntityRenderDispatcher notes) - this now fires during
		// WorldRenderer's render-state extraction pass, before any MatrixStack/VertexConsumerProvider
		// exists for this block entity, so only the block entity itself can be substituted/cancelled.
		public static class Pre extends Single {

			public Pre(BlockEntity blockEntity) {
				this.blockEntity = blockEntity;
			}

			public void setBlockEntity(BlockEntity blockEntity) {
				this.blockEntity = blockEntity;
			}
		}
	}

	public static class PreAll extends EventBlockEntityRender {
	}

	public static class PostAll extends EventBlockEntityRender {
	}
}
