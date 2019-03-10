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
import buildcraft.api.blueprints.Schematic;
import buildcraft.api.blueprints.SchematicBlock;
import buildcraft.api.blueprints.SchematicEntity;
import buildcraft.api.core.*;
import buildcraft.core.builders.*;
import buildcraft.core.builders.BuildingSlotBlock.Mode;
import buildcraft.core.lib.inventory.InventoryCopy;
import buildcraft.core.lib.inventory.InventoryIterator;
import buildcraft.core.lib.utils.BlockUtils;
import com.gamerforea.buildcraft.ModUtils;
import com.gamerforea.eventhelper.util.EventUtils;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import java.util.*;
import java.util.Map.Entry;

public class BptBuilderBlueprint extends BptBuilderBase
{
	protected HashSet<Integer> builtEntities = new HashSet<>();
	protected HashMap<BuilderItemMetaPair, List<BuildingSlotBlock>> buildList = new HashMap<>();
	protected int[] buildStageOccurences;

	private ArrayList<RequirementItemStack> neededItems = new ArrayList<>();

	private LinkedList<BuildingSlotEntity> entityList = new LinkedList<>();
	private LinkedList<BuildingSlot> postProcessing = new LinkedList<>();
	private BuildingSlotMapIterator iterator;
	private IndexRequirementMap requirementMap = new IndexRequirementMap();

	public BptBuilderBlueprint(Blueprint bluePrint, World world, int x, int y, int z)
	{
		super(bluePrint, world, x, y, z);
	}

