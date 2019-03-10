package buildcraft.compat.nei;

import buildcraft.api.recipes.IFlexibleRecipe;
import buildcraft.api.recipes.IFlexibleRecipeViewable;
import buildcraft.compat.CompatModuleNEI;

import java.util.Collection;

public class NEIIntegrationBC
{
	protected static boolean isValid(IFlexibleRecipeViewable recipe)
	{
		if (recipe.getOutput() == null)
			return false;

		if (CompatModuleNEI.disableFacadeNEI && ((IFlexibleRecipe) recipe).getId().startsWith("buildcraft:facade"))
			return false;

		Collection<Object> inputs = recipe.getInputs();

		// TODO gamerforEA code start
		if (inputs.isEmpty())
			return false;
		// TODO gamerforEA code end

		for (Object o : inputs)
		{
			if (o == null)
				return false;
		}

		return true;
	}
}
