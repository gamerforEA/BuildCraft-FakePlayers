/**
 * Copyright (c) 2011-2017, SpaceToad and the BuildCraft Team
 * http://www.mod-buildcraft.com
 * <p/>
 * BuildCraft is distributed under the terms of the Minecraft Mod Public
 * License 1.0, or MMPL. Please check the contents of the license located in
 * http://www.mod-buildcraft.com/MMPL-1.0.txt
 */
package buildcraft.robotics.ai;

import buildcraft.api.core.IZone;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.DockingStation;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.robotics.IStationFilter;
import buildcraft.robotics.statements.ActionStationForbidRobot;

public class AIRobotSearchStation extends AIRobot
{

	public DockingStation targetStation;
	private IStationFilter filter;
	private IZone zone;

	public AIRobotSearchStation(EntityRobotBase iRobot)
	{
		super(iRobot);
	}

	public AIRobotSearchStation(EntityRobotBase iRobot, IStationFilter iFilter, IZone iZone)
	{
		this(iRobot);

		this.filter = iFilter;
		this.zone = iZone;
	}

	@Override
	public void start()
	{
		if (this.robot.getDockingStation() != null && this.filter.matches(this.robot.getDockingStation()))
		{
			this.targetStation = this.robot.getDockingStation();
			this.terminate();
			return;
		}

		double potentialStationDistance = Float.MAX_VALUE;
		DockingStation potentialStation = null;

		for (DockingStation station : this.robot.getRegistry().getStations())
		{
			if (!station.isInitialized())
				continue;

			if (station.isTaken() && station.robotIdTaking() != this.robot.getRobotId())
				continue;

			if (this.zone != null && !this.zone.contains(station.x(), station.y(), station.z()))
				continue;

			if (this.filter.matches(station))
			{
				if (ActionStationForbidRobot.isForbidden(station, this.robot))
					continue;

				double dx = this.robot.posX - station.x();
				double dy = this.robot.posY - station.y();
				double dz = this.robot.posZ - station.z();
				double distance = dx * dx + dy * dy + dz * dz;

				if (potentialStation == null || distance < potentialStationDistance)
				{
					// TODO gamerforEA code start
					if (this.robot.fake.cantBreak(station.x(), station.y(), station.z()))
						continue;
					// TODO gamerforEA code end

					potentialStation = station;
					potentialStationDistance = distance;
				}
			}
		}

		if (potentialStation != null)
			this.targetStation = potentialStation;

		this.terminate();
	}

	@Override
	public void delegateAIEnded(AIRobot ai)
	{
		this.terminate();
	}

	@Override
	public boolean success()
	{
		return this.targetStation != null;
	}
}
