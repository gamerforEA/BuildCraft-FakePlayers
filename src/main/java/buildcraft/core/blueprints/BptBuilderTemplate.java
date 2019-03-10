/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.core.blueprints;

import buildcraft.api.blueprints.BuilderAPI;
import buildcraft.api.blueprints.SchematicBlockBase;
import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.core.IInvSlot;
import buildcraft.core.builders.BuildingSlot;
import buildcraft.core.builders.BuildingSlotBlock;
import buildcraft.core.builders.BuildingSlotBlock.Mode;
import buildcraft.core.builders.BuildingSlotIterator;
import buildcraft.core.builders.TileAbstractBuilder;
import buildcraft.core.lib.inventory.InventoryIterator;
import buildcraft.core.lib.utils.BlockUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.LinkedList;

public class BptBuilderTemplate extends BptBuilderBase
{
	private LinkedList<BuildingSlotBlock> clearList = new LinkedList<>();
	private LinkedList<BuildingSlotBlock> buildList = new LinkedList<>();
	private BuildingSlotIterator iteratorBuild, iteratorClear;

	public BptBuilderTemplate(BlueprintBase bluePrint, World world, int x, int y, int z)
	{
		super(bluePrint, world, x, y, z);
	}

	@Override
	protected void internalInit()
	{
		if (this.blueprint.excavate)
			for (int j = this.blueprint.sizeY - 1; j >= 0; --j)
			{
				int yCoord = j + this.y - this.blueprint.anchorY;

				if (yCoord < 0 || yCoord >= this.context.world.getHeight())
					continue;

				for (int i = 0; i < this.blueprint.sizeX; ++i)
				{
					int xCoord = i + this.x - this.blueprint.anchorX;

					for (int k = 0; k < this.blueprint.sizeZ; ++k)
					{
						int zCoord = k + this.z - this.blueprint.anchorZ;

						SchematicBlockBase slot = this.blueprint.get(i, j, k);

						if (slot == null && !this.isLocationUsed(xCoord, yCoord, zCoord))
						{
							BuildingSlotBlock b = new BuildingSlotBlock();

							b.schematic = null;
							b.x = xCoord;
							b.y = yCoord;
							b.z = zCoord;
							b.mode = Mode.ClearIfInvalid;
							b.buildStage = 0;

							this.clearList.add(b);
						}
					}
				}
			}

		for (int j = 0; j < this.blueprint.sizeY; ++j)
		{
			int yCoord = j + this.y - this.blueprint.anchorY;

			if (yCoord < 0 || yCoord >= this.context.world.getHeight())
				continue;

			for (int i = 0; i < this.blueprint.sizeX; ++i)
			{
				int xCoord = i + this.x - this.blueprint.anchorX;

				for (int k = 0; k < this.blueprint.sizeZ; ++k)
				{
					int zCoord = k + this.z - this.blueprint.anchorZ;

					SchematicBlockBase slot = this.blueprint.get(i, j, k);

					if (slot != null && !this.isLocationUsed(xCoord, yCoord, zCoord))
					{
						BuildingSlotBlock b = new BuildingSlotBlock();

						b.schematic = slot;
						b.x = xCoord;
						b.y = yCoord;
						b.z = zCoord;

						b.mode = Mode.Build;
						b.buildStage = 1;

						this.buildList.add(b);
					}
				}
			}
		}

		this.iteratorBuild = new BuildingSlotIterator(this.buildList);
		this.iteratorClear = new BuildingSlotIterator(this.clearList);
	}

	private void checkDone()
	{
		this.done = this.buildList.size() == 0 && this.clearList.size() == 0;
	}

	@Override
	public BuildingSlot reserveNextBlock(World world)
	{
		return null;
	}

	@Override
	public BuildingSlot getNextBlock(World world, TileAbstractBuilder inv)
	{
		if (this.buildList.size() != 0 || this.clearList.size() != 0)
		{
			BuildingSlotBlock slot = this.internalGetNextBlock(world, inv);
			this.checkDone();

			if (slot != null)
				return slot;
		}
		else
			this.checkDone();

		return null;
	}

	private BuildingSlotBlock internalGetNextBlock(World world, TileAbstractBuilder builder)
	{
		BuildingSlotBlock result = null;

		IInvSlot firstSlotToConsume = null;

		for (IInvSlot invSlot : InventoryIterator.getIterable(builder, ForgeDirection.UNKNOWN))
		{
			if (!builder.isBuildingMaterialSlot(invSlot.getIndex()))
				continue;

			ItemStack stack = invSlot.getStackInSlot();

			if (stack != null && stack.stackSize > 0)
			{
				firstSlotToConsume = invSlot;
				break;
			}
		}

		// Step 1: Check the cleared
		this.iteratorClear.startIteration();
		while (this.iteratorClear.hasNext())
		{
			BuildingSlotBlock slot = this.iteratorClear.next();

			if (slot.buildStage > this.clearList.getFirst().buildStage)
			{
				this.iteratorClear.reset();
				break;
			}

			if (!world.blockExists(slot.x, slot.y, slot.z))
				continue;

			if (this.canDestroy(builder, this.context, slot))
				// TODO gamerforEA condition replace, old code: isBlockBreakCanceled(world, slot.x, slot.y, slot.z)
				if (BlockUtils.isUnbreakableBlock(world, slot.x, slot.y, slot.z) || builder.fake.cantBreak(slot.x, slot.y, slot.z) || BuildCraftAPI.isSoftBlock(world, slot.x, slot.y, slot.z))
				// TODO gamerforEA code end
				{
					this.iteratorClear.remove();
					this.markLocationUsed(slot.x, slot.y, slot.z);
				}
				else
				{
					this.consumeEnergyToDestroy(builder, slot);
					this.createDestroyItems(slot);

					result = slot;
					this.iteratorClear.remove();
					this.markLocationUsed(slot.x, slot.y, slot.z);
					break;
				}
		}

		if (result != null)
			return result;

		// Step 2: Check the built, but only if we have anything to place and enough energy
		if (firstSlotToConsume == null)
			return null;

		this.iteratorBuild.startIteration();

		while (this.iteratorBuild.hasNext())
		{
			BuildingSlotBlock slot = this.iteratorBuild.next();

			if (slot.buildStage > this.buildList.getFirst().buildStage)
			{
				this.iteratorBuild.reset();
				break;
			}

			// TODO gamerforEA condition replace, old code: isBlockPlaceCanceled(world, slot.x, slot.y, slot.z, slot.schematic)
			if (BlockUtils.isUnbreakableBlock(world, slot.x, slot.y, slot.z) || builder.fake.cantBreak(slot.x, slot.y, slot.z) || !BuildCraftAPI.isSoftBlock(world, slot.x, slot.y, slot.z))
			// TODO gamerforEA code end
			{
				this.iteratorBuild.remove();
				this.markLocationUsed(slot.x, slot.y, slot.z);
			}
			else if (builder.consumeEnergy(BuilderAPI.BUILD_ENERGY))
			{
				slot.addStackConsumed(firstSlotToConsume.decreaseStackInSlot(1));
				result = slot;
				this.iteratorBuild.remove();
				this.markLocationUsed(slot.x, slot.y, slot.z);
				break;
			}
		}

		return result;
	}
}
