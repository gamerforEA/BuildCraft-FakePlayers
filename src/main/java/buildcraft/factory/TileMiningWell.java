/**
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.factory;

import buildcraft.BuildCraftCore;
import buildcraft.BuildCraftFactory;
import buildcraft.api.blueprints.BuilderAPI;
import buildcraft.api.core.SafeTimeTracker;
import buildcraft.api.tiles.IControllable;
import buildcraft.api.tiles.IHasWork;
import buildcraft.api.transport.IPipeConnection;
import buildcraft.api.transport.IPipeTile;
import buildcraft.core.internal.ILEDProvider;
import buildcraft.core.lib.RFBattery;
import buildcraft.core.lib.block.TileBuildCraft;
import buildcraft.core.lib.utils.BlockMiner;
import buildcraft.core.lib.utils.BlockUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class TileMiningWell extends TileBuildCraft implements IHasWork, IPipeConnection, IControllable, ILEDProvider
{
	private boolean isDigging = true;
	private BlockMiner miner;
	private int ledState;
	private int ticksSinceAction = 9001;

	private SafeTimeTracker updateTracker = new SafeTimeTracker(BuildCraftCore.updateFactor);

	public TileMiningWell()
	{
		super();
		this.setBattery(new RFBattery(2 * 64 * BuilderAPI.BREAK_ENERGY, BuilderAPI.BREAK_ENERGY * 4 + BuilderAPI.BUILD_ENERGY, 0));
	}

	/**
	 * Dig the next available piece of land if not done. As soon as it reaches
	 * bedrock, lava or goes below 0, it's considered done.
	 */
	@Override
	public void updateEntity()
	{
		super.updateEntity();

		if (this.worldObj.isRemote)
			return;

		if (this.updateTracker.markTimeIfDelay(this.worldObj))
			this.sendNetworkUpdate();

		this.ticksSinceAction++;

		if (this.mode == Mode.Off)
		{
			if (this.miner != null)
			{
				this.miner.invalidate();
				this.miner = null;
			}
			this.isDigging = false;
			return;
		}

		if (this.getBattery().getEnergyStored() == 0)
			return;

		if (this.miner == null)
		{
			World world = this.worldObj;

			int depth = this.yCoord - 1;

			while (world.getBlock(this.xCoord, depth, this.zCoord) == BuildCraftFactory.plainPipeBlock)
				depth = depth - 1;

			if (depth < 1 || depth < this.yCoord - BuildCraftFactory.miningDepth || !BlockUtils.canChangeBlock(world, this.xCoord, depth, this.zCoord))
			{
				this.isDigging = false;
				// Drain energy, because at 0 energy this will stop doing calculations.
				this.getBattery().useEnergy(0, 10, false);
				return;
			}

			if (world.isAirBlock(this.xCoord, depth, this.zCoord) || world.getBlock(this.xCoord, depth, this.zCoord).isReplaceable(world, this.xCoord, depth, this.zCoord))
			{
				this.ticksSinceAction = 0;
				// TODO gamerforEA code start
				if (!this.fake.cantBreak(this.xCoord, depth, this.zCoord))
					// TODO gamerforEA code end
					world.setBlock(this.xCoord, depth, this.zCoord, BuildCraftFactory.plainPipeBlock);
			}
			else
				this.miner = new BlockMiner(world, this, this.xCoord, depth, this.zCoord);
		}

		if (this.miner != null)
		{
			this.isDigging = true;
			this.ticksSinceAction = 0;

			int usedEnergy = this.miner.acceptEnergy(this.getBattery().getEnergyStored());
			this.getBattery().useEnergy(usedEnergy, usedEnergy, false);

			if (this.miner.hasFailed())
				this.isDigging = false;

			if (this.miner.hasFailed() || this.miner.hasMined())
				this.miner = null;
		}
	}

	@Override
	public void invalidate()
	{
		super.invalidate();
		if (this.miner != null)
			this.miner.invalidate();
		if (this.worldObj != null && this.yCoord > 2)
			BuildCraftFactory.miningWellBlock.removePipes(this.worldObj, this.xCoord, this.yCoord, this.zCoord);
	}

	@Override
	public void writeData(ByteBuf stream)
	{
		super.writeData(stream);

		this.ledState = (this.ticksSinceAction < 2 ? 16 : 0) | this.getBattery().getEnergyStored() * 15 / this.getBattery().getMaxEnergyStored();
		stream.writeByte(this.ledState);
	}

	@Override
	public void readData(ByteBuf stream)
	{
		super.readData(stream);

		int newLedState = stream.readUnsignedByte();
		if (newLedState != this.ledState)
		{
			this.ledState = newLedState;
			this.worldObj.markBlockRangeForRenderUpdate(this.xCoord, this.yCoord, this.zCoord, this.xCoord, this.yCoord, this.zCoord);
		}
	}

	@Override
	public boolean hasWork()
	{
		return this.isDigging;
	}

	@Override
	public ConnectOverride overridePipeConnection(IPipeTile.PipeType type, ForgeDirection with)
	{
		return type == IPipeTile.PipeType.ITEM ? ConnectOverride.CONNECT : ConnectOverride.DEFAULT;
	}

	@Override
	public boolean acceptsControlMode(Mode mode)
	{
		return mode == Mode.Off || mode == Mode.On;
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
