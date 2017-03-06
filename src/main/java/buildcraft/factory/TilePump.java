/**
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
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

import buildcraft.BuildCraftCore;
import buildcraft.BuildCraftFactory;
import buildcraft.api.core.BlockIndex;
import buildcraft.api.core.SafeTimeTracker;
import buildcraft.api.power.IRedstoneEngineReceiver;
import buildcraft.api.tiles.IHasWork;
import buildcraft.core.CoreConstants;
import buildcraft.core.internal.ILEDProvider;
import buildcraft.core.lib.EntityBlock;
import buildcraft.core.lib.RFBattery;
import buildcraft.core.lib.TileBuffer;
import buildcraft.core.lib.block.TileBuildCraft;
import buildcraft.core.lib.fluids.SingleUseTank;
import buildcraft.core.lib.fluids.TankUtils;
import buildcraft.core.lib.utils.BlockUtils;
import buildcraft.core.lib.utils.Utils;
import buildcraft.core.proxy.CoreProxy;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

public class TilePump extends TileBuildCraft implements IHasWork, IFluidHandler, IRedstoneEngineReceiver, ILEDProvider
{
	public static final int REBUID_DELAY = 512;
	public static int MAX_LIQUID = FluidContainerRegistry.BUCKET_VOLUME * 16;
	public SingleUseTank tank = new SingleUseTank("tank", MAX_LIQUID, this);

	private EntityBlock tube;
	private TreeMap<Integer, Deque<BlockIndex>> pumpLayerQueues = new TreeMap<Integer, Deque<BlockIndex>>();
	private double tubeY = Double.NaN;
	private int aimY = 0;

	private SafeTimeTracker timer = new SafeTimeTracker(REBUID_DELAY);
	private int tick = Utils.RANDOM.nextInt(32);
	private int tickPumped = this.tick - 20;
	private int numFluidBlocksFound = 0;
	private boolean powered = false;

	private int ledState;
	// tick % 16 => min. 16 ticks per network update
	private SafeTimeTracker updateTracker = new SafeTimeTracker(Math.max(16, BuildCraftCore.updateFactor));

	public TilePump()
	{
		super();
		this.setBattery(new RFBattery(1000, 150, 0));
	}

	@Override
	public void updateEntity()
	{
		super.updateEntity();

		if (this.powered)
		{
			this.pumpLayerQueues.clear();
			this.destroyTube();
		}
		else
			this.createTube();

		if (this.worldObj.isRemote)
			return;

		if (this.updateTracker.markTimeIfDelay(this.worldObj))
			this.sendNetworkUpdate();

		this.pushToConsumers();

		if (this.powered)
			return;

		if (this.tube == null)
			return;

		if (this.tube.posY - this.aimY > 0.01)
		{
			this.tubeY = this.tube.posY - 0.01;
			this.setTubePosition();
			this.sendNetworkUpdate();
			return;
		}

		this.tick++;

		if (this.tick % 16 != 0)
			return;

		BlockIndex index = this.getNextIndexToPump(false);

		FluidStack fluidToPump = index != null ? BlockUtils.drainBlock(this.worldObj, index.x, index.y, index.z, false) : null;
		if (fluidToPump != null)
		{
			if (this.isFluidAllowed(fluidToPump.getFluid()) && this.tank.fill(fluidToPump, false) == fluidToPump.amount)
				if (this.getBattery().useEnergy(100, 100, false) > 0)
				{
					// TODO gamerforEA code start
					if (this.fake.cantBreak(index.x, index.y, index.z))
						return;
					// TODO gamerforEA code end

					if (fluidToPump.getFluid() != FluidRegistry.WATER || BuildCraftCore.consumeWaterSources || this.numFluidBlocksFound < 9)
					{
						index = this.getNextIndexToPump(true);
						BlockUtils.drainBlock(this.worldObj, index.x, index.y, index.z, true);
					}

					this.tank.fill(fluidToPump, true);
					this.tickPumped = this.tick;
				}
		}
		else if (this.tick % 128 == 0)
		{
			// TODO: improve that decision
			this.rebuildQueue();

			if (this.getNextIndexToPump(false) == null)
				for (int y = this.yCoord - 1; y > 0; --y)
					if (this.isPumpableFluid(this.xCoord, y, this.zCoord))
					{
						this.aimY = y;
						return;
					}
					else if (this.isBlocked(this.xCoord, y, this.zCoord))
						return;
		}
	}

	public void onNeighborBlockChange(Block block)
	{
		boolean p = this.worldObj.isBlockIndirectlyGettingPowered(this.xCoord, this.yCoord, this.zCoord);

		if (this.powered != p)
		{
			this.powered = p;

			if (!this.worldObj.isRemote)
				this.sendNetworkUpdate();
		}
	}

	private boolean isBlocked(int x, int y, int z)
	{
		Material mat = BlockUtils.getBlock(this.worldObj, x, y, z).getMaterial();

		return mat.blocksMovement();
	}

	private void pushToConsumers()
	{
		if (this.cache == null)
			this.cache = TileBuffer.makeBuffer(this.worldObj, this.xCoord, this.yCoord, this.zCoord, false);

		TankUtils.pushFluidToConsumers(this.tank, 400, this.cache);
	}

	private void createTube()
	{
		if (this.tube == null)
		{
			this.tube = FactoryProxy.proxy.newPumpTube(this.worldObj);

			if (!Double.isNaN(this.tubeY))
				this.tube.posY = this.tubeY;
			else
				this.tube.posY = this.yCoord;

			this.tubeY = this.tube.posY;

			if (this.aimY == 0)
				this.aimY = this.yCoord;

			this.setTubePosition();

			this.worldObj.spawnEntityInWorld(this.tube);

			if (!this.worldObj.isRemote)
				this.sendNetworkUpdate();
		}
	}

	private void destroyTube()
	{
		if (this.tube != null)
		{
			CoreProxy.proxy.removeEntity(this.tube);
			this.tube = null;
			this.tubeY = Double.NaN;
			this.aimY = 0;
		}
	}

	private BlockIndex getNextIndexToPump(boolean remove)
	{
		if (this.pumpLayerQueues.isEmpty())
		{
			if (this.timer.markTimeIfDelay(this.worldObj))
				this.rebuildQueue();

			return null;
		}

		Deque<BlockIndex> topLayer = this.pumpLayerQueues.lastEntry().getValue();

		if (topLayer != null)
		{
			if (topLayer.isEmpty())
				this.pumpLayerQueues.pollLastEntry();

			if (remove)
			{
				BlockIndex index = topLayer.pollLast();
				return index;
			}
			else
				return topLayer.peekLast();
		}
		else
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

	public void rebuildQueue()
	{
		this.numFluidBlocksFound = 0;
		this.pumpLayerQueues.clear();
		int x = this.xCoord;
		int y = this.aimY;
		int z = this.zCoord;
		Fluid pumpingFluid = BlockUtils.getFluid(BlockUtils.getBlock(this.worldObj, x, y, z));

		if (pumpingFluid == null)
			return;

		if (pumpingFluid != this.tank.getAcceptedFluid() && this.tank.getAcceptedFluid() != null)
			return;

		Set<BlockIndex> visitedBlocks = new HashSet<BlockIndex>();
		Deque<BlockIndex> fluidsFound = new LinkedList<BlockIndex>();

		this.queueForPumping(x, y, z, visitedBlocks, fluidsFound, pumpingFluid);

		//		long timeoutTime = System.nanoTime() + 10000;

		while (!fluidsFound.isEmpty())
		{
			Deque<BlockIndex> fluidsToExpand = fluidsFound;
			fluidsFound = new LinkedList<BlockIndex>();

			for (BlockIndex index : fluidsToExpand)
			{
				this.queueForPumping(index.x, index.y + 1, index.z, visitedBlocks, fluidsFound, pumpingFluid);
				this.queueForPumping(index.x + 1, index.y, index.z, visitedBlocks, fluidsFound, pumpingFluid);
				this.queueForPumping(index.x - 1, index.y, index.z, visitedBlocks, fluidsFound, pumpingFluid);
				this.queueForPumping(index.x, index.y, index.z + 1, visitedBlocks, fluidsFound, pumpingFluid);
				this.queueForPumping(index.x, index.y, index.z - 1, visitedBlocks, fluidsFound, pumpingFluid);

				if (pumpingFluid == FluidRegistry.WATER && !BuildCraftCore.consumeWaterSources && this.numFluidBlocksFound >= 9)
					return;

				//				if (System.nanoTime() > timeoutTime)
				//					return;
			}
		}
	}

	public void queueForPumping(int x, int y, int z, Set<BlockIndex> visitedBlocks, Deque<BlockIndex> fluidsFound, Fluid pumpingFluid)
	{
		BlockIndex index = new BlockIndex(x, y, z);
		if (visitedBlocks.add(index))
		{
			if ((x - this.xCoord) * (x - this.xCoord) + (z - this.zCoord) * (z - this.zCoord) > 64 * 64)
				return;

			Block block = BlockUtils.getBlock(this.worldObj, x, y, z);

			if (BlockUtils.getFluid(block) == pumpingFluid)
				fluidsFound.add(index);

			if (this.canDrainBlock(block, x, y, z, pumpingFluid))
			{
				this.getLayerQueue(y).add(index);
				this.numFluidBlocksFound++;
			}
		}
	}

	private boolean isPumpableFluid(int x, int y, int z)
	{
		Fluid fluid = BlockUtils.getFluid(BlockUtils.getBlock(this.worldObj, x, y, z));

		if (fluid == null)
			return false;
		else if (!this.isFluidAllowed(fluid))
			return false;
		else
			return !(this.tank.getAcceptedFluid() != null && this.tank.getAcceptedFluid() != fluid);
	}

	private boolean canDrainBlock(Block block, int x, int y, int z, Fluid fluid)
	{
		if (!this.isFluidAllowed(fluid))
			return false;

		FluidStack fluidStack = BlockUtils.drainBlock(block, this.worldObj, x, y, z, false);

		if (fluidStack == null || fluidStack.amount <= 0)
			return false;
		else
			return fluidStack.getFluid() == fluid;
	}

	private boolean isFluidAllowed(Fluid fluid)
	{
		return BuildCraftFactory.pumpDimensionList.isFluidAllowed(fluid, this.worldObj.provider.dimensionId);
	}

	@Override
	public void readFromNBT(NBTTagCompound data)
	{
		super.readFromNBT(data);

		this.tank.readFromNBT(data);

		this.powered = data.getBoolean("powered");

		this.aimY = data.getInteger("aimY");
		this.tubeY = data.getFloat("tubeY");
	}

	@Override
	public void writeToNBT(NBTTagCompound data)
	{
		super.writeToNBT(data);

		this.tank.writeToNBT(data);

		data.setBoolean("powered", this.powered);

		data.setInteger("aimY", this.aimY);

		if (this.tube != null)
			data.setFloat("tubeY", (float) this.tube.posY);
		else
			data.setFloat("tubeY", this.yCoord);
	}

	@Override
	public boolean hasWork()
	{
		BlockIndex next = this.getNextIndexToPump(false);

		if (next != null)
			return this.isPumpableFluid(next.x, next.y, next.z);
		else
			return false;
	}

	@Override
	public void writeData(ByteBuf buf)
	{
		buf.writeShort(this.aimY);
		buf.writeFloat((float) this.tubeY);
		buf.writeBoolean(this.powered);
		this.ledState = (this.tick - this.tickPumped < 48 ? 16 : 0) | this.getBattery().getEnergyStored() * 15 / this.getBattery().getMaxEnergyStored();
		buf.writeByte(this.ledState);
	}

	@Override
	public void readData(ByteBuf data)
	{
		this.aimY = data.readShort();
		this.tubeY = data.readFloat();
		this.powered = data.readBoolean();

		int newLedState = data.readUnsignedByte();
		if (newLedState != this.ledState)
		{
			this.ledState = newLedState;
			this.worldObj.markBlockRangeForRenderUpdate(this.xCoord, this.yCoord, this.zCoord, this.xCoord, this.yCoord, this.zCoord);
		}

		this.setTubePosition();
	}

	private void setTubePosition()
	{
		if (this.tube != null)
		{
			this.tube.iSize = CoreConstants.PIPE_MAX_POS - CoreConstants.PIPE_MIN_POS;
			this.tube.kSize = CoreConstants.PIPE_MAX_POS - CoreConstants.PIPE_MIN_POS;
			this.tube.jSize = this.yCoord - this.tube.posY;

			this.tube.setPosition(this.xCoord + CoreConstants.PIPE_MIN_POS, this.tubeY, this.zCoord + CoreConstants.PIPE_MIN_POS);
		}
	}

	@Override
	public void invalidate()
	{
		super.invalidate();
		this.destroy();
	}

	@Override
	public void onChunkUnload()
	{
		super.onChunkUnload();

		if (this.tube != null)
		{
			// Remove the entity to stop it from piling up.
			CoreProxy.proxy.removeEntity(this.tube);
			this.tube = null;
		}
	}

	@Override
	public void validate()
	{
		super.validate();
	}

	@Override
	public void destroy()
	{
		this.pumpLayerQueues.clear();
		this.destroyTube();
	}

	// IFluidHandler implementation.
	@Override
	public int fill(ForgeDirection from, FluidStack resource, boolean doFill)
	{
		// not acceptable
		return 0;
	}

	@Override
	public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain)
	{
		return this.tank.drain(maxDrain, doDrain);
	}

	@Override
	public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain)
	{
		if (resource == null)
			return null;
		else if (!resource.isFluidEqual(this.tank.getFluid()))
			return null;
		else
			return this.drain(from, resource.amount, doDrain);
	}

	@Override
	public boolean canFill(ForgeDirection from, Fluid fluid)
	{
		return false;
	}

	@Override
	public boolean canDrain(ForgeDirection from, Fluid fluid)
	{
		return true;
	}

	@Override
	public FluidTankInfo[] getTankInfo(ForgeDirection from)
	{
		return new FluidTankInfo[] { this.tank.getInfo() };
	}

	@Override
	public boolean canConnectRedstoneEngine(ForgeDirection side)
	{
		return !BuildCraftFactory.pumpsNeedRealPower;
	}

	@Override
	public int getLEDLevel(int led)
	{
		if (led == 0)
			return this.ledState & 15;
		else
			return this.ledState >> 4 > 0 ? 15 : 0;
	}
}
