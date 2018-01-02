package com.gamerforea.buildcraft;

import com.gamerforea.eventhelper.util.FastUtils;
import com.google.common.collect.Sets;
import cpw.mods.fml.common.registry.GameData;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.common.config.Configuration;

import java.util.Set;

public final class EventConfig
{
	public static final Set<String> autoCraftBlackList = Sets.newHashSet("minecraft:stone", "IC2:blockMachine:5");
	public static final Set<String> builderNbtTagBlackList = Sets.newHashSet();
	public static boolean builderNbtDebug = false;
	public static boolean builderNbtDisable = false;

	static
	{
		try
		{
			Configuration cfg = FastUtils.getConfig("BuildCraft");
			String c = Configuration.CATEGORY_GENERAL;
			readStringSet(cfg, "autoCraftBlackList", c, "Чёрный список блоков для автокрафта", autoCraftBlackList);
			readStringSet(cfg, "builderNbtTagBlackList", c, "Чёрный список NBT тэгов для строителя", builderNbtTagBlackList);
			builderNbtDebug = cfg.getBoolean("builderNbtDebug", c, builderNbtDebug, "Вывод NBT тайлов для Строителя");
			builderNbtDisable = cfg.getBoolean("builderNbtDisable", c, builderNbtDisable, "Выключить перенос NBT тайлов для Строителя");
			cfg.save();
		}
		catch (final Throwable throwable)
		{
			System.err.println("Failed load config. Use default values.");
			throwable.printStackTrace();
		}
	}

	public static final boolean inList(Set<String> blackList, Item item, int meta)
	{
		if (item instanceof ItemBlock)
			return inList(blackList, ((ItemBlock) item).field_150939_a, meta);

		return inList(blackList, getId(item), meta);
	}

	public static final boolean inList(Set<String> blackList, Block block, int meta)
	{
		return inList(blackList, getId(block), meta);
	}

	private static final boolean inList(Set<String> blackList, String id, int meta)
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
