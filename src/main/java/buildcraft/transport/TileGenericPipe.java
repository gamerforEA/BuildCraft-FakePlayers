/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.transport;

import buildcraft.BuildCraftCore;
import buildcraft.BuildCraftTransport;
import buildcraft.api.core.*;
import buildcraft.api.gates.IGateExpansion;
import buildcraft.api.power.IRedstoneEngineReceiver;
import buildcraft.api.tiles.IDebuggable;
import buildcraft.api.transport.*;
import buildcraft.api.transport.pluggable.IFacadePluggable;
import buildcraft.api.transport.pluggable.PipePluggable;
import buildcraft.core.DefaultProps;
import buildcraft.core.internal.IDropControlInventory;
import buildcraft.core.lib.ITileBufferHolder;
import buildcraft.core.lib.TileBuffer;
import buildcraft.core.lib.network.IGuiReturnHandler;
import buildcraft.core.lib.network.ISyncedTile;
import buildcraft.core.lib.network.Packet;
import buildcraft.core.lib.network.PacketTileState;
import buildcraft.core.lib.utils.Utils;
import buildcraft.transport.ItemFacade.FacadeState;
import buildcraft.transport.gates.GateFactory;
import buildcraft.transport.gates.GatePluggable;
import buildcraft.transport.pluggable.PlugPluggable;
import cofh.api.energy.IEnergyHandler;
import com.gamerforea.buildcraft.ModUtils;
import com.gamerforea.eventhelper.fake.FakePlayerContainer;
import com.gamerforea.eventhelper.fake.FakePlayerContainerTileEntity;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;
import org.apache.logging.log4j.Level;

import java.util.List;

