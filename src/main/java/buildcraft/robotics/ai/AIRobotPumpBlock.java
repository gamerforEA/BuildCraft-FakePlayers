/**
 * Copyright (c) 2011-2015, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import buildcraft.api.core.BlockIndex;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.core.lib.utils.BlockUtils;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;

public class AIRobotPumpBlock extends AIRobot
{
	private BlockIndex blockToPump;
	private long waited = 0;
	private int pumped = 0;

	public AIRobotPumpBlock(EntityRobotBase iRobot)
	{
		super(iRobot);
	}

	public AIRobotPumpBlock(EntityRobotBase iRobot, BlockIndex iBlockToPump)
	{
		this(iRobot);

		this.blockToPump = iBlockToPump;
	}

	@Override
	public void start()
	{
		this.robot.aimItemAt(this.blockToPump.x, this.blockToPump.y, this.blockToPump.z);
	}

	@Override
	public void preempt(AIRobot ai)
	{
		super.preempt(ai);
	}

	@Override
	public void update()
	{
		if (this.waited < 40)
			this.waited++;
		else
		{
			FluidStack fluidStack = BlockUtils.drainBlock(this.robot.worldObj, this.blockToPump.x, this.blockToPump.y, this.blockToPump.z, false);
			// TODO gamerforEA add condition [2]
			if (fluidStack != null && !this.robot.fake.cantBreak(this.blockToPump.x, this.blockToPump.y, this.blockToPump.z))
				if (this.robot.fill(ForgeDirection.UNKNOWN, fluidStack, true) > 0)
					BlockUtils.drainBlock(this.robot.worldObj, this.blockToPump.x, this.blockToPump.y, this.blockToPump.z, true);
			this.terminate();
		}

	}

	@Override
	public int getEnergyCost()
	{
		return 5;
	}

	@Override
	public boolean success()
	{
		return this.pumped > 0;
	}
}
