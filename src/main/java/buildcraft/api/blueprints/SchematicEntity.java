/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p>
 * The BuildCraft API is distributed under the terms of the MIT License.
 * Please check the contents of the license, which should be located
 * as "LICENSE.API" in the BuildCraft source code distribution.
 */
package buildcraft.api.blueprints;

import buildcraft.api.core.Position;
import com.gamerforea.buildcraft.EventConfig;
import com.gamerforea.buildcraft.ModUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;

public class SchematicEntity extends Schematic
{
	public Class<? extends Entity> entity;

	/**
	 * This tree contains additional data to be stored in the blueprint. By
	 * default, it will be initialized from Schematic.readFromWord with the
	 * standard readNBT function of the corresponding tile (if any) and will be
	 * loaded from BptBlock.writeToWorld using the standard writeNBT function.
	 */
	public NBTTagCompound entityNBT = new NBTTagCompound();

	/**
	 * This field contains requirements for a given block when stored in the
	 * blueprint. Modders can either rely on this list or compute their own int
	 * Schematic.
	 */
	public ItemStack[] storedRequirements = new ItemStack[0];
	public BuildingPermission defaultPermission = BuildingPermission.ALL;

	@Override
	public void getRequirementsForPlacement(IBuilderContext context, LinkedList<ItemStack> requirements)
	{
		Collections.addAll(requirements, this.storedRequirements);
	}

	public void writeToWorld(IBuilderContext context)
	{
		// TODO gamerforEA code replace, old code:
		// Entity e = EntityList.createEntityFromNBT(this.entityNBT, context.world());
		NBTTagCompound originalNbt = (NBTTagCompound) this.entityNBT.copy();
		Entity e;
		try
		{
			if (EventConfig.builderRemoveItems)
				ModUtils.removeItems(this.entityNBT);
			e = EntityList.createEntityFromNBT(this.entityNBT, context.world());
		}
		finally
		{
			this.entityNBT = originalNbt;
		}
		// TODO gamerforEA code end

		context.world().spawnEntityInWorld(e);
	}

	public void readFromWorld(IBuilderContext context, Entity entity)
	{
		entity.writeToNBTOptional(this.entityNBT);
	}

	@Override
	public void translateToBlueprint(Translation transform)
	{
		NBTTagList nbttaglist = this.entityNBT.getTagList("Pos", 6);
		Position pos = new Position(nbttaglist.func_150309_d(0), nbttaglist.func_150309_d(1), nbttaglist.func_150309_d(2));
		pos = transform.translate(pos);

		this.entityNBT.setTag("Pos", this.newDoubleNBTList(pos.x, pos.y, pos.z));
	}

	@Override
	public void translateToWorld(Translation transform)
	{
		NBTTagList nbttaglist = this.entityNBT.getTagList("Pos", 6);
		Position pos = new Position(nbttaglist.func_150309_d(0), nbttaglist.func_150309_d(1), nbttaglist.func_150309_d(2));
		pos = transform.translate(pos);

		this.entityNBT.setTag("Pos", this.newDoubleNBTList(pos.x, pos.y, pos.z));
	}

	@Override
	public void idsToBlueprint(MappingRegistry registry)
	{
		registry.scanAndTranslateStacksToRegistry(this.entityNBT);
	}

	@Override
	public void idsToWorld(MappingRegistry registry)
	{
		try
		{
			registry.scanAndTranslateStacksToWorld(this.entityNBT);
		}
		catch (MappingNotFoundException e)
		{
			this.entityNBT = new NBTTagCompound();
		}
	}

	@Override
	public void rotateLeft(IBuilderContext context)
	{
		NBTTagList nbttaglist = this.entityNBT.getTagList("Pos", 6);
		Position pos = new Position(nbttaglist.func_150309_d(0), nbttaglist.func_150309_d(1), nbttaglist.func_150309_d(2));
		pos = context.rotatePositionLeft(pos);
		this.entityNBT.setTag("Pos", this.newDoubleNBTList(pos.x, pos.y, pos.z));

		nbttaglist = this.entityNBT.getTagList("Rotation", 5);
		float yaw = nbttaglist.func_150308_e(0);
		yaw += 90;
		this.entityNBT.setTag("Rotation", this.newFloatNBTList(yaw, nbttaglist.func_150308_e(1)));
	}

	@Override
	public void writeSchematicToNBT(NBTTagCompound nbt, MappingRegistry registry)
	{
		super.writeSchematicToNBT(nbt, registry);

		nbt.setInteger("entityId", registry.getIdForEntity(this.entity));
		nbt.setTag("entity", this.entityNBT);

		NBTTagList rq = new NBTTagList();

		for (ItemStack stack : this.storedRequirements)
		{
			NBTTagCompound sub = new NBTTagCompound();
			stack.writeToNBT(stack.writeToNBT(sub));
			sub.setInteger("id", registry.getIdForItem(stack.getItem()));
			rq.appendTag(sub);
		}

		nbt.setTag("rq", rq);
	}

	@Override
	public void readSchematicFromNBT(NBTTagCompound nbt, MappingRegistry registry)
	{
		super.readSchematicFromNBT(nbt, registry);

		this.entityNBT = nbt.getCompoundTag("entity");

		NBTTagList rq = nbt.getTagList("rq", Constants.NBT.TAG_COMPOUND);

		ArrayList<ItemStack> rqs = new ArrayList<>();

		for (int i = 0; i < rq.tagCount(); ++i)
		{
			try
			{
				NBTTagCompound sub = rq.getCompoundTagAt(i);

				if (sub.getInteger("id") >= 0)
				{
					// Maps the id in the blueprint to the id in the world
					sub.setInteger("id", Item.itemRegistry.getIDForObject(registry.getItemForId(sub.getInteger("id"))));

					rqs.add(ItemStack.loadItemStackFromNBT(sub));
				}
				else
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

	protected NBTTagList newDoubleNBTList(double... par1ArrayOfDouble)
	{
		NBTTagList nbttaglist = new NBTTagList();

		for (double d1 : par1ArrayOfDouble)
		{
			nbttaglist.appendTag(new NBTTagDouble(d1));
		}

		return nbttaglist;
	}

	protected NBTTagList newFloatNBTList(float... par1ArrayOfFloat)
	{
		NBTTagList nbttaglist = new NBTTagList();

		for (float f1 : par1ArrayOfFloat)
		{
			nbttaglist.appendTag(new NBTTagFloat(f1));
		}

		return nbttaglist;
	}

	public boolean isAlreadyBuilt(IBuilderContext context)
	{
		NBTTagList nbttaglist = this.entityNBT.getTagList("Pos", 6);
		Position newPosition = new Position(nbttaglist.func_150309_d(0), nbttaglist.func_150309_d(1), nbttaglist.func_150309_d(2));

		for (Object o : context.world().loadedEntityList)
		{
			Entity e = (Entity) o;

			Position existingPositon = new Position(e.posX, e.posY, e.posZ);

			if (existingPositon.isClose(newPosition, 0.1F))
				return true;
		}

		return false;
	}

	@Override
	public int buildTime()
	{
		return 5;
	}

	@Override
	public BuildingPermission getBuildingPermission()
	{
		return this.defaultPermission;
	}
}
