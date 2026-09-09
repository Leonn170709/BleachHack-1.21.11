package org.bleachhack.mixin;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

import org.bleachhack.BleachHack;
import org.bleachhack.event.events.EventRenderBlock;
import org.bleachhack.module.mods.Xray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public class MixinAbstractBlockState {

	@Inject(method = "isOpaque", at = @At("HEAD"), cancellable = true)
	private void isOpaque(CallbackInfoReturnable<Boolean> callback) {
		EventRenderBlock.Opaque event = new EventRenderBlock.Opaque((BlockState) (Object) this);
		BleachHack.eventBus.post(event);

		if (event.isOpaque() != null)
			callback.setReturnValue(event.isOpaque());
	}

	/*
	 * The three hooks below are Xray's renderer-agnostic path: Sodium swaps out vanilla's whole chunk
	 * mesher, so MixinChunkRebuildTask never runs there, but both renderers still ask the block state
	 * itself these questions. Direct static calls rather than events on purpose - these are hot enough
	 * that an event allocation per call would show up.
	 * ponytail: Xray is the only consumer; give them events if a second module ever needs them.
	 */

	// No render type -> no geometry, in both vanilla's SectionBuilder and Sodium's meshing task.
	@Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
	private void getRenderType(CallbackInfoReturnable<BlockRenderType> callback) {
		if (Xray.isHidden((BlockState) (Object) this))
			callback.setReturnValue(BlockRenderType.INVISIBLE);
	}

	// A hidden block must not cull its neighbours' faces, or the ores next to it stay invisible.
	// Vanilla checks this via Block.shouldDrawSide, Sodium reimplements the check but reads the same shape.
	@Inject(method = "getCullingFace", at = @At("HEAD"), cancellable = true)
	private void getCullingFace(Direction direction, CallbackInfoReturnable<VoxelShape> callback) {
		if (Xray.isHidden((BlockState) (Object) this))
			callback.setReturnValue(VoxelShapes.empty());
	}

	// Section-level occlusion culling. Vanilla's is switched off via chunkCullingEnabled, Sodium's
	// visibility graph only listens to this.
	@Inject(method = "isOpaqueFullCube", at = @At("HEAD"), cancellable = true)
	private void isOpaqueFullCube(CallbackInfoReturnable<Boolean> callback) {
		if (Xray.isHidden((BlockState) (Object) this))
			callback.setReturnValue(false);
	}
}