public class TileGenericPipe extends TileEntity
		implements IFluidHandler, IPipeTile, ITileBufferHolder, IEnergyHandler, IDropControlInventory, ISyncedTile,
		ISolidSideTile, IGuiReturnHandler, IRedstoneEngineReceiver, IDebuggable, IPipeConnection
{
	public boolean initialized = false;
	public final PipeRenderState renderState = new PipeRenderState();
	public final PipePluggableState pluggableState = new PipePluggableState();
	public final CoreState coreState = new CoreState();
	public boolean[] pipeConnectionsBuffer = new boolean[6];

	public Pipe pipe;
	public int redstoneInput;
	public int[] redstoneInputSide = new int[ForgeDirection.VALID_DIRECTIONS.length];

	protected boolean deletePipe = false;
	protected boolean sendClientUpdate = false;
	protected boolean blockNeighborChange = false;
	protected int blockNeighborChangedSides = 0;
	protected boolean refreshRenderState = false;
	protected boolean pipeBound = false;
	protected boolean resyncGateExpansions = false;
	protected boolean attachPluggables = false;
	protected SideProperties sideProperties = new SideProperties();

	private TileBuffer[] tileBuffer;
	private int glassColor = -1;

	// TODO gamerforEA code start
	public final FakePlayerContainer fake = new FakePlayerContainerTileEntity(ModUtils.profile, this);
	// TODO gamerforEA code end

	public static class CoreState implements ISerializable
	{
		public int pipeId = -1;

		@Override
		public void writeData(ByteBuf data)
		{
			data.writeInt(this.pipeId);
		}

		@Override
		public void readData(ByteBuf data)
		{
			this.pipeId = data.readInt();
		}
	}

	public static class SideProperties
	{
		PipePluggable[] pluggables = new PipePluggable[ForgeDirection.VALID_DIRECTIONS.length];

		public void writeToNBT(NBTTagCompound nbt)
		{
			for (int i = 0; i < ForgeDirection.VALID_DIRECTIONS.length; i++)
			{
				PipePluggable pluggable = this.pluggables[i];
				final String key = "pluggable[" + i + "]";
				if (pluggable == null)
					nbt.removeTag(key);
				else
				{
					NBTTagCompound pluggableData = new NBTTagCompound();
					pluggableData.setString("pluggableName", PipeManager.getPluggableName(pluggable.getClass()));
					pluggable.writeToNBT(pluggableData);
					nbt.setTag(key, pluggableData);
				}
			}
		}

		public void readFromNBT(NBTTagCompound nbt)
		{
			for (int i = 0; i < ForgeDirection.VALID_DIRECTIONS.length; i++)
			{
				final String key = "pluggable[" + i + "]";
				if (!nbt.hasKey(key))
					continue;
				try
				{
					NBTTagCompound pluggableData = nbt.getCompoundTag(key);
					Class<?> pluggableClass = null;
					// Migration support for 6.1.x/6.2.x
					if (pluggableData.hasKey("pluggableClass"))
					{
						String c = pluggableData.getString("pluggableClass");
						if ("buildcraft.transport.gates.ItemGate$GatePluggable".equals(c))
							pluggableClass = GatePluggable.class;
						else if ("buildcraft.transport.ItemFacade$FacadePluggable".equals(c))
							pluggableClass = FacadePluggable.class;
						else if ("buildcraft.transport.ItemPlug$PlugPluggable".equals(c))
							pluggableClass = PlugPluggable.class;
						else if ("buildcraft.transport.gates.ItemRobotStation$RobotStationPluggable".equals(c) || "buildcraft.transport.ItemRobotStation$RobotStationPluggable".equals(c))
							pluggableClass = PipeManager.getPluggableByName("robotStation");
					}
					else
						pluggableClass = PipeManager.getPluggableByName(pluggableData.getString("pluggableName"));
					if (pluggableClass != null)
					{
						if (!PipePluggable.class.isAssignableFrom(pluggableClass))
						{
							BCLog.logger.warn("Wrong pluggable class: " + pluggableClass);
							continue;
						}
						PipePluggable pluggable = (PipePluggable) pluggableClass.newInstance();
						pluggable.readFromNBT(pluggableData);
						this.pluggables[i] = pluggable;
					}
				}
				catch (Exception e)
				{
					BCLog.logger.warn("Failed to load side state");
					e.printStackTrace();
				}
			}

			// Migration code
			for (int i = 0; i < ForgeDirection.VALID_DIRECTIONS.length; i++)
			{
				PipePluggable pluggable = null;
				if (nbt.hasKey("facadeState[" + i + "]"))
					pluggable = new FacadePluggable(FacadeState.readArray(nbt.getTagList("facadeState[" + i + "]", Constants.NBT.TAG_COMPOUND)));
				else // Migration support for 5.0.x and 6.0.x
					if (nbt.hasKey("facadeBlocks[" + i + "]"))
					{
						// 5.0.x
						Block block = (Block) Block.blockRegistry.getObjectById(nbt.getInteger("facadeBlocks[" + i + "]"));
						int blockId = nbt.getInteger("facadeBlocks[" + i + "]");

						if (blockId != 0)
						{
							int metadata = nbt.getInteger("facadeMeta[" + i + "]");
							pluggable = new FacadePluggable(new FacadeState[] { FacadeState.create(block, metadata) });
						}
					}
					else if (nbt.hasKey("facadeBlocksStr[" + i + "][0]"))
					{
						// 6.0.x
						FacadeState mainState = FacadeState.create((Block) Block.blockRegistry.getObject(nbt.getString("facadeBlocksStr[" + i + "][0]")), nbt.getInteger("facadeMeta[" + i + "][0]"));
						if (nbt.hasKey("facadeBlocksStr[" + i + "][1]"))
						{
							FacadeState phasedState = FacadeState.create((Block) Block.blockRegistry.getObject(nbt.getString("facadeBlocksStr[" + i + "][1]")), nbt.getInteger("facadeMeta[" + i + "][1]"), PipeWire.fromOrdinal(nbt.getInteger("facadeWires[" + i + "]")));
							pluggable = new FacadePluggable(new FacadeState[] { mainState, phasedState });
						}
						else
							pluggable = new FacadePluggable(new FacadeState[] { mainState });
					}

				if (nbt.getBoolean("plug[" + i + "]"))
					pluggable = new PlugPluggable();

				if (pluggable != null)
					this.pluggables[i] = pluggable;
			}
		}

		public void rotateLeft()
		{
			PipePluggable[] newPluggables = new PipePluggable[ForgeDirection.VALID_DIRECTIONS.length];
			for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
			{
				newPluggables[dir.getRotation(ForgeDirection.UP).ordinal()] = this.pluggables[dir.ordinal()];
			}
			this.pluggables = newPluggables;
		}

		public boolean dropItem(TileGenericPipe pipe, ForgeDirection direction, EntityPlayer player)
		{
			boolean result = false;
			PipePluggable pluggable = this.pluggables[direction.ordinal()];
			if (pluggable != null)
			{
				pluggable.onDetachedPipe(pipe, direction);
				if (!pipe.getWorld().isRemote)
				{
					ItemStack[] stacks = pluggable.getDropItems(pipe);
					if (stacks != null)
						for (ItemStack stack : stacks)
						{
							Utils.dropTryIntoPlayerInventory(pipe.worldObj, pipe.xCoord, pipe.yCoord, pipe.zCoord, stack, player);
						}
				}
				result = true;
			}
			return result;
		}

		public void invalidate()
		{
			for (PipePluggable p : this.pluggables)
			{
				if (p != null)
					p.invalidate();
			}
		}

		public void validate(TileGenericPipe pipe)
		{
			for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS)
			{
				PipePluggable p = this.pluggables[d.ordinal()];

				if (p != null)
					p.validate(pipe, d);
			}
		}
	}

	public TileGenericPipe()
	{
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);

		if (this.glassColor >= 0)
			nbt.setByte("stainedColor", (byte) this.glassColor);
		for (int i = 0; i < ForgeDirection.VALID_DIRECTIONS.length; i++)
		{
			final String key = "redstoneInputSide[" + i + "]";
			nbt.setByte(key, (byte) this.redstoneInputSide[i]);
		}

		if (this.pipe != null)
		{
			nbt.setInteger("pipeId", Item.getIdFromItem(this.pipe.item));
			this.pipe.writeToNBT(nbt);
		}
		else
			nbt.setInteger("pipeId", this.coreState.pipeId);

		this.sideProperties.writeToNBT(nbt);

		// TODO gamerforEA code start
		this.fake.writeToNBT(nbt);
		// TODO gamerforEA code end
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		super.readFromNBT(nbt);

		this.glassColor = nbt.hasKey("stainedColor") ? nbt.getByte("stainedColor") : -1;

		this.redstoneInput = 0;

		for (int i = 0; i < ForgeDirection.VALID_DIRECTIONS.length; i++)
		{
			final String key = "redstoneInputSide[" + i + "]";
			if (nbt.hasKey(key))
			{
				this.redstoneInputSide[i] = nbt.getByte(key);

				if (this.redstoneInputSide[i] > this.redstoneInput)
					this.redstoneInput = this.redstoneInputSide[i];
			}
			else
				this.redstoneInputSide[i] = 0;
		}

		this.coreState.pipeId = nbt.getInteger("pipeId");
		this.pipe = BlockGenericPipe.createPipe(Item.getItemById(this.coreState.pipeId));
		this.bindPipe();

		if (this.pipe != null)
			this.pipe.readFromNBT(nbt);
		else
		{
			BCLog.logger.log(Level.WARN, "Pipe failed to load from NBT at {0},{1},{2}", this.xCoord, this.yCoord, this.zCoord);
			this.deletePipe = true;
		}

		this.sideProperties.readFromNBT(nbt);
		this.attachPluggables = true;

		// TODO gamerforEA code start
		this.fake.readFromNBT(nbt);
		// TODO gamerforEA code end
	}

	@Override
	public void invalidate()
	{
		this.initialized = false;
		this.tileBuffer = null;

		if (this.pipe != null)
			this.pipe.invalidate();

		this.sideProperties.invalidate();

		super.invalidate();
	}

	@Override
	public void validate()
	{
		super.validate();
		this.initialized = false;
		this.tileBuffer = null;
		this.bindPipe();

		if (this.pipe != null)
			this.pipe.validate();

		this.sideProperties.validate(this);
	}

	protected void notifyBlockChanged()
	{
		this.worldObj.notifyBlockOfNeighborChange(this.xCoord, this.yCoord, this.zCoord, this.getBlock());
		this.scheduleRenderUpdate();
		this.sendUpdateToClient();
		if (this.pipe != null)
			this.pipe.scheduleWireUpdate();
	}

	@Override
	public void updateEntity()
	{
		if (!this.worldObj.isRemote)
		{
			if (this.deletePipe)
				this.worldObj.setBlockToAir(this.xCoord, this.yCoord, this.zCoord);

			if (this.pipe == null)
				return;

			if (!this.initialized)
				this.initialize(this.pipe);
		}

		if (this.attachPluggables)
		{
			this.attachPluggables = false;
			// Attach callback
			for (int i = 0; i < ForgeDirection.VALID_DIRECTIONS.length; i++)
			{
				if (this.sideProperties.pluggables[i] != null)
				{
					this.pipe.eventBus.registerHandler(this.sideProperties.pluggables[i]);
					this.sideProperties.pluggables[i].onAttachedPipe(this, ForgeDirection.getOrientation(i));
				}
			}
			this.notifyBlockChanged();
		}

		if (!BlockGenericPipe.isValid(this.pipe))
			return;

		this.pipe.updateEntity();

		for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS)
		{
			PipePluggable p = this.getPipePluggable(direction);
			if (p != null)
				p.update(this, direction);
		}

		if (this.worldObj.isRemote)
		{
			if (this.resyncGateExpansions)
				this.syncGateExpansions();

			return;
		}

		if (this.blockNeighborChange)
		{
			for (int i = 0; i < 6; i++)
			{
				if ((this.blockNeighborChangedSides & 1 << i) != 0)
				{
					this.blockNeighborChangedSides ^= 1 << i;
					this.computeConnection(ForgeDirection.getOrientation(i));
				}
			}
			this.pipe.onNeighborBlockChange(0);
			this.blockNeighborChange = false;
			this.refreshRenderState = true;
		}

		if (this.refreshRenderState)
		{
			this.refreshRenderState();
			this.refreshRenderState = false;
		}

		if (this.sendClientUpdate)
		{
			this.sendClientUpdate = false;

			if (this.worldObj instanceof WorldServer)
			{
				WorldServer world = (WorldServer) this.worldObj;
				Packet updatePacket = this.getBCDescriptionPacket();

				for (Object o : world.playerEntities)
				{
					EntityPlayerMP player = (EntityPlayerMP) o;

					if (world.getPlayerManager().isPlayerWatchingChunk(player, this.xCoord >> 4, this.zCoord >> 4))
						BuildCraftCore.instance.sendToPlayer(player, updatePacket);
				}
			}
		}
	}

	public void initializeFromItemMetadata(int i)
	{
		if (i >= 1 && i <= 16)
			this.setPipeColor(i - 1 & 15);
		else
			this.setPipeColor(-1);
	}

	public int getItemMetadata()
	{
		return this.getPipeColor() >= 0 ? 1 + this.getPipeColor() : 0;
	}

	@Override
	public int getPipeColor()
	{
		return this.worldObj.isRemote ? this.renderState.getGlassColor() : this.glassColor;
	}

	public boolean setPipeColor(int color)
	{
		if (!this.worldObj.isRemote && color >= -1 && color < 16 && this.glassColor != color)
		{
			this.glassColor = color;
			this.notifyBlockChanged();
			this.worldObj.notifyBlocksOfNeighborChange(this.xCoord, this.yCoord, this.zCoord, this.blockType);
			return true;
		}
		return false;
	}

	/**
	 * PRECONDITION: worldObj must not be null
	 */
	protected void refreshRenderState()
	{
		this.renderState.setGlassColor((byte) this.glassColor);

		// Pipe connections;
		for (ForgeDirection o : ForgeDirection.VALID_DIRECTIONS)
		{
			this.renderState.pipeConnectionMatrix.setConnected(o, this.pipeConnectionsBuffer[o.ordinal()]);
		}

		// Pipe Textures
		for (int i = 0; i < 7; i++)
		{
			ForgeDirection o = ForgeDirection.getOrientation(i);
			this.renderState.textureMatrix.setIconIndex(o, this.pipe.getIconIndex(o));
		}

		// WireState
		for (PipeWire color : PipeWire.values())
		{
			this.renderState.wireMatrix.setWire(color, this.pipe.wireSet[color.ordinal()]);

			for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS)
			{
				this.renderState.wireMatrix.setWireConnected(color, direction, this.pipe.isWireConnectedTo(this.getTile(direction), color, direction));
			}

			boolean lit = this.pipe.wireSignalStrength[color.ordinal()] > 0;

			switch (color)
			{
				case RED:
					this.renderState.wireMatrix.setWireIndex(color, lit ? WireIconProvider.Texture_Red_Lit : WireIconProvider.Texture_Red_Dark);
					break;
				case BLUE:
					this.renderState.wireMatrix.setWireIndex(color, lit ? WireIconProvider.Texture_Blue_Lit : WireIconProvider.Texture_Blue_Dark);
					break;
				case GREEN:
					this.renderState.wireMatrix.setWireIndex(color, lit ? WireIconProvider.Texture_Green_Lit : WireIconProvider.Texture_Green_Dark);
					break;
				case YELLOW:
					this.renderState.wireMatrix.setWireIndex(color, lit ? WireIconProvider.Texture_Yellow_Lit : WireIconProvider.Texture_Yellow_Dark);
					break;
				default:
					break;

			}
		}

		/* TODO: Rewrite the requiresRenderUpdate API to run on the
		   server side instead of the client side to save network bandwidth */
		this.pluggableState.setPluggables(this.sideProperties.pluggables);

		if (this.renderState.isDirty())
			this.renderState.clean();
		this.sendUpdateToClient();
	}

	public void initialize(Pipe<?> pipe)
	{
		this.initialized = false;

		this.blockType = this.getBlockType();

		if (pipe == null)
		{
			BCLog.logger.log(Level.WARN, "Pipe failed to initialize at {0},{1},{2}, deleting", this.xCoord, this.yCoord, this.zCoord);
			this.worldObj.setBlockToAir(this.xCoord, this.yCoord, this.zCoord);
			return;
		}

		this.pipe = pipe;

		for (ForgeDirection o : ForgeDirection.VALID_DIRECTIONS)
		{
			TileEntity tile = this.getTile(o);

			if (tile instanceof ITileBufferHolder)
				((ITileBufferHolder) tile).blockCreated(o, BuildCraftTransport.genericPipeBlock, this);
			if (tile instanceof IPipeTile)
				((IPipeTile) tile).scheduleNeighborChange();
		}

		this.bindPipe();

		this.computeConnections();
		this.scheduleNeighborChange();
		this.scheduleRenderUpdate();

		if (!pipe.isInitialized())
			pipe.initialize();

		this.initialized = true;
	}

	private void bindPipe()
	{
		if (!this.pipeBound && this.pipe != null)
		{
			this.pipe.setTile(this);
			this.coreState.pipeId = Item.getIdFromItem(this.pipe.item);
			this.pipeBound = true;
		}
	}

	public boolean isInitialized()
	{
		return this.initialized;
	}

	@Override
	public void scheduleNeighborChange()
	{
		this.blockNeighborChange = true;
		this.blockNeighborChangedSides = 0x3F;
	}

	public void scheduleNeighborChange(ForgeDirection direction)
	{
		this.blockNeighborChange = true;
		this.blockNeighborChangedSides |= direction == ForgeDirection.UNKNOWN ? 0x3F : 1 << direction.ordinal();
	}

	@Override
	public boolean canInjectItems(ForgeDirection from)
	{
		if (this.getPipeType() != IPipeTile.PipeType.ITEM)
			return false;
		return this.isPipeConnected(from);
	}

	@Override
	public int injectItem(ItemStack payload, boolean doAdd, ForgeDirection from, EnumColor color)
	{
		if (BlockGenericPipe.isValid(this.pipe) && this.pipe.transport instanceof PipeTransportItems && this.isPipeConnected(from) && this.pipe.inputOpen(from))
		{

			if (doAdd)
			{
				Position itemPos = new Position(this.xCoord + 0.5, this.yCoord + 0.5, this.zCoord + 0.5, from.getOpposite());
				itemPos.moveBackwards(0.4);

				TravelingItem pipedItem = TravelingItem.make(itemPos.x, itemPos.y, itemPos.z, payload);
				if (pipedItem.isCorrupted())
					return 0;

				pipedItem.color = color;
				((PipeTransportItems) this.pipe.transport).injectItem(pipedItem, itemPos.orientation);
			}
			return payload.stackSize;
		}

		return 0;
	}

	@Override
	public int injectItem(ItemStack payload, boolean doAdd, ForgeDirection from)
	{
		return this.injectItem(payload, doAdd, from, null);
	}

	@Override
	public PipeType getPipeType()
	{
		if (BlockGenericPipe.isValid(this.pipe))
			return this.pipe.transport.getPipeType();
		return null;
	}

	@Override
	public int x()
	{
		return this.xCoord;
	}

	@Override
	public int y()
	{
		return this.yCoord;
	}

	@Override
	public int z()
	{
		return this.zCoord;
	}

	/* SMP */

	public Packet getBCDescriptionPacket()
	{
		this.bindPipe();
		this.updateCoreState();

		PacketTileState packet = new PacketTileState(this.xCoord, this.yCoord, this.zCoord);

		if (this.pipe != null && this.pipe.transport != null)
			this.pipe.transport.sendDescriptionPacket();

		packet.addStateForSerialization((byte) 0, this.coreState);
		packet.addStateForSerialization((byte) 1, this.renderState);
		packet.addStateForSerialization((byte) 2, this.pluggableState);

		if (this.pipe instanceof ISerializable)
			packet.addStateForSerialization((byte) 3, (ISerializable) this.pipe);

		return packet;
	}

	@Override
	public net.minecraft.network.Packet getDescriptionPacket()
	{
		return Utils.toPacket(this.getBCDescriptionPacket(), 1);
	}

	public void sendUpdateToClient()
	{
		this.sendClientUpdate = true;
	}

	@Override
	public void blockRemoved(ForgeDirection from)
	{

	}

	public TileBuffer[] getTileCache()
	{
		if (this.tileBuffer == null && this.pipe != null)
			this.tileBuffer = TileBuffer.makeBuffer(this.worldObj, this.xCoord, this.yCoord, this.zCoord, this.pipe.transport.delveIntoUnloadedChunks());
		return this.tileBuffer;
	}

	@Override
	public void blockCreated(ForgeDirection from, Block block, TileEntity tile)
	{
		TileBuffer[] cache = this.getTileCache();
		if (cache != null)
			cache[from.getOpposite().ordinal()].set(block, tile);
	}

	@Override
	public Block getBlock(ForgeDirection to)
	{
		TileBuffer[] cache = this.getTileCache();
		if (cache != null)
			return cache[to.ordinal()].getBlock();
		else
			return null;
	}

	@Override
	public TileEntity getTile(ForgeDirection to)
	{
		return this.getTile(to, false);
	}

	public TileEntity getTile(ForgeDirection to, boolean forceUpdate)
	{
		TileBuffer[] cache = this.getTileCache();
		if (cache != null)
			return cache[to.ordinal()].getTile(forceUpdate);
		else
			return null;
	}

	protected boolean canPipeConnect_internal(TileEntity with, ForgeDirection side)
	{
		if (!(this.pipe instanceof IPipeConnectionForced) || !((IPipeConnectionForced) this.pipe).ignoreConnectionOverrides(side))
			if (with instanceof IPipeConnection)
			{
				IPipeConnection.ConnectOverride override = ((IPipeConnection) with).overridePipeConnection(this.pipe.transport.getPipeType(), side.getOpposite());
				if (override != IPipeConnection.ConnectOverride.DEFAULT)
					return override == IPipeConnection.ConnectOverride.CONNECT;
			}

		if (with instanceof IPipeTile)
		{
			IPipeTile other = (IPipeTile) with;

			if (other.hasBlockingPluggable(side.getOpposite()))
				return false;

			if (other.getPipeColor() >= 0 && this.glassColor >= 0 && other.getPipeColor() != this.glassColor)
				return false;

			Pipe<?> otherPipe = (Pipe<?>) other.getPipe();

			if (!BlockGenericPipe.isValid(otherPipe))
				return false;

			if (!otherPipe.canPipeConnect(this, side.getOpposite()))
				return false;
		}

		return this.pipe.canPipeConnect(with, side);
	}

	/**
	 * Checks if this tile can connect to another tile
	 *
	 * @param with - The other Tile
	 * @param side - The orientation to get to the other tile ('with')
	 * @return true if pipes are considered connected
	 */
	protected boolean canPipeConnect(TileEntity with, ForgeDirection side)
	{
		if (with == null)
			return false;

		if (this.hasBlockingPluggable(side))
			return false;

		if (!BlockGenericPipe.isValid(this.pipe))
			return false;

		return this.canPipeConnect_internal(with, side);
	}

	@Override
	public boolean hasBlockingPluggable(ForgeDirection side)
	{
		PipePluggable pluggable = this.getPipePluggable(side);
		if (pluggable == null)
			return false;

		if (pluggable instanceof IPipeConnection)
		{
			IPipe neighborPipe = this.getNeighborPipe(side);
			if (neighborPipe != null)
			{
				IPipeConnection.ConnectOverride override = ((IPipeConnection) pluggable).overridePipeConnection(neighborPipe.getTile().getPipeType(), side);
				if (override == IPipeConnection.ConnectOverride.CONNECT)
					return true;
				else if (override == IPipeConnection.ConnectOverride.DISCONNECT)
					return false;
			}
		}
		return pluggable.isBlocking(this, side);
	}

	protected void computeConnections()
	{
		for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS)
		{
			this.computeConnection(side);
		}
	}

	protected void computeConnection(ForgeDirection side)
	{
		TileBuffer[] cache = this.getTileCache();
		if (cache == null)
			return;

		TileBuffer t = cache[side.ordinal()];
		// For blocks which are not loaded, keep the old connection value.
		if (t.exists() || !this.initialized)
		{
			t.refresh();

			this.pipeConnectionsBuffer[side.ordinal()] = this.canPipeConnect(t.getTile(), side);
		}
	}

	@Override
	public boolean isPipeConnected(ForgeDirection with)
	{
		if (this.worldObj.isRemote)
			return this.renderState.pipeConnectionMatrix.isConnected(with);
		else
			return this.pipeConnectionsBuffer[with.ordinal()];
	}

	@Override
	public boolean doDrop()
	{
		if (BlockGenericPipe.isValid(this.pipe))
			return this.pipe.doDrop();
		else
			return false;
	}

	@Override
	public void onChunkUnload()
	{
		if (this.pipe != null)
			this.pipe.onChunkUnload();
	}

	/**
	 * ITankContainer implementation *
	 */
	@Override
	public int fill(ForgeDirection from, FluidStack resource, boolean doFill)
	{
		if (BlockGenericPipe.isValid(this.pipe) && this.pipe.transport instanceof IFluidHandler && !this.hasBlockingPluggable(from))
			return ((IFluidHandler) this.pipe.transport).fill(from, resource, doFill);
		else
			return 0;
	}

	@Override
	public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain)
	{
		if (BlockGenericPipe.isValid(this.pipe) && this.pipe.transport instanceof IFluidHandler && !this.hasBlockingPluggable(from))
			return ((IFluidHandler) this.pipe.transport).drain(from, maxDrain, doDrain);
		else
			return null;
	}

	@Override
	public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain)
	{
		if (BlockGenericPipe.isValid(this.pipe) && this.pipe.transport instanceof IFluidHandler && !this.hasBlockingPluggable(from))
			return ((IFluidHandler) this.pipe.transport).drain(from, resource, doDrain);
		else
			return null;
	}

	@Override
	public boolean canFill(ForgeDirection from, Fluid fluid)
	{
		if (BlockGenericPipe.isValid(this.pipe) && this.pipe.transport instanceof IFluidHandler && !this.hasBlockingPluggable(from))
			return ((IFluidHandler) this.pipe.transport).canFill(from, fluid);
		else
			return false;
	}

	@Override
	public boolean canDrain(ForgeDirection from, Fluid fluid)
	{
		if (BlockGenericPipe.isValid(this.pipe) && this.pipe.transport instanceof IFluidHandler && !this.hasBlockingPluggable(from))
			return ((IFluidHandler) this.pipe.transport).canDrain(from, fluid);
		else
			return false;
	}

	@Override
	public FluidTankInfo[] getTankInfo(ForgeDirection from)
	{
		return null;
	}

	@Override
	public void scheduleRenderUpdate()
	{
		this.refreshRenderState = true;
	}

	public boolean hasFacade(ForgeDirection direction)
	{
		if (direction == null || direction == ForgeDirection.UNKNOWN)
			return false;
		else
			return this.sideProperties.pluggables[direction.ordinal()] instanceof IFacadePluggable;
	}

	public boolean hasGate(ForgeDirection direction)
	{
		if (direction == null || direction == ForgeDirection.UNKNOWN)
			return false;
		else
			return this.sideProperties.pluggables[direction.ordinal()] instanceof GatePluggable;
	}

	public boolean setPluggable(ForgeDirection direction, PipePluggable pluggable)
	{
		return this.setPluggable(direction, pluggable, null);
	}

	public boolean setPluggable(ForgeDirection direction, PipePluggable pluggable, EntityPlayer player)
	{
		if (this.worldObj != null && this.worldObj.isRemote)
			return false;

		if (direction == null || direction == ForgeDirection.UNKNOWN)
			return false;

		// Remove old pluggable
		if (this.sideProperties.pluggables[direction.ordinal()] != null)
		{
			this.sideProperties.dropItem(this, direction, player);
			this.pipe.eventBus.unregisterHandler(this.sideProperties.pluggables[direction.ordinal()]);
		}

		this.sideProperties.pluggables[direction.ordinal()] = pluggable;
		if (pluggable != null)
		{
			this.pipe.eventBus.registerHandler(pluggable);
			pluggable.onAttachedPipe(this, direction);
		}
		this.notifyBlockChanged();
		return true;
	}

	protected void updateCoreState()
	{
	}

	public boolean hasEnabledFacade(ForgeDirection direction)
	{
		return this.hasFacade(direction) && !((FacadePluggable) this.getPipePluggable(direction)).isTransparent();
	}

	// Legacy
	public void setGate(Gate gate, int direction)
	{
		if (this.sideProperties.pluggables[direction] == null)
		{
			gate.setDirection(ForgeDirection.getOrientation(direction));
			this.pipe.gates[direction] = gate;
			this.sideProperties.pluggables[direction] = new GatePluggable(gate);
		}
	}

	@SideOnly(Side.CLIENT)
	public IIconProvider getPipeIcons()
	{
		if (this.pipe == null)
			return null;
		return this.pipe.getIconProvider();
	}

	@Override
	public ISerializable getStateInstance(byte stateId)
	{
		switch (stateId)
		{
			case 0:
				return this.coreState;
			case 1:
				return this.renderState;
			case 2:
				return this.pluggableState;
			case 3:
				return (ISerializable) this.pipe;
		}
		throw new RuntimeException("Unknown state requested: " + stateId + " this is a bug!");
	}

	@Override
	public void afterStateUpdated(byte stateId)
	{
		if (!this.worldObj.isRemote)
			return;

		switch (stateId)
		{
			case 0:
				if (this.pipe != null)
					break;

				if (this.coreState.pipeId != 0)
					this.initialize(BlockGenericPipe.createPipe((Item) Item.itemRegistry.getObjectById(this.coreState.pipeId)));

				if (this.pipe == null)
					break;

				this.worldObj.markBlockRangeForRenderUpdate(this.xCoord, this.yCoord, this.zCoord, this.xCoord, this.yCoord, this.zCoord);
				break;

			case 1:
				if (this.renderState.needsRenderUpdate())
				{
					this.worldObj.markBlockRangeForRenderUpdate(this.xCoord, this.yCoord, this.zCoord, this.xCoord, this.yCoord, this.zCoord);
					this.renderState.clean();
				}
				break;
			case 2:
				PipePluggable[] newPluggables = this.pluggableState.getPluggables();

				// mark for render update if necessary
				for (int i = 0; i < ForgeDirection.VALID_DIRECTIONS.length; i++)
				{
					PipePluggable old = this.sideProperties.pluggables[i];
					PipePluggable newer = newPluggables[i];
					if (old == null && newer == null)
						continue;
					else if (old != null && newer != null && old.getClass() == newer.getClass())
					{
						if (newer.requiresRenderUpdate(old))
						{
							this.worldObj.markBlockRangeForRenderUpdate(this.xCoord, this.yCoord, this.zCoord, this.xCoord, this.yCoord, this.zCoord);
							break;
						}
					}
					else
					{
						// one of them is null but not the other, so update
						this.worldObj.markBlockRangeForRenderUpdate(this.xCoord, this.yCoord, this.zCoord, this.xCoord, this.yCoord, this.zCoord);
						break;
					}
				}
				this.sideProperties.pluggables = newPluggables.clone();

				for (int i = 0; i < ForgeDirection.VALID_DIRECTIONS.length; i++)
				{
					final PipePluggable pluggable = this.getPipePluggable(ForgeDirection.getOrientation(i));
					if (pluggable != null && pluggable instanceof GatePluggable)
					{
						final GatePluggable gatePluggable = (GatePluggable) pluggable;
						Gate gate = this.pipe.gates[i];
						if (gate == null || gate.logic != gatePluggable.getLogic() || gate.material != gatePluggable.getMaterial())
							this.pipe.gates[i] = GateFactory.makeGate(this.pipe, gatePluggable.getMaterial(), gatePluggable.getLogic(), ForgeDirection.getOrientation(i));
					}
					else
						this.pipe.gates[i] = null;
				}

				this.syncGateExpansions();
				break;
		}
	}

	private void syncGateExpansions()
	{
		this.resyncGateExpansions = false;
		for (int i = 0; i < ForgeDirection.VALID_DIRECTIONS.length; i++)
		{
			Gate gate = this.pipe.gates[i];
			if (gate == null)
				continue;
			GatePluggable gatePluggable = (GatePluggable) this.sideProperties.pluggables[i];
			if (gatePluggable.getExpansions().length > 0)
				for (IGateExpansion expansion : gatePluggable.getExpansions())
				{
					if (expansion != null)
					{
						if (!gate.expansions.containsKey(expansion))
							gate.addGateExpansion(expansion);
					}
					else
						this.resyncGateExpansions = true;
				}
		}
	}

	@Override
	@SideOnly(Side.CLIENT)
	public double getMaxRenderDistanceSquared()
	{
		return DefaultProps.PIPE_CONTENTS_RENDER_DIST * DefaultProps.PIPE_CONTENTS_RENDER_DIST;
	}

	@Override
	public boolean shouldRefresh(Block oldBlock, Block newBlock, int oldMeta, int newMeta, World world, int x, int y, int z)
	{
		return oldBlock != newBlock;
	}

	@Override
	public boolean isSolidOnSide(ForgeDirection side)
	{
		if (this.hasPipePluggable(side) && this.getPipePluggable(side).isSolidOnSide(this, side))
			return true;

		if (BlockGenericPipe.isValid(this.pipe) && this.pipe instanceof ISolidSideTile)
			return ((ISolidSideTile) this.pipe).isSolidOnSide(side);
		return false;
	}

	@Override
	public PipePluggable getPipePluggable(ForgeDirection side)
	{
		if (side == null || side == ForgeDirection.UNKNOWN)
			return null;

		return this.sideProperties.pluggables[side.ordinal()];
	}

	@Override
	public boolean hasPipePluggable(ForgeDirection side)
	{
		if (side == null || side == ForgeDirection.UNKNOWN)
			return false;

		return this.sideProperties.pluggables[side.ordinal()] != null;
	}

	public Block getBlock()
	{
		return this.getBlockType();
	}

	@Override
	public World getWorld()
	{
		return this.worldObj;
	}

	public boolean isUseableByPlayer(EntityPlayer player)
	{
		return this.worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord) == this;
	}

	@Override
	public void writeGuiData(ByteBuf data)
	{
		if (BlockGenericPipe.isValid(this.pipe) && this.pipe instanceof IGuiReturnHandler)
			((IGuiReturnHandler) this.pipe).writeGuiData(data);
	}

	@Override
	public void readGuiData(ByteBuf data, EntityPlayer sender)
	{
		if (BlockGenericPipe.isValid(this.pipe) && this.pipe instanceof IGuiReturnHandler)
			((IGuiReturnHandler) this.pipe).readGuiData(data, sender);
	}

	private IEnergyHandler internalGetEnergyHandler(ForgeDirection side)
	{
		if (this.hasPipePluggable(side))
		{
			PipePluggable pluggable = this.getPipePluggable(side);
			if (pluggable instanceof IEnergyHandler)
				return (IEnergyHandler) pluggable;
			else if (pluggable.isBlocking(this, side))
				return null;
		}
		if (this.pipe instanceof IEnergyHandler)
			return (IEnergyHandler) this.pipe;
		return null;
	}

	@Override
	public boolean canConnectEnergy(ForgeDirection from)
	{
		IEnergyHandler handler = this.internalGetEnergyHandler(from);
		if (handler != null)
			return handler.canConnectEnergy(from);
		else
			return false;
	}

	@Override
	public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate)
	{
		IEnergyHandler handler = this.internalGetEnergyHandler(from);
		if (handler != null)
			return handler.receiveEnergy(from, maxReceive, simulate);
		else
			return 0;
	}

	@Override
	public int extractEnergy(ForgeDirection from, int maxExtract, boolean simulate)
	{
		IEnergyHandler handler = this.internalGetEnergyHandler(from);
		if (handler != null)
			return handler.extractEnergy(from, maxExtract, simulate);
		else
			return 0;
	}

	@Override
	public int getEnergyStored(ForgeDirection from)
	{
		IEnergyHandler handler = this.internalGetEnergyHandler(from);
		if (handler != null)
			return handler.getEnergyStored(from);
		else
			return 0;
	}

	@Override
	public int getMaxEnergyStored(ForgeDirection from)
	{
		IEnergyHandler handler = this.internalGetEnergyHandler(from);
		if (handler != null)
			return handler.getMaxEnergyStored(from);
		else
			return 0;
	}

	@Override
	public Block getNeighborBlock(ForgeDirection dir)
	{
		return this.getBlock(dir);
	}

	@Override
	public TileEntity getNeighborTile(ForgeDirection dir)
	{
		return this.getTile(dir);
	}

	@Override
	public IPipe getNeighborPipe(ForgeDirection dir)
	{
		TileEntity neighborTile = this.getTile(dir);
		if (neighborTile instanceof IPipeTile)
			return ((IPipeTile) neighborTile).getPipe();
		else
			return null;
	}

	@Override
	public IPipe getPipe()
	{
		return this.pipe;
	}

	@Override
	public boolean canConnectRedstoneEngine(ForgeDirection side)
	{
		if (this.pipe instanceof IRedstoneEngineReceiver)
			return ((IRedstoneEngineReceiver) this.pipe).canConnectRedstoneEngine(side);
		else
			return this.getPipeType() != PipeType.POWER && this.getPipeType() != PipeType.STRUCTURE;
	}

	@Override
	public void getDebugInfo(List<String> info, ForgeDirection side, ItemStack debugger, EntityPlayer player)
	{
		if (this.pipe instanceof IDebuggable)
			((IDebuggable) this.pipe).getDebugInfo(info, side, debugger, player);
		if (this.pipe.transport instanceof IDebuggable)
			((IDebuggable) this.pipe.transport).getDebugInfo(info, side, debugger, player);
		if (this.getPipePluggable(side) != null && this.getPipePluggable(side) instanceof IDebuggable)
			((IDebuggable) this.getPipePluggable(side)).getDebugInfo(info, side, debugger, player);
	}

	@Override
	public ConnectOverride overridePipeConnection(PipeType type, ForgeDirection with)
	{
		if (type == PipeType.POWER && this.hasPipePluggable(with) && this.getPipePluggable(with) instanceof IEnergyHandler)
			return ConnectOverride.CONNECT;
		return ConnectOverride.DEFAULT;
	}
}
