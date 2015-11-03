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
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.robotics.EntityRobot;
import net.minecraft.entity.Entity;

public class AIRobotAttack extends AIRobot
{
	private Entity target;

	private int delay = 10;

	public AIRobotAttack(EntityRobotBase iRobot)
	{
		super(iRobot);
	}

	public AIRobotAttack(EntityRobotBase iRobot, Entity iTarget)
	{
		this(iRobot);

		this.target = iTarget;
	}

	@Override
	public void preempt(AIRobot ai)
	{
		if (ai instanceof AIRobotGotoBlock)
			// target may become null in the event of a load. In that case, just
			// go to the expected location.
			if (this.target != null && this.robot.getDistanceToEntity(this.target) <= 2.0)
			{
				this.abortDelegateAI();
				this.robot.setItemActive(true);
			}
	}

	@Override
	public void update()
	{
		if (this.target == null || this.target.isDead)
		{
			this.terminate();
			return;
		}

		if (this.robot.getDistanceToEntity(this.target) > 2.0)
		{
			this.startDelegateAI(new AIRobotGotoBlock(this.robot, (int) Math.floor(this.target.posX), (int) Math.floor(this.target.posY), (int) Math.floor(this.target.posZ)));
			this.robot.setItemActive(false);

			return;
		}

		this.delay++;

		if (this.delay > 20)
		{
			this.delay = 0;

			// TODO gamerforEA code start
			if (EventUtils.cantDamage(this.robot.fake.getPlayer(), this.target))
				return;
			// TODO gamerforEA code end

			((EntityRobot) this.robot).attackTargetEntityWithCurrentItem(this.target);
			this.robot.aimItemAt((int) Math.floor(this.target.posX), (int) Math.floor(this.target.posY), (int) Math.floor(this.target.posZ));
		}
	}

	@Override
	public void end()
	{
		this.robot.setItemActive(false);
	}

	@Override
	public void delegateAIEnded(AIRobot ai)
	{
		if (ai instanceof AIRobotGotoBlock)
		{
			if (!ai.success())
				this.robot.unreachableEntityDetected(this.target);
			this.terminate();
		}
	}

	@Override
	public int getEnergyCost()
	{
		return BuilderAPI.BREAK_ENERGY * 2 / 20;
	}
}