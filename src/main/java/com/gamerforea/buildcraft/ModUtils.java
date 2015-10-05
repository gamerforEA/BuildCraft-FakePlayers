package com.gamerforea.buildcraft;

import com.gamerforea.eventhelper.util.FastUtils;
import com.mojang.authlib.GameProfile;

import buildcraft.BuildCraftCore;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

public final class ModUtils
{
	public static final GameProfile profile = BuildCraftCore.gameProfile;
	private static FakePlayer player = null;

	public static final FakePlayer getModFake(World world)
	{
		if (player == null)
			player = FastUtils.getFake(world, profile);
		else
			player.worldObj = world;

		return player;
	}
}