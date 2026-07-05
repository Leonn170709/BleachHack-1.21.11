/*
 * This file is part of the BleachHack distribution (https://github.com/BleachDev/BleachHack/).
 * Copyright (c) 2021 Bleach and contributors.
 *
 * This source code is subject to the terms of the GNU General Public
 * License, version 3. If a copy of the GPL was not distributed with this
 * file, You can obtain one at: https://www.gnu.org/licenses/gpl-3.0.txt
 */
package org.bleachhack.mixin;

import org.joml.FrustumIntersection;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.render.Frustum;

@Mixin(Frustum.class)
public interface AccessorFrustum {
	
	@Accessor
	public abstract FrustumIntersection getFrustumIntersection();
	
	@Accessor
	public abstract void setFrustumIntersection(FrustumIntersection vector4f);
	
	@Accessor("x")
	public abstract double getX();

	@Accessor("x")
	public abstract void setX(double x);

	@Accessor("y")
	public abstract double getY();

	@Accessor("y")
	public abstract void setY(double y);

	@Accessor("z")
	public abstract double getZ();

	@Accessor("z")
	public abstract void setZ(double z);
}
