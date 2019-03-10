/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.builders;

import buildcraft.BuildCraftCore;
import buildcraft.api.core.Position;
import buildcraft.core.Box;
import buildcraft.core.Box.Kind;
import buildcraft.core.LaserData;
import buildcraft.core.blueprints.*;
import buildcraft.core.builders.BuildingItem;
import buildcraft.core.builders.IBuildingItemsProvider;
import buildcraft.core.internal.IBoxProvider;
import buildcraft.core.lib.block.TileBuildCraft;
import buildcraft.core.lib.network.Packet;
import buildcraft.core.lib.network.command.CommandWriter;
import buildcraft.core.lib.network.command.ICommandReceiver;
import buildcraft.core.lib.network.command.PacketCommand;
import buildcraft.core.lib.utils.NetworkUtils;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayList;
import java.util.HashSet;

public class TileConstructionMarker extends TileBuildCraft
		implements IBuildingItemsProvider, IBoxProvider, ICommandReceiver
{

	public static HashSet<TileConstructionMarker> currentMarkers = new HashSet<>();

	public ForgeDirection direction = ForgeDirection.UNKNOWN;

	public LaserData laser;
	public ItemStack itemBlueprint;
	public Box box = new Box();

	public BptBuilderBase bluePrintBuilder;
	public BptContext bptContext;

	private ArrayList<BuildingItem> buildersInAction = new ArrayList<>();
	private NBTTagCompound initNBT;

	@Override
	public void initialize()
	{
		super.initialize();
		this.box.kind = Kind.BLUE_STRIPES;

		if (this.worldObj.isRemote)
			BuildCraftCore.instance.sendToServer(new PacketCommand(this, "uploadBuildersInAction", null));
	}

	private Packet createLaunchItemPacket(final BuildingItem i)
	{
		return new PacketCommand(this, "launchItem", new CommandWriter()
		{
			@Override
			public void write(ByteBuf data)
			{
				i.writeData(data);
			}
		});
	}

	@Override
	public void updateEntity()
	{
		super.updateEntity();

		BuildingItem toRemove = null;

		for (BuildingItem i : this.buildersInAction)
		{
			i.update();

			if (i.isDone)
				toRemove = i;
		}

		if (toRemove != null)
			this.buildersInAction.remove(toRemove);

		if (this.worldObj.isRemote)
			return;

		if (this.itemBlueprint != null && ItemBlueprint.getId(this.itemBlueprint) != null && this.bluePrintBuilder == null)
		{
			BlueprintBase bpt = ItemBlueprint.loadBlueprint(this.itemBlueprint);
			if (bpt instanceof Blueprint)
			{
				bpt = bpt.adjustToWorld(this.worldObj, this.xCoord, this.yCoord, this.zCoord, this.direction);
				if (bpt != null)
				{
					this.bluePrintBuilder = new BptBuilderBlueprint((Blueprint) bpt, this.worldObj, this.xCoord, this.yCoord, this.zCoord);
					this.bptContext = this.bluePrintBuilder.getContext();
					this.box.initialize(this.bluePrintBuilder);
					this.sendNetworkUpdate();
				}
			}
			else
				return;
		}

		if (this.laser == null && this.direction != ForgeDirection.UNKNOWN)
		{
			this.laser = new LaserData();
			this.laser.head = new Position(this.xCoord + 0.5F, this.yCoord + 0.5F, this.zCoord + 0.5F);
			this.laser.tail = new Position(this.xCoord + 0.5F + this.direction.offsetX * 0.5F, this.yCoord + 0.5F + this.direction.offsetY * 0.5F, this.zCoord + 0.5F + this.direction.offsetZ * 0.5F);
			this.laser.isVisible = true;
			this.sendNetworkUpdate();
		}

		if (this.initNBT != null)
		{
			if (this.bluePrintBuilder != null)
				this.bluePrintBuilder.loadBuildStateToNBT(this.initNBT.getCompoundTag("builderState"), this);

			this.initNBT = null;
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);

		nbt.setByte("direction", (byte) this.direction.ordinal());

		if (this.itemBlueprint != null)
		{
			NBTTagCompound bptNBT = new NBTTagCompound();
			this.itemBlueprint.writeToNBT(bptNBT);
			nbt.setTag("itemBlueprint", bptNBT);
		}

		NBTTagCompound bptNBT = new NBTTagCompound();

		if (this.bluePrintBuilder != null)
		{
			NBTTagCompound builderCpt = new NBTTagCompound();
			this.bluePrintBuilder.saveBuildStateToNBT(builderCpt, this);
			bptNBT.setTag("builderState", builderCpt);
		}

		nbt.setTag("bptBuilder", bptNBT);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		super.readFromNBT(nbt);

		this.direction = ForgeDirection.getOrientation(nbt.getByte("direction"));

		if (nbt.hasKey("itemBlueprint"))
			this.itemBlueprint = ItemStack.loadItemStackFromNBT(nbt.getCompoundTag("itemBlueprint"));

		// The rest of load has to be done upon initialize.
		this.initNBT = (NBTTagCompound) nbt.getCompoundTag("bptBuilder").copy();
	}

	public void setBlueprint(ItemStack currentItem)
	{
		this.itemBlueprint = currentItem;
		this.sendNetworkUpdate();
	}

	@Override
	public ArrayList<BuildingItem> getBuilders()
	{
		return this.buildersInAction;
	}

	@Override
	public void validate()
	{
		super.validate();
		if (!this.worldObj.isRemote)
			currentMarkers.add(this);
	}

	@Override
	public void invalidate()
	{
		super.invalidate();
		if (!this.worldObj.isRemote)
			currentMarkers.remove(this);
	}

	public boolean needsToBuild()
	{
		return !this.isInvalid() && this.bluePrintBuilder != null && !this.bluePrintBuilder.isDone(this);
	}

	public BptContext getContext()
	{
		return this.bptContext;
	}

	@Override
	public void addAndLaunchBuildingItem(BuildingItem item)
	{
		this.buildersInAction.add(item);
		BuildCraftCore.instance.sendToPlayersNear(this.createLaunchItemPacket(item), this);
	}

	@Override
	public void receiveCommand(String command, Side side, Object sender, ByteBuf stream)
	{
		if (side.isServer() && "uploadBuildersInAction".equals(command))
			for (BuildingItem i : this.buildersInAction)
			{
				BuildCraftCore.instance.sendToPlayer((EntityPlayer) sender, this.createLaunchItemPacket(i));
			}
		else if (side.isClient() && "launchItem".equals(command))
		{
			BuildingItem item = new BuildingItem();
			item.readData(stream);
			this.buildersInAction.add(item);
		}
	}

	@Override
	public Box getBox()
	{
		return this.box;
	}

	@Override
	public AxisAlignedBB getRenderBoundingBox()
	{
		Box renderBox = new Box(this).extendToEncompass(this.box);

		return renderBox.expand(50).getBoundingBox();
	}

	@Override
	public void writeData(ByteBuf stream)
	{
		this.box.writeData(stream);
		stream.writeByte((this.laser != null ? 1 : 0) | (this.itemBlueprint != null ? 2 : 0));
		if (this.laser != null)
			this.laser.writeData(stream);
		if (this.itemBlueprint != null)
			NetworkUtils.writeStack(stream, this.itemBlueprint);
	}

	@Override
	public void readData(ByteBuf stream)
	{
		this.box.readData(stream);
		int flags = stream.readUnsignedByte();
		if ((flags & 1) != 0)
		{
			this.laser = new LaserData();
			this.laser.readData(stream);
		}
		else
			this.laser = null;

		ItemStack newItemBlueprint = (flags & 2) != 0 ? NetworkUtils.readStack(stream) : null;

		// TODO gamerforEA code start
		if (FMLCommonHandler.instance().getSide().isServer())
			return;
		// TODO gamerforEA code end

		this.itemBlueprint = newItemBlueprint;
	}
}
