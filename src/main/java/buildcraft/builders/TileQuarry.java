/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.builders;

import buildcraft.BuildCraftBuilders;
import buildcraft.BuildCraftCore;
import buildcraft.api.blueprints.BuilderAPI;
import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.core.IAreaProvider;
import buildcraft.api.core.SafeTimeTracker;
import buildcraft.api.filler.FillerManager;
import buildcraft.api.statements.IStatementParameter;
import buildcraft.api.tiles.IControllable;
import buildcraft.api.tiles.IHasWork;
import buildcraft.api.transport.IPipeConnection;
import buildcraft.api.transport.IPipeTile;
import buildcraft.core.Box;
import buildcraft.core.Box.Kind;
import buildcraft.core.CoreConstants;
import buildcraft.core.DefaultAreaProvider;
import buildcraft.core.DefaultProps;
import buildcraft.core.blueprints.Blueprint;
import buildcraft.core.blueprints.BptBuilderBase;
import buildcraft.core.blueprints.BptBuilderBlueprint;
import buildcraft.core.builders.TileAbstractBuilder;
import buildcraft.core.builders.patterns.FillerPattern;
import buildcraft.core.internal.IDropControlInventory;
import buildcraft.core.internal.ILEDProvider;
import buildcraft.core.lib.RFBattery;
import buildcraft.core.lib.utils.BlockMiner;
import buildcraft.core.lib.utils.BlockUtils;
import buildcraft.core.lib.utils.Utils;
import buildcraft.core.proxy.CoreProxy;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.IFluidBlock;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class TileQuarry extends TileAbstractBuilder
		implements IHasWork, ISidedInventory, IDropControlInventory, IPipeConnection, IControllable, ILEDProvider
{
	private enum Stage
	{
		BUILDING,
		DIGGING,
		MOVING,
		IDLE,
		DONE
	}

	public EntityMechanicalArm arm;
	public EntityPlayer placedBy;

	protected Box box = new Box();
	private int targetX, targetY, targetZ;
	private double headPosX, headPosY, headPosZ;
	private double speed = 0.03;
	private Stage stage = Stage.BUILDING;
	private boolean movingHorizontally;
	private boolean movingVertically;
	private float headTrajectory;

	private SafeTimeTracker updateTracker = new SafeTimeTracker(BuildCraftCore.updateFactor);

	private BptBuilderBase builder;

	private final LinkedList<int[]> visitList = Lists.newLinkedList();

	private boolean loadDefaultBoundaries = false;
	private Ticket chunkTicket;

	private boolean frameProducer = true;

	private NBTTagCompound initNBT = null;

	private BlockMiner miner;
	private int ledState;

	public TileQuarry()
	{
		this.box.kind = Kind.STRIPES;
		this.setBattery(new RFBattery((int) (2 * 64 * BuilderAPI.BREAK_ENERGY * BuildCraftCore.miningMultiplier), (int) (1000 * BuildCraftCore.miningMultiplier), 0));
	}

	public void createUtilsIfNeeded()
	{
		if (!this.worldObj.isRemote)
			if (this.builder == null)
			{
				if (!this.box.isInitialized())
					this.setBoundaries(this.loadDefaultBoundaries);

				this.initializeBlueprintBuilder();
			}

		if (this.stage != Stage.BUILDING)
		{
			this.box.isVisible = false;

			if (this.arm == null)
				this.createArm();

			if (this.findTarget(false))
				if (this.headPosX < this.box.xMin || this.headPosX > this.box.xMax || this.headPosZ < this.box.zMin || this.headPosZ > this.box.zMax)
					this.setHead(this.box.xMin + 1, this.yCoord + 2, this.box.zMin + 1);
		}
		else
			this.box.isVisible = true;
	}

	private void createArm()
	{
		this.worldObj.spawnEntityInWorld(new EntityMechanicalArm(this.worldObj, this.box.xMin + CoreConstants.PIPE_MAX_POS, this.yCoord + this.box.sizeY() - 1 + CoreConstants.PIPE_MIN_POS, this.box.zMin + CoreConstants.PIPE_MAX_POS, this.box.sizeX() - 2 + CoreConstants.PIPE_MIN_POS * 2, this.box.sizeZ() - 2 + CoreConstants.PIPE_MIN_POS * 2, this));
	}

	// Callback from the arm once it's created
	public void setArm(EntityMechanicalArm arm)
	{
		this.arm = arm;
	}

	public boolean areChunksLoaded()
	{
		if (BuildCraftBuilders.quarryLoadsChunks)
			// Small optimization
			return true;

		// Each chunk covers the full height, so we only check one of them per height.
		return this.worldObj.blockExists(this.box.xMin, this.box.yMax, this.box.zMin) && this.worldObj.blockExists(this.box.xMax, this.box.yMax, this.box.zMin) && this.worldObj.blockExists(this.box.xMin, this.box.yMax, this.box.zMax) && this.worldObj.blockExists(this.box.xMax, this.box.yMax, this.box.zMax);
	}

	@Override
	public void updateEntity()
	{
		super.updateEntity();

		if (this.worldObj.isRemote)
		{
			if (this.stage != Stage.DONE)
				this.moveHead(this.speed);

			return;
		}

		if (this.stage == Stage.DONE)
			if (this.mode == Mode.Loop)
				this.stage = Stage.IDLE;
			else
				return;

		if (!this.areChunksLoaded())
			return;

		if (this.mode == Mode.Off && this.stage != Stage.MOVING)
			return;

		this.createUtilsIfNeeded();

		if (this.stage == Stage.BUILDING)
		{
			if (this.builder != null && !this.builder.isDone(this))
				this.builder.buildNextSlot(this.worldObj, this, this.xCoord, this.yCoord, this.zCoord);
			else
				this.stage = Stage.IDLE;
		}
		else if (this.stage == Stage.DIGGING)
			this.dig();
		else if (this.stage == Stage.IDLE)
		{
			this.idling();

			// We are sending a network packet update ONLY below.
			// In this case, since idling() does it anyway, we should return.
			return;
		}
		else if (this.stage == Stage.MOVING)
		{
			int energyUsed = this.getBattery().useEnergy(20, (int) Math.ceil(20D + (double) this.getBattery().getEnergyStored() / 10), false);

			if (energyUsed >= 20)
			{

				this.speed = 0.1 + energyUsed / 2000F;

				// If it's raining or snowing above the head, slow down.
				if (this.worldObj.isRaining())
				{
					int headBPX = (int) this.headPosX;
					int headBPY = (int) this.headPosY;
					int headBPZ = (int) this.headPosZ;
					if (this.worldObj.getHeightValue(headBPX, headBPZ) < headBPY)
						this.speed *= 0.7;
				}

				this.moveHead(this.speed);
			}
			else
				this.speed = 0;
		}

		if (this.updateTracker.markTimeIfDelay(this.worldObj))
			this.sendNetworkUpdate();
	}

	protected void dig()
	{
		if (this.worldObj.isRemote)
			return;

		if (this.miner == null)
		{
			// Hmm. Probably shouldn't be mining if there's no miner.
			this.stage = Stage.IDLE;
			return;
		}

		int rfTaken = this.miner.acceptEnergy(this.getBattery().getEnergyStored());
		this.getBattery().useEnergy(rfTaken, rfTaken, false);

		if (this.miner.hasMined())
		{
			// Collect any lost items laying around.
			double[] head = this.getHead();
			AxisAlignedBB axis = AxisAlignedBB.getBoundingBox(head[0] - 2, head[1] - 2, head[2] - 2, head[0] + 3, head[1] + 3, head[2] + 3);
			List<EntityItem> result = this.worldObj.getEntitiesWithinAABB(EntityItem.class, axis);
			for (EntityItem entity : result)
			{
				if (entity.isDead)
					continue;

				ItemStack mineable = entity.getEntityItem();
				if (mineable.stackSize <= 0)
					continue;
				CoreProxy.proxy.removeEntity(entity);
				this.miner.mineStack(mineable);
			}
		}

		if (this.miner.hasMined() || this.miner.hasFailed())
		{
			this.miner = null;

			if (!this.findFrame())
			{
				this.initializeBlueprintBuilder();
				this.stage = Stage.BUILDING;
			}
			else
				this.stage = Stage.IDLE;
		}
	}

	protected boolean findFrame()
	{
		for (int i = 2; i < 6; i++)
		{
			ForgeDirection o = ForgeDirection.getOrientation(i);
			if (this.box.contains(this.xCoord + o.offsetX, this.yCoord + o.offsetY, this.zCoord + o.offsetZ))
				return this.worldObj.getBlock(this.xCoord + o.offsetX, this.yCoord + o.offsetY, this.zCoord + o.offsetZ) == BuildCraftBuilders.frameBlock;
		}

		// Could not find any location in box - this is strange, so obviously
		// we're going to ignore it!
		return true;
	}

	protected void idling()
	{
		if (!this.findTarget(true))
		{
			// I believe the issue is box going null becuase of bad chunkloader positioning
			if (this.arm != null && this.box != null)
				this.setTarget(this.box.xMin + 1, this.yCoord + 2, this.box.zMin + 1);

			this.stage = Stage.DONE;
		}
		else
			this.stage = Stage.MOVING;

		this.movingHorizontally = true;
		this.movingVertically = true;
		double[] head = this.getHead();
		int[] target = this.getTarget();
		this.headTrajectory = (float) Math.atan2(target[2] - head[2], target[0] - head[0]);
		this.sendNetworkUpdate();
	}

	public boolean findTarget(boolean doSet)
	{
		if (this.worldObj.isRemote)
			return false;

		boolean columnVisitListIsUpdated = false;

		if (this.visitList.isEmpty())
		{
			this.createColumnVisitList();
			columnVisitListIsUpdated = true;
		}

		if (!doSet)
			return !this.visitList.isEmpty();

		if (this.visitList.isEmpty())
			return false;

		int[] nextTarget = this.visitList.removeFirst();

		if (!columnVisitListIsUpdated)
			for (int y = nextTarget[1] + 1; y < this.yCoord + 3; y++)
			{
				if (this.isQuarriableBlock(nextTarget[0], y, nextTarget[2]))
				{
					this.createColumnVisitList();
					columnVisitListIsUpdated = true;
					nextTarget = null;
					break;
				}
			}

		if (columnVisitListIsUpdated && nextTarget == null && !this.visitList.isEmpty())
			nextTarget = this.visitList.removeFirst();
		else if (columnVisitListIsUpdated && nextTarget == null)
			return false;

		this.setTarget(nextTarget[0], nextTarget[1] + 1, nextTarget[2]);

		return true;
	}

	/**
	 * Make the column visit list: called once per layer
	 */
	private void createColumnVisitList()
	{
		this.visitList.clear();
		boolean[][] blockedColumns = new boolean[this.builder.blueprint.sizeX - 2][this.builder.blueprint.sizeZ - 2];

		for (int searchY = this.yCoord + 3; searchY >= 1; --searchY)
		{
			int startX, endX, incX;

			if (searchY % 2 == 0)
			{
				startX = 0;
				endX = this.builder.blueprint.sizeX - 2;
				incX = 1;
			}
			else
			{
				startX = this.builder.blueprint.sizeX - 3;
				endX = -1;
				incX = -1;
			}

			for (int searchX = startX; searchX != endX; searchX += incX)
			{
				int startZ, endZ, incZ;

				if (searchX % 2 == searchY % 2)
				{
					startZ = 0;
					endZ = this.builder.blueprint.sizeZ - 2;
					incZ = 1;
				}
				else
				{
					startZ = this.builder.blueprint.sizeZ - 3;
					endZ = -1;
					incZ = -1;
				}

				for (int searchZ = startZ; searchZ != endZ; searchZ += incZ)
				{
					if (!blockedColumns[searchX][searchZ])
					{
						int bx = this.box.xMin + searchX + 1, by = searchY, bz = this.box.zMin + searchZ + 1;

						Block block = this.worldObj.getBlock(bx, by, bz);

						if (!BlockUtils.canChangeBlock(block, this.worldObj, bx, by, bz))
							blockedColumns[searchX][searchZ] = true;
						else if (!BuildCraftAPI.isSoftBlock(this.worldObj, bx, by, bz) && !(block instanceof BlockLiquid) && !(block instanceof IFluidBlock))
							this.visitList.add(new int[] { bx, by, bz });

						// Stop at two planes - generally any obstructions will have been found and will force a recompute prior to this

						if (this.visitList.size() > this.builder.blueprint.sizeZ * this.builder.blueprint.sizeX * 2)
							return;
					}
				}
			}
		}
	}

	@Override
	public void readFromNBT(NBTTagCompound nbttagcompound)
	{
		super.readFromNBT(nbttagcompound);

		if (nbttagcompound.hasKey("box"))
		{
			this.box.initialize(nbttagcompound.getCompoundTag("box"));

			this.loadDefaultBoundaries = false;
		}
		else if (nbttagcompound.hasKey("xSize"))
		{
			// This is a legacy save, get old data

			int xMin = nbttagcompound.getInteger("xMin");
			int zMin = nbttagcompound.getInteger("zMin");

			int xSize = nbttagcompound.getInteger("xSize");
			int ySize = nbttagcompound.getInteger("ySize");
			int zSize = nbttagcompound.getInteger("zSize");

			this.box.initialize(xMin, this.yCoord, zMin, xMin + xSize - 1, this.yCoord + ySize - 1, zMin + zSize - 1);

			this.loadDefaultBoundaries = false;
		}
		else
			this.loadDefaultBoundaries = true;

		this.targetX = nbttagcompound.getInteger("targetX");
		this.targetY = nbttagcompound.getInteger("targetY");
		this.targetZ = nbttagcompound.getInteger("targetZ");
		this.headPosX = nbttagcompound.getDouble("headPosX");
		this.headPosY = nbttagcompound.getDouble("headPosY");
		this.headPosZ = nbttagcompound.getDouble("headPosZ");

		// The rest of load has to be done upon initialize.
		this.initNBT = (NBTTagCompound) nbttagcompound.getCompoundTag("bpt").copy();
	}

	@Override
	public void writeToNBT(NBTTagCompound nbttagcompound)
	{
		super.writeToNBT(nbttagcompound);

		nbttagcompound.setInteger("targetX", this.targetX);
		nbttagcompound.setInteger("targetY", this.targetY);
		nbttagcompound.setInteger("targetZ", this.targetZ);
		nbttagcompound.setDouble("headPosX", this.headPosX);
		nbttagcompound.setDouble("headPosY", this.headPosY);
		nbttagcompound.setDouble("headPosZ", this.headPosZ);

		NBTTagCompound boxTag = new NBTTagCompound();
		this.box.writeToNBT(boxTag);
		nbttagcompound.setTag("box", boxTag);

		NBTTagCompound bptNBT = new NBTTagCompound();

		if (this.builder != null)
		{
			NBTTagCompound builderCpt = new NBTTagCompound();
			this.builder.saveBuildStateToNBT(builderCpt, this);
			bptNBT.setTag("builderState", builderCpt);
		}

		nbttagcompound.setTag("bpt", bptNBT);
	}

	@SuppressWarnings("rawtypes")
	public void positionReached()
	{
		if (this.worldObj.isRemote)
			return;

		if (this.isQuarriableBlock(this.targetX, this.targetY - 1, this.targetZ))
		{
			this.miner = new BlockMiner(this.worldObj, this, this.targetX, this.targetY - 1, this.targetZ);
			this.stage = Stage.DIGGING;
		}
		else
			this.stage = Stage.IDLE;
	}

	private boolean isQuarriableBlock(int bx, int by, int bz)
	{
		Block block = this.worldObj.getBlock(bx, by, bz);
		return BlockUtils.canChangeBlock(block, this.worldObj, bx, by, bz) && !BuildCraftAPI.isSoftBlock(this.worldObj, bx, by, bz) && !(block instanceof BlockLiquid) && !(block instanceof IFluidBlock);
	}

	@Override
	protected int getNetworkUpdateRange()
	{
		return DefaultProps.NETWORK_UPDATE_RANGE + (int) Math.ceil(Math.sqrt(this.yCoord * this.yCoord + this.box.sizeX() * this.box.sizeX() + this.box.sizeZ() * this.box.sizeZ()));
	}

	@Override
	public void invalidate()
	{
		if (this.chunkTicket != null)
			ForgeChunkManager.releaseTicket(this.chunkTicket);

		super.invalidate();
		this.destroy();
	}

	@Override
	public void onChunkUnload()
	{
		this.destroy();
	}

	@Override
	public void destroy()
	{
		if (this.arm != null)
			this.arm.setDead();

		this.arm = null;

		this.frameProducer = false;

		if (this.miner != null)
			this.miner.invalidate();
	}

	@Override
	public boolean hasWork()
	{
		return this.stage != Stage.DONE;
	}

	private void setBoundaries(boolean useDefaultI)
	{
		boolean useDefault = useDefaultI;

		if (BuildCraftBuilders.quarryLoadsChunks && this.chunkTicket == null)
			this.chunkTicket = ForgeChunkManager.requestTicket(BuildCraftBuilders.instance, this.worldObj, Type.NORMAL);

		if (this.chunkTicket != null)
		{
			this.chunkTicket.getModData().setInteger("quarryX", this.xCoord);
			this.chunkTicket.getModData().setInteger("quarryY", this.yCoord);
			this.chunkTicket.getModData().setInteger("quarryZ", this.zCoord);
		}

		IAreaProvider a = null;

		if (!useDefault)
			a = Utils.getNearbyAreaProvider(this.worldObj, this.xCoord, this.yCoord, this.zCoord);

		if (a == null)
		{
			a = new DefaultAreaProvider(this.xCoord, this.yCoord, this.zCoord, this.xCoord + 10, this.yCoord + 4, this.zCoord + 10);

			useDefault = true;
		}

		int xSize = a.xMax() - a.xMin() + 1;
		int zSize = a.zMax() - a.zMin() + 1;

		if (xSize < 3 || zSize < 3 || this.chunkTicket != null && xSize * zSize >> 8 >= this.chunkTicket.getMaxChunkListDepth())
		{
			if (this.placedBy != null)
				// TODO gamerforEA code start
				if (this.placedBy instanceof EntityPlayerMP && ((EntityPlayerMP) this.placedBy).playerNetServerHandler == null)
					this.placedBy = null;
				else
					// TODO gamerforEA code end
					this.placedBy.addChatMessage(new ChatComponentTranslation("chat.buildcraft.quarry.tooSmall", xSize, zSize, this.chunkTicket != null ? this.chunkTicket.getMaxChunkListDepth() : 0));

			a = new DefaultAreaProvider(this.xCoord, this.yCoord, this.zCoord, this.xCoord + 10, this.yCoord + 4, this.zCoord + 10);
			useDefault = true;
		}

		xSize = a.xMax() - a.xMin() + 1;
		int ySize = a.yMax() - a.yMin() + 1;
		zSize = a.zMax() - a.zMin() + 1;

		this.box.initialize(a);

		if (ySize < 5)
		{
			ySize = 5;
			this.box.yMax = this.box.yMin + ySize - 1;
		}

		if (useDefault)
		{
			int xMin, zMin;

			int dir = this.worldObj.getBlockMetadata(this.xCoord, this.yCoord, this.zCoord);
			ForgeDirection o = ForgeDirection.getOrientation(dir > 6 ? 6 : dir).getOpposite();

			switch (o)
			{
				case EAST:
					xMin = this.xCoord + 1;
					zMin = this.zCoord - 4 - 1;
					break;
				case WEST:
					xMin = this.xCoord - 9 - 2;
					zMin = this.zCoord - 4 - 1;
					break;
				case SOUTH:
					xMin = this.xCoord - 4 - 1;
					zMin = this.zCoord + 1;
					break;
				case NORTH:
				default:
					xMin = this.xCoord - 4 - 1;
					zMin = this.zCoord - 9 - 2;
					break;
			}

			this.box.initialize(xMin, this.yCoord, zMin, xMin + xSize - 1, this.yCoord + ySize - 1, zMin + zSize - 1);
		}

		a.removeFromWorld();
		if (this.chunkTicket != null)
			this.forceChunkLoading(this.chunkTicket);

		this.sendNetworkUpdate();
	}

	private void initializeBlueprintBuilder()
	{
		Blueprint bpt = ((FillerPattern) FillerManager.registry.getPattern("buildcraft:frame")).getBlueprint(this.box, this.worldObj, new IStatementParameter[0], BuildCraftBuilders.frameBlock, 0);

		if (bpt != null)
		{
			this.builder = new BptBuilderBlueprint(bpt, this.worldObj, this.box.xMin, this.yCoord, this.box.zMin);
			this.speed = 0;
			this.stage = Stage.BUILDING;
			this.sendNetworkUpdate();
		}
	}

	@Override
	public void writeData(ByteBuf stream)
	{
		super.writeData(stream);
		this.box.writeData(stream);
		stream.writeInt(this.targetX);
		stream.writeShort(this.targetY);
		stream.writeInt(this.targetZ);
		stream.writeDouble(this.headPosX);
		stream.writeDouble(this.headPosY);
		stream.writeDouble(this.headPosZ);
		stream.writeFloat((float) this.speed);
		stream.writeFloat(this.headTrajectory);
		int flags = this.stage.ordinal();
		flags |= this.movingHorizontally ? 0x10 : 0;
		flags |= this.movingVertically ? 0x20 : 0;
		stream.writeByte(flags);

		this.ledState = (this.hasWork() && this.mode != Mode.Off && this.getTicksSinceEnergyReceived() < 12 ? 16 : 0) | this.getBattery().getEnergyStored() * 15 / this.getBattery().getMaxEnergyStored();
		stream.writeByte(this.ledState);
	}

	@Override
	public void readData(ByteBuf stream)
	{
		super.readData(stream);
		this.box.readData(stream);
		this.targetX = stream.readInt();
		this.targetY = stream.readUnsignedShort();
		this.targetZ = stream.readInt();
		this.headPosX = stream.readDouble();
		this.headPosY = stream.readDouble();
		this.headPosZ = stream.readDouble();
		this.speed = stream.readFloat();
		this.headTrajectory = stream.readFloat();
		int flags = stream.readUnsignedByte();
		this.stage = Stage.values()[flags & 0x07];
		this.movingHorizontally = (flags & 0x10) != 0;
		this.movingVertically = (flags & 0x20) != 0;
		int newLedState = stream.readUnsignedByte();
		if (newLedState != this.ledState)
		{
			this.ledState = newLedState;
			this.worldObj.markBlockRangeForRenderUpdate(this.xCoord, this.yCoord, this.zCoord, this.xCoord, this.yCoord, this.zCoord);
		}

		this.createUtilsIfNeeded();

		if (this.arm != null)
		{
			this.arm.setHead(this.headPosX, this.headPosY, this.headPosZ);
			this.arm.updatePosition();
		}
	}

	@Override
	public void initialize()
	{
		super.initialize();

		if (!this.getWorldObj().isRemote && !this.box.initialized)
			this.setBoundaries(false);

		this.createUtilsIfNeeded();

		if (this.initNBT != null && this.builder != null)
			this.builder.loadBuildStateToNBT(this.initNBT.getCompoundTag("builderState"), this);

		this.initNBT = null;

		this.sendNetworkUpdate();
	}

	public void reinitalize()
	{
		this.initializeBlueprintBuilder();
	}

	@Override
	public int getSizeInventory()
	{
		return 1;
	}

	@Override
	public ItemStack getStackInSlot(int i)
	{
		if (this.frameProducer)
			return new ItemStack(BuildCraftBuilders.frameBlock);
		else
			return null;
	}

	@Override
	public ItemStack decrStackSize(int i, int j)
	{
		if (this.frameProducer)
			return new ItemStack(BuildCraftBuilders.frameBlock, j);
		else
			return null;
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack itemstack)
	{
	}

	@Override
	public ItemStack getStackInSlotOnClosing(int slot)
	{
		return null;
	}

	@Override
	public String getInventoryName()
	{
		return "";
	}

	@Override
	public int getInventoryStackLimit()
	{
		return 0;
	}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemstack)
	{
		return false;
	}

	@Override
	public boolean isUseableByPlayer(EntityPlayer entityplayer)
	{
		return false;
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
	public boolean isBuildingMaterialSlot(int i)
	{
		return true;
	}

	public void moveHead(double instantSpeed)
	{
		int[] target = this.getTarget();
		double[] head = this.getHead();

		if (this.movingHorizontally)
		{
			if (Math.abs(target[0] - head[0]) < instantSpeed * 2 && Math.abs(target[2] - head[2]) < instantSpeed * 2)
			{
				head[0] = target[0];
				head[2] = target[2];

				this.movingHorizontally = false;

				if (!this.movingVertically)
				{
					this.positionReached();
					head[1] = target[1];
				}
			}
			else
			{
				head[0] += MathHelper.cos(this.headTrajectory) * instantSpeed;
				head[2] += MathHelper.sin(this.headTrajectory) * instantSpeed;
			}
			this.setHead(head[0], head[1], head[2]);
		}

		if (this.movingVertically)
		{
			if (Math.abs(target[1] - head[1]) < instantSpeed * 2)
			{
				head[1] = target[1];

				this.movingVertically = false;
				if (!this.movingHorizontally)
				{
					this.positionReached();
					head[0] = target[0];
					head[2] = target[2];
				}
			}
			else if (target[1] > head[1])
				head[1] += instantSpeed;
			else
				head[1] -= instantSpeed;
			this.setHead(head[0], head[1], head[2]);
		}

		this.updatePosition();
	}

	private void updatePosition()
	{
		if (this.arm != null && this.worldObj.isRemote)
		{
			this.arm.setHead(this.headPosX, this.headPosY, this.headPosZ);
			this.arm.updatePosition();
		}
	}

	private void setHead(double x, double y, double z)
	{
		this.headPosX = x;
		this.headPosY = y;
		this.headPosZ = z;
	}

	private double[] getHead()
	{
		return new double[] { this.headPosX, this.headPosY, this.headPosZ };
	}

	private int[] getTarget()
	{
		return new int[] { this.targetX, this.targetY, this.targetZ };
	}

	private void setTarget(int x, int y, int z)
	{
		this.targetX = x;
		this.targetY = y;
		this.targetZ = z;
	}

	public void forceChunkLoading(Ticket ticket)
	{
		if (this.chunkTicket == null)
			this.chunkTicket = ticket;

		Set<ChunkCoordIntPair> chunks = Sets.newHashSet();
		ChunkCoordIntPair quarryChunk = new ChunkCoordIntPair(this.xCoord >> 4, this.zCoord >> 4);
		chunks.add(quarryChunk);
		ForgeChunkManager.forceChunk(ticket, quarryChunk);

		for (int chunkX = this.box.xMin >> 4; chunkX <= this.box.xMax >> 4; chunkX++)
		{
			for (int chunkZ = this.box.zMin >> 4; chunkZ <= this.box.zMax >> 4; chunkZ++)
			{
				ChunkCoordIntPair chunk = new ChunkCoordIntPair(chunkX, chunkZ);
				ForgeChunkManager.forceChunk(ticket, chunk);
				chunks.add(chunk);
			}
		}

		if (this.placedBy != null && !(this.placedBy instanceof FakePlayer))
			// TODO gamerforEA code start
			if (this.placedBy instanceof EntityPlayerMP && ((EntityPlayerMP) this.placedBy).playerNetServerHandler == null)
				this.placedBy = null;
			else
				// TODO gamerforEA code end
				this.placedBy.addChatMessage(new ChatComponentTranslation("chat.buildcraft.quarry.chunkloadInfo", this.xCoord, this.yCoord, this.zCoord, chunks.size()));
	}

	@Override
	public boolean hasCustomInventoryName()
	{
		return false;
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox()
	{
		return new Box(this).extendToEncompass(this.box).expand(50).getBoundingBox();
	}

	@Override
	public Box getBox()
	{
		return this.box;
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side)
	{
		return new int[] {};
	}

	@Override
	public boolean canInsertItem(int p1, ItemStack p2, int p3)
	{
		return false;
	}

	@Override
	public boolean canExtractItem(int p1, ItemStack p2, int p3)
	{
		return false;
	}

	@Override
	public boolean acceptsControlMode(Mode mode)
	{
		return mode == Mode.Off || mode == Mode.On || mode == Mode.Loop;
	}

	@Override
	public boolean doDrop()
	{
		return false;
	}

	@Override
	public ConnectOverride overridePipeConnection(IPipeTile.PipeType type, ForgeDirection with)
	{
		return type == IPipeTile.PipeType.ITEM ? ConnectOverride.CONNECT : ConnectOverride.DEFAULT;
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
