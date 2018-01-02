package buildcraft.core;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import net.minecraft.item.Item;
import net.minecraft.stats.Achievement;
import net.minecraftforge.common.AchievementPage;

public class AchievementManager
{
	public final AchievementPage page;

	public AchievementManager(String name)
	{
		this.page = new AchievementPage(name);
		AchievementPage.registerAchievementPage(this.page);
	}

	public Achievement registerAchievement(Achievement a)
	{
		if (a.theItemStack != null && a.theItemStack.getItem() != null)
			this.page.getAchievements().add(a.registerStat());
		return a;
	}

	@SubscribeEvent
	public void onCrafting(PlayerEvent.ItemCraftedEvent event)
	{
		// TODO gamerforEA code start
		if (event.crafting == null || event.player == null)
			return;
		// TODO gamerforEA code end

		Item item = event.crafting.getItem();
		int damage = event.crafting.getItemDamage();

		for (Achievement a : this.page.getAchievements())
		{
			if (item.equals(a.theItemStack.getItem()) && damage == a.theItemStack.getItemDamage())
				event.player.addStat(a, 1);
		}
	}
}
