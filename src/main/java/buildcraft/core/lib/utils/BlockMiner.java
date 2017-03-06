package buildcraft.core.lib.utils;

import java.util.List;

import com.gamerforea.buildcraft.ModUtils;
import com.gamerforea.eventhelper.util.EventUtils;

import buildcraft.BuildCraftCore;
import buildcraft.core.lib.block.TileBuildCraft;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

public class BlockMiner
{
	protected final World world;
	protected final TileEntity owner;
	protected final int x, y, z, minerId;

	private boolean hasMined, hasFailed;
	private int energyRequired, energyAccepted;

	public BlockMiner(World world, TileEntity owner, int x, int y, int z)
	{
		this.world = world;
		this.owner = owner;
		this.x = x;
		this.y = y;
		this.z = z;
		this.minerId = world.rand.nextInt();
	}

	public boolean hasMined()
	{
		return this.hasMined;
	}

	public boolean hasFailed()
	{
		return this.hasFailed;
	}

	public void mineStack(ItemStack stack)
	{
		// First, try to add to a nearby chest
		stack.stackSize -= Utils.addToRandomInventoryAround(this.owner.getWorldObj(), this.owner.xCoord, this.owner.yCoord, this.owner.zCoord, stack);

		// Second, try to add to adjacent pipes
		if (stack.stackSize > 0)
			stack.stackSize -= Utils.addToRandomInjectableAround(this.owner.getWorldObj(), this.owner.xCoord, this.owner.yCoord, this.owner.zCoord, ForgeDirection.UNKNOWN, stack);

		// Lastly, throw the object away
		if (stack.stackSize > 0)
		{
			float f = this.world.rand.nextFloat() * 0.8F + 0.1F;
			float f1 = this.world.rand.nextFloat() * 0.8F + 0.1F;
			float f2 = this.world.rand.nextFloat() * 0.8F + 0.1F;

			EntityItem entityitem = new EntityItem(this.owner.getWorldObj(), this.owner.xCoord + f, this.owner.yCoord + f1 + 0.5F, this.owner.zCoord + f2, stack);

			entityitem.lifespan = BuildCraftCore.itemLifespan * 20;
			entityitem.delayBeforeCanPickup = 10;

			float f3 = 0.05F;
			entityitem.motionX = (float) this.world.rand.nextGaussian() * f3;
			entityitem.motionY = (float) this.world.rand.nextGaussian() * f3 + 1.0F;
			entityitem.motionZ = (float) this.world.rand.nextGaussian() * f3;
			this.owner.getWorldObj().spawnEntityInWorld(entityitem);
		}
	}

	public void invalidate()
	{
		this.world.destroyBlockInWorldPartially(this.minerId, this.x, this.y, this.z, -1);
	}

	public int acceptEnergy(int offeredAmount)
	{
		if (BlockUtils.isUnbreakableBlock(this.world, this.x, this.y, this.z))
		{
			this.hasFailed = true;
			return 0;
		}

		this.energyRequired = BlockUtils.computeBlockBreakEnergy(this.world, this.x, this.y, this.z);

		int usedAmount = MathUtils.clamp(offeredAmount, 0, Math.max(0, this.energyRequired - this.energyAccepted));
		this.energyAccepted += usedAmount;

		if (this.energyAccepted >= this.energyRequired)
		{
			this.world.destroyBlockInWorldPartially(this.minerId, this.x, this.y, this.z, -1);

			this.hasMined = true;

			/* TODO gamerforEA code replace, old code:
			BlockEvent.BreakEvent breakEvent = new BlockEvent.BreakEvent(this.x, this.y, this.z, this.world, block, meta, CoreProxy.proxy.getBuildCraftPlayer((WorldServer) this.owner.getWorldObj(), this.owner.xCoord, this.owner.yCoord, this.owner.zCoord).get());
			MinecraftForge.EVENT_BUS.post(breakEvent);

			if (!breakEvent.isCanceled()) */
			EntityPlayer player = this.owner instanceof TileBuildCraft ? ((TileBuildCraft) this.owner).fake.get() : ModUtils.getModFake(this.world);
			if (!EventUtils.cantBreak(player, this.x, this.y, this.z))
			// TODO gamerforEA code end
			{
				Block block = this.world.getBlock(this.x, this.y, this.z);
				int meta = this.world.getBlockMetadata(this.x, this.y, this.z);

				List<ItemStack> stacks = BlockUtils.getItemStackFromBlock((WorldServer) this.world, this.x, this.y, this.z);

				if (stacks != null)
					for (ItemStack s : stacks)
						if (s != null)
							this.mineStack(s);

				this.world.playAuxSFXAtEntity(null, 2001, this.x, this.y, this.z, Block.getIdFromBlock(block) + (meta << 12));

				this.world.setBlockToAir(this.x, this.y, this.z);
			}
			else
				this.hasFailed = true;
		}
		else
			this.world.destroyBlockInWorldPartially(this.minerId, this.x, this.y, this.z, MathUtils.clamp((int) Math.floor(this.energyAccepted * 10 / this.energyRequired), 0, 9));
		return usedAmount;
	}
}
