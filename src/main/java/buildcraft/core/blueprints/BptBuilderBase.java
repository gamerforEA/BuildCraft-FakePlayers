/**
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.core.blueprints;

import java.util.BitSet;

import org.apache.logging.log4j.Level;

import com.gamerforea.buildcraft.FakePlayerUtils;

import buildcraft.BuildCraftCore;
import buildcraft.api.blueprints.BuilderAPI;
import buildcraft.api.blueprints.IBuilderContext;
import buildcraft.api.blueprints.MappingNotFoundException;
import buildcraft.api.blueprints.SchematicBlock;
import buildcraft.api.blueprints.SchematicBlockBase;
import buildcraft.api.core.BCLog;
import buildcraft.api.core.BlockIndex;
import buildcraft.api.core.IAreaProvider;
import buildcraft.api.core.Position;
import buildcraft.core.Box;
import buildcraft.core.builders.BuildingItem;
import buildcraft.core.builders.BuildingSlot;
import buildcraft.core.builders.BuildingSlotBlock;
import buildcraft.core.builders.IBuildingItemsProvider;
import buildcraft.core.builders.TileAbstractBuilder;
import buildcraft.core.lib.block.TileBuildCraft;
import buildcraft.core.lib.utils.BitSetUtils;
import buildcraft.core.lib.utils.BlockUtils;
import buildcraft.core.proxy.CoreProxy;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.world.BlockEvent;

public abstract class BptBuilderBase implements IAreaProvider
{
	public BlueprintBase blueprint;
	public BptContext context;
	protected BitSet usedLocations;
	protected boolean done;
	protected int x, y, z;
	protected boolean initialized = false;

	private long nextBuildDate = 0;

	public BptBuilderBase(BlueprintBase bluePrint, World world, int x, int y, int z)
	{
		this.blueprint = bluePrint;
		this.x = x;
		this.y = y;
		this.z = z;
		this.usedLocations = new BitSet(bluePrint.sizeX * bluePrint.sizeY * bluePrint.sizeZ);
		this.done = false;

		Box box = new Box();
		box.initialize(this);

		this.context = bluePrint.getContext(world, box);
	}

	protected boolean isLocationUsed(int i, int j, int k)
	{
		int xCoord = i - this.x + this.blueprint.anchorX;
		int yCoord = j - this.y + this.blueprint.anchorY;
		int zCoord = k - this.z + this.blueprint.anchorZ;
		return this.usedLocations.get((zCoord * this.blueprint.sizeY + yCoord) * this.blueprint.sizeX + xCoord);
	}

	protected void markLocationUsed(int i, int j, int k)
	{
		int xCoord = i - this.x + this.blueprint.anchorX;
		int yCoord = j - this.y + this.blueprint.anchorY;
		int zCoord = k - this.z + this.blueprint.anchorZ;
		this.usedLocations.set((zCoord * this.blueprint.sizeY + yCoord) * this.blueprint.sizeX + xCoord, true);
	}

	public void initialize()
	{
		if (!this.initialized)
		{
			this.internalInit();
			this.initialized = true;
		}
	}

	protected abstract void internalInit();

	protected abstract BuildingSlot reserveNextBlock(World world);

	protected abstract BuildingSlot getNextBlock(World world, TileAbstractBuilder inv);

	public boolean buildNextSlot(World world, TileAbstractBuilder builder, double x, double y, double z)
	{
		this.initialize();

		if (world.getTotalWorldTime() < this.nextBuildDate)
			return false;

		BuildingSlot slot = this.getNextBlock(world, builder);

		if (this.buildSlot(world, builder, slot, x + 0.5F, y + 0.5F, z + 0.5F))
		{
			this.nextBuildDate = world.getTotalWorldTime() + slot.buildTime();
			return true;
		}
		else
			return false;
	}

	public boolean buildSlot(World world, IBuildingItemsProvider builder, BuildingSlot slot, double x, double y, double z)
	{
		this.initialize();

		if (slot != null)
		{
			// TODO gamerforEA code start
			EntityPlayer player = builder instanceof TileBuildCraft ? ((TileBuildCraft) builder).getOwnerFake() : CoreProxy.proxy.getBuildCraftPlayer((WorldServer) world).get();
			int xCoord = (int) slot.getDestination().x;
			int yCoord = (int) slot.getDestination().y;
			int zCoord = (int) slot.getDestination().z;
			if (FakePlayerUtils.cantBreak(player, xCoord, yCoord, zCoord))
				return false;
			// TODO gamerforEA code end
			slot.built = true;
			BuildingItem i = new BuildingItem();
			i.origin = new Position(x, y, z);
			i.destination = slot.getDestination();
			i.slotToBuild = slot;
			i.context = this.getContext();
			i.setStacksToDisplay(slot.getStacksToDisplay());
			builder.addAndLaunchBuildingItem(i);

			return true;
		}

		return false;
	}

	public BuildingSlot reserveNextSlot(World world)
	{
		this.initialize();

		return this.reserveNextBlock(world);
	}

	@Override
	public int xMin()
	{
		return this.x - this.blueprint.anchorX;
	}

	@Override
	public int yMin()
	{
		return this.y - this.blueprint.anchorY;
	}

	@Override
	public int zMin()
	{
		return this.z - this.blueprint.anchorZ;
	}

	@Override
	public int xMax()
	{
		return this.x + this.blueprint.sizeX - this.blueprint.anchorX - 1;
	}

	@Override
	public int yMax()
	{
		return this.y + this.blueprint.sizeY - this.blueprint.anchorY - 1;
	}

	@Override
	public int zMax()
	{
		return this.z + this.blueprint.sizeZ - this.blueprint.anchorZ - 1;
	}

	@Override
	public void removeFromWorld()
	{

	}

	public AxisAlignedBB getBoundingBox()
	{
		return AxisAlignedBB.getBoundingBox(this.xMin(), this.yMin(), this.zMin(), this.xMax(), this.yMax(), this.zMax());
	}

	public void postProcessing(World world)
	{

	}

	public BptContext getContext()
	{
		return this.context;
	}

	public boolean isDone(IBuildingItemsProvider builder)
	{
		return this.done && builder.getBuilders().size() == 0;
	}

	private int getBlockBreakEnergy(BuildingSlotBlock slot)
	{
		return BlockUtils.computeBlockBreakEnergy(this.context.world(), slot.x, slot.y, slot.z);
	}

	protected final boolean canDestroy(TileAbstractBuilder builder, IBuilderContext context, BuildingSlotBlock slot)
	{
		return builder.energyAvailable() >= this.getBlockBreakEnergy(slot);
	}

	public void consumeEnergyToDestroy(TileAbstractBuilder builder, BuildingSlotBlock slot)
	{
		builder.consumeEnergy(this.getBlockBreakEnergy(slot));
	}

	public void createDestroyItems(BuildingSlotBlock slot)
	{
		int hardness = (int) Math.ceil((double) this.getBlockBreakEnergy(slot) / BuilderAPI.BREAK_ENERGY);

		for (int i = 0; i < hardness; ++i)
			slot.addStackConsumed(new ItemStack(BuildCraftCore.buildToolBlock));
	}

	public void useRequirements(IInventory inv, BuildingSlot slot)
	{
	}

	public void saveBuildStateToNBT(NBTTagCompound nbt, IBuildingItemsProvider builder)
	{
		nbt.setByteArray("usedLocationList", BitSetUtils.toByteArray(this.usedLocations));

		NBTTagList buildingList = new NBTTagList();

		for (BuildingItem item : builder.getBuilders())
		{
			NBTTagCompound sub = new NBTTagCompound();
			item.writeToNBT(sub);
			buildingList.appendTag(sub);
		}

		nbt.setTag("buildersInAction", buildingList);
	}

	public void loadBuildStateToNBT(NBTTagCompound nbt, IBuildingItemsProvider builder)
	{
		if (nbt.hasKey("usedLocationList"))
			this.usedLocations = BitSetUtils.fromByteArray(nbt.getByteArray("usedLocationList"));

		NBTTagList buildingList = nbt.getTagList("buildersInAction", Constants.NBT.TAG_COMPOUND);

		for (int i = 0; i < buildingList.tagCount(); ++i)
		{
			BuildingItem item = new BuildingItem();

			try
			{
				item.readFromNBT(buildingList.getCompoundTagAt(i));
				item.context = this.getContext();
				builder.getBuilders().add(item);
			}
			catch (MappingNotFoundException e)
			{
				BCLog.logger.log(Level.WARN, "can't load building item", e);
			}
		}

		// 6.4.6 and below migration

		if (nbt.hasKey("clearList"))
		{
			NBTTagList clearList = nbt.getTagList("clearList", Constants.NBT.TAG_COMPOUND);

			for (int i = 0; i < clearList.tagCount(); ++i)
			{
				NBTTagCompound cpt = clearList.getCompoundTagAt(i);
				BlockIndex o = new BlockIndex(cpt);
				this.markLocationUsed(o.x, o.y, o.z);
			}
		}

		if (nbt.hasKey("builtList"))
		{
			NBTTagList builtList = nbt.getTagList("builtList", Constants.NBT.TAG_COMPOUND);

			for (int i = 0; i < builtList.tagCount(); ++i)
			{
				NBTTagCompound cpt = builtList.getCompoundTagAt(i);
				BlockIndex o = new BlockIndex(cpt);
				this.markLocationUsed(o.x, o.y, o.z);
			}
		}
	}

	protected boolean isBlockBreakCanceled(World world, int x, int y, int z)
	{
		if (!world.isAirBlock(x, y, z))
		{
			BlockEvent.BreakEvent breakEvent = new BlockEvent.BreakEvent(x, y, z, world, world.getBlock(x, y, z), world.getBlockMetadata(x, y, z), CoreProxy.proxy.getBuildCraftPlayer((WorldServer) world).get());
			MinecraftForge.EVENT_BUS.post(breakEvent);
			return breakEvent.isCanceled();
		}
		return false;
	}

	protected boolean isBlockPlaceCanceled(World world, int x, int y, int z, SchematicBlockBase schematic)
	{
		Block block = schematic instanceof SchematicBlock ? ((SchematicBlock) schematic).block : Blocks.stone;
		int meta = schematic instanceof SchematicBlock ? ((SchematicBlock) schematic).meta : 0;

		BlockEvent.PlaceEvent placeEvent = new BlockEvent.PlaceEvent(new BlockSnapshot(world, x, y, z, block, meta), Blocks.air, CoreProxy.proxy.getBuildCraftPlayer((WorldServer) world, x, y, z).get());

		MinecraftForge.EVENT_BUS.post(placeEvent);
		return placeEvent.isCanceled();
	}
}