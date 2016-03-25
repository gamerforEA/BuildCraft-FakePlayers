/**
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.core.builders;

import java.util.LinkedList;
import java.util.List;

import buildcraft.api.blueprints.IBuilderContext;
import buildcraft.api.blueprints.MappingNotFoundException;
import buildcraft.api.blueprints.MappingRegistry;
import buildcraft.api.blueprints.Schematic;
import buildcraft.api.core.Position;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public abstract class BuildingSlot
{

	public LinkedList<ItemStack> stackConsumed;

	public boolean reserved = false;

	public boolean built = false;

	// TODO gamerforEA code start
	public boolean writeToWorld(EntityPlayer player, IBuilderContext context)
	{
		return this.writeToWorld(context);
	}
	// TODO gamerforEA code end

	public boolean writeToWorld(IBuilderContext context)
	{
		return false;
	}

	public void writeCompleted(IBuilderContext context, double complete)
	{

	}

	public void postProcessing(IBuilderContext context)
	{

	}

	public LinkedList<ItemStack> getRequirements(IBuilderContext context)
	{
		return new LinkedList<ItemStack>();
	}

	public abstract Position getDestination();

	public void addStackConsumed(ItemStack stack)
	{
		if (this.stackConsumed == null)
			this.stackConsumed = new LinkedList<ItemStack>();

		this.stackConsumed.add(stack);
	}

	public List<ItemStack> getStacksToDisplay()
	{
		return this.getSchematic().getStacksToDisplay(this.stackConsumed);
	}

	public abstract boolean isAlreadyBuilt(IBuilderContext context);

	public abstract Schematic getSchematic();

	public abstract void writeToNBT(NBTTagCompound nbt, MappingRegistry registry);

	public abstract void readFromNBT(NBTTagCompound nbt, MappingRegistry registry) throws MappingNotFoundException;

	public abstract int getEnergyRequirement();

	public abstract int buildTime();

}
