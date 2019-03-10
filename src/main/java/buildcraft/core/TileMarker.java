/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.core;

import buildcraft.BuildCraftCore;
import buildcraft.api.core.ISerializable;
import buildcraft.api.core.Position;
import buildcraft.api.tiles.ITileAreaProvider;
import buildcraft.core.lib.EntityBlock;
import buildcraft.core.lib.block.TileBuildCraft;
import buildcraft.core.lib.utils.LaserUtils;
import buildcraft.core.proxy.CoreProxy;
import io.netty.buffer.ByteBuf;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class TileMarker extends TileBuildCraft implements ITileAreaProvider
{
	public static class TileWrapper implements ISerializable
	{

		public int x, y, z;
		private TileMarker marker;

		public TileWrapper()
		{
			this.x = Integer.MAX_VALUE;
			this.y = Integer.MAX_VALUE;
			this.z = Integer.MAX_VALUE;
		}

		public TileWrapper(int x, int y, int z)
		{
			this.x = x;
			this.y = y;
			this.z = z;
		}

		public boolean isSet()
		{
			return this.x != Integer.MAX_VALUE;
		}

		public TileMarker getMarker(World world)
		{
			if (!this.isSet())
				return null;

			if (this.marker == null)
			{
				TileEntity tile = world.getTileEntity(this.x, this.y, this.z);
				if (tile instanceof TileMarker)
					this.marker = (TileMarker) tile;
			}

			return this.marker;
		}

		public void reset()
		{
			this.x = Integer.MAX_VALUE;
			this.y = Integer.MAX_VALUE;
			this.z = Integer.MAX_VALUE;
		}

		@Override
		public void readData(ByteBuf stream)
		{
			this.x = stream.readInt();
			if (this.isSet())
			{
				this.y = stream.readShort();
				this.z = stream.readInt();
			}
		}

		@Override
		public void writeData(ByteBuf stream)
		{
			stream.writeInt(this.x);
			if (this.isSet())
			{
				// Only X is used for checking if a vector is set, so we can save space on the Y coordinate.
				stream.writeShort(this.y);
				stream.writeInt(this.z);
			}
		}
	}

	public static class Origin implements ISerializable
	{
		public TileWrapper vectO = new TileWrapper();
		public TileWrapper[] vect = { new TileWrapper(), new TileWrapper(), new TileWrapper() };
		public int xMin, yMin, zMin, xMax, yMax, zMax;

		public boolean isSet()
		{
			return this.vectO.isSet();
		}

		@Override
		public void writeData(ByteBuf stream)
		{
			this.vectO.writeData(stream);
			for (TileWrapper tw : this.vect)
			{
				tw.writeData(stream);
			}
			stream.writeInt(this.xMin);
			stream.writeShort(this.yMin);
			stream.writeInt(this.zMin);
			stream.writeInt(this.xMax);
			stream.writeShort(this.yMax);
			stream.writeInt(this.zMax);
		}

		@Override
		public void readData(ByteBuf stream)
		{
			this.vectO.readData(stream);
			for (TileWrapper tw : this.vect)
			{
				tw.readData(stream);
			}
			this.xMin = stream.readInt();
			this.yMin = stream.readShort();
			this.zMin = stream.readInt();
			this.xMax = stream.readInt();
			this.yMax = stream.readShort();
			this.zMax = stream.readInt();
		}
	}

	public Origin origin = new Origin();
	public boolean showSignals = false;

	private Position initVectO;
	private Position[] initVect;
	private EntityBlock[] lasers;
	private EntityBlock[] signals;

	public void updateSignals()
	{
		if (!this.worldObj.isRemote)
		{
			this.showSignals = this.worldObj.isBlockIndirectlyGettingPowered(this.xCoord, this.yCoord, this.zCoord);
			this.sendNetworkUpdate();
		}
	}

	private void switchSignals()
	{
		if (this.signals != null)
		{
			for (EntityBlock b : this.signals)
			{
				if (b != null)
					CoreProxy.proxy.removeEntity(b);
			}
			this.signals = null;
		}
		if (this.showSignals)
		{
			this.signals = new EntityBlock[6];
			if (!this.origin.isSet() || !this.origin.vect[0].isSet())
			{
				this.signals[0] = LaserUtils.createLaser(this.worldObj, new Position(this.xCoord, this.yCoord, this.zCoord), new Position(this.xCoord + DefaultProps.MARKER_RANGE - 1, this.yCoord, this.zCoord), LaserKind.Blue);
				this.signals[1] = LaserUtils.createLaser(this.worldObj, new Position(this.xCoord - DefaultProps.MARKER_RANGE + 1, this.yCoord, this.zCoord), new Position(this.xCoord, this.yCoord, this.zCoord), LaserKind.Blue);
			}

			if (!this.origin.isSet() || !this.origin.vect[1].isSet())
			{
				this.signals[2] = LaserUtils.createLaser(this.worldObj, new Position(this.xCoord, this.yCoord, this.zCoord), new Position(this.xCoord, this.yCoord + DefaultProps.MARKER_RANGE - 1, this.zCoord), LaserKind.Blue);
				this.signals[3] = LaserUtils.createLaser(this.worldObj, new Position(this.xCoord, this.yCoord - DefaultProps.MARKER_RANGE + 1, this.zCoord), new Position(this.xCoord, this.yCoord, this.zCoord), LaserKind.Blue);
			}

			if (!this.origin.isSet() || !this.origin.vect[2].isSet())
			{
				this.signals[4] = LaserUtils.createLaser(this.worldObj, new Position(this.xCoord, this.yCoord, this.zCoord), new Position(this.xCoord, this.yCoord, this.zCoord + DefaultProps.MARKER_RANGE - 1), LaserKind.Blue);
				this.signals[5] = LaserUtils.createLaser(this.worldObj, new Position(this.xCoord, this.yCoord, this.zCoord - DefaultProps.MARKER_RANGE + 1), new Position(this.xCoord, this.yCoord, this.zCoord), LaserKind.Blue);
			}
		}
	}

	@Override
	public void initialize()
	{
		super.initialize();

		this.updateSignals();

		if (this.initVectO != null)
		{
			this.origin = new Origin();

			this.origin.vectO = new TileWrapper((int) this.initVectO.x, (int) this.initVectO.y, (int) this.initVectO.z);

			for (int i = 0; i < 3; ++i)
			{
				if (this.initVect[i] != null)
				{
					TileEntity tile = this.worldObj.getTileEntity((int) this.initVect[i].x, (int) this.initVect[i].y, (int) this.initVect[i].z);

					// TODO gamerforEA code start
					if (tile instanceof TileMarker)
						// TODO gamerforEA code end
						this.linkTo((TileMarker) tile, i);
				}
			}
		}
	}

	public void tryConnection()
	{
		if (this.worldObj.isRemote)
			return;

		for (int j = 0; j < 3; ++j)
		{
			if (!this.origin.isSet() || !this.origin.vect[j].isSet())
				this.setVect(j);
		}

		this.sendNetworkUpdate();
	}

	void setVect(int n)
	{
		int[] coords = new int[3];

		coords[0] = this.xCoord;
		coords[1] = this.yCoord;
		coords[2] = this.zCoord;

		if (!this.origin.isSet() || !this.origin.vect[n].isSet())
			for (int j = 1; j < DefaultProps.MARKER_RANGE; ++j)
			{
				coords[n] += j;

				Block block = this.worldObj.getBlock(coords[0], coords[1], coords[2]);

				if (block == BuildCraftCore.markerBlock)
				{
					TileEntity tile = this.worldObj.getTileEntity(coords[0], coords[1], coords[2]);

					// TODO gamerforEA code start
					if (tile instanceof TileMarker)
					// TODO gamerforEA code end
					{
						TileMarker marker = (TileMarker) tile;
						if (this.linkTo(marker, n))
							break;
					}
				}

				coords[n] -= j;
				coords[n] -= j;

				block = this.worldObj.getBlock(coords[0], coords[1], coords[2]);

				if (block == BuildCraftCore.markerBlock)
				{
					TileEntity tile = this.worldObj.getTileEntity(coords[0], coords[1], coords[2]);

					// TODO gamerforEA code start
					if (tile instanceof TileMarker)
					// TODO gamerforEA code end
					{
						TileMarker marker = (TileMarker) tile;
						if (this.linkTo(marker, n))
							break;
					}
				}

				coords[n] += j;
			}
	}

	private boolean linkTo(TileMarker marker, int n)
	{
		if (marker == null)
			return false;

		if (this.origin.isSet() && marker.origin.isSet())
			return false;

		if (!this.origin.isSet() && !marker.origin.isSet())
		{
			this.origin = new Origin();
			marker.origin = this.origin;
			this.origin.vectO = new TileWrapper(this.xCoord, this.yCoord, this.zCoord);
			this.origin.vect[n] = new TileWrapper(marker.xCoord, marker.yCoord, marker.zCoord);
		}
		else if (!this.origin.isSet())
		{
			this.origin = marker.origin;
			this.origin.vect[n] = new TileWrapper(this.xCoord, this.yCoord, this.zCoord);
		}
		else
		{
			marker.origin = this.origin;
			this.origin.vect[n] = new TileWrapper(marker.xCoord, marker.yCoord, marker.zCoord);
		}

		this.origin.vectO.getMarker(this.worldObj).createLasers();
		this.updateSignals();
		marker.updateSignals();

		return true;
	}

	private void createLasers()
	{
		if (this.lasers != null)
			for (EntityBlock entity : this.lasers)
			{
				if (entity != null)
					CoreProxy.proxy.removeEntity(entity);
			}

		this.lasers = new EntityBlock[12];
		Origin o = this.origin;

		if (!this.origin.vect[0].isSet())
		{
			o.xMin = this.origin.vectO.x;
			o.xMax = this.origin.vectO.x;
		}
		else if (this.origin.vect[0].x < this.xCoord)
		{
			o.xMin = this.origin.vect[0].x;
			o.xMax = this.xCoord;
		}
		else
		{
			o.xMin = this.xCoord;
			o.xMax = this.origin.vect[0].x;
		}

		if (!this.origin.vect[1].isSet())
		{
			o.yMin = this.origin.vectO.y;
			o.yMax = this.origin.vectO.y;
		}
		else if (this.origin.vect[1].y < this.yCoord)
		{
			o.yMin = this.origin.vect[1].y;
			o.yMax = this.yCoord;
		}
		else
		{
			o.yMin = this.yCoord;
			o.yMax = this.origin.vect[1].y;
		}

		if (!this.origin.vect[2].isSet())
		{
			o.zMin = this.origin.vectO.z;
			o.zMax = this.origin.vectO.z;
		}
		else if (this.origin.vect[2].z < this.zCoord)
		{
			o.zMin = this.origin.vect[2].z;
			o.zMax = this.zCoord;
		}
		else
		{
			o.zMin = this.zCoord;
			o.zMax = this.origin.vect[2].z;
		}

		this.lasers = LaserUtils.createLaserBox(this.worldObj, o.xMin, o.yMin, o.zMin, o.xMax, o.yMax, o.zMax, LaserKind.Red);
	}

	@Override
	public int xMin()
	{
		if (this.origin.isSet())
			return this.origin.xMin;
		return this.xCoord;
	}

	@Override
	public int yMin()
	{
		if (this.origin.isSet())
			return this.origin.yMin;
		return this.yCoord;
	}

	@Override
	public int zMin()
	{
		if (this.origin.isSet())
			return this.origin.zMin;
		return this.zCoord;
	}

	@Override
	public int xMax()
	{
		if (this.origin.isSet())
			return this.origin.xMax;
		return this.xCoord;
	}

	@Override
	public int yMax()
	{
		if (this.origin.isSet())
			return this.origin.yMax;
		return this.yCoord;
	}

	@Override
	public int zMax()
	{
		if (this.origin.isSet())
			return this.origin.zMax;
		return this.zCoord;
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
		TileMarker markerOrigin = null;

		if (this.origin.isSet())
		{
			markerOrigin = this.origin.vectO.getMarker(this.worldObj);

			Origin o = this.origin;

			if (markerOrigin != null && markerOrigin.lasers != null)
			{
				for (EntityBlock entity : markerOrigin.lasers)
				{
					if (entity != null)
						entity.setDead();
				}
				markerOrigin.lasers = null;
			}

			for (TileWrapper m : o.vect)
			{
				TileMarker mark = m.getMarker(this.worldObj);

				if (mark != null)
				{
					if (mark.lasers != null)
					{
						for (EntityBlock entity : mark.lasers)
						{
							if (entity != null)
								entity.setDead();
						}
						mark.lasers = null;
					}

					if (mark != this)
						mark.origin = new Origin();
				}
			}

			if (markerOrigin != this && markerOrigin != null)
				markerOrigin.origin = new Origin();

			for (TileWrapper wrapper : o.vect)
			{
				TileMarker mark = wrapper.getMarker(this.worldObj);

				if (mark != null)
					mark.updateSignals();
			}
			if (markerOrigin != null)
				markerOrigin.updateSignals();
		}

		if (this.signals != null)
			for (EntityBlock block : this.signals)
			{
				if (block != null)
					block.setDead();
			}

		this.signals = null;

		if (!this.worldObj.isRemote && markerOrigin != null && markerOrigin != this)
			markerOrigin.sendNetworkUpdate();
	}

	@Override
	public void removeFromWorld()
	{
		if (!this.origin.isSet())
			return;

		Origin o = this.origin;

		for (TileWrapper m : o.vect.clone())
		{
			if (m.isSet())
			{
				this.worldObj.setBlockToAir(m.x, m.y, m.z);

				BuildCraftCore.markerBlock.dropBlockAsItem(this.worldObj, m.x, m.y, m.z, 0, 0);
			}
		}

		this.worldObj.setBlockToAir(o.vectO.x, o.vectO.y, o.vectO.z);

		BuildCraftCore.markerBlock.dropBlockAsItem(this.worldObj, o.vectO.x, o.vectO.y, o.vectO.z, 0, 0);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbttagcompound)
	{
		super.readFromNBT(nbttagcompound);

		if (nbttagcompound.hasKey("vectO"))
		{
			this.initVectO = new Position(nbttagcompound.getCompoundTag("vectO"));
			this.initVect = new Position[3];

			for (int i = 0; i < 3; ++i)
			{
				if (nbttagcompound.hasKey("vect" + i))
					this.initVect[i] = new Position(nbttagcompound.getCompoundTag("vect" + i));
			}
		}
	}

	@Override
	public void writeToNBT(NBTTagCompound nbttagcompound)
	{
		super.writeToNBT(nbttagcompound);

		if (this.origin.isSet() && this.origin.vectO.getMarker(this.worldObj) == this)
		{
			NBTTagCompound vectO = new NBTTagCompound();

			new Position(this.origin.vectO.getMarker(this.worldObj)).writeToNBT(vectO);
			nbttagcompound.setTag("vectO", vectO);

			for (int i = 0; i < 3; ++i)
			{
				if (this.origin.vect[i].isSet())
				{
					NBTTagCompound vect = new NBTTagCompound();
					new Position(this.origin.vect[i].x, this.origin.vect[i].y, this.origin.vect[i].z).writeToNBT(vect);
					nbttagcompound.setTag("vect" + i, vect);
				}
			}

		}
	}

	@Override
	public void writeData(ByteBuf stream)
	{
		this.origin.writeData(stream);
		stream.writeBoolean(this.showSignals);
	}

	@Override
	public void readData(ByteBuf stream)
	{
		this.origin.readData(stream);
		this.showSignals = stream.readBoolean();

		this.switchSignals();

		if (this.origin.vectO.isSet() && this.origin.vectO.getMarker(this.worldObj) != null)
		{
			this.origin.vectO.getMarker(this.worldObj).updateSignals();

			for (TileWrapper w : this.origin.vect)
			{
				TileMarker m = w.getMarker(this.worldObj);

				if (m != null)
					m.updateSignals();
			}
		}

		this.createLasers();
	}

	@Override
	public boolean isValidFromLocation(int x, int y, int z)
	{
		// Rules:
		// - one or two, but not three, of the coordinates must be equal to the marker's location
		// - one of the coordinates must be either -1 or 1 away
		// - it must be physically touching the box
		// - however, it cannot be INSIDE the box
		int equal = (x == this.xCoord ? 1 : 0) + (y == this.yCoord ? 1 : 0) + (z == this.zCoord ? 1 : 0);
		int touching = 0;

		if (equal == 0 || equal == 3)
			return false;

		if (x < this.xMin() - 1 || x > this.xMax() + 1 || y < this.yMin() - 1 || y > this.yMax() + 1 || z < this.zMin() - 1 || z > this.zMax() + 1)
			return false;

		if (x >= this.xMin() && x <= this.xMax() && y >= this.yMin() && y <= this.yMax() && z >= this.zMin() && z <= this.zMax())
			return false;

		if (this.xMin() - x == 1 || x - this.xMax() == 1)
			touching++;

		if (this.yMin() - y == 1 || y - this.yMax() == 1)
			touching++;

		if (this.zMin() - z == 1 || z - this.zMax() == 1)
			touching++;

		return touching == 1;
	}
}
