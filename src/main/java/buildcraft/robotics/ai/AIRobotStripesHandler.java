/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import buildcraft.api.core.BlockIndex;
import buildcraft.api.core.Position;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.api.transport.IStripesActivator;
import buildcraft.api.transport.IStripesHandler;
import buildcraft.api.transport.IStripesHandler.StripesHandlerType;
import buildcraft.api.transport.PipeManager;
import buildcraft.core.lib.inventory.InvUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;

public class AIRobotStripesHandler extends AIRobot implements IStripesActivator
{
	private BlockIndex useToBlock;
	private int useCycles = 0;

	public AIRobotStripesHandler(EntityRobotBase iRobot)
	{
		super(iRobot);
	}

	public AIRobotStripesHandler(EntityRobotBase iRobot, BlockIndex index)
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
		if (this.useToBlock == null)
		{
			this.setSuccess(false);
			this.terminate();
			return;
		}

		this.useCycles++;

		if (this.useCycles > 60)
		{
			ItemStack stack = this.robot.getHeldItem();

			ForgeDirection direction = ForgeDirection.NORTH;

			Position p = new Position(this.useToBlock.x, this.useToBlock.y, this.useToBlock.z);

			// TODO gamerforEA code replace, replace, old code:
			// EntityPlayer player = CoreProxy.proxy.getBuildCraftPlayer((WorldServer) this.robot.worldObj, (int) p.x, (int) p.y, (int) p.z).get();
			EntityPlayer player = this.robot.fake.get();
			player.posX = (int) p.x;
			player.posY = (int) p.y;
			player.posZ = (int) p.z;
			// TODO gamerforEA code end

			player.rotationPitch = 0;
			player.rotationYaw = 180;

			for (IStripesHandler handler : PipeManager.stripesHandlers)
			{
				if (handler.getType() == StripesHandlerType.ITEM_USE && handler.shouldHandle(stack))
				{
					if (handler.handle(this.robot.worldObj, (int) p.x, (int) p.y, (int) p.z, direction, stack, player, this))
					{
						this.robot.setItemInUse(null);
						this.terminate();
						return;
					}
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
		return 15;
	}

	@Override
	public void sendItem(ItemStack stack, ForgeDirection direction)
	{
		InvUtils.dropItems(this.robot.worldObj, stack, (int) Math.floor(this.robot.posX), (int) Math.floor(this.robot.posY), (int) Math.floor(this.robot.posZ));
	}

	@Override
	public void dropItem(ItemStack stack, ForgeDirection direction)
	{
		this.sendItem(stack, direction);
	}
}
