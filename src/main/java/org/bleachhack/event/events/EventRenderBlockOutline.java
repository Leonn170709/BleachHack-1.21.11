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

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;

public class EventRenderBlockOutline extends Event {

	private MatrixStack matrices;
	private VertexConsumer vertexConsumer;
	private BlockPos pos;

	// 1.21.11: the BlockState is no longer available at this call site (see task #4 notes -
	// WorldRenderer's OutlineRenderState only carries the position + collision/interaction shapes).
	public EventRenderBlockOutline(MatrixStack matrices, VertexConsumer vertexConsumer, BlockPos pos) {
		this.matrices = matrices;
		this.vertexConsumer = vertexConsumer;
		this.pos = pos;
	}

	public MatrixStack getMatrices() {
		return matrices;
	}

	public void setMatrices(MatrixStack matrices) {
		this.matrices = matrices;
	}

	public VertexConsumer getVertexConsumer() {
		return vertexConsumer;
	}

	public void setVertexConsumer(VertexConsumer vertexConsumer) {
		this.vertexConsumer = vertexConsumer;
	}

	public BlockPos getPos() {
		return pos;
	}

	public void setPos(BlockPos pos) {
		this.pos = pos;
	}

}
