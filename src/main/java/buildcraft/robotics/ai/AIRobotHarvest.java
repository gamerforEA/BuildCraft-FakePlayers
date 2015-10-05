package buildcraft.robotics.ai;

import java.util.ArrayList;
import java.util.List;

import com.gamerforea.eventhelper.util.EventUtils;

import buildcraft.api.core.BlockIndex;
import buildcraft.api.core.BuildCraftAPI;
import buildcraft.api.crops.CropManager;
import buildcraft.api.robots.AIRobot;
import buildcraft.api.robots.EntityRobotBase;
import buildcraft.core.lib.utils.BlockUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;

public class AIRobotHarvest extends AIRobot
{
	private BlockIndex blockFound;
	private int delay = 0;

	public AIRobotHarvest(EntityRobotBase iRobot)
	{
		super(iRobot);
	}

	public AIRobotHarvest(EntityRobotBase iRobot, BlockIndex iBlockFound)
	{
		super(iRobot);
		this.blockFound = iBlockFound;
	}

	@Override
	public void update()
	{
		if (this.blockFound == null)
		{
			this.setSuccess(false);
			this.terminate();
			return;
		}

		if (this.delay++ > 20)
		{
			if (!BuildCraftAPI.getWorldProperty("harvestable").get(this.robot.worldObj, this.blockFound.x, this.blockFound.y, this.blockFound.z))
			{
				this.setSuccess(false);
				this.terminate();
				return;
			}

			// TODO gamerforEA code start
			if (EventUtils.cantBreak(this.robot.fake.getPlayer(), this.blockFound.x, this.blockFound.y, this.blockFound.z))
			{
				this.setSuccess(false);
				this.terminate();
				return;
			}
			// TODO gamerforEA code end

			List<ItemStack> drops = new ArrayList<ItemStack>();
			if (!CropManager.harvestCrop(this.robot.worldObj, this.blockFound.x, this.blockFound.y, this.blockFound.z, drops))
			{
				this.setSuccess(false);
				this.terminate();
				return;
			}
			for (ItemStack stack : drops)
				BlockUtils.dropItem((WorldServer) this.robot.worldObj, MathHelper.floor_double(this.robot.posX), MathHelper.floor_double(this.robot.posY), MathHelper.floor_double(this.robot.posZ), 6000, stack);
		}
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

		if (this.blockFound != null)
		{
			NBTTagCompound sub = new NBTTagCompound();
			this.blockFound.writeTo(sub);
			nbt.setTag("blockFound", sub);
		}
	}

	@Override
	public void loadSelfFromNBT(NBTTagCompound nbt)
	{
		super.loadSelfFromNBT(nbt);

		if (nbt.hasKey("blockFound"))
			this.blockFound = new BlockIndex(nbt.getCompoundTag("blockFound"));
	}
}