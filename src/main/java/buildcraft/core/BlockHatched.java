package buildcraft.core;

import buildcraft.BuildCraftCore;
import buildcraft.api.transport.IPipeTile;
import buildcraft.core.lib.block.BlockBuildCraft;
import buildcraft.core.lib.utils.ResourceUtils;
import buildcraft.core.lib.utils.Utils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

public abstract class BlockHatched extends BlockBuildCraft
{
	private IIcon itemHatch;

	protected BlockHatched(Material material)
	{
		super(material);

		this.setRotatable(true);
		this.setPassCount(2);
	}

	@Override
	public int getLightValue(IBlockAccess world, int x, int y, int z)
	{
		return 1;
	}

	@Override
	// TODO gamerforEA code start
	@SideOnly(Side.CLIENT)
	// TODO gamerforEA code end
	public void registerBlockIcons(IIconRegister register)
	{
		super.registerBlockIcons(register);
		String base = ResourceUtils.getObjectPrefix(Block.blockRegistry.getNameForObject(this));
		this.itemHatch = register.registerIcon(base + "/item_hatch");
	}

	@Override
	public IIcon getIcon(IBlockAccess access, int x, int y, int z, int side)
	{
		// The quarry's pipe connection method has no idea about "sides".
		if (this.renderPass == 1)
			return Utils.isPipeConnected(access, x, y, z, ForgeDirection.getOrientation(side), IPipeTile.PipeType.ITEM) ? this.itemHatch : BuildCraftCore.transparentTexture;
		else
			return super.getIcon(access, x, y, z, side);
	}

	@Override
	public IIcon getIconAbsolute(IBlockAccess access, int x, int y, int z, int side, int meta)
	{
		if (this.renderPass == 0)
			return super.getIconAbsolute(access, x, y, z, side, meta);
		else
			return null;
	}

	@Override
	public IIcon getIconAbsolute(int side, int meta)
	{
		if (this.renderPass == 0)
			return super.getIconAbsolute(side, meta);
		else
			return side == 1 ? this.itemHatch : null;
	}

	@Override
	public boolean renderAsNormalBlock()
	{
		return false;
	}
}