	@Override
	protected void internalInit()
	{
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
			}
		}

		LinkedList<BuildingSlotBlock> tmpStandalone = new LinkedList<>();
		LinkedList<BuildingSlotBlock> tmpExpanding = new LinkedList<>();

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

					SchematicBlock slot = (SchematicBlock) this.blueprint.get(i, j, k);

					if (slot == null)
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
							case EXPANDING:
								tmpExpanding.add(b);
								b.buildStage = 2;
								break;
						}
					else
						this.postProcessing.add(b);
				}
			}
		}

		for (BuildingSlotBlock b : tmpStandalone)
		{
			this.addToBuildList(b);
		}
		for (BuildingSlotBlock b : tmpExpanding)
		{
			this.addToBuildList(b);
		}

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
		{
			for (BuildingSlotBlock b : lb)
			{
				if (b.mode == Mode.ClearIfInvalid)
				{
					// TODO gamerforEA code start
					if (EventUtils.cantBreak(ModUtils.getModFake(this.context.world()), b.x, b.y, b.z))
						continue;
					// TODO gamerforEA code end

					this.context.world.setBlockToAir(b.x, b.y, b.z);
				}
				else if (!b.schematic.doNotBuild())
				{
					b.stackConsumed = new LinkedList<>();

					try
					{
						for (ItemStack stk : b.getRequirements(this.context))
						{
							if (stk != null)
								b.stackConsumed.add(stk.copy());
						}
					}
					catch (Throwable t)
					{
						// Defensive code against errors in implementers
						t.printStackTrace();
						BCLog.logger.throwing(t);
					}

					b.writeToWorld(this.context);
				}
			}
		}

		for (BuildingSlotEntity e : this.entityList)
		{
			e.stackConsumed = new LinkedList<>();

			try
			{
				for (ItemStack stk : e.getRequirements(this.context))
				{
					if (stk != null)
						e.stackConsumed.add(stk.copy());
				}
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
		{
			for (BuildingSlotBlock b : lb)
			{
				if (b.mode != Mode.ClearIfInvalid)
					b.postProcessing(this.context);
			}
		}

		for (BuildingSlotEntity e : this.entityList)
		{
			e.postProcessing(this.context);
		}
	}

	private void checkDone()
	{
		this.done = this.getBuildListCount() == 0 && this.entityList.size() == 0;
	}

	private int getBuildListCount()
	{
		int out = 0;
		if (this.buildStageOccurences != null)
			for (int buildStageOccurence : this.buildStageOccurences)
			{
				out += buildStageOccurence;
			}
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
				this.buildList.put(imp, new ArrayList<>());
			this.buildList.get(imp).add(b);

			if (this.buildStageOccurences == null)
				this.buildStageOccurences = new int[Math.max(3, b.buildStage + 1)];
			else if (this.buildStageOccurences.length <= b.buildStage)
			{
				int[] newBSO = new int[b.buildStage + 1];
				System.arraycopy(this.buildStageOccurences, 0, newBSO, 0, this.buildStageOccurences.length);
				this.buildStageOccurences = newBSO;
			}
			this.buildStageOccurences[b.buildStage]++;

			if (b.mode == Mode.Build)
			{
				this.requirementMap.add(b, this.context);
				b.internalRequirementRemovalListener = this.requirementMap;
			}
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

	protected boolean readyForSlotLookup(TileAbstractBuilder builder)
	{
		return builder == null || builder.energyAvailable() >= BuilderAPI.BREAK_ENERGY;
	}

	/**
	 * Gets the next available block. If builder is not null, then building will
	 * be verified and performed. Otherwise, the next possible building slot is
	 * returned, possibly for reservation, with no building.
	 */
	private BuildingSlot internalGetNextBlock(World world, TileAbstractBuilder builder)
	{
		if (!this.readyForSlotLookup(builder))
			return null;

		if (this.iterator == null)
			this.iterator = new BuildingSlotMapIterator(this, builder);

		BuildingSlotBlock slot;
		this.iterator.refresh(builder);

		while (this.readyForSlotLookup(builder) && (slot = this.iterator.next()) != null)
		{
			if (!world.blockExists(slot.x, slot.y, slot.z))
				continue;

			boolean skipped = false;

			for (int i = 0; i < slot.buildStage; i++)
			{
				if (this.buildStageOccurences[i] > 0)
				{
					this.iterator.skipKey();
					skipped = true;
					break;
				}
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
					if (slot.mode == Mode.Build)
					{
						this.requirementMap.remove(slot);

						// Even slots that considered already built may need
						// post processing calls. For example, flowing water
						// may need to be adjusted, engines may need to be
						// turned to the right direction, etc.
						this.postProcessing.add(slot);
					}

					this.iterator.remove();
					continue;
				}

				if (BlockUtils.isUnbreakableBlock(world, slot.x, slot.y, slot.z))
				{
					// if the block can't be broken, just forget this iterator
					this.iterator.remove();
					this.markLocationUsed(slot.x, slot.y, slot.z);
					this.requirementMap.remove(slot);
				}
				else if (slot.mode == Mode.ClearIfInvalid)
				{
					// TODO gamerforEA condition replace, old code: isBlockBreakCanceled(world, slot.x, slot.y, slot.z)
					if (BuildCraftAPI.isSoftBlock(world, slot.x, slot.y, slot.z) || EventUtils.cantBreak(builder != null ? builder.fake.get() : ModUtils.getModFake(world), slot.x, slot.y, slot.z))
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
						if (!BuildCraftAPI.isSoftBlock(world, slot.x, slot.y, slot.z) || this.requirementMap.contains(new BlockIndex(slot.x, slot.y, slot.z)))
							continue; // Can't build yet, wait (#2751)
							// TODO gamerforEA condition replace, old code: isBlockPlaceCanceled(world, slot.x, slot.y, slot.z, slot.schematic)
						else if (builder.fake.cantBreak(slot.x, slot.y, slot.z))
						// TODO gamerforEA code end
						{
							// Forge does not allow us to place a block in
							// this position.
							this.iterator.remove();
							this.requirementMap.remove(slot);
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
					this.requirementMap.remove(slot);
					this.iterator.remove();
				}
			}
			catch (Throwable t)
			{
				// Defensive code against errors in implementers
				t.printStackTrace();
				BCLog.logger.throwing(t);
				this.iterator.remove();
				this.requirementMap.remove(slot);
			}
		}

		return null;
	}

	// TODO: Remove recomputeNeededItems() and replace with something more efficient
	private BuildingSlot internalGetNextEntity(World world, TileAbstractBuilder builder)
	{
		Iterator<BuildingSlotEntity> it = this.entityList.iterator();

		while (it.hasNext())
		{
			BuildingSlotEntity slot = it.next();

			if (slot.isAlreadyBuilt(this.context))
			{
				it.remove();
				this.recomputeNeededItems();
			}
			else if (this.checkRequirements(builder, slot.schematic))
			{
				builder.consumeEnergy(slot.getEnergyRequirement());
				this.useRequirements(builder, slot);

				it.remove();
				this.recomputeNeededItems();
				this.postProcessing.add(slot);
				this.builtEntities.add(slot.sequenceNumber);
				return slot;
			}
		}

		return null;
	}

	public boolean checkRequirements(TileAbstractBuilder builder, Schematic slot)
	{
		LinkedList<ItemStack> tmpReq = new LinkedList<>();

		try
		{
			LinkedList<ItemStack> req = new LinkedList<>();

			slot.getRequirementsForPlacement(this.context, req);

			for (ItemStack stk : req)
			{
				if (stk != null)
					tmpReq.add(stk.copy());
			}
		}
		catch (Throwable t)
		{
			// Defensive code against errors in implementers
			t.printStackTrace();
			BCLog.logger.throwing(t);
		}

		LinkedList<ItemStack> stacksUsed = new LinkedList<>();

		if (this.context.world().getWorldInfo().getGameType() == GameType.CREATIVE)
		{
			stacksUsed.addAll(tmpReq);

			return !(builder.energyAvailable() < slot.getEnergyRequirement(stacksUsed));
		}

		IInventory invCopy = new InventoryCopy(builder);

		for (ItemStack reqStk : tmpReq)
		{
			boolean itemBlock = reqStk.getItem() instanceof ItemBlock;
			Fluid fluid = itemBlock ? FluidRegistry.lookupFluidForBlock(((ItemBlock) reqStk.getItem()).field_150939_a) : null;

			if (fluid != null && builder.drainBuild(new FluidStack(fluid, FluidContainerRegistry.BUCKET_VOLUME), true))
				continue;

			for (IInvSlot slotInv : InventoryIterator.getIterable(invCopy, ForgeDirection.UNKNOWN))
			{
				if (!builder.isBuildingMaterialSlot(slotInv.getIndex()))
					continue;

				ItemStack invStk = slotInv.getStackInSlot();
				if (invStk == null || invStk.stackSize == 0)
					continue;

				FluidStack fluidStack = fluid != null ? FluidContainerRegistry.getFluidForFilledItem(invStk) : null;
				boolean compatibleContainer = fluidStack != null && fluidStack.getFluid() == fluid && fluidStack.amount >= FluidContainerRegistry.BUCKET_VOLUME;

				if (slot.isItemMatchingRequirement(invStk, reqStk) || compatibleContainer)
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

		LinkedList<ItemStack> tmpReq = new LinkedList<>();

		try
		{
			for (ItemStack stk : slot.getRequirements(this.context))
			{
				if (stk != null)
					tmpReq.add(stk.copy());
			}
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
			{
				slot.addStackConsumed(s);
			}

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

				if (fluidFound || slot.getSchematic().isItemMatchingRequirement(invStk, reqStk))
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

	public List<RequirementItemStack> getNeededItems()
	{
		return this.neededItems;
	}

	protected void onRemoveBuildingSlotBlock(BuildingSlotBlock slot)
	{
		this.buildStageOccurences[slot.buildStage]--;
		LinkedList<ItemStack> stacks = new LinkedList<>();

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

		HashMap<StackKey, Integer> computeStacks = new HashMap<>();

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

		for (RequirementItemStack ris : this.neededItems)
		{
			StackKey stackKey = new StackKey(ris.stack);
			if (computeStacks.containsKey(stackKey))
			{
				Integer num = computeStacks.get(stackKey);
				if (ris.size <= num)
				{
					this.recomputeNeededItems();
					return;
				}
				else
					this.neededItems.set(this.neededItems.indexOf(ris), new RequirementItemStack(ris.stack, ris.size - num));
			}
		}

		this.sortNeededItems();
	}

	private void sortNeededItems()
	{
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
					else
						return Integer.compare(os2.getItemDamage(), os1.getItemDamage());
				}
			}
		});
	}

	private void recomputeNeededItems()
	{
		this.neededItems.clear();

		HashMap<StackKey, Integer> computeStacks = new HashMap<>();

		for (List<BuildingSlotBlock> lb : this.buildList.values())
		{
			for (BuildingSlotBlock slot : lb)
			{
				if (slot == null)
					continue;

				LinkedList<ItemStack> stacks = new LinkedList<>();

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
		}

		for (BuildingSlotEntity slot : this.entityList)
		{
			LinkedList<ItemStack> stacks = new LinkedList<>();

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
		{
			this.neededItems.add(new RequirementItemStack(e.getKey().stack.copy(), e.getValue()));
		}

		this.sortNeededItems();
	}

	@Override
	public void postProcessing(World world)
	{
		for (BuildingSlot s : this.postProcessing)
		{
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
		{
			this.builtEntities.add(i);
		}
	}
}
