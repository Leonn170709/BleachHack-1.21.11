/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.mixin;

import java.util.Map;
import java.util.WeakHashMap;

import org.bleachhack.BleachHack;
import org.bleachhack.event.events.EventEntityRender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;

// 1.21.11 decoupled entity rendering from the live Entity - renderLabelIfPresent(and render(...))
// now only get a snapshotted EntityRenderState (S), which doesn't carry a reference back to the
// Entity it was built from. Modules (Nametags) still need the real Entity for instanceof checks, so
// this stashes the entity<->state association from updateRenderState (which still runs with both)
// into a per-renderer weak map, keyed by state identity, and looks it up again at render time.
@Mixin(EntityRenderer.class)
public abstract class MixinEntityRenderer<T extends Entity, S extends EntityRenderState> {

	@Unique
	private final Map<S, T> bleachhack$stateToEntity = new WeakHashMap<>();

	@Inject(method = "updateRenderState", at = @At("RETURN"))
	private void updateRenderState(T entity, S state, float tickDelta, CallbackInfo ci) {
		bleachhack$stateToEntity.put(state, entity);
	}

	@Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true)
	private void renderLabelIfPresent(S state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo info) {
		Entity entity = bleachhack$stateToEntity.get(state);
		if (entity == null) {
			return;
		}

		EventEntityRender.Single.Label event = new EventEntityRender.Single.Label(entity, matrices, queue);
		BleachHack.eventBus.post(event);

		if (event.isCancelled()) {
			info.cancel();
		}
	}
}
