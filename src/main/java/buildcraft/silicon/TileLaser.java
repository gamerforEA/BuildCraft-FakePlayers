/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.silicon;

import buildcraft.api.core.Position;
import buildcraft.api.core.SafeTimeTracker;
import buildcraft.api.power.ILaserTarget;
import buildcraft.api.power.ILaserTargetBlock;
import buildcraft.api.tiles.IControllable;
import buildcraft.api.tiles.IHasWork;
import buildcraft.core.Box;
import buildcraft.core.EntityLaser;
import buildcraft.core.LaserData;
import buildcraft.core.lib.RFBattery;
import buildcraft.core.lib.block.TileBuildCraft;
import buildcraft.core.lib.utils.BlockUtils;
import com.gamerforea.buildcraft.EventConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.LinkedList;
import java.util.List;

public class TileLaser extends TileBuildCraft implements IHasWork, IControllable
{
	private static final float LASER_OFFSET = 2.0F / 16.0F;
	private static final short POWER_AVERAGING = 100;

	public LaserData laser = new LaserData();

	private final SafeTimeTracker laserTickTracker = new SafeTimeTracker(10);
	private final SafeTimeTracker searchTracker = new SafeTimeTracker(100, 100);
	private final SafeTimeTracker networkTracker = new SafeTimeTracker(20, 3);
	private ILaserTarget laserTarget;
	private int powerIndex = 0;

	private short powerAverage = 0;
	private final short[] power = new short[POWER_AVERAGING];

	public TileLaser()
	{
		this.setBattery(new RFBattery(10000, 250, 0));
	}

	@Override
	public void initialize()
	{
		super.initialize();

		if (this.laser == null)
			this.laser = new LaserData();

		this.laser.isVisible = false;
		this.laser.head = new Position(this.xCoord, this.yCoord, this.zCoord);
		this.laser.tail = new Position(this.xCoord, this.yCoord, this.zCoord);
		this.laser.isGlowing = true;
	}

	@Override
	public void updateEntity()
	{
		super.updateEntity();

		this.laser.iterateTexture();

		if (this.worldObj.isRemote)
			return;

		// If a gate disabled us, remove laser and do nothing.
		if (this.mode == IControllable.Mode.Off)
		{
			this.removeLaser();
			return;
		}

		// Check for any available tables at a regular basis
		if (this.canFindTable())
			this.findTable();

		// If we still don't have a valid table or the existing has
		// become invalid, we disable the laser and do nothing.
		if (!this.isValidTable())
		{
			this.removeLaser();
			return;
		}

		// Disable the laser and do nothing if no energy is available.
		if (this.getBattery().getEnergyStored() == 0)
		{
			this.removeLaser();
			return;
		}

		// We have a laser
		if (this.laser != null)
		{
			// We have a table and can work, so we create a laser if
			// necessary.
			this.laser.isVisible = true;

			// We may update laser
			if (this.canUpdateLaser())
				this.updateLaser();
		}

		int energyToSent = this.getMaxPowerSent();

		// TODO gamerforEA code start
		if (EventConfig.maxRecievedLaserEnergyPerTick > 0 && this.laserTarget instanceof TileLaserTableBase)
			energyToSent = Math.min(energyToSent, Math.max(0, EventConfig.maxRecievedLaserEnergyPerTick - ((TileLaserTableBase) this.laserTarget).getRecievedLaserEnergyInLastTick()));
		if (energyToSent > 0)
		// TODO gamerforEA code end
		{
			// Consume power and transfer it to the table.
			int localPower = this.getBattery().useEnergy(0, energyToSent, false);
			this.laserTarget.receiveLaserEnergy(localPower);

			if (this.laser != null)
				this.pushPower(localPower);

			this.onPowerSent(localPower);
		}

		this.sendNetworkUpdate();
	}

	protected int getMaxPowerSent()
	{
		// TODO gamerforEA code replace, old code:
		// return 40;
		return EventConfig.maxLaserEnergyPerTick;
		// TODO gamerforEA code end
	}

	protected void onPowerSent(int power)
	{
	}

	protected boolean canFindTable()
	{
		return this.searchTracker.markTimeIfDelay(this.worldObj);
	}

	protected boolean canUpdateLaser()
	{
		return this.laserTickTracker.markTimeIfDelay(this.worldObj);
	}

	protected boolean isValidTable()
	{
		return this.laserTarget != null && !this.laserTarget.isInvalidTarget() && this.laserTarget.requiresLaserEnergy();
	}

