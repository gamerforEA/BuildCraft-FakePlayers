/**
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.core.builders;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import com.gamerforea.buildcraft.ModUtils;

import buildcraft.BuildCraftBuilders;
import buildcraft.api.blueprints.BuildingPermission;
import buildcraft.api.blueprints.IBuilderContext;
import buildcraft.api.blueprints.MappingNotFoundException;
import buildcraft.api.blueprints.MappingRegistry;
import buildcraft.api.blueprints.SchematicBlock;
import buildcraft.api.blueprints.SchematicBlockBase;
import buildcraft.api.blueprints.SchematicFactory;
import buildcraft.api.blueprints.SchematicMask;
import buildcraft.api.core.BCLog;
import buildcraft.api.core.Position;
import buildcraft.core.blueprints.IndexRequirementMap;
import buildcraft.core.lib.utils.BlockUtils;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.Constants;

public class BuildingSlotBlock extends BuildingSlot
{
	public int x, y, z;
	public SchematicBlockBase schematic;

	// TODO: Remove this ugly hack
	public IndexRequirementMap internalRequirementRemovalListener;

	public enum Mode
	{
		ClearIfInvalid, Build
	}

	public Mode mode = Mode.Build;

	public int buildStage = 0;

	@Override
	public SchematicBlockBase getSchematic()
	{
		if (this.schematic == null)
			return new SchematicMask(false);
		else
			return this.schematic;
	}

	// TODO gamerforEA code start
	public boolean writeToWorld(IBuilderContext context)
	{
		return this.writeToWorld(ModUtils.getModFake(context.world()), context);
	}
	// TODO gamerforEA code end

	@Override
	// TODO gamerforEA add EntityPlayer parameter
	public boolean writeToWorld(EntityPlayer player, IBuilderContext context)
	{
		if (this.internalRequirementRemovalListener != null)
			this.internalRequirementRemovalListener.remove(this);

		if (this.mode == Mode.ClearIfInvalid)
		{
			if (!this.getSchematic().isAlreadyBuilt(context, this.x, this.y, this.z))
				if (BuildCraftBuilders.dropBrokenBlocks)
					return BlockUtils.breakBlock((WorldServer) context.world(), this.x, this.y, this.z);
				else
				{
					context.world().setBlockToAir(this.x, this.y, this.z);
					return true;
				}
		}
		else
			try
			{
				// TODO gamerforEA add EntityPlayer parameter
				this.getSchematic().placeInWorld(player, context, this.x, this.y, this.z, this.stackConsumed);

				// This is also slightly hackish, but that's what you get when
				// you're unable to break an API too much.
				if (!this.getSchematic().isAlreadyBuilt(context, this.x, this.y, this.z))
					if (context.world().isAirBlock(this.x, this.y, this.z))
						return false;
					else if (!(this.getSchematic() instanceof SchematicBlock) || context.world().getBlock(this.x, this.y, this.z).isAssociatedBlock(((SchematicBlock) this.getSchematic()).block))
					{
						BCLog.logger.warn("Placed block does not match expectations! Most likely a bug in BuildCraft or a supported mod. Removed mismatched block.");
						BCLog.logger.warn("Location: " + this.x + ", " + this.y + ", " + this.z + " - Block: " + Block.blockRegistry.getNameForObject(context.world().getBlock(this.x, this.y, this.z)) + "@" + context.world().getBlockMetadata(this.x, this.y, this.z));
						context.world().removeTileEntity(this.x, this.y, this.z);
						context.world().setBlockToAir(this.x, this.y, this.z);
						return true;
					}
					else
						return false;

				// This is slightly hackish, but it's a very important way to verify
				// the stored requirements for anti-cheating purposes.
				if (!context.world().isAirBlock(this.x, this.y, this.z) && this.getSchematic().getBuildingPermission() == BuildingPermission.ALL && this.getSchematic() instanceof SchematicBlock)
				{
					SchematicBlock sb = (SchematicBlock) this.getSchematic();
					// Copy the old array of stored requirements.
					ItemStack[] oldRequirementsArray = sb.storedRequirements;
					List<ItemStack> oldRequirements = Arrays.asList(oldRequirementsArray);
					sb.storedRequirements = new ItemStack[0];
					sb.storeRequirements(context, this.x, this.y, this.z);
					for (ItemStack s : sb.storedRequirements)
					{
						boolean contains = false;
						for (ItemStack ss : oldRequirements)
							if (this.getSchematic().isItemMatchingRequirement(s, ss))
							{
								contains = true;
								break;
							}
						if (!contains)
						{
							BCLog.logger.warn("Blueprint has MISMATCHING REQUIREMENTS! Potential corrupted/hacked blueprint! Removed mismatched block.");
							BCLog.logger.warn("Location: " + this.x + ", " + this.y + ", " + this.z + " - ItemStack: " + s.toString());
							context.world().removeTileEntity(this.x, this.y, this.z);
							context.world().setBlockToAir(this.x, this.y, this.z);
							return true;
						}
					}
					// Restore the stored requirements.
					sb.storedRequirements = oldRequirementsArray;
				}

				// Once the schematic has been written, we're going to issue
				// calls
				// to various functions, in particular updating the tile entity.
				// If these calls issue problems, in order to avoid corrupting
				// the world, we're logging the problem and setting the block to
				// air.

				TileEntity e = context.world().getTileEntity(this.x, this.y, this.z);

				if (e != null)
					e.updateEntity();

				return true;
			}
			catch (Throwable t)
			{
				t.printStackTrace();
				context.world().setBlockToAir(this.x, this.y, this.z);
				return false;
			}

		return false;
	}

	@Override
	public void postProcessing(IBuilderContext context)
	{
		this.getSchematic().postProcessing(context, this.x, this.y, this.z);
	}

	@Override
	public LinkedList<ItemStack> getRequirements(IBuilderContext context)
	{
		if (this.mode == Mode.ClearIfInvalid)
			return new LinkedList<ItemStack>();
		else
		{
			LinkedList<ItemStack> req = new LinkedList<ItemStack>();

			this.getSchematic().getRequirementsForPlacement(context, req);

			return req;
		}
	}

	@Override
	public Position getDestination()
	{
		return new Position(this.x + 0.5, this.y + 0.5, this.z + 0.5);
	}

	@Override
	public void writeCompleted(IBuilderContext context, double complete)
	{
		if (this.mode == Mode.ClearIfInvalid)
			context.world().destroyBlockInWorldPartially(0, this.x, this.y, this.z, (int) (complete * 10.0F) - 1);
	}

	@Override
	public boolean isAlreadyBuilt(IBuilderContext context)
	{
		return this.schematic.isAlreadyBuilt(context, this.x, this.y, this.z);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt, MappingRegistry registry)
	{
		nbt.setByte("mode", (byte) this.mode.ordinal());
		nbt.setInteger("x", this.x);
		nbt.setInteger("y", this.y);
		nbt.setInteger("z", this.z);

		if (this.schematic != null)
		{
			NBTTagCompound schematicNBT = new NBTTagCompound();
			SchematicFactory.getFactory(this.schematic.getClass()).saveSchematicToWorldNBT(schematicNBT, this.schematic, registry);
			nbt.setTag("schematic", schematicNBT);
		}

		NBTTagList nbtStacks = new NBTTagList();

		if (this.stackConsumed != null)
			for (ItemStack stack : this.stackConsumed)
			{
				NBTTagCompound nbtStack = new NBTTagCompound();
				stack.writeToNBT(nbtStack);
				nbtStacks.appendTag(nbtStack);
			}

		nbt.setTag("stackConsumed", nbtStacks);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt, MappingRegistry registry) throws MappingNotFoundException
	{
		this.mode = Mode.values()[nbt.getByte("mode")];
		this.x = nbt.getInteger("x");
		this.y = nbt.getInteger("y");
		this.z = nbt.getInteger("z");

		if (nbt.hasKey("schematic"))
			this.schematic = (SchematicBlockBase) SchematicFactory.createSchematicFromWorldNBT(nbt.getCompoundTag("schematic"), registry);

		this.stackConsumed = new LinkedList<ItemStack>();

		NBTTagList nbtStacks = nbt.getTagList("stackConsumed", Constants.NBT.TAG_COMPOUND);

		for (int i = 0; i < nbtStacks.tagCount(); ++i)
			this.stackConsumed.add(ItemStack.loadItemStackFromNBT(nbtStacks.getCompoundTagAt(i)));

	}

	@Override
	public List<ItemStack> getStacksToDisplay()
	{
		if (this.mode == Mode.ClearIfInvalid)
			return this.stackConsumed;
		else
			return this.getSchematic().getStacksToDisplay(this.stackConsumed);
	}

	@Override
	public int getEnergyRequirement()
	{
		return this.schematic.getEnergyRequirement(this.stackConsumed);
	}

	@Override
	public int buildTime()
	{
		if (this.schematic == null)
			return 1;
		else
			return this.schematic.buildTime();
	}

}
