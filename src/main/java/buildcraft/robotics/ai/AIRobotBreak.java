/**
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import com.gamerforea.eventhelper.util.EventUtils;

import buildcraft.api.blueprints.BuilderAPI;
import buildcraft.api.core.BlockIndex;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.core.lib.utils.BlockUtils;
import net.minecraft.block.Block;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.ForgeEventFactory;

public class AIRobotBreak extends AIRobot
{
	private BlockIndex blockToBreak;
	private float blockDamage = 0;

	private Block block;
	private int meta;
	private float hardness;
	private float speed;

	public AIRobotBreak(EntityRobotBase iRobot)
	{
		super(iRobot);
	}

	public AIRobotBreak(EntityRobotBase iRobot, BlockIndex iBlockToBreak)
	{
		this(iRobot);

		this.blockToBreak = iBlockToBreak;
	}

	@Override
	public void start()
	{
		this.robot.aimItemAt(this.blockToBreak.x, this.blockToBreak.y, this.blockToBreak.z);

		this.robot.setItemActive(true);
		this.block = this.robot.worldObj.getBlock(this.blockToBreak.x, this.blockToBreak.y, this.blockToBreak.z);
		this.meta = this.robot.worldObj.getBlockMetadata(this.blockToBreak.x, this.blockToBreak.y, this.blockToBreak.z);
		this.hardness = BlockUtils.getBlockHardnessMining(this.robot.worldObj, this.blockToBreak.x, this.blockToBreak.y, this.blockToBreak.z, this.block, this.robot.getHeldItem());
		this.speed = this.getBreakSpeed(this.robot, this.robot.getHeldItem(), this.block, this.meta);
	}

	@Override
	public void update()
	{
		if (this.block == null)
		{
			this.block = this.robot.worldObj.getBlock(this.blockToBreak.x, this.blockToBreak.y, this.blockToBreak.z);
			if (this.block == null)
			{
				this.setSuccess(false);
				this.terminate();
				return;
			}
			this.meta = this.robot.worldObj.getBlockMetadata(this.blockToBreak.x, this.blockToBreak.y, this.blockToBreak.z);
			this.hardness = BlockUtils.getBlockHardnessMining(this.robot.worldObj, this.blockToBreak.x, this.blockToBreak.y, this.blockToBreak.z, this.block, this.robot.getHeldItem());
			this.speed = this.getBreakSpeed(this.robot, this.robot.getHeldItem(), this.block, this.meta);
		}

		if (this.block.isAir(this.robot.worldObj, this.blockToBreak.x, this.blockToBreak.y, this.blockToBreak.z) || this.hardness < 0)
		{
			this.setSuccess(false);
			this.terminate();
			return;
		}

		if (this.hardness != 0)
			this.blockDamage += this.speed / this.hardness / 30F;
		else
			// Instantly break the block
			this.blockDamage = 1.1F;

		if (this.blockDamage > 1.0F)
		{
			this.robot.worldObj.destroyBlockInWorldPartially(this.robot.getEntityId(), this.blockToBreak.x, this.blockToBreak.y, this.blockToBreak.z, -1);
			this.blockDamage = 0;

			// TODO gamerforEA add condition [1], add EntityPlayer parameter [2]
			if (!EventUtils.cantBreak(this.robot.fake.getPlayer(), this.blockToBreak.x, this.blockToBreak.y, this.blockToBreak.z) && BlockUtils.harvestBlock(this.robot.fake.getPlayer(), (WorldServer) this.robot.worldObj, this.blockToBreak.x, this.blockToBreak.y, this.blockToBreak.z, this.robot.getHeldItem()))
			{
				this.robot.worldObj.playAuxSFXAtEntity(null, 2001, this.blockToBreak.x, this.blockToBreak.y, this.blockToBreak.z, Block.getIdFromBlock(this.block) + (this.meta << 12));

				if (this.robot.getHeldItem() != null)
				{
					this.robot.getHeldItem().getItem().onBlockDestroyed(this.robot.getHeldItem(), this.robot.worldObj, this.block, this.blockToBreak.x, this.blockToBreak.y, this.blockToBreak.z, this.robot);

					if (this.robot.getHeldItem().stackSize == 0)
						this.robot.setItemInUse(null);
				}
			}
			else
				this.setSuccess(false);

			this.terminate();
		}
		else
			this.robot.worldObj.destroyBlockInWorldPartially(this.robot.getEntityId(), this.blockToBreak.x, this.blockToBreak.y, this.blockToBreak.z, (int) (this.blockDamage * 10.0F) - 1);

	}

	@Override
	public void end()
	{
		this.robot.setItemActive(false);
		this.robot.worldObj.destroyBlockInWorldPartially(this.robot.getEntityId(), this.blockToBreak.x, this.blockToBreak.y, this.blockToBreak.z, -1);
	}

	private float getBreakSpeed(EntityRobotBase robot, ItemStack usingItem, Block block, int meta)
	{
		ItemStack stack = usingItem;
		float f = stack == null ? 1.0F : stack.getItem().getDigSpeed(stack, block, meta);

		if (f > 1.0F)
		{
			int i = EnchantmentHelper.getEfficiencyModifier(robot);

			if (i > 0)
			{
				float f1 = i * i + 1;

				boolean canHarvest = ForgeHooks.canToolHarvestBlock(block, meta, usingItem);

				if (!canHarvest && f <= 1.0F)
					f += f1 * 0.08F;
				else
					f += f1;
			}
		}

		// TODO gamerforEA add EntityPlayer parameter
		f = ForgeEventFactory.getBreakSpeed(BlockUtils.getFakePlayerWithTool(this.robot.fake.getPlayer(), (WorldServer) robot.worldObj, this.blockToBreak.x, this.blockToBreak.y, this.blockToBreak.z, robot.getHeldItem()), block, meta, f, this.blockToBreak.x, this.blockToBreak.y, this.blockToBreak.z);
		return f < 0 ? 0 : f;
	}

	@Override
	public int getEnergyCost()
	{
		return (int) Math.ceil((float) BuilderAPI.BREAK_ENERGY * 2 / 30.0F);
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

		if (this.blockToBreak != null)
		{
			NBTTagCompound sub = new NBTTagCompound();
			this.blockToBreak.writeTo(sub);
			nbt.setTag("blockToBreak", sub);
		}
	}

	@Override
	public void loadSelfFromNBT(NBTTagCompound nbt)
	{
		super.loadSelfFromNBT(nbt);

		if (nbt.hasKey("blockToBreak"))
			this.blockToBreak = new BlockIndex(nbt.getCompoundTag("blockToBreak"));
	}
}
