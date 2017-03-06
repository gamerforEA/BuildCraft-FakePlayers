package com.gamerforea.buildcraft;

import java.util.Set;

import com.gamerforea.eventhelper.util.FastUtils;
import com.google.common.collect.Sets;

import cpw.mods.fml.common.registry.GameData;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.common.config.Configuration;

public final class EventConfig
{
	public static final Set<String> autoCraftBlackList = Sets.newHashSet("minecraft:stone", "IC2:blockMachine:5");
	public static final Set<String> builderNbtTagBlackList = Sets.newHashSet();
	public static boolean builderNbtDebug = false;

	static
	{
		try
		{
			Configuration cfg = FastUtils.getConfig("BuildCraft");
			readStringSet(cfg, "autoCraftBlackList", "general", "Чёрный список блоков для автокрафта", autoCraftBlackList);
			readStringSet(cfg, "builderNbtTagBlackList", "general", "Чёрный список NBT тэгов для строителя", builderNbtTagBlackList);
			builderNbtDebug = cfg.getBoolean("builderNbtDebug", "general", builderNbtDebug, "Вывод NBT тайлов для Строителя");
			cfg.save();
		}
		catch (final Throwable throwable)
		{
			System.err.println("Failed load config. Use default values.");
			throwable.printStackTrace();
		}
	}

	public static final boolean inBlackList(Set<String> blackList, Item item, int meta)
	{
		if (item instanceof ItemBlock)
			return inBlackList(blackList, ((ItemBlock) item).field_150939_a, meta);

		return inBlackList(blackList, getId(item), meta);
	}

	public static final boolean inBlackList(Set<String> blackList, Block block, int meta)
	{
		return inBlackList(blackList, getId(block), meta);
	}

	private static final boolean inBlackList(Set<String> blackList, String id, int meta)
	{
		return id != null && (blackList.contains(id) || blackList.contains(id + ':' + meta));
	}

	private static final void readStringSet(final Configuration cfg, final String name, final String category, final String comment, final Set<String> def)
	{
		final Set<String> temp = getStringSet(cfg, name, category, comment, def);
		def.clear();
		def.addAll(temp);
	}

	private static final Set<String> getStringSet(final Configuration cfg, final String name, final String category, final String comment, final Set<String> def)
	{
		return getStringSet(cfg, name, category, comment, def.toArray(new String[def.size()]));
	}

	private static final Set<String> getStringSet(final Configuration cfg, final String name, final String category, final String comment, final String... def)
	{
		return Sets.newHashSet(cfg.getStringList(name, category, def, comment));
	}

	private static final String getId(Item item)
	{
		return GameData.getItemRegistry().getNameForObject(item);
	}

	private static final String getId(Block block)
	{
		return GameData.getBlockRegistry().getNameForObject(block);
	}
}
