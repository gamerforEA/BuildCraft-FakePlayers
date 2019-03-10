package com.gamerforea.buildcraft;

import com.gamerforea.eventhelper.nexus.ModNexus;
import com.gamerforea.eventhelper.nexus.ModNexusFactory;
import com.gamerforea.eventhelper.nexus.NexusUtils;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.FakePlayer;

import java.util.Set;

@ModNexus(name = "BuildCraft", uuid = "77456a1f-b9f9-3f84-8863-ddef5b8e2209")
public final class ModUtils
{
	public static final ModNexusFactory NEXUS_FACTORY = NexusUtils.getFactory();
	public static final ThreadLocal<EntityPlayer> CURRENT_PLAYER = new ThreadLocal<>();

	public static FakePlayer getModFake(World world)
	{
		return NEXUS_FACTORY.getFake(world);
	}

	public static void removeItems(NBTBase currentNbt)
	{
		if (currentNbt == null)
			return;

		int nbtBaseId = currentNbt.getId();
		if (nbtBaseId == Constants.NBT.TAG_COMPOUND)
		{
			NBTTagCompound nbt = (NBTTagCompound) currentNbt;
			if (!nbt.hasNoTags())
			{
				if (nbt.hasKey("id", Constants.NBT.TAG_SHORT) && nbt.hasKey("Count", Constants.NBT.TAG_BYTE) && nbt.hasKey("Damage", Constants.NBT.TAG_SHORT))
				{
					nbt.setShort("id", (short) 0);
					nbt.setByte("Count", (byte) 0);
					nbt.setShort("Damage", (short) 0);
					nbt.removeTag("tag");
				}

				Set<String> keySet = nbt.func_150296_c();
				for (String key : keySet)
				{
					NBTBase subNbt = nbt.getTag(key);
					int tagType = subNbt.getId();
					if (tagType == Constants.NBT.TAG_COMPOUND || tagType == Constants.NBT.TAG_LIST)
						removeItems(subNbt);
				}
			}
		}
		else if (nbtBaseId == Constants.NBT.TAG_LIST)
		{
			NBTTagList nbt = (NBTTagList) currentNbt;
			int size = nbt.tagCount();
			if (size > 0)
			{
				int tagType = nbt.func_150303_d();
				if (tagType == Constants.NBT.TAG_COMPOUND)
					for (int i = 0; i < size; i++)
					{
						removeItems(nbt.getCompoundTagAt(i));
					}
			}
		}
	}
}
