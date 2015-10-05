/**
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.factory;

import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeMap;

import com.gamerforea.eventhelper.util.EventUtils;

import buildcraft.api.core.BlockIndex;
import buildcraft.api.core.BuildCraftAPI;
import buildcraft.core.lib.block.TileBuildCraft;
import buildcraft.core.lib.fluids.Tank;
import buildcraft.core.lib.fluids.TankUtils;
import buildcraft.core.lib.utils.BlockUtils;
import buildcraft.core.lib.utils.Utils;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

public class TileFloodGate extends TileBuildCraft implements IFluidHandler
{
	public static final int[] REBUILD_DELAY = new int[8];
	public static final int MAX_LIQUID = FluidContainerRegistry.BUCKET_VOLUME * 2;
	private final TreeMap<Integer, Deque<BlockIndex>> pumpLayerQueues = new TreeMap<Integer, Deque<BlockIndex>>();
	private final Set<BlockIndex> visitedBlocks = new HashSet<BlockIndex>();
	private Deque<BlockIndex> fluidsFound = new LinkedList<BlockIndex>();
	private final Tank tank = new Tank("tank", MAX_LIQUID, this);
	private int rebuildDelay;
	private int tick = Utils.RANDOM.nextInt();
	private boolean powered = false;
	private boolean[] blockedSides = new boolean[6];

	static
	{
		REBUILD_DELAY[0] = 128;
		REBUILD_DELAY[1] = 256;
		REBUILD_DELAY[2] = 512;
		REBUILD_DELAY[3] = 1024;
		REBUILD_DELAY[4] = 2048;
		REBUILD_DELAY[5] = 4096;
		REBUILD_DELAY[6] = 8192;
		REBUILD_DELAY[7] = 16384;
	}

	public TileFloodGate()
	{
	}

	@Override
	public void updateEntity()
	{
		super.updateEntity();

		if (this.worldObj.isRemote)
			return;

		if (this.powered)
			return;

		this.tick++;
		if (this.tick % 16 == 0)
		{
			FluidStack fluidtoFill = this.tank.drain(FluidContainerRegistry.BUCKET_VOLUME, false);
			if (fluidtoFill != null && fluidtoFill.amount == FluidContainerRegistry.BUCKET_VOLUME)
			{
				Fluid fluid = fluidtoFill.getFluid();
				if (fluid == null || !fluid.canBePlacedInWorld())
					return;

				if (fluid == FluidRegistry.WATER && this.worldObj.provider.dimensionId == -1)
				{
					this.tank.drain(FluidContainerRegistry.BUCKET_VOLUME, true);
					return;
				}

				if (this.tick % REBUILD_DELAY[this.rebuildDelay] == 0)
				{
					this.rebuildDelay++;
					if (this.rebuildDelay >= REBUILD_DELAY.length)
						this.rebuildDelay = REBUILD_DELAY.length - 1;
					this.rebuildQueue();
				}
				BlockIndex index = this.getNextIndexToFill(true);

				if (index != null && this.placeFluid(index.x, index.y, index.z, fluid))
				{
					this.tank.drain(FluidContainerRegistry.BUCKET_VOLUME, true);
					this.rebuildDelay = 0;
				}
			}
		}
	}

	private boolean placeFluid(int x, int y, int z, Fluid fluid)
	{
		Block block = BlockUtils.getBlock(this.worldObj, x, y, z);

		if (this.canPlaceFluidAt(block, x, y, z))
		{
			// TODO gamerforEA code start
			if (EventUtils.cantBreak(this.fake.getPlayer(), x, y, z))
				return false;
			// TODO gamerforEA code end

			boolean placed;
			Block b = TankUtils.getFluidBlock(fluid, true);

			if (b instanceof BlockFluidBase)
			{
				BlockFluidBase blockFluid = (BlockFluidBase) b;
				placed = this.worldObj.setBlock(x, y, z, b, blockFluid.getMaxRenderHeightMeta(), 3);
			}
			else
				placed = this.worldObj.setBlock(x, y, z, b);

			if (placed)
			{
				this.queueAdjacent(x, y, z);
				this.expandQueue();
			}

			return placed;
		}

		return false;
	}

	private BlockIndex getNextIndexToFill(boolean remove)
	{
		if (this.pumpLayerQueues.isEmpty())
			return null;

		Deque<BlockIndex> bottomLayer = this.pumpLayerQueues.firstEntry().getValue();

		if (bottomLayer != null)
		{
			if (bottomLayer.isEmpty())
				this.pumpLayerQueues.pollFirstEntry();
			if (remove)
			{
				BlockIndex index = bottomLayer.pollFirst();
				return index;
			}
			return bottomLayer.peekFirst();
		}

		return null;
	}

	private Deque<BlockIndex> getLayerQueue(int layer)
	{
		Deque<BlockIndex> pumpQueue = this.pumpLayerQueues.get(layer);
		if (pumpQueue == null)
		{
			pumpQueue = new LinkedList<BlockIndex>();
			this.pumpLayerQueues.put(layer, pumpQueue);
		}
		return pumpQueue;
	}

	/**
	 * Nasty expensive function, don't call if you don't have to.
	 */
	void rebuildQueue()
	{
		this.pumpLayerQueues.clear();
		this.visitedBlocks.clear();
		this.fluidsFound.clear();

		this.queueAdjacent(this.xCoord, this.yCoord, this.zCoord);

		this.expandQueue();
	}

	private void expandQueue()
	{
		if (this.tank.getFluidType() == null)
			return;
		while (!this.fluidsFound.isEmpty())
		{
			Deque<BlockIndex> fluidsToExpand = this.fluidsFound;
			this.fluidsFound = new LinkedList<BlockIndex>();

			for (BlockIndex index : fluidsToExpand)
				this.queueAdjacent(index.x, index.y, index.z);
		}
	}

	public void queueAdjacent(int x, int y, int z)
	{
		if (this.tank.getFluidType() == null)
			return;
		for (int i = 0; i < 6; i++)
			if (i != 1 && !this.blockedSides[i])
			{
				ForgeDirection dir = ForgeDirection.getOrientation(i);
				this.queueForFilling(x + dir.offsetX, y + dir.offsetY, z + dir.offsetZ);
			}
	}

	public void queueForFilling(int x, int y, int z)
	{
		if (y < 0 || y > 255)
			return;
		BlockIndex index = new BlockIndex(x, y, z);
		if (this.visitedBlocks.add(index))
		{
			if ((x - this.xCoord) * (x - this.xCoord) + (z - this.zCoord) * (z - this.zCoord) > 64 * 64)
				return;

			Block block = BlockUtils.getBlock(this.worldObj, x, y, z);
			if (BlockUtils.getFluid(block) == this.tank.getFluidType())
				this.fluidsFound.add(index);
			if (this.canPlaceFluidAt(block, x, y, z))
				this.getLayerQueue(y).addLast(index);
		}
	}

	private boolean canPlaceFluidAt(Block block, int x, int y, int z)
	{
		return BuildCraftAPI.isSoftBlock(this.worldObj, x, y, z) && !BlockUtils.isFullFluidBlock(block, this.worldObj, x, y, z);
	}

	public void onNeighborBlockChange(Block block)
	{
		boolean p = this.worldObj.isBlockIndirectlyGettingPowered(this.xCoord, this.yCoord, this.zCoord);
		if (this.powered != p)
		{
			this.powered = p;
			if (!p)
				this.rebuildQueue();
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound data)
	{
		super.readFromNBT(data);
		this.tank.readFromNBT(data);
		this.rebuildDelay = data.getByte("rebuildDelay");
		this.powered = data.getBoolean("powered");
		for (int i = 0; i < 6; i++)
			this.blockedSides[i] = data.getBoolean("blocked[" + i + "]");
	}

	@Override
	public void writeToNBT(NBTTagCompound data)
	{
		super.writeToNBT(data);
		this.tank.writeToNBT(data);
		data.setByte("rebuildDelay", (byte) this.rebuildDelay);
		data.setBoolean("powered", this.powered);
		for (int i = 0; i < 6; i++)
			if (this.blockedSides[i])
				data.setBoolean("blocked[" + i + "]", true);
	}

	// TODO: fit in single byte
	@Override
	public void readData(ByteBuf stream)
	{
		for (int i = 0; i < 6; i++)
			this.blockedSides[i] = stream.readBoolean();
	}

	@Override
	public void writeData(ByteBuf stream)
	{
		for (int i = 0; i < 6; i++)
			stream.writeBoolean(this.blockedSides[i]);
	}

	public void switchSide(ForgeDirection side)
	{
		if (side.ordinal() != 1)
		{
			this.blockedSides[side.ordinal()] = !this.blockedSides[side.ordinal()];

			this.rebuildQueue();
			this.sendNetworkUpdate();
			this.worldObj.markBlockRangeForRenderUpdate(this.xCoord, this.yCoord, this.zCoord, this.xCoord, this.yCoord, this.zCoord);
		}
	}

	@Override
	public void invalidate()
	{
		super.invalidate();
		this.destroy();
	}

	@Override
	public void destroy()
	{
		this.pumpLayerQueues.clear();
	}

	// IFluidHandler implementation.
	@Override
	public int fill(ForgeDirection from, FluidStack resource, boolean doFill)
	{
		return this.tank.fill(resource, doFill);
	}

	@Override
	public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain)
	{
		return null;
	}

	@Override
	public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain)
	{
		return null;
	}

	@Override
	public boolean canFill(ForgeDirection from, Fluid fluid)
	{
		return true;
	}

	@Override
	public boolean canDrain(ForgeDirection from, Fluid fluid)
	{
		return false;
	}

	@Override
	public FluidTankInfo[] getTankInfo(ForgeDirection from)
	{
		return new FluidTankInfo[] { this.tank.getInfo() };
	}

	public boolean isSideBlocked(int side)
	{
		return this.blockedSides[side];
	}
}