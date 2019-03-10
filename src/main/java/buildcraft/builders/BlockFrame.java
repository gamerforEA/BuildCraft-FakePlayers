/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.builders;

import buildcraft.api.core.BlockIndex;
import buildcraft.core.CoreConstants;
import buildcraft.core.internal.IFramePipeConnection;
import buildcraft.core.lib.utils.Utils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;

public class BlockFrame extends Block implements IFramePipeConnection
{
	private static final ThreadLocal<Boolean> isRemovingFrames = new ThreadLocal<>();

	public BlockFrame()
	{
		super(Material.glass);
		this.setHardness(0.5F);
	}

	@Override
	public void breakBlock(World world, int x, int y, int z, Block block, int meta)
	{
		if (world.isRemote)
			return;

		if (isRemovingFrames.get() == null)
			this.removeNeighboringFrames(world, x, y, z);
	}

	public void removeNeighboringFrames(World world, int x, int y, int z)
	{
		isRemovingFrames.set(true);

		Set<BlockIndex> frameCoords = new ConcurrentSkipListSet<>();
		frameCoords.add(new BlockIndex(x, y, z));

		while (frameCoords.size() > 0)
		{
			Iterator<BlockIndex> frameCoordIterator = frameCoords.iterator();
			while (frameCoordIterator.hasNext())
			{
				BlockIndex i = frameCoordIterator.next();
				for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS)
				{
					Block nBlock = world.getBlock(i.x + dir.offsetX, i.y + dir.offsetY, i.z + dir.offsetZ);
					if (nBlock == this)
					{
						world.setBlockToAir(i.x + dir.offsetX, i.y + dir.offsetY, i.z + dir.offsetZ);
						frameCoords.add(new BlockIndex(i.x + dir.offsetX, i.y + dir.offsetY, i.z + dir.offsetZ));
					}
				}
				frameCoordIterator.remove();
			}
		}

