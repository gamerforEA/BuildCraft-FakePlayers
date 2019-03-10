/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p>
 * The BuildCraft API is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located
 * as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.blueprints;

import buildcraft.api.core.BlockIndex;
import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockOre;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.BlockFluidBase;

import java.util.*;

public class SchematicBlock extends SchematicBlockBase
{
	public static final BlockIndex[] RELATIVE_INDEXES = { new BlockIndex(0, -1, 0), new BlockIndex(0, 1, 0), new BlockIndex(0, 0, -1), new BlockIndex(0, 0, 1), new BlockIndex(-1, 0, 0), new BlockIndex(1, 0, 0), };

	public Block block = null;
	public int meta = 0;
	public BuildingPermission defaultPermission = BuildingPermission.ALL;

	/**
	 * This field contains requirements for a given block when stored in the
	 * blueprint. Modders can either rely on this list or compute their own int
	 * Schematic.
	 */
	public ItemStack[] storedRequirements = new ItemStack[0];

	private boolean doNotUse = false;

	@Override
	public void getRequirementsForPlacement(IBuilderContext context, LinkedList<ItemStack> requirements)
	{
		if (this.block != null)
			if (this.storedRequirements.length != 0)
				Collections.addAll(requirements, this.storedRequirements);
			else
				requirements.add(new ItemStack(this.block, 1, this.meta));
	}

	@Override
	public boolean isAlreadyBuilt(IBuilderContext context, int x, int y, int z)
	{
		return this.block == context.world().getBlock(x, y, z) && this.meta == context.world().getBlockMetadata(x, y, z);
	}

	@Override
	public void placeInWorld(IBuilderContext context, int x, int y, int z, LinkedList<ItemStack> stacks)
	{
		super.placeInWorld(context, x, y, z, stacks);

		this.setBlockInWorld(context, x, y, z);
	}

	@Override
	public void storeRequirements(IBuilderContext context, int x, int y, int z)
	{
		super.storeRequirements(context, x, y, z);

		if (this.block != null)
		{
			// TODO gamerforEA code start (fix Fortune enchant dupe)
			if (this.block instanceof BlockOre)
			{
				this.storedRequirements = new ItemStack[] { new ItemStack(this.block) };
				return;
			}
			// TODO gamerforEA code end

			ArrayList<ItemStack> req = this.block.getDrops(context.world(), x, y, z, context.world().getBlockMetadata(x, y, z), 0);

			if (req != null)
			{
				this.storedRequirements = new ItemStack[req.size()];
				req.toArray(this.storedRequirements);
			}
		}
	}

	@Override
	public void writeSchematicToNBT(NBTTagCompound nbt, MappingRegistry registry)
	{
		super.writeSchematicToNBT(nbt, registry);

		this.writeBlockToNBT(nbt, registry);
		this.writeRequirementsToNBT(nbt, registry);
	}

	@Override
	public void readSchematicFromNBT(NBTTagCompound nbt, MappingRegistry registry)
	{
		super.readSchematicFromNBT(nbt, registry);

		this.readBlockFromNBT(nbt, registry);
		if (!this.doNotUse())
			this.readRequirementsFromNBT(nbt, registry);
	}

	/**
	 * Get a list of relative block coordinates which have to be built before
	 * this block can be placed.
	 */
	public Set<BlockIndex> getPrerequisiteBlocks(IBuilderContext context)
	{
		Set<BlockIndex> indexes = new HashSet<>();
		if (this.block instanceof BlockFalling)
			indexes.add(RELATIVE_INDEXES[ForgeDirection.DOWN.ordinal()]);
		return indexes;
	}

	@Override
	public BuildingStage getBuildStage()
	{
		if (this.block instanceof BlockFluidBase || this.block instanceof BlockLiquid)
			return BuildingStage.EXPANDING;
		else
			return BuildingStage.STANDALONE;
	}

	@Override
	public BuildingPermission getBuildingPermission()
	{
		return this.defaultPermission;
	}

	// Utility functions
	protected void setBlockInWorld(IBuilderContext context, int x, int y, int z)
	{
		// Meta needs to be specified twice, depending on the block behavior
		context.world().setBlock(x, y, z, this.block, this.meta, 3);
		context.world().setBlockMetadataWithNotify(x, y, z, this.meta, 3);
	}

	@Override
	public boolean doNotUse()
	{
		return this.doNotUse;
	}

	protected void readBlockFromNBT(NBTTagCompound nbt, MappingRegistry registry)
	{
		try
		{
			this.block = registry.getBlockForId(nbt.getInteger("blockId"));
			this.meta = nbt.getInteger("blockMeta");
		}
		catch (MappingNotFoundException e)
		{
			this.doNotUse = true;
		}
	}

	protected void readRequirementsFromNBT(NBTTagCompound nbt, MappingRegistry registry)
	{
		if (nbt.hasKey("rq"))
		{
			NBTTagList rq = nbt.getTagList("rq", Constants.NBT.TAG_COMPOUND);

			ArrayList<ItemStack> rqs = new ArrayList<>();
			for (int i = 0; i < rq.tagCount(); ++i)
			{
				try
				{
					NBTTagCompound sub = rq.getCompoundTagAt(i);
					if (sub.getInteger("id") >= 0)
					{
						registry.stackToWorld(sub);
						rqs.add(ItemStack.loadItemStackFromNBT(sub));
					}
					else
						this.defaultPermission = BuildingPermission.CREATIVE_ONLY;
				}
				catch (MappingNotFoundException e)
				{
					this.defaultPermission = BuildingPermission.CREATIVE_ONLY;
				}
				catch (Throwable t)
				{
					t.printStackTrace();
					this.defaultPermission = BuildingPermission.CREATIVE_ONLY;
				}
			}

			this.storedRequirements = rqs.toArray(new ItemStack[0]);
		}
		else
			this.storedRequirements = new ItemStack[0];
	}

	protected void writeBlockToNBT(NBTTagCompound nbt, MappingRegistry registry)
	{
		nbt.setInteger("blockId", registry.getIdForBlock(this.block));
		nbt.setInteger("blockMeta", this.meta);
	}

	protected void writeRequirementsToNBT(NBTTagCompound nbt, MappingRegistry registry)
	{
		if (this.storedRequirements.length > 0)
		{
			NBTTagList rq = new NBTTagList();

			for (ItemStack stack : this.storedRequirements)
			{
				NBTTagCompound sub = new NBTTagCompound();
				stack.writeToNBT(sub);
				registry.stackToRegistry(sub);
				rq.appendTag(sub);
			}

			nbt.setTag("rq", rq);
		}
	}
}
