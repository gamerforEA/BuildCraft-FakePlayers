/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.core.lib.block;

import buildcraft.BuildCraftCore;
import buildcraft.api.core.ISerializable;
import buildcraft.api.tiles.IControllable;
import buildcraft.core.DefaultProps;
import buildcraft.core.lib.RFBattery;
import buildcraft.core.lib.TileBuffer;
import buildcraft.core.lib.network.Packet;
import buildcraft.core.lib.network.PacketTileUpdate;
import buildcraft.core.lib.utils.Utils;
import cofh.api.energy.IEnergyHandler;
import com.gamerforea.buildcraft.ModUtils;
import com.gamerforea.eventhelper.fake.FakePlayerContainer;
import com.gamerforea.eventhelper.fake.FakePlayerContainerTileEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.HashSet;

/**
 * For future maintainers: This class intentionally does not implement just
 * every interface out there. For some of them (such as IControllable), we
 * expect the tiles supporting it to implement it - but TileBuildCraft provides
 * all the underlying functionality to stop code repetition.
 */
public abstract class TileBuildCraft extends TileEntity implements IEnergyHandler, ISerializable
{
	protected TileBuffer[] cache;
	protected HashSet<EntityPlayer> guiWatchers = new HashSet<EntityPlayer>();
	protected IControllable.Mode mode;

	private boolean init = false;
	private String owner = "[BuildCraft]";
	private RFBattery battery;

	private int receivedTick, extractedTick;
	private long worldTimeEnergyReceive;

	// TODO gamerforEA code start
	public final FakePlayerContainer fake = new FakePlayerContainerTileEntity(ModUtils.profile, this);
	// TODO gamerforEA code end

	public String getOwner()
	{
		return this.owner;
	}

	public void addGuiWatcher(EntityPlayer player)
	{
		if (!this.guiWatchers.contains(player))
			this.guiWatchers.add(player);
	}

	public void removeGuiWatcher(EntityPlayer player)
	{
		if (this.guiWatchers.contains(player))
			this.guiWatchers.remove(player);
	}

	@Override
	public void updateEntity()
	{
		if (!this.init && !this.isInvalid())
		{
			this.initialize();
			this.init = true;
		}

		if (this.battery != null)
		{
			this.receivedTick = 0;
			this.extractedTick = 0;
		}
	}

	public void initialize()
	{

	}

	@Override
	public void validate()
	{
		super.validate();
		this.cache = null;
	}

	@Override
	public void invalidate()
	{
		this.init = false;
		super.invalidate();
		this.cache = null;
	}

	public void onBlockPlacedBy(EntityLivingBase entity, ItemStack stack)
	{
		if (entity instanceof EntityPlayer)
		{
			this.owner = ((EntityPlayer) entity).getDisplayName();

			// TODO gamerforEA code start
			this.fake.setProfile(((EntityPlayer) entity).getGameProfile());
			// TODO gamerforEA code end
		}
	}

	public void destroy()
	{
		this.cache = null;
	}

	public void sendNetworkUpdate()
	{
		if (this.worldObj != null && !this.worldObj.isRemote)
			BuildCraftCore.instance.sendToPlayers(this.getPacketUpdate(), this.worldObj, this.xCoord, this.yCoord, this.zCoord, this.getNetworkUpdateRange());
	}

	protected int getNetworkUpdateRange()
	{
		return DefaultProps.NETWORK_UPDATE_RANGE;
	}

	@Override
	public void writeData(ByteBuf stream)
	{

	}

	@Override
	public void readData(ByteBuf stream)
	{

	}

	public Packet getPacketUpdate()
	{
		return new PacketTileUpdate(this);
	}

	@Override
	public net.minecraft.network.Packet getDescriptionPacket()
	{
		return Utils.toPacket(this.getPacketUpdate(), 0);
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);
		nbt.setString("owner", this.owner);
		if (this.battery != null)
		{
			NBTTagCompound batteryNBT = new NBTTagCompound();
			this.battery.writeToNBT(batteryNBT);
			nbt.setTag("battery", batteryNBT);
		}
		if (this.mode != null)
			nbt.setByte("lastMode", (byte) this.mode.ordinal());

		// TODO gamerforEA code start
		this.fake.writeToNBT(nbt);
		// TODO gamerforEA code end
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		super.readFromNBT(nbt);
		if (nbt.hasKey("owner"))
			this.owner = nbt.getString("owner");
		if (this.battery != null)
			this.battery.readFromNBT(nbt.getCompoundTag("battery"));
		if (nbt.hasKey("lastMode"))
			this.mode = IControllable.Mode.values()[nbt.getByte("lastMode")];

		// TODO gamerforEA code start
		this.fake.readFromNBT(nbt);
		// TODO gamerforEA code end
	}

	protected int getTicksSinceEnergyReceived()
	{
		return (int) (this.worldObj.getTotalWorldTime() - this.worldTimeEnergyReceive);
	}

	@Override
	public int hashCode()
	{
		return (this.xCoord * 37 + this.yCoord) * 37 + this.zCoord;
	}

	@Override
	public boolean equals(Object cmp)
	{
		return this == cmp;
	}

	@Override
	public boolean canConnectEnergy(ForgeDirection from)
	{
		return this.battery != null;
	}

	@Override
	public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate)
	{
		if (this.battery != null && this.canConnectEnergy(from))
		{
			int received = this.battery.receiveEnergy(Math.min(maxReceive, this.battery.getMaxEnergyReceive() - this.receivedTick), simulate);
			if (!simulate)
			{
				this.receivedTick += received;
				this.worldTimeEnergyReceive = this.worldObj.getTotalWorldTime();
			}
			return received;
		}
		else
			return 0;
	}

	/**
	 * If you want to use this, implement IEnergyProvider.
	 */
	@Override
	public int extractEnergy(ForgeDirection from, int maxExtract, boolean simulate)
	{
		if (this.battery != null && this.canConnectEnergy(from))
		{
			int extracted = this.battery.extractEnergy(Math.min(maxExtract, this.battery.getMaxEnergyExtract() - this.extractedTick), simulate);
			if (!simulate)
				this.extractedTick += extracted;
			return extracted;
		}
		else
			return 0;
	}

	@Override
	public int getEnergyStored(ForgeDirection from)
	{
		if (this.battery != null && this.canConnectEnergy(from))
			return this.battery.getEnergyStored();
		else
			return 0;
	}

	@Override
	public int getMaxEnergyStored(ForgeDirection from)
	{
		if (this.battery != null && this.canConnectEnergy(from))
			return this.battery.getMaxEnergyStored();
		else
			return 0;
	}

	public RFBattery getBattery()
	{
		return this.battery;
	}

	protected void setBattery(RFBattery battery)
	{
		this.battery = battery;
	}

	public Block getBlock(ForgeDirection side)
	{
		if (this.cache == null)
			this.cache = TileBuffer.makeBuffer(this.worldObj, this.xCoord, this.yCoord, this.zCoord, false);
		return this.cache[side.ordinal()].getBlock();
	}

	public TileEntity getTile(ForgeDirection side)
	{
		if (this.cache == null)
			this.cache = TileBuffer.makeBuffer(this.worldObj, this.xCoord, this.yCoord, this.zCoord, false);
		return this.cache[side.ordinal()].getTile();
	}

	public IControllable.Mode getControlMode()
	{
		return this.mode;
	}

	public void setControlMode(IControllable.Mode mode)
	{
		this.mode = mode;
	}
}
