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

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;

public class EventEntityRender extends Event {

	public static class Single extends EventEntityRender {

		protected Entity entity;

		public Entity getEntity() {
			return entity;
		}

		// 1.21.11: entity rendering no longer takes a MatrixStack/VertexConsumerProvider directly (it
		// now builds an EntityRenderState up front and submits draws through an OrderedRenderCommandQueue
		// - see task #3/#4 notes), so Pre only supports cancelling a specific entity's render now, at the
		// visibility-check stage - nothing ever used getMatrix()/getVertex() on it besides the mixin itself.
		public static class Pre extends Single {

			public Pre(Entity entity) {
				this.entity = entity;
			}
		}

		// Label rendering (nametags) still gets a MatrixStack, but the VertexConsumerProvider was
		// replaced by an OrderedRenderCommandQueue (submit-based batching instead of direct vertex
		// buffers) - and the label Text itself is no longer passed in directly, so it's dropped.
		public static class Label extends Single {

			protected MatrixStack matrices;
			protected OrderedRenderCommandQueue vertex;

			public Label(Entity entity, MatrixStack matrices, OrderedRenderCommandQueue vertex) {
				this.entity = entity;
				this.matrices = matrices;
				this.vertex = vertex;
			}

			public MatrixStack getMatrix() {
				return matrices;
			}

			public OrderedRenderCommandQueue getVertex() {
				return vertex;
			}

			public void setMatrix(MatrixStack matrices) {
				this.matrices = matrices;
			}

			public void setVertex(OrderedRenderCommandQueue vertex) {
				this.vertex = vertex;
			}
		}
	}

	public static class PreAll extends EventEntityRender {
	}

	public static class PostAll extends EventEntityRender {
	}
}
