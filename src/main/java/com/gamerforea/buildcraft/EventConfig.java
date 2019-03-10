package com.gamerforea.buildcraft;

import com.gamerforea.eventhelper.config.*;
import com.google.common.collect.Sets;

import java.util.Set;

import static net.minecraftforge.common.config.Configuration.CATEGORY_GENERAL;
import static net.minecraftforge.common.config.Configuration.CATEGORY_SPLITTER;

@Config(name = "BuildCraft")
public final class EventConfig
{
	private static final String CATEGORY_BLACKLISTS = "blacklists";
	private static final String CATEGORY_OTHER = "other";
	private static final String CATEGORY_OTHER_BUILDER = CATEGORY_OTHER + CATEGORY_SPLITTER + "builder";
	private static final String CATEGORY_OTHER_LASER = CATEGORY_OTHER + CATEGORY_SPLITTER + "laser";
	private static final String CATEGORY_PERFORMANCE = "performance";

	@ConfigItemBlockList(name = "autoCraft",
						 category = CATEGORY_BLACKLISTS,
						 comment = "Чёрный список предметов для автокрафта",
						 oldName = "autoCraftBlackList",
						 oldCategory = CATEGORY_GENERAL)
	public static final ItemBlockList autoCraftBlackList = new ItemBlockList(true);

	@ConfigStringCollection(name = "builderNbtTag",
							category = CATEGORY_BLACKLISTS,
							comment = "Чёрный список NBT тэгов для строителя",
							oldName = "builderNbtTagBlackList",
							oldCategory = CATEGORY_GENERAL)
	public static final Set<String> builderNbtTagBlackList = Sets.newHashSet();

	@ConfigBoolean(name = "nbtDebug",
				   category = CATEGORY_OTHER_BUILDER,
				   comment = "Вывод NBT тайлов для Строителя",
				   oldName = "builderNbtDebug",
				   oldCategory = CATEGORY_GENERAL)
	public static boolean builderNbtDebug = false;

	@ConfigBoolean(name = "nbtDisable",
				   category = CATEGORY_OTHER_BUILDER,
				   comment = "Выключить перенос NBT тайлов для Строителя",
				   oldName = "builderNbtDisable",
				   oldCategory = CATEGORY_GENERAL)
	public static boolean builderNbtDisable = false;

	@ConfigBoolean(name = "removeItems",
				   category = CATEGORY_OTHER_BUILDER,
				   comment = "Включить удаление предметов из NBT тайлов и мобов для Строителя",
				   oldName = "builderRemoveItems",
				   oldCategory = CATEGORY_GENERAL)
	public static boolean builderRemoveItems = false;

	@ConfigInt(name = "maxRecievedEnergyPerTick",
			   category = CATEGORY_OTHER_LASER,
			   comment = "Максимальное количество энергии, принимаемое Сборочным столом в тик (0 - без ограничений)",
			   oldName = "maxRecievedLaserEnergyPerTick",
			   oldCategory = CATEGORY_GENERAL,
			   min = 0)
	public static int maxRecievedLaserEnergyPerTick = 0;

	@ConfigInt(name = "maxEnergyPerTick",
			   category = CATEGORY_OTHER_LASER,
			   comment = "Максимальное количество энергии, передаваемое Лазером в тик",
			   oldName = "maxLaserEnergyPerTick",
			   oldCategory = CATEGORY_GENERAL,
			   min = 1)
	public static int maxLaserEnergyPerTick = 40;

	@ConfigBoolean(category = CATEGORY_OTHER, comment = "Фикс уязвимостей с пакетами")
	public static boolean networkFix = true;

	@ConfigBoolean(category = CATEGORY_PERFORMANCE,
				   comment = "Проверка всех известных координат жидкостей помпой вместо вместо перестройки очереди (может повысить производительность)",
				   oldCategory = CATEGORY_GENERAL)
	public static boolean pumpFullQueueCheck = false;

	public static void init()
	{
		ConfigUtils.readConfig(EventConfig.class);
	}

	static
	{
		init();
	}
}
