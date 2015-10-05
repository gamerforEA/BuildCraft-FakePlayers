/**
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 *
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import com.gamerforea.eventhelper.util.EventUtils;

import buildcraft.api.core.BlockIndex;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.core.lib.utils.BlockUtils;
import buildcraft.core.proxy.CoreProxy;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

public class AIRobotUseToolOnBlock extends AIRobot
{
	private BlockIndex useToBlock;
	private int useCycles = 0;

	public AIRobotUseToolOnBlock(EntityRobotBase iRobot)
	{
		super(iRobot);
	}

	public AIRobotUseToolOnBlock(EntityRobotBase iRobot, BlockIndex index)
	{
		this(iRobot);

		this.useToBlock = index;
	}

	@Override
	public void start()
	{
		this.robot.aimItemAt(this.useToBlock.x, this.useToBlock.y, this.useToBlock.z);
		this.robot.setItemActive(true);
	}

	@Override
	public void update()
	{
		this.useCycles++;

		if (this.useCycles > 40)
		{
			ItemStack stack = this.robot.getHeldItem();

			EntityPlayer player = CoreProxy.proxy.getBuildCraftPlayer((WorldServer) this.robot.worldObj).get();
			// TODO gamerforEA add condition [1]
			if (!EventUtils.cantBreak(this.robot.fake.getPlayer(), this.useToBlock.x, this.useToBlock.y, this.useToBlock.z) && BlockUtils.useItemOnBlock(this.robot.worldObj, player, stack, this.useToBlock.x, this.useToBlock.y, this.useToBlock.z, ForgeDirection.UP))
			{
				if (this.robot.getHeldItem().isItemStackDamageable())
				{
					this.robot.getHeldItem().damageItem(1, this.robot);

					if (this.robot.getHeldItem().getItemDamage() >= this.robot.getHeldItem().getMaxDamage())
						this.robot.setItemInUse(null);
				}
				else
					this.robot.setItemInUse(null);
			}
			else
			{
				this.setSuccess(false);
				if (!this.robot.getHeldItem().isItemStackDamageable())
				{
					BlockUtils.dropItem((WorldServer) this.robot.worldObj, MathHelper.floor_double(this.robot.posX), MathHelper.floor_double(this.robot.posY), MathHelper.floor_double(this.robot.posZ), 6000, stack);
					this.robot.setItemInUse(null);
				}
			}

			this.terminate();
		}
	}

	@Override
	public void end()
	{
		this.robot.setItemActive(false);
	}

	@Override
	public int getEnergyCost()
	{
		return 8;
	}

	@Override
	public boolean canLoadFromNBT()
	{
		return true;
	}

	@Override
	public void writeSelfToNBT(NBTTagCompound nbt)
	{
		super.writeSelfToNBT(nbt);

		if (this.useToBlock != null)
		{
			NBTTagCompound sub = new NBTTagCompound();
			this.useToBlock.writeTo(sub);
			nbt.setTag("blockFound", sub);
		}
	}

	@Override
	public void loadSelfFromNBT(NBTTagCompound nbt)
	{
		super.loadSelfFromNBT(nbt);

		if (nbt.hasKey("blockFound"))
			this.useToBlock = new BlockIndex(nbt.getCompoundTag("blockFound"));
	}
}