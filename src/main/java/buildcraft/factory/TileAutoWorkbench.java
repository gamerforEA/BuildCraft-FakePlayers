/**
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.factory;

import java.lang.ref.WeakReference;

import com.gamerforea.buildcraft.EventConfig;

import buildcraft.api.core.IInvSlot;
import buildcraft.api.power.IRedstoneEngine;
import buildcraft.api.power.IRedstoneEngineReceiver;
import buildcraft.api.tiles.IHasWork;
import buildcraft.core.lib.RFBattery;
import buildcraft.core.lib.block.TileBuildCraft;
import buildcraft.core.lib.gui.ContainerDummy;
import buildcraft.core.lib.inventory.InvUtils;
import buildcraft.core.lib.inventory.InventoryConcatenator;
import buildcraft.core.lib.inventory.InventoryIterator;
import buildcraft.core.lib.inventory.SimpleInventory;
import buildcraft.core.lib.inventory.StackHelper;
import buildcraft.core.lib.utils.CraftingUtils;
import buildcraft.core.lib.utils.Utils;
import buildcraft.core.proxy.CoreProxy;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.inventory.SlotCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

public class TileAutoWorkbench extends TileBuildCraft implements ISidedInventory, IHasWork, IRedstoneEngineReceiver
{

	public static final int SLOT_RESULT = 9;
	public static final int CRAFT_TIME = 256;
	public static final int UPDATE_TIME = 16;
	private static final int[] SLOTS = Utils.createSlotArray(0, 10);

	public int progress = 0;
	public LocalInventoryCrafting craftMatrix = new LocalInventoryCrafting();

	private SimpleInventory resultInv = new SimpleInventory(1, "Auto Workbench", 64);
	private SimpleInventory inputInv = new SimpleInventory(9, "Auto Workbench", 64);

	private IInventory inv = InventoryConcatenator.make().add(this.inputInv).add(this.resultInv).add(this.craftMatrix);

	private SlotCrafting craftSlot;
	private InventoryCraftResult craftResult = new InventoryCraftResult();

	private int[] bindings = new int[9];
	private int[] bindingCounts = new int[9];

	private int update = Utils.RANDOM.nextInt();

	private boolean hasWork = false;
	private boolean scheduledCacheRebuild = false;

	public TileAutoWorkbench()
	{
		super();
		this.setBattery(new RFBattery(16, 16, 0));
	}

	@Override
	public boolean hasWork()
	{
		return this.hasWork;
	}

	@Override
	public boolean canConnectRedstoneEngine(ForgeDirection side)
	{
		return true;
	}

	@Override
	public boolean canConnectEnergy(ForgeDirection side)
	{
		TileEntity tile = this.worldObj.getTileEntity(this.xCoord + side.offsetX, this.yCoord + side.offsetY, this.zCoord + side.offsetZ);
		return tile instanceof IRedstoneEngine;
	}

	public class LocalInventoryCrafting extends InventoryCrafting
	{
		public IRecipe currentRecipe;
		public boolean useBindings, isJammed;

		public LocalInventoryCrafting()
		{
			super(new ContainerDummy(), 3, 3);
		}

		@Override
		public ItemStack getStackInSlot(int slot)
		{
			if (this.useBindings)
			{
				if (slot >= 0 && slot < 9 && TileAutoWorkbench.this.bindings[slot] >= 0)
					return TileAutoWorkbench.this.inputInv.getStackInSlot(TileAutoWorkbench.this.bindings[slot]);
				else
					return null;
			}
			else
				return super.getStackInSlot(slot);
		}

		public ItemStack getRecipeOutput()
		{
			this.currentRecipe = this.findRecipe(); // Fixes repair recipe handling (why is it not dynamic?)
			if (this.currentRecipe == null)
				return null;
			ItemStack result = this.currentRecipe.getCraftingResult(this);
			if (result != null)
				result = result.copy();
			return result;
		}

		private IRecipe findRecipe()
		{
			for (IInvSlot slot : InventoryIterator.getIterable(this, ForgeDirection.UP))
			{
				ItemStack stack = slot.getStackInSlot();
				if (stack == null)
					continue;
				if (stack.getItem().hasContainerItem(stack))
					return null;
			}

			return CraftingUtils.findMatchingRecipe(TileAutoWorkbench.this.craftMatrix, TileAutoWorkbench.this.worldObj);
		}

		public void rebuildCache()
		{
			this.currentRecipe = this.findRecipe();
			TileAutoWorkbench.this.hasWork = this.currentRecipe != null && this.currentRecipe.getRecipeOutput() != null;

			ItemStack result = this.getRecipeOutput();
			ItemStack resultInto = TileAutoWorkbench.this.resultInv.getStackInSlot(0);

			if (resultInto != null && (!StackHelper.canStacksMerge(resultInto, result) || resultInto.stackSize + result.stackSize > resultInto.getMaxStackSize()))
				this.isJammed = true;
			else
				this.isJammed = false;
		}

		@Override
		public void setInventorySlotContents(int slot, ItemStack stack)
		{
			if (this.useBindings)
			{
				if (slot >= 0 && slot < 9 && TileAutoWorkbench.this.bindings[slot] >= 0)
					TileAutoWorkbench.this.inputInv.setInventorySlotContents(TileAutoWorkbench.this.bindings[slot], stack);
				return;
			}
			super.setInventorySlotContents(slot, stack);
			TileAutoWorkbench.this.scheduledCacheRebuild = true;
		}

		@Override
		public void markDirty()
		{
			super.markDirty();
			TileAutoWorkbench.this.scheduledCacheRebuild = true;
		}

		@Override
		public ItemStack decrStackSize(int slot, int amount)
		{
			if (this.useBindings)
				if (slot >= 0 && slot < 9 && TileAutoWorkbench.this.bindings[slot] >= 0)
					return TileAutoWorkbench.this.inputInv.decrStackSize(TileAutoWorkbench.this.bindings[slot], amount);
				else
					return null;
			TileAutoWorkbench.this.scheduledCacheRebuild = true;
			return this.decrStackSize(slot, amount);
		}

		public void setUseBindings(boolean use)
		{
			this.useBindings = use;
		}
	}

	public WeakReference<EntityPlayer> getInternalPlayer()
	{
		return CoreProxy.proxy.getBuildCraftPlayer((WorldServer) this.worldObj, this.xCoord, this.yCoord + 1, this.zCoord);
	}

	@Override
	public void markDirty()
	{
		super.markDirty();
		this.inv.markDirty();
	}

	@Override
	public int getSizeInventory()
	{
		return 10;
	}

	@Override
	public ItemStack getStackInSlot(int slot)
	{
		return this.inv.getStackInSlot(slot);
	}

	@Override
	public ItemStack decrStackSize(int slot, int count)
	{
		return this.inv.decrStackSize(slot, count);
	}

	@Override
	public void setInventorySlotContents(int slot, ItemStack stack)
	{
		this.inv.setInventorySlotContents(slot, stack);
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int slot)
	{
		return this.inv.getStackInSlotOnClosing(slot);
	}

	@Override
	public String getInventoryName()
	{
		return "";
	}

	@Override
	public int getInventoryStackLimit()
	{
		return 64;
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer player)
	{
		return this.worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord) == this && player.getDistanceSq(this.xCoord + 0.5D, this.yCoord + 0.5D, this.zCoord + 0.5D) <= 64.0D;
	}

	@Override
	public void readFromNBT(NBTTagCompound data)
	{
		super.readFromNBT(data);
		this.resultInv.readFromNBT(data);
		if (data.hasKey("input"))
		{
			InvUtils.readInvFromNBT(this.inputInv, "input", data);
			InvUtils.readInvFromNBT(this.craftMatrix, "matrix", data);
		}
		else
		{
			InvUtils.readInvFromNBT(this.inputInv, "matrix", data);
			for (int i = 0; i < 9; i++)
			{
				ItemStack inputStack = this.inputInv.getStackInSlot(i);
				if (inputStack != null)
				{
					ItemStack matrixStack = inputStack.copy();
					matrixStack.stackSize = 1;
					this.craftMatrix.setInventorySlotContents(i, matrixStack);
				}
			}
		}

		this.craftMatrix.rebuildCache();
	}

	@Override
	public void writeToNBT(NBTTagCompound data)
	{
		super.writeToNBT(data);
		this.resultInv.writeToNBT(data);
		InvUtils.writeInvToNBT(this.inputInv, "input", data);
		InvUtils.writeInvToNBT(this.craftMatrix, "matrix", data);
	}

	@Override
	public boolean canUpdate()
	{
		return true;
	}

	@Override
	public void updateEntity()
	{
		super.updateEntity();

		if (this.worldObj.isRemote)
			return;

		if (this.scheduledCacheRebuild)
		{
			this.craftMatrix.rebuildCache();
			this.scheduledCacheRebuild = false;
		}

		if (this.craftMatrix.isJammed || this.craftMatrix.currentRecipe == null)
		{
			this.progress = 0;
			return;
		}

		if (this.craftSlot == null)
			this.craftSlot = new SlotCrafting(this.getInternalPlayer().get(), this.craftMatrix, this.craftResult, 0, 0, 0);

		if (!this.hasWork)
			return;

		int updateNext = this.update + this.getBattery().getEnergyStored() + 1;
		int updateThreshold = (this.update & ~15) + 16;
		this.update = Math.min(updateThreshold, updateNext);
		if (this.update % UPDATE_TIME == 0)
			this.updateCrafting();
		this.getBattery().setEnergy(0);
	}

	public int getProgressScaled(int i)
	{
		return this.progress * i / CRAFT_TIME;
	}

	/**
	 * Increment craft job, find recipes, produce output
	 */
	private void updateCrafting()
	{
		this.progress += UPDATE_TIME;

		for (int i = 0; i < 9; i++)
			this.bindingCounts[i] = 0;
		for (int i = 0; i < 9; i++)
		{
			ItemStack comparedStack = this.craftMatrix.getStackInSlot(i);
			if (comparedStack == null || comparedStack.getItem() == null)
			{
				this.bindings[i] = -1;
				continue;
			}

			if (this.bindings[i] == -1 || !StackHelper.isMatchingItem(this.inputInv.getStackInSlot(this.bindings[i]), comparedStack, true, true))
			{
				boolean found = false;
				for (int j = 0; j < 9; j++)
				{
					if (j == this.bindings[i])
						continue;

					ItemStack inputInvStack = this.inputInv.getStackInSlot(j);

					if (StackHelper.isMatchingItem(inputInvStack, comparedStack, true, false) && inputInvStack.stackSize > this.bindingCounts[j])
					{
						// TODO gamerforEA code start
						if (!ItemStack.areItemStackTagsEqual(inputInvStack, comparedStack))
							continue;
						// TODO gamerforEA code end

						found = true;
						this.bindings[i] = j;
						this.bindingCounts[j]++;
						break;
					}
				}
				if (!found)
				{
					this.craftMatrix.isJammed = true;
					this.progress = 0;
					return;
				}
			}
			else
				this.bindingCounts[this.bindings[i]]++;
		}

		for (int i = 0; i < 9; i++)
			if (this.bindingCounts[i] > 0)
			{
				ItemStack stack = this.inputInv.getStackInSlot(i);
				if (stack != null && stack.stackSize < this.bindingCounts[i])
				{
					// Do not break progress yet, instead give it a chance to rebuild
					// It will quit when trying to find a valid binding to "fit in"
					for (int j = 0; j < 9; j++)
						if (this.bindings[j] == i)
							this.bindings[j] = -1;
					return;
				}
			}

		if (this.progress < CRAFT_TIME)
			return;

		this.progress = 0;

		this.craftMatrix.setUseBindings(true);
		ItemStack result = this.craftMatrix.getRecipeOutput();

		// TODO gamerforEA code start
		if (EventConfig.inList(EventConfig.autoCraftBlackList, result.getItem(), result.getItemDamage()))
			result = null;
		ItemStack resultInto = this.resultInv.getStackInSlot(0);
		if (resultInto != null && result != null && result.isItemEqual(resultInto) && !ItemStack.areItemStackTagsEqual(result, resultInto))
			result = null;
		// TODO gamerforEA code end

		if (result != null && result.stackSize > 0)
		{
			// TODO gamerforEA code clear: ItemStack resultInto = this.resultInv.getStackInSlot(0);

			this.craftSlot.onPickupFromSlot(this.getInternalPlayer().get(), result);

			if (resultInto == null)
				this.resultInv.setInventorySlotContents(0, result);
			else
				resultInto.stackSize += result.stackSize;
		}

		this.craftMatrix.setUseBindings(false);
		this.craftMatrix.rebuildCache();
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
	public boolean isItemValidForSlot(int slot, ItemStack stack)
	{
		if (slot == SLOT_RESULT)
			return false;
		if (stack.getItem().hasContainerItem(stack))
			return false;
		return true;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int var1)
	{
		return SLOTS;
	}

	@Override
	public boolean canInsertItem(int slot, ItemStack stack, int side)
	{
		if (slot >= 9)
			return false;
		ItemStack slotStack = this.inv.getStackInSlot(slot);
		if (StackHelper.canStacksMerge(stack, slotStack))
			return true;
		for (int i = 0; i < 9; i++)
		{
			ItemStack inputStack = this.craftMatrix.getStackInSlot(i);
			if (inputStack != null && StackHelper.isMatchingItem(inputStack, stack, true, false))
				return true;
		}
		return false;
	}

	@Override
	public boolean canExtractItem(int slot, ItemStack stack, int side)
	{
		return slot == SLOT_RESULT;
	}

	@Override
	public boolean hasCustomInventoryName()
	{
		return false;
	}
}
