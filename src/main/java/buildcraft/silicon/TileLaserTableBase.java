/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.silicon;

import buildcraft.api.power.ILaserTarget;
import buildcraft.api.tiles.IHasWork;
import buildcraft.core.lib.block.TileBuildCraft;
import buildcraft.core.lib.inventory.SimpleInventory;
import buildcraft.core.lib.inventory.StackHelper;
import buildcraft.core.lib.utils.AverageInt;
import buildcraft.core.lib.utils.Utils;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

public abstract class TileLaserTableBase extends TileBuildCraft implements ILaserTarget, IInventory, IHasWork
{
	public int clientRequiredEnergy = 0;
	protected SimpleInventory inv = new SimpleInventory(this.getSizeInventory(), "inv", 64);
	private int energy = 0;
	private int recentEnergyAverage;
	private AverageInt recentEnergyAverageUtil = new AverageInt(20);

	// TODO gamerforEA code start
	private int recievedLaserEnergyInLastTick;
	private long lastTick;

	public int getRecievedLaserEnergyInLastTick()
	{
		this.resetRecievedLaserEnergyIfNeeded();
		return this.recievedLaserEnergyInLastTick;
	}

	private void resetRecievedLaserEnergyIfNeeded()
	{
		long currentTick = this.worldObj.getTotalWorldTime();
		if (this.lastTick != currentTick)
		{
			this.recievedLaserEnergyInLastTick = 0;
			this.lastTick = currentTick;
		}
	}
	// TODO gamerforEA code end

	@Override
	public void updateEntity()
	{
		super.updateEntity();
		this.recentEnergyAverageUtil.tick();
	}

	public int getEnergy()
	{
		return this.energy;
	}

	public void setEnergy(int energy)
	{
		this.energy = energy;
	}

	public void addEnergy(int energy)
	{
		this.energy += energy;
	}

	public void subtractEnergy(int energy)
	{
		this.energy -= energy;
	}

	public abstract int getRequiredEnergy();

	public int getProgressScaled(int ratio)
	{
		if (this.clientRequiredEnergy == 0)
			return 0;
		if (this.energy >= this.clientRequiredEnergy)
			return ratio;
		return (int) ((double) this.energy / (double) this.clientRequiredEnergy * ratio);
	}

	public int getRecentEnergyAverage()
	{
		return this.recentEnergyAverage;
	}

	public abstract boolean canCraft();

	@Override
	public boolean requiresLaserEnergy()
	{
		return this.canCraft() && this.energy < this.getRequiredEnergy() * 5F;
	}

	@Override
	public void receiveLaserEnergy(int energy)
	{
		// TODO gamerforEA code start
		if (this.hasWorldObj())
		{
			this.resetRecievedLaserEnergyIfNeeded();
			this.recievedLaserEnergyInLastTick += energy;
		}
		// TODO gamerforEA code end

		this.energy += energy;
		this.recentEnergyAverageUtil.push(energy);
	}

	@Override
	public boolean isInvalidTarget()
	{
		return this.isInvalid();
	}

	@Override
	public double getXCoord()
	{
		return this.xCoord;
	}

	@Override
	public double getYCoord()
	{
		return this.yCoord;
	}

	@Override
	public double getZCoord()
	{
		return this.zCoord;
	}

	@Override
	public ItemStack getStackInSlot(int slot)
	{
		return this.inv.getStackInSlot(slot);
	}

	@Override
	public ItemStack decrStackSize(int slot, int amount)
	{
		return this.inv.decrStackSize(slot, amount);
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int slot)
	{
		return this.inv.getStackInSlotOnClosing(slot);
	}

	@Override
	public void setInventorySlotContents(int slot, ItemStack stack)
	{
		this.inv.setInventorySlotContents(slot, stack);
	}

