/**
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.core.blueprints;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map.Entry;

import com.gamerforea.buildcraft.FakePlayerUtils;

import buildcraft.api.blueprints.BuilderAPI;
import buildcraft.api.blueprints.Schematic;
import buildcraft.api.blueprints.SchematicBlock;
import buildcraft.api.blueprints.SchematicEntity;
import buildcraft.api.core.BCLog;
import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.core.IInvSlot;
import buildcraft.api.core.StackKey;
import buildcraft.core.builders.BuilderItemMetaPair;
import buildcraft.core.builders.BuildingSlot;
import buildcraft.core.builders.BuildingSlotBlock;
import buildcraft.core.builders.BuildingSlotBlock.Mode;
import buildcraft.core.builders.BuildingSlotEntity;
import buildcraft.core.builders.BuildingSlotMapIterator;
import buildcraft.core.builders.IBuildingItemsProvider;
import buildcraft.core.builders.TileAbstractBuilder;
import buildcraft.core.lib.block.TileBuildCraft;
import buildcraft.core.lib.inventory.InventoryCopy;
import buildcraft.core.lib.inventory.InventoryIterator;
import buildcraft.core.lib.inventory.StackHelper;
import buildcraft.core.lib.utils.BlockUtils;
import buildcraft.core.proxy.CoreProxy;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

public class BptBuilderBlueprint extends BptBuilderBase
{
	public ArrayList<RequirementItemStack> neededItems = new ArrayList<RequirementItemStack>();

	protected HashSet<Integer> builtEntities = new HashSet<Integer>();

	private HashMap<BuilderItemMetaPair, List<BuildingSlotBlock>> buildList = new HashMap<BuilderItemMetaPair, List<BuildingSlotBlock>>();
	private int[] buildStageOccurences;
	private LinkedList<BuildingSlotEntity> entityList = new LinkedList<BuildingSlotEntity>();
	private LinkedList<BuildingSlot> postProcessing = new LinkedList<BuildingSlot>();
	private BuildingSlotMapIterator iterator;

	public BptBuilderBlueprint(Blueprint bluePrint, World world, int x, int y, int z)
	{
		super(bluePrint, world, x, y, z);
	}

	@Override
	protected void internalInit()
	{
		for (int j = this.blueprint.sizeY - 1; j >= 0; --j)
			for (int i = 0; i < this.blueprint.sizeX; ++i)
				for (int k = 0; k < this.blueprint.sizeZ; ++k)
				{
					int xCoord = i + this.x - this.blueprint.anchorX;
					int yCoord = j + this.y - this.blueprint.anchorY;
					int zCoord = k + this.z - this.blueprint.anchorZ;

					if (yCoord < 0 || yCoord >= this.context.world.getHeight())
						continue;

					if (!this.isLocationUsed(xCoord, yCoord, zCoord))
					{
						SchematicBlock slot = (SchematicBlock) this.blueprint.get(i, j, k);

						if (slot == null && !this.blueprint.excavate)
							continue;

						if (slot == null)
						{
							slot = new SchematicBlock();
							slot.meta = 0;
							slot.block = Blocks.air;
						}

						if (!SchematicRegistry.INSTANCE.isAllowedForBuilding(slot.block, slot.meta))
							continue;

						BuildingSlotBlock b = new BuildingSlotBlock();
						b.schematic = slot;
						b.x = xCoord;
						b.y = yCoord;
						b.z = zCoord;
						b.mode = Mode.ClearIfInvalid;
						b.buildStage = 0;

						this.addToBuildList(b);
					}
				}

		LinkedList<BuildingSlotBlock> tmpStandalone = new LinkedList<BuildingSlotBlock>();
		LinkedList<BuildingSlotBlock> tmpSupported = new LinkedList<BuildingSlotBlock>();
		LinkedList<BuildingSlotBlock> tmpExpanding = new LinkedList<BuildingSlotBlock>();

		for (int j = 0; j < this.blueprint.sizeY; ++j)
			for (int i = 0; i < this.blueprint.sizeX; ++i)
				for (int k = 0; k < this.blueprint.sizeZ; ++k)
				{
					int xCoord = i + this.x - this.blueprint.anchorX;
					int yCoord = j + this.y - this.blueprint.anchorY;
					int zCoord = k + this.z - this.blueprint.anchorZ;

					SchematicBlock slot = (SchematicBlock) this.blueprint.get(i, j, k);

					if (slot == null || yCoord < 0 || yCoord >= this.context.world.getHeight())
						continue;

					if (!SchematicRegistry.INSTANCE.isAllowedForBuilding(slot.block, slot.meta))
						continue;

					BuildingSlotBlock b = new BuildingSlotBlock();
					b.schematic = slot;
					b.x = xCoord;
					b.y = yCoord;
					b.z = zCoord;
					b.mode = Mode.Build;

					if (!this.isLocationUsed(xCoord, yCoord, zCoord))
						switch (slot.getBuildStage())
						{
							case STANDALONE:
								tmpStandalone.add(b);
								b.buildStage = 1;
								break;
							case SUPPORTED:
								tmpSupported.add(b);
								b.buildStage = 2;
								break;
							case EXPANDING:
								tmpExpanding.add(b);
								b.buildStage = 3;
								break;

						}
					else
						this.postProcessing.add(b);
				}

		for (BuildingSlotBlock b : tmpStandalone)
			this.addToBuildList(b);
		for (BuildingSlotBlock b : tmpSupported)
			this.addToBuildList(b);
		for (BuildingSlotBlock b : tmpExpanding)
			this.addToBuildList(b);

		int seqId = 0;

		for (SchematicEntity e : ((Blueprint) this.blueprint).entities)
		{

			BuildingSlotEntity b = new BuildingSlotEntity();
			b.schematic = e;
			b.sequenceNumber = seqId;

			if (!this.builtEntities.contains(seqId))
				this.entityList.add(b);
			else
				this.postProcessing.add(b);

			seqId++;
		}

		this.recomputeNeededItems();
	}

	public void deploy()
	{
		this.initialize();

		for (List<BuildingSlotBlock> lb : this.buildList.values())
			for (BuildingSlotBlock b : lb)
				if (b.mode == Mode.ClearIfInvalid)
					this.context.world.setBlockToAir(b.x, b.y, b.z);
				else if (!b.schematic.doNotBuild())
				{
					b.stackConsumed = new LinkedList<ItemStack>();

					try
					{
						for (ItemStack stk : b.getRequirements(this.context))
							if (stk != null)
								b.stackConsumed.add(stk.copy());
					}
					catch (Throwable t)
					{
						// Defensive code against errors in implementers
						t.printStackTrace();
						BCLog.logger.throwing(t);
					}

					b.writeToWorld(this.context);
				}

		for (BuildingSlotEntity e : this.entityList)
		{
			e.stackConsumed = new LinkedList<ItemStack>();

			try
			{
				for (ItemStack stk : e.getRequirements(this.context))
					if (stk != null)
						e.stackConsumed.add(stk.copy());
			}
			catch (Throwable t)
			{
				// Defensive code against errors in implementers
				t.printStackTrace();
				BCLog.logger.throwing(t);
			}

			e.writeToWorld(this.context);
		}

		for (List<BuildingSlotBlock> lb : this.buildList.values())
			for (BuildingSlotBlock b : lb)
				if (b.mode != Mode.ClearIfInvalid)
					b.postProcessing(this.context);

		for (BuildingSlotEntity e : this.entityList)
			e.postProcessing(this.context);
	}

	private void checkDone()
	{
		this.recomputeNeededItems();

		if (this.getBuildListCount() == 0 && this.entityList.size() == 0)
			this.done = true;
		else
			this.done = false;
	}

	private int getBuildListCount()
	{
		int out = 0;
		if (this.buildStageOccurences != null)
			for (int buildStageOccurence : this.buildStageOccurences)
				out += buildStageOccurence;
		return out;
	}

	@Override
	public BuildingSlot reserveNextBlock(World world)
	{
		if (this.getBuildListCount() != 0)
		{
			BuildingSlot slot = this.internalGetNextBlock(world, null);
			this.checkDone();

			if (slot != null)
				slot.reserved = true;

			return slot;
		}

		return null;
	}

	private void addToBuildList(BuildingSlotBlock b)
	{
		if (b != null)
		{
			BuilderItemMetaPair imp = new BuilderItemMetaPair(this.context, b);
			if (!this.buildList.containsKey(imp))
				this.buildList.put(imp, new ArrayList<BuildingSlotBlock>());
			this.buildList.get(imp).add(b);
			if (this.buildStageOccurences == null)
				this.buildStageOccurences = new int[Math.max(4, b.buildStage + 1)];
			else if (this.buildStageOccurences.length <= b.buildStage)
			{
				int[] newBSO = new int[b.buildStage + 1];
				System.arraycopy(this.buildStageOccurences, 0, newBSO, 0, this.buildStageOccurences.length);
				this.buildStageOccurences = newBSO;
			}
			this.buildStageOccurences[b.buildStage]++;
		}
	}

	@Override
	public BuildingSlot getNextBlock(World world, TileAbstractBuilder inv)
	{
		if (this.getBuildListCount() != 0)
		{
			BuildingSlot slot = this.internalGetNextBlock(world, inv);
			this.checkDone();
			return slot;
		}

		if (this.entityList.size() != 0)
		{
			BuildingSlot slot = this.internalGetNextEntity(world, inv);
			this.checkDone();
			return slot;
		}

		this.checkDone();
		return null;
	}

	/**
	 * Gets the next available block. If builder is not null, then building will
	 * be verified and performed. Otherwise, the next possible building slot is
	 * returned, possibly for reservation, with no building.
	 */
	private BuildingSlot internalGetNextBlock(World world, TileAbstractBuilder builder)
	{
		if (builder != null && builder.energyAvailable() < BuilderAPI.BREAK_ENERGY)
			return null;

		this.iterator = new BuildingSlotMapIterator(this.buildList, builder, this.buildStageOccurences);
		BuildingSlotBlock slot;

		while ((slot = this.iterator.next()) != null)
		{
			if (!world.blockExists(slot.x, slot.y, slot.z))
				continue;

			boolean skipped = false;

			for (int i = 0; i < slot.buildStage; i++)
				if (this.buildStageOccurences[i] > 0)
				{
					this.iterator.skipList();
					skipped = true;
					break;
				}

			if (skipped)
				continue;

			if (slot.built)
			{
				this.iterator.remove();
				this.markLocationUsed(slot.x, slot.y, slot.z);
				this.postProcessing.add(slot);

				continue;
			}

			if (slot.reserved)
				continue;

			try
			{
				if (slot.isAlreadyBuilt(this.context))
				{
					if (slot.mode == Mode.Build) // Even slots that considered already built may need
						// post processing calls. For example, flowing water
						// may need to be adjusted, engines may need to be
						// turned to the right direction, etc.
						this.postProcessing.add(slot);

					this.iterator.remove();

					continue;
				}

				if (BlockUtils.isUnbreakableBlock(world, slot.x, slot.y, slot.z))
				{
					// if the block can't be broken, just forget this iterator
					this.iterator.remove();
					this.markLocationUsed(slot.x, slot.y, slot.z);
				}
				else if (slot.mode == Mode.ClearIfInvalid)
				{
					// TODO gamerforEA condition replace, old code: isBlockBreakCanceled(world, slot.x, slot.y, slot.z)
					if (BuildCraftAPI.isSoftBlock(world, slot.x, slot.y, slot.z) || FakePlayerUtils.cantBreak(builder instanceof TileBuildCraft ? ((TileBuildCraft) builder).getOwnerFake() : CoreProxy.proxy.getBuildCraftPlayer((WorldServer) world).get(), slot.x, slot.y, slot.z))
					// TODO gamerforEA code end
					{
						this.iterator.remove();
						this.markLocationUsed(slot.x, slot.y, slot.z);
					}
					else if (builder == null)
					{
						this.createDestroyItems(slot);
						return slot;
					}
					else if (this.canDestroy(builder, this.context, slot))
					{
						this.consumeEnergyToDestroy(builder, slot);
						this.createDestroyItems(slot);

						this.iterator.remove();
						this.markLocationUsed(slot.x, slot.y, slot.z);
						return slot;
					}
				}
				else if (!slot.schematic.doNotBuild())
				{
					if (builder == null)
						return slot;
					else if (this.checkRequirements(builder, slot.schematic))
					{
						if (!BuildCraftAPI.isSoftBlock(world, slot.x, slot.y, slot.z))
							continue; // Can't build yet, wait (#2751)
						else if (FakePlayerUtils.cantBreak(builder instanceof TileBuildCraft ? ((TileBuildCraft) builder).getOwnerFake() : CoreProxy.proxy.getBuildCraftPlayer((WorldServer) world).get(), slot.x, slot.y, slot.z))
						// TODO gamerforEA code end
						{
							// Forge does not allow us to place a block in
							// this position.
							this.iterator.remove();
							this.markLocationUsed(slot.x, slot.y, slot.z);
							continue;
						}

						// At this stage, regardless of the fact that the
						// block can actually be built or not, we'll try.
						// When the item reaches the actual block, we'll
						// verify that the location is indeed clear, and
						// avoid building otherwise.
						builder.consumeEnergy(slot.getEnergyRequirement());
						this.useRequirements(builder, slot);

						this.iterator.remove();
						this.markLocationUsed(slot.x, slot.y, slot.z);
						this.postProcessing.add(slot);
						return slot;
					}
				}
				else
				{
					// Even slots that don't need to be build may need
					// post processing, see above for the argument.
					this.postProcessing.add(slot);
					this.iterator.remove();
				}
			}
			catch (Throwable t)
			{
				// Defensive code against errors in implementers
				t.printStackTrace();
				BCLog.logger.throwing(t);
				this.iterator.remove();
			}
		}

		return null;
	}

	private BuildingSlot internalGetNextEntity(World world, TileAbstractBuilder builder)
	{
		Iterator<BuildingSlotEntity> it = this.entityList.iterator();

		while (it.hasNext())
		{
			BuildingSlotEntity slot = it.next();

			if (slot.isAlreadyBuilt(this.context))
				it.remove();
			else if (this.checkRequirements(builder, slot.schematic))
			{
				builder.consumeEnergy(slot.getEnergyRequirement());
				this.useRequirements(builder, slot);

				it.remove();
				this.postProcessing.add(slot);
				this.builtEntities.add(slot.sequenceNumber);
				return slot;
			}
		}

		return null;
	}

	public boolean checkRequirements(TileAbstractBuilder builder, Schematic slot)
	{
		LinkedList<ItemStack> tmpReq = new LinkedList<ItemStack>();

		try
		{
			LinkedList<ItemStack> req = new LinkedList<ItemStack>();

			slot.getRequirementsForPlacement(this.context, req);

			for (ItemStack stk : req)
				if (stk != null)
					tmpReq.add(stk.copy());
		}
		catch (Throwable t)
		{
			// Defensive code against errors in implementers
			t.printStackTrace();
			BCLog.logger.throwing(t);
		}

		LinkedList<ItemStack> stacksUsed = new LinkedList<ItemStack>();

		if (this.context.world().getWorldInfo().getGameType() == GameType.CREATIVE)
		{
			for (ItemStack s : tmpReq)
				stacksUsed.add(s);

			return !(builder.energyAvailable() < slot.getEnergyRequirement(stacksUsed));
		}

		for (ItemStack reqStk : tmpReq)
		{
			boolean itemBlock = reqStk.getItem() instanceof ItemBlock;
			Fluid fluid = itemBlock ? FluidRegistry.lookupFluidForBlock(((ItemBlock) reqStk.getItem()).field_150939_a) : null;

			if (fluid != null && builder.drainBuild(new FluidStack(fluid, FluidContainerRegistry.BUCKET_VOLUME), true))
				continue;

			for (IInvSlot slotInv : InventoryIterator.getIterable(new InventoryCopy(builder), ForgeDirection.UNKNOWN))
			{
				if (!builder.isBuildingMaterialSlot(slotInv.getIndex()))
					continue;

				ItemStack invStk = slotInv.getStackInSlot();
				if (invStk == null || invStk.stackSize == 0)
					continue;

				FluidStack fluidStack = fluid != null ? FluidContainerRegistry.getFluidForFilledItem(invStk) : null;
				boolean compatibleContainer = fluidStack != null && fluidStack.getFluid() == fluid && fluidStack.amount >= FluidContainerRegistry.BUCKET_VOLUME;

				if (StackHelper.isMatchingItem(reqStk, invStk, true, true) || compatibleContainer)
				{
					try
					{
						stacksUsed.add(slot.useItem(this.context, reqStk, slotInv));
					}
					catch (Throwable t)
					{
						// Defensive code against errors in implementers
						t.printStackTrace();
						BCLog.logger.throwing(t);
					}

					if (reqStk.stackSize == 0)
						break;
				}
			}

			if (reqStk.stackSize != 0)
				return false;
		}

		return builder.energyAvailable() >= slot.getEnergyRequirement(stacksUsed);
	}

	@Override
	public void useRequirements(IInventory inv, BuildingSlot slot)
	{
		if (slot instanceof BuildingSlotBlock && ((BuildingSlotBlock) slot).mode == Mode.ClearIfInvalid)
			return;

		LinkedList<ItemStack> tmpReq = new LinkedList<ItemStack>();

		try
		{
			for (ItemStack stk : slot.getRequirements(this.context))
				if (stk != null)
					tmpReq.add(stk.copy());
		}
		catch (Throwable t)
		{
			// Defensive code against errors in implementers
			t.printStackTrace();
			BCLog.logger.throwing(t);

		}

		if (this.context.world().getWorldInfo().getGameType() == GameType.CREATIVE)
		{
			for (ItemStack s : tmpReq)
				slot.addStackConsumed(s);

			return;
		}

		ListIterator<ItemStack> itr = tmpReq.listIterator();

		while (itr.hasNext())
		{
			ItemStack reqStk = itr.next();
			boolean smallStack = reqStk.stackSize == 1;
			ItemStack usedStack = reqStk;

			boolean itemBlock = reqStk.getItem() instanceof ItemBlock;
			Fluid fluid = itemBlock ? FluidRegistry.lookupFluidForBlock(((ItemBlock) reqStk.getItem()).field_150939_a) : null;

			if (fluid != null && inv instanceof TileAbstractBuilder && ((TileAbstractBuilder) inv).drainBuild(new FluidStack(fluid, FluidContainerRegistry.BUCKET_VOLUME), true))
				continue;

			for (IInvSlot slotInv : InventoryIterator.getIterable(inv, ForgeDirection.UNKNOWN))
			{
				if (inv instanceof TileAbstractBuilder && !((TileAbstractBuilder) inv).isBuildingMaterialSlot(slotInv.getIndex()))
					continue;

				ItemStack invStk = slotInv.getStackInSlot();

				if (invStk == null || invStk.stackSize == 0)
					continue;

				FluidStack fluidStack = fluid != null ? FluidContainerRegistry.getFluidForFilledItem(invStk) : null;
				boolean fluidFound = fluidStack != null && fluidStack.getFluid() == fluid && fluidStack.amount >= FluidContainerRegistry.BUCKET_VOLUME;

				if (fluidFound || StackHelper.isCraftingEquivalent(reqStk, invStk, true))
				{
					try
					{
						usedStack = slot.getSchematic().useItem(this.context, reqStk, slotInv);
						slot.addStackConsumed(usedStack);
					}
					catch (Throwable t)
					{
						// Defensive code against errors in implementers
						t.printStackTrace();
						BCLog.logger.throwing(t);
					}

					if (reqStk.stackSize == 0)
						break;
				}
			}

			if (reqStk.stackSize != 0)
				return;

			if (smallStack)
				itr.set(usedStack); // set to the actual item used.
		}
	}

	public void recomputeNeededItems()
	{
		this.neededItems.clear();

		HashMap<StackKey, Integer> computeStacks = new HashMap<StackKey, Integer>();

		for (List<BuildingSlotBlock> lb : this.buildList.values())
			for (BuildingSlotBlock slot : lb)
			{
				if (slot == null)
					continue;

				LinkedList<ItemStack> stacks = new LinkedList<ItemStack>();

				try
				{
					stacks = slot.getRequirements(this.context);
				}
				catch (Throwable t)
				{
					// Defensive code against errors in implementers
					t.printStackTrace();
					BCLog.logger.throwing(t);
				}

				for (ItemStack stack : stacks)
				{
					if (stack == null || stack.getItem() == null || stack.stackSize == 0)
						continue;

					StackKey key = new StackKey(stack);

					if (!computeStacks.containsKey(key))
						computeStacks.put(key, stack.stackSize);
					else
					{
						Integer num = computeStacks.get(key);
						num += stack.stackSize;

						computeStacks.put(key, num);
					}
				}
			}

		for (BuildingSlotEntity slot : this.entityList)
		{
			LinkedList<ItemStack> stacks = new LinkedList<ItemStack>();

			try
			{
				stacks = slot.getRequirements(this.context);
			}
			catch (Throwable t)
			{
				// Defensive code against errors in implementers
				t.printStackTrace();
				BCLog.logger.throwing(t);
			}

			for (ItemStack stack : stacks)
			{
				if (stack == null || stack.getItem() == null || stack.stackSize == 0)
					continue;

				StackKey key = new StackKey(stack);

				if (!computeStacks.containsKey(key))
					computeStacks.put(key, stack.stackSize);
				else
				{
					Integer num = computeStacks.get(key);
					num += stack.stackSize;

					computeStacks.put(key, num);
				}

			}
		}

		for (Entry<StackKey, Integer> e : computeStacks.entrySet())
			this.neededItems.add(new RequirementItemStack(e.getKey().stack.copy(), e.getValue()));

		Collections.sort(this.neededItems, new Comparator<RequirementItemStack>()
		{
			@Override
			public int compare(RequirementItemStack o1, RequirementItemStack o2)
			{
				if (o1.size != o2.size)
					return o1.size < o2.size ? 1 : -1;
				else
				{
					ItemStack os1 = o1.stack;
					ItemStack os2 = o2.stack;
					if (Item.getIdFromItem(os1.getItem()) > Item.getIdFromItem(os2.getItem()))
						return -1;
					else if (Item.getIdFromItem(os1.getItem()) < Item.getIdFromItem(os2.getItem()))
						return 1;
					else if (os1.getItemDamage() > os2.getItemDamage())
						return -1;
					else if (os1.getItemDamage() < os2.getItemDamage())
						return 1;
					else
						return 0;
				}
			}
		});
	}

	@Override
	public void postProcessing(World world)
	{
		for (BuildingSlot s : this.postProcessing)
			try
			{
				s.postProcessing(this.context);
			}
			catch (Throwable t)
			{
				// Defensive code against errors in implementers
				t.printStackTrace();
				BCLog.logger.throwing(t);
			}
	}

	@Override
	public void saveBuildStateToNBT(NBTTagCompound nbt, IBuildingItemsProvider builder)
	{
		super.saveBuildStateToNBT(nbt, builder);

		int[] entitiesBuiltArr = new int[this.builtEntities.size()];

		int id = 0;

		for (Integer i : this.builtEntities)
		{
			entitiesBuiltArr[id] = i;
			id++;
		}

		nbt.setIntArray("builtEntities", entitiesBuiltArr);
	}

	@Override
	public void loadBuildStateToNBT(NBTTagCompound nbt, IBuildingItemsProvider builder)
	{
		super.loadBuildStateToNBT(nbt, builder);

		int[] entitiesBuiltArr = nbt.getIntArray("builtEntities");

		for (int i = 0; i < entitiesBuiltArr.length; ++i)
			this.builtEntities.add(i);
	}

}
