/**
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.core.builders;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import com.gamerforea.buildcraft.ModUtils;

import buildcraft.BuildCraftCore;
import buildcraft.api.blueprints.IBuilderContext;
import buildcraft.api.blueprints.MappingNotFoundException;
import buildcraft.api.blueprints.MappingRegistry;
import buildcraft.api.core.ISerializable;
import buildcraft.api.core.Position;
import buildcraft.core.BlockBuildTool;
import buildcraft.core.StackAtPosition;
import buildcraft.core.lib.block.TileBuildCraft;
import buildcraft.core.lib.inventory.InvUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.Constants;

public class BuildingItem implements IBuildingItem, ISerializable
{

	public static int ITEMS_SPACE = 2;

	public Position origin, destination;
	public LinkedList<StackAtPosition> stacksToDisplay = new LinkedList<StackAtPosition>();

	public boolean isDone = false;

	public BuildingSlot slotToBuild;
	public IBuilderContext context;

	private long previousUpdate;
	private float lifetimeDisplay = 0;
	private float maxLifetime = 0;
	private boolean initialized = false;
	private double vx, vy, vz;
	private double maxHeight;
	private float lifetime = 0;

	public void initialize()
	{
		if (!this.initialized)
		{
			double dx = this.destination.x - this.origin.x;
			double dy = this.destination.y - this.origin.y;
			double dz = this.destination.z - this.origin.z;

			double size = Math.sqrt(dx * dx + dy * dy + dz * dz);

			this.maxLifetime = (float) size * 4;

			// maxHeight = 5.0 + (destination.y - origin.y) / 2.0;

			this.maxHeight = size / 2;

			// the below computation is an approximation of the distance to
			// travel for the object. It really follows a sinus, but we compute
			// the size of a triangle for simplification.

			Position middle = new Position();
			middle.x = (this.destination.x + this.origin.x) / 2;
			middle.y = (this.destination.y + this.origin.y) / 2;
			middle.z = (this.destination.z + this.origin.z) / 2;

			Position top = new Position();
			top.x = middle.x;
			top.y = middle.y + this.maxHeight;
			top.z = middle.z;

			Position originToTop = new Position();
			originToTop.x = top.x - this.origin.x;
			originToTop.y = top.y - this.origin.y;
			originToTop.z = top.z - this.origin.z;

			Position destinationToTop = new Position();
			destinationToTop.x = this.destination.x - this.origin.x;
			destinationToTop.y = this.destination.y - this.origin.y;
			destinationToTop.z = this.destination.z - this.origin.z;

			double d1 = Math.sqrt(originToTop.x * originToTop.x + originToTop.y * originToTop.y + originToTop.z * originToTop.z);

			double d2 = Math.sqrt(destinationToTop.x * destinationToTop.x + destinationToTop.y * destinationToTop.y + destinationToTop.z * destinationToTop.z);

			d1 = d1 / size * this.maxLifetime;
			d2 = d2 / size * this.maxLifetime;

			this.maxLifetime = (float) d1 + (float) d2;

			this.vx = dx / this.maxLifetime;
			this.vy = dy / this.maxLifetime;
			this.vz = dz / this.maxLifetime;

			if (this.stacksToDisplay.size() == 0)
			{
				StackAtPosition sPos = new StackAtPosition();
				sPos.stack = new ItemStack(BuildCraftCore.buildToolBlock);
				this.stacksToDisplay.add(sPos);
			}

			this.initialized = true;
		}
	}

	public Position getDisplayPosition(float time)
	{
		Position result = new Position();

		result.x = this.origin.x + this.vx * time;
		result.y = this.origin.y + this.vy * time + MathHelper.sin(time / this.maxLifetime * (float) Math.PI) * this.maxHeight;
		result.z = this.origin.z + this.vz * time;

		return result;
	}

	public void update()
	{
		if (this.isDone)
			return;

		this.initialize();

		this.lifetime++;

		if (this.lifetime > this.maxLifetime + this.stacksToDisplay.size() * ITEMS_SPACE - 1)
		{
			this.isDone = true;
			this.build();
		}

		this.lifetimeDisplay = this.lifetime;
		this.previousUpdate = new Date().getTime();

		if (this.slotToBuild != null && this.lifetime > this.maxLifetime)
			this.slotToBuild.writeCompleted(this.context, (this.lifetime - this.maxLifetime) / (this.stacksToDisplay.size() * ITEMS_SPACE));
	}

	public void displayUpdate()
	{
		this.initialize();

		float tickDuration = 50.0F; // miliseconds
		long currentUpdate = new Date().getTime();
		float timeSpan = currentUpdate - this.previousUpdate;
		this.previousUpdate = currentUpdate;

		float displayPortion = timeSpan / tickDuration;

		if (this.lifetimeDisplay - this.lifetime <= 1.0)
			this.lifetimeDisplay += 1.0 * displayPortion;
	}

	private void build()
	{
		if (this.slotToBuild != null)
		{
			/*if (BlockUtil.isToughBlock(context.world(), destX, destY, destZ)) {
				BlockUtil.breakBlock(context.world(), destX, destY, destZ, BuildCraftBuilders.fillerLifespanTough);
			} else {
				BlockUtil.breakBlock(context.world(), destX, destY, destZ, BuildCraftBuilders.fillerLifespanNormal);
			}*/

			int destX = (int) Math.floor(this.destination.x);
			int destY = (int) Math.floor(this.destination.y);
			int destZ = (int) Math.floor(this.destination.z);
			Block oldBlock = this.context.world().getBlock(destX, destY, destZ);
			int oldMeta = this.context.world().getBlockMetadata(destX, destY, destZ);

			// TODO gamerforEA code replace, old code: if (this.slotToBuild.writeToWorld(this.context))
			int originX = MathHelper.floor_double(this.origin.x);
			int originY = MathHelper.floor_double(this.origin.y);
			int originZ = MathHelper.floor_double(this.origin.z);
			TileEntity originTile = this.context.world().getTileEntity(originX, originY, originZ);
			EntityPlayer player = originTile instanceof TileBuildCraft ? ((TileBuildCraft) originTile).fake.get() : ModUtils.getModFake(this.context.world());
			if (this.slotToBuild.writeToWorld(player, this.context))
				// TODO gamerforEA code end
				this.context.world().playAuxSFXAtEntity(null, 2001, destX, destY, destZ, Block.getIdFromBlock(oldBlock) + (oldMeta << 12));
			else if (this.slotToBuild.stackConsumed != null)
				for (ItemStack s : this.slotToBuild.stackConsumed)
					if (s != null && !(s.getItem() instanceof ItemBlock && Block.getBlockFromItem(s.getItem()) instanceof BlockBuildTool))
						InvUtils.dropItems(this.context.world(), s, destX, destY, destZ);
		}
	}

	public LinkedList<StackAtPosition> getStacks()
	{
		int d = 0;

		for (StackAtPosition s : this.stacksToDisplay)
		{
			float stackLife = this.lifetimeDisplay - d;

			if (stackLife <= this.maxLifetime && stackLife > 0)
			{
				s.pos = this.getDisplayPosition(stackLife);
				s.display = true;
			}
			else
				s.display = false;

			d += ITEMS_SPACE;
		}

		return this.stacksToDisplay;
	}

	@Override
	public boolean isDone()
	{
		return this.isDone;
	}

	public void writeToNBT(NBTTagCompound nbt)
	{
		NBTTagCompound originNBT = new NBTTagCompound();
		this.origin.writeToNBT(originNBT);
		nbt.setTag("origin", originNBT);

		NBTTagCompound destinationNBT = new NBTTagCompound();
		this.destination.writeToNBT(destinationNBT);
		nbt.setTag("destination", destinationNBT);

		nbt.setFloat("lifetime", this.lifetime);

		NBTTagList items = new NBTTagList();

		for (StackAtPosition s : this.stacksToDisplay)
		{
			NBTTagCompound cpt = new NBTTagCompound();
			s.stack.writeToNBT(cpt);
			items.appendTag(cpt);
		}

		nbt.setTag("items", items);

		MappingRegistry registry = new MappingRegistry();

		NBTTagCompound slotNBT = new NBTTagCompound();
		NBTTagCompound registryNBT = new NBTTagCompound();

		this.slotToBuild.writeToNBT(slotNBT, registry);
		registry.write(registryNBT);

		nbt.setTag("registry", registryNBT);

		if (this.slotToBuild instanceof BuildingSlotBlock)
			nbt.setByte("slotKind", (byte) 0);
		else
			nbt.setByte("slotKind", (byte) 1);

		nbt.setTag("slotToBuild", slotNBT);
	}

	public void readFromNBT(NBTTagCompound nbt) throws MappingNotFoundException
	{
		this.origin = new Position(nbt.getCompoundTag("origin"));
		this.destination = new Position(nbt.getCompoundTag("destination"));
		this.lifetime = nbt.getFloat("lifetime");

		NBTTagList items = nbt.getTagList("items", Constants.NBT.TAG_COMPOUND);

		for (int i = 0; i < items.tagCount(); ++i)
		{
			StackAtPosition sPos = new StackAtPosition();
			sPos.stack = ItemStack.loadItemStackFromNBT(items.getCompoundTagAt(i));
			this.stacksToDisplay.add(sPos);
		}

		MappingRegistry registry = new MappingRegistry();
		registry.read(nbt.getCompoundTag("registry"));

		if (nbt.getByte("slotKind") == 0)
			this.slotToBuild = new BuildingSlotBlock();
		else
			this.slotToBuild = new BuildingSlotEntity();

		this.slotToBuild.readFromNBT(nbt.getCompoundTag("slotToBuild"), registry);
	}

	public void setStacksToDisplay(List<ItemStack> stacks)
	{
		if (stacks != null)
			for (ItemStack s : stacks)
				for (int i = 0; i < s.stackSize; ++i)
				{
					StackAtPosition sPos = new StackAtPosition();
					sPos.stack = s.copy();
					sPos.stack.stackSize = 1;
					this.stacksToDisplay.add(sPos);
				}
	}

	@Override
	public void readData(ByteBuf stream)
	{
		this.origin = new Position();
		this.destination = new Position();
		this.origin.readData(stream);
		this.destination.readData(stream);
		this.lifetime = stream.readFloat();
		this.stacksToDisplay.clear();
		int size = stream.readUnsignedShort();
		for (int i = 0; i < size; i++)
		{
			StackAtPosition e = new StackAtPosition();
			e.readData(stream);
			this.stacksToDisplay.add(e);
		}
	}

	@Override
	public void writeData(ByteBuf stream)
	{
		this.origin.writeData(stream);
		this.destination.writeData(stream);
		stream.writeFloat(this.lifetime);
		stream.writeShort(this.stacksToDisplay.size());
		for (StackAtPosition s : this.stacksToDisplay)
			s.writeData(stream);
	}

	@Override
	public int hashCode()
	{
		return 131 * this.origin.hashCode() + this.destination.hashCode();
	}
}
