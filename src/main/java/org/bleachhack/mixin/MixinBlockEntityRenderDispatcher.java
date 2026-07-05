/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.mixin;

import org.bleachhack.BleachHack;
import org.bleachhack.event.events.EventBlockEntityRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;

// 1.21.11 moved block-entity rendering to the same RenderState/OrderedRenderCommandQueue
// architecture as entities (see MixinWorldRenderer's entity-render notes) - BlockEntityRenderDispatcher
// (renamed BlockEntityRenderManager) no longer has a render(...) call taking a VertexConsumerProvider
// to wrap. The hook moved to WorldRenderer's per-block-entity render-state extraction call instead,
// which still lets NoRender substitute/cancel a specific block entity's render before it happens.
@Mixin(WorldRenderer.class)
public class MixinBlockEntityRenderDispatcher {

	@Redirect(method = "fillBlockEntityRenderStates", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/block/entity/BlockEntityRenderManager;"
			+ "getRenderState(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/render/command/ModelCommandRenderer$CrumblingOverlayCommand;)"
			+ "Lnet/minecraft/client/render/block/entity/state/BlockEntityRenderState;"))
	private BlockEntityRenderState getRenderState(BlockEntityRenderManager manager, BlockEntity blockEntity, float tickProgress,
			ModelCommandRenderer.CrumblingOverlayCommand crumblingOverlay) {
		EventBlockEntityRender.Single.Pre event = new EventBlockEntityRender.Single.Pre(blockEntity);
		BleachHack.eventBus.post(event);

		if (event.isCancelled()) {
			return null;
		}

		return manager.getRenderState(event.getBlockEntity(), tickProgress, crumblingOverlay);
	}
}