	@Override
	public int getInventoryStackLimit()
	{
		return this.inv.getInventoryStackLimit();
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer player)
	{
		return this.worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord) == this && !this.isInvalid();
	}

	@Override
	public void openInventory()
	{
	}

	@Override
	public void closeInventory()
	{
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);
		this.inv.writeToNBT(nbt, "inv");
		nbt.setInteger("energy", this.energy);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		super.readFromNBT(nbt);
		this.inv.readFromNBT(nbt, "inv");
		this.energy = nbt.getInteger("energy");
	}

	protected void outputStack(ItemStack remaining, boolean autoEject)
	{
		this.outputStack(remaining, null, 0, autoEject);
	}

	protected void outputStack(ItemStack remaining, IInventory inv, int slot, boolean autoEject)
	{
		if (autoEject)
		{
			if (remaining != null && remaining.stackSize > 0)
				remaining.stackSize -= Utils.addToRandomInventoryAround(this.worldObj, this.xCoord, this.yCoord, this.zCoord, remaining);

			if (remaining != null && remaining.stackSize > 0)
				remaining.stackSize -= Utils.addToRandomInjectableAround(this.worldObj, this.xCoord, this.yCoord, this.zCoord, ForgeDirection.UNKNOWN, remaining);
		}

		if (inv != null && remaining != null && remaining.stackSize > 0)
		{
			ItemStack inside = inv.getStackInSlot(slot);

			if (inside == null || inside.stackSize <= 0)
			{
				inv.setInventorySlotContents(slot, remaining);
				return;
			}
			if (StackHelper.canStacksMerge(inside, remaining))
				remaining.stackSize -= StackHelper.mergeStacks(remaining, inside, true);
		}

		if (remaining != null && remaining.stackSize > 0)
		{
			EntityItem entityitem = new EntityItem(this.worldObj, this.xCoord + 0.5, this.yCoord + 0.7, this.zCoord + 0.5, remaining);

			this.worldObj.spawnEntityInWorld(entityitem);
		}
	}

	public void getGUINetworkData(int id, int data)
	{
		int currentStored = this.energy;
		int requiredEnergy = this.clientRequiredEnergy;

		switch (id)
		{
			case 0:
				requiredEnergy = requiredEnergy & 0xFFFF0000 | data & 0xFFFF;
				this.clientRequiredEnergy = requiredEnergy;
				break;
			case 1:
				currentStored = currentStored & 0xFFFF0000 | data & 0xFFFF;
				this.energy = currentStored;
				break;
			case 2:
				requiredEnergy = requiredEnergy & 0xFFFF | (data & 0xFFFF) << 16;
				this.clientRequiredEnergy = requiredEnergy;
				break;
			case 3:
				currentStored = currentStored & 0xFFFF | (data & 0xFFFF) << 16;
				this.energy = currentStored;
				break;
			case 4:
				this.recentEnergyAverage = this.recentEnergyAverage & 0xFFFF0000 | data & 0xFFFF;
				break;
			case 5:
				this.recentEnergyAverage = this.recentEnergyAverage & 0xFFFF | (data & 0xFFFF) << 16;
				break;
		}
	}

	public void sendGUINetworkData(Container container, ICrafting iCrafting)
	{
		int requiredEnergy = this.getRequiredEnergy();
		int currentStored = this.energy;
		int lRecentEnergy = (int) (this.recentEnergyAverageUtil.getAverage() * 100f);
		iCrafting.sendProgressBarUpdate(container, 0, requiredEnergy & 0xFFFF);
		iCrafting.sendProgressBarUpdate(container, 1, currentStored & 0xFFFF);
		iCrafting.sendProgressBarUpdate(container, 2, requiredEnergy >>> 16 & 0xFFFF);
		iCrafting.sendProgressBarUpdate(container, 3, currentStored >>> 16 & 0xFFFF);
		iCrafting.sendProgressBarUpdate(container, 4, lRecentEnergy & 0xFFFF);
		iCrafting.sendProgressBarUpdate(container, 5, lRecentEnergy >>> 16 & 0xFFFF);
	}
}