	protected void findTable()
	{
		int meta = this.getBlockMetadata();

		int minX = this.xCoord - 5;
		int minY = this.yCoord - 5;
		int minZ = this.zCoord - 5;
		int maxX = this.xCoord + 5;
		int maxY = this.yCoord + 5;
		int maxZ = this.zCoord + 5;

		switch (ForgeDirection.getOrientation(meta))
		{
			case WEST:
				maxX = this.xCoord;
				break;
			case EAST:
				minX = this.xCoord;
				break;
			case DOWN:
				maxY = this.yCoord;
				break;
			case UP:
				minY = this.yCoord;
				break;
			case NORTH:
				maxZ = this.zCoord;
				break;
			default:
			case SOUTH:
				minZ = this.zCoord;
				break;
		}

		List<ILaserTarget> targets = new LinkedList<>();

		if (minY < 0)
			minY = 0;
		if (maxY > 255)
			maxY = 255;

		for (int y = minY; y <= maxY; ++y)
		{
			for (int x = minX; x <= maxX; ++x)
			{
				for (int z = minZ; z <= maxZ; ++z)
				{
					if (BlockUtils.getBlock(this.worldObj, x, y, z) instanceof ILaserTargetBlock)
					{
						TileEntity tile = BlockUtils.getTileEntity(this.worldObj, x, y, z);

						if (tile instanceof ILaserTarget)
						{
							ILaserTarget table = (ILaserTarget) tile;

							if (table.requiresLaserEnergy())
								targets.add(table);
						}
					}
				}
			}
		}

		if (targets.isEmpty())
			return;

		this.laserTarget = targets.get(this.worldObj.rand.nextInt(targets.size()));
	}

	protected void updateLaser()
	{

		int meta = this.getBlockMetadata();
		double px = 0, py = 0, pz = 0;

		switch (ForgeDirection.getOrientation(meta))
		{

			case WEST:
				px = -LASER_OFFSET;
				break;
			case EAST:
				px = LASER_OFFSET;
				break;
			case DOWN:
				py = -LASER_OFFSET;
				break;
			case UP:
				py = LASER_OFFSET;
				break;
			case NORTH:
				pz = -LASER_OFFSET;
				break;
			case SOUTH:
			default:
				pz = LASER_OFFSET;
				break;
		}

		Position head = new Position(this.xCoord + 0.5 + px, this.yCoord + 0.5 + py, this.zCoord + 0.5 + pz);
		Position tail = new Position(this.laserTarget.getXCoord() + 0.475 + (this.worldObj.rand.nextFloat() - 0.5) / 5F, this.laserTarget.getYCoord() + 9F / 16F, this.laserTarget.getZCoord() + 0.475 + (this.worldObj.rand.nextFloat() - 0.5) / 5F);

		this.laser.head = head;
		this.laser.tail = tail;

		if (!this.laser.isVisible)
			this.laser.isVisible = true;
	}

	protected void removeLaser()
	{
		if (this.powerAverage > 0)
			this.pushPower(0);
		if (this.laser.isVisible)
		{
			this.laser.isVisible = false;
			// force sending the network update even if the network tracker
			// refuses.
			super.sendNetworkUpdate();
		}
	}

	@Override
	public void sendNetworkUpdate()
	{
		if (this.networkTracker.markTimeIfDelay(this.worldObj))
			super.sendNetworkUpdate();
	}

	@Override
	public void readFromNBT(NBTTagCompound nbttagcompound)
	{
		super.readFromNBT(nbttagcompound);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbttagcompound)
	{
		super.writeToNBT(nbttagcompound);
	}

	@Override
	public void readData(ByteBuf stream)
	{
		this.laser = new LaserData();
		this.laser.readData(stream);
		this.powerAverage = stream.readShort();
	}

	@Override
	public void writeData(ByteBuf stream)
	{
		this.laser.writeData(stream);
		stream.writeShort(this.powerAverage);
	}

	@Override
	public void invalidate()
	{
		super.invalidate();
		this.removeLaser();
	}

	@Override
	public boolean hasWork()
	{
		return this.isValidTable();
	}

	private void pushPower(int received)
	{
		this.powerAverage -= this.power[this.powerIndex];
		this.powerAverage += received;
		this.power[this.powerIndex] = (short) received;
		this.powerIndex++;

		if (this.powerIndex == this.power.length)
			this.powerIndex = 0;
	}

	public ResourceLocation getTexture()
	{
		double avg = this.powerAverage / POWER_AVERAGING;

		if (avg <= 10.0)
			return EntityLaser.LASER_TEXTURES[0];
		else if (avg <= 20.0)
			return EntityLaser.LASER_TEXTURES[1];
		else if (avg <= 30.0)
			return EntityLaser.LASER_TEXTURES[2];
		else
			return EntityLaser.LASER_TEXTURES[3];
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox()
	{
		return new Box(this).extendToEncompass(this.laser.tail).getBoundingBox();
	}

	@Override
	public boolean acceptsControlMode(Mode mode)
	{
		return mode == IControllable.Mode.On || mode == IControllable.Mode.Off;
	}
}
