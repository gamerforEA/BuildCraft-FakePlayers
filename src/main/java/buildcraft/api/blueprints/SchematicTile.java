/**
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 *
 * The BuildCraft API is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located
 * as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.blueprints;

import java.util.ArrayList;
import java.util.LinkedList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.gamerforea.buildcraft.EventConfig;

import buildcraft.api.core.JavaTools;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.Constants;

public class SchematicTile extends SchematicBlock
{

	/**
	 * This tree contains additional data to be stored in the blueprint. By
	 * default, it will be initialized from Schematic.readFromWord with the
	 * standard readNBT function of the corresponding tile (if any) and will be
	 * loaded from BptBlock.writeToWorld using the standard writeNBT function.
	 */
	public NBTTagCompound tileNBT = new NBTTagCompound();

	@Override
	public void idsToBlueprint(MappingRegistry registry)
	{
		registry.scanAndTranslateStacksToRegistry(this.tileNBT);
	}

	@Override
	public void idsToWorld(MappingRegistry registry)
	{
		try
		{
			registry.scanAndTranslateStacksToWorld(this.tileNBT);
		}
		catch (MappingNotFoundException e)
		{
			this.tileNBT = new NBTTagCompound();
		}
	}

	public void onNBTLoaded()
	{

	}

	/**
	 * Places the block in the world, at the location specified in the slot.
	 */
	@Override
	public void placeInWorld(IBuilderContext context, int x, int y, int z, LinkedList<ItemStack> stacks)
	{
		super.placeInWorld(context, x, y, z, stacks);

		if (this.block.hasTileEntity(this.meta))
		{
			this.tileNBT.setInteger("x", x);
			this.tileNBT.setInteger("y", y);
			this.tileNBT.setInteger("z", z);

			// TODO gamerforEA code start
			NBTTagCompound originalNbt = (NBTTagCompound) this.tileNBT.copy();

			Logger log = LogManager.getLogger("BuildCraftEvents");
			if (EventConfig.builderNbtDebug)
				log.info("Process {}. NBT: {}", originalNbt.getString("id"), originalNbt);

			for (String key : EventConfig.builderNbtTagBlackList)
			{
				NBTTagCompound nbt = this.tileNBT;
				String[] parts = key.split("/");
				String tag = parts[0];

				if (parts.length > 1)
				{
					tag = parts[parts.length - 1];
					for (int i = 0; i < parts.length - 1; i++)
						if (nbt.hasKey(parts[i], Constants.NBT.TAG_COMPOUND))
							nbt = nbt.getCompoundTag(parts[i]);
						else
						{
							nbt = null;
							break;
						}
				}

				if (nbt != null)
				{
					boolean has = EventConfig.builderNbtDebug && nbt.hasKey(tag);
					nbt.removeTag(tag);
					if (has)
						log.info("Tag \"{}\" removed", key);
				}
			}
			// TODO gamerforEA code end

			context.world().setTileEntity(x, y, z, TileEntity.createAndLoadEntity(this.tileNBT));

			// TODO gamerforEA code start
			this.tileNBT = originalNbt;
			// TODO gamerforEA code end
		}
	}

	@Override
	public void initializeFromObjectAt(IBuilderContext context, int x, int y, int z)
	{
		super.initializeFromObjectAt(context, x, y, z);

		if (this.block.hasTileEntity(this.meta))
		{
			TileEntity tile = context.world().getTileEntity(x, y, z);

			if (tile != null)
				tile.writeToNBT(this.tileNBT);

			this.tileNBT = (NBTTagCompound) this.tileNBT.copy();
			this.onNBTLoaded();
		}
	}

	@Override
	public void storeRequirements(IBuilderContext context, int x, int y, int z)
	{
		super.storeRequirements(context, x, y, z);

		if (this.block.hasTileEntity(this.meta))
		{
			TileEntity tile = context.world().getTileEntity(x, y, z);

			if (tile instanceof IInventory)
			{
				IInventory inv = (IInventory) tile;

				ArrayList<ItemStack> rqs = new ArrayList<ItemStack>();

				for (int i = 0; i < inv.getSizeInventory(); ++i)
					if (inv.getStackInSlot(i) != null)
						rqs.add(inv.getStackInSlot(i));

				this.storedRequirements = JavaTools.concat(this.storedRequirements, rqs.toArray(new ItemStack[rqs.size()]));
			}
		}
	}

	@Override
	public void writeSchematicToNBT(NBTTagCompound nbt, MappingRegistry registry)
	{
		super.writeSchematicToNBT(nbt, registry);

		nbt.setTag("blockCpt", this.tileNBT);
	}

	@Override
	public void readSchematicFromNBT(NBTTagCompound nbt, MappingRegistry registry)
	{
		super.readSchematicFromNBT(nbt, registry);

		this.tileNBT = nbt.getCompoundTag("blockCpt");
		this.onNBTLoaded();
	}

	@Override
	public int buildTime()
	{
		return 5;
	}
}
