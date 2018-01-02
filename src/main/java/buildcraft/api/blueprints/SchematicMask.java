/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p>
 * The BuildCraft API is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located
 * as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.blueprints;

import buildcraft.api.core.BuildCraftAPI;
import com.gamerforea.buildcraft.ModUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.LinkedList;

public class SchematicMask extends SchematicBlockBase
{

	public boolean isConcrete = true;

	public SchematicMask()
	{

	}

	public SchematicMask(boolean isConcrete)
	{
		this.isConcrete = isConcrete;
	}

	// TODO gamerforEA code start
	@Override
	public void placeInWorld(IBuilderContext context, int x, int y, int z, LinkedList<ItemStack> stacks)
	{
		this.placeInWorld(ModUtils.getModFake(context.world()), context, x, y, z, stacks);
	}
	// TODO gamerforEA code end

	@Override
	// TODO gamerforEA add EntityPlayer parameter
	public void placeInWorld(EntityPlayer player, IBuilderContext context, int x, int y, int z, LinkedList<ItemStack> stacks)
	{
		if (this.isConcrete)
		{
			if (stacks.size() == 0 || !BuildCraftAPI.isSoftBlock(context.world(), x, y, z))
				return;
			else
			{
				ItemStack stack = stacks.getFirst();

				// TODO gamerforEA code clear: EntityPlayer player = BuildCraftAPI.proxy.getBuildCraftPlayer((WorldServer) context.world()).get();

				// force the block to be air block, in case it's just a soft
				// block which replacement is not straightforward
				context.world().setBlock(x, y, z, Blocks.air, 0, 3);

				// Find nearest solid surface to place on
				ForgeDirection dir = ForgeDirection.DOWN;
				while (dir != ForgeDirection.UNKNOWN && BuildCraftAPI.isSoftBlock(context.world(), x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ))
				{
					dir = ForgeDirection.getOrientation(dir.ordinal() + 1);
				}

				stack.tryPlaceItemIntoWorld(player, context.world(), x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ, dir.getOpposite().ordinal(), 0.0f, 0.0f, 0.0f);
			}
		}
		else
			context.world().setBlock(x, y, z, Blocks.air, 0, 3);
	}

	@Override
	public boolean isAlreadyBuilt(IBuilderContext context, int x, int y, int z)
	{
		if (this.isConcrete)
			return !BuildCraftAPI.getWorldProperty("replaceable").get(context.world(), x, y, z);
		else
			return BuildCraftAPI.getWorldProperty("replaceable").get(context.world(), x, y, z);
	}

	@Override
	public void writeSchematicToNBT(NBTTagCompound nbt, MappingRegistry registry)
	{
		nbt.setBoolean("isConcrete", this.isConcrete);
	}

	@Override
	public void readSchematicFromNBT(NBTTagCompound nbt, MappingRegistry registry)
	{
		this.isConcrete = nbt.getBoolean("isConcrete");
	}
}