		isRemovingFrames.remove();
	}

	@Override
	public boolean isOpaqueCube()
	{
		return false;
	}

	@Override
	public boolean renderAsNormalBlock()
	{
		return false;
	}

	@Override
	public Item getItemDropped(int i, Random random, int j)
	{
		return null;
	}

	@Override
	public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int metadata, int fortune)
	{
		return new ArrayList<>();
	}

	@Override
	public int getRenderType()
	{
		return BuilderProxy.frameRenderId;
	}

	@Override
	public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int i, int j, int k)
	{
		float xMin = CoreConstants.PIPE_MIN_POS, xMax = CoreConstants.PIPE_MAX_POS, yMin = CoreConstants.PIPE_MIN_POS, yMax = CoreConstants.PIPE_MAX_POS, zMin = CoreConstants.PIPE_MIN_POS, zMax = CoreConstants.PIPE_MAX_POS;

		if (Utils.checkLegacyPipesConnections(world, i, j, k, i - 1, j, k))
			xMin = 0.0F;

		if (Utils.checkLegacyPipesConnections(world, i, j, k, i + 1, j, k))
			xMax = 1.0F;

		if (Utils.checkLegacyPipesConnections(world, i, j, k, i, j - 1, k))
			yMin = 0.0F;

		if (Utils.checkLegacyPipesConnections(world, i, j, k, i, j + 1, k))
			yMax = 1.0F;

		if (Utils.checkLegacyPipesConnections(world, i, j, k, i, j, k - 1))
			zMin = 0.0F;

		if (Utils.checkLegacyPipesConnections(world, i, j, k, i, j, k + 1))
			zMax = 1.0F;

		return AxisAlignedBB.getBoundingBox((double) i + xMin, (double) j + yMin, (double) k + zMin, (double) i + xMax, (double) j + yMax, (double) k + zMax);
	}

	@Override
	@SuppressWarnings({ "all" })
	// @Override (client only)
	public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int i, int j, int k)
	{
		return getCollisionBoundingBoxFromPool(world, i, j, k);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void addCollisionBoxesToList(World world, int i, int j, int k, AxisAlignedBB axisalignedbb, List arraylist, Entity par7Entity)
	{
		this.setBlockBounds(CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MAX_POS, CoreConstants.PIPE_MAX_POS, CoreConstants.PIPE_MAX_POS);
		super.addCollisionBoxesToList(world, i, j, k, axisalignedbb, arraylist, par7Entity);

		if (Utils.checkLegacyPipesConnections(world, i, j, k, i - 1, j, k))
		{
			this.setBlockBounds(0.0F, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MAX_POS, CoreConstants.PIPE_MAX_POS, CoreConstants.PIPE_MAX_POS);
			super.addCollisionBoxesToList(world, i, j, k, axisalignedbb, arraylist, par7Entity);
		}

		if (Utils.checkLegacyPipesConnections(world, i, j, k, i + 1, j, k))
		{
			this.setBlockBounds(CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, 1.0F, CoreConstants.PIPE_MAX_POS, CoreConstants.PIPE_MAX_POS);
			super.addCollisionBoxesToList(world, i, j, k, axisalignedbb, arraylist, par7Entity);
		}

		if (Utils.checkLegacyPipesConnections(world, i, j, k, i, j - 1, k))
		{
			this.setBlockBounds(CoreConstants.PIPE_MIN_POS, 0.0F, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MAX_POS, CoreConstants.PIPE_MAX_POS, CoreConstants.PIPE_MAX_POS);
			super.addCollisionBoxesToList(world, i, j, k, axisalignedbb, arraylist, par7Entity);
		}

		if (Utils.checkLegacyPipesConnections(world, i, j, k, i, j + 1, k))
		{
			this.setBlockBounds(CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MAX_POS, 1.0F, CoreConstants.PIPE_MAX_POS);
			super.addCollisionBoxesToList(world, i, j, k, axisalignedbb, arraylist, par7Entity);
		}

		if (Utils.checkLegacyPipesConnections(world, i, j, k, i, j, k - 1))
		{
			this.setBlockBounds(CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, 0.0F, CoreConstants.PIPE_MAX_POS, CoreConstants.PIPE_MAX_POS, CoreConstants.PIPE_MAX_POS);
			super.addCollisionBoxesToList(world, i, j, k, axisalignedbb, arraylist, par7Entity);
		}

		if (Utils.checkLegacyPipesConnections(world, i, j, k, i, j, k + 1))
		{
			this.setBlockBounds(CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MIN_POS, CoreConstants.PIPE_MAX_POS, CoreConstants.PIPE_MAX_POS, 1.0F);
			super.addCollisionBoxesToList(world, i, j, k, axisalignedbb, arraylist, par7Entity);
		}

		this.setBlockBounds(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
	}

	@Override
	public MovingObjectPosition collisionRayTrace(World world, int i, int j, int k, Vec3 vec3d, Vec3 vec3d1)
	{
		float xMin = CoreConstants.PIPE_MIN_POS, xMax = CoreConstants.PIPE_MAX_POS, yMin = CoreConstants.PIPE_MIN_POS, yMax = CoreConstants.PIPE_MAX_POS, zMin = CoreConstants.PIPE_MIN_POS, zMax = CoreConstants.PIPE_MAX_POS;

		if (Utils.checkLegacyPipesConnections(world, i, j, k, i - 1, j, k))
			xMin = 0.0F;

		if (Utils.checkLegacyPipesConnections(world, i, j, k, i + 1, j, k))
			xMax = 1.0F;

		if (Utils.checkLegacyPipesConnections(world, i, j, k, i, j - 1, k))
			yMin = 0.0F;

		if (Utils.checkLegacyPipesConnections(world, i, j, k, i, j + 1, k))
			yMax = 1.0F;

		if (Utils.checkLegacyPipesConnections(world, i, j, k, i, j, k - 1))
			zMin = 0.0F;

		if (Utils.checkLegacyPipesConnections(world, i, j, k, i, j, k + 1))
			zMax = 1.0F;

		this.setBlockBounds(xMin, yMin, zMin, xMax, yMax, zMax);

		MovingObjectPosition r = super.collisionRayTrace(world, i, j, k, vec3d, vec3d1);

		this.setBlockBounds(0, 0, 0, 1, 1, 1);

		return r;
	}

	@Override
	public boolean isPipeConnected(IBlockAccess blockAccess, int x1, int y1, int z1, int x2, int y2, int z2)
	{
		return blockAccess.getBlock(x2, y2, z2) == this;
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public void getSubBlocks(Item item, CreativeTabs tab, List list)
	{
		list.add(new ItemStack(this));
	}

	@Override
	// TODO gamerforEA code start
	@SideOnly(Side.CLIENT)
	// TODO gamerforEA code end
	public void registerBlockIcons(IIconRegister register)
	{
		this.blockIcon = register.registerIcon("buildcraftbuilders:frameBlock/default");
	}
}
