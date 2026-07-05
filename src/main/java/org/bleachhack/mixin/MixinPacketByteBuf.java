package org.bleachhack.mixin;

import org.bleachhack.module.ModuleManager;
import org.bleachhack.module.mods.AntiChunkBan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import net.minecraft.nbt.NbtSizeTracker;
import net.minecraft.network.PacketByteBuf;

@Mixin(PacketByteBuf.class)
public class MixinPacketByteBuf {

	// readNbt() -> readNbt(ByteBuf) -> readNbt(ByteBuf, NbtSizeTracker.forPacket()) - the no-arg
	// instance method used to call the NbtSizeTracker-taking overload directly, but it now goes
	// through the static readNbt(ByteBuf) first, which is where the (now hardcoded) size tracker
	// argument is actually passed, so that's the real interception point.
	@ModifyArg(method = "readNbt(Lio/netty/buffer/ByteBuf;)Lnet/minecraft/nbt/NbtCompound;",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketByteBuf;readNbt(Lio/netty/buffer/ByteBuf;Lnet/minecraft/nbt/NbtSizeTracker;)Lnet/minecraft/nbt/NbtElement;"))
    private static NbtSizeTracker increaseLimit(NbtSizeTracker in) {
        return ModuleManager.getModule(AntiChunkBan.class).isEnabled() ? NbtSizeTracker.ofUnlimitedBytes() : in;
    }
}
