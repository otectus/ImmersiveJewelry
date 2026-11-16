package com.otectus.immersivejewelry.client.compat.jei;

import com.mojang.blaze3d.platform.InputConstants;
import com.otectus.immersivejewelry.client.workstation.WorkstationClient;
import com.pockyl.diegesis.api.client.DiegesisClient;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiKeyMappings;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IRecipesGui;
import org.jetbrains.annotations.Nullable;

/**
 * JEI at the workstations, through Diegesis' recipe-viewer hooks (1.0.0-beta.10). JEI takes no input on a station's
 * screen (it has no GUI there), so the station's own key handler asks this when a key it has no use for at that moment
 * is pressed over a stack: the drawer's stack under the cursor ({@code DiegesisClient.hoveredStack}) or a real stack the
 * station shows. JEI's show-recipe and show-uses keys open JEI's page for it, over the station without ending it
 * ({@code DiegesisClient.registerChildScreen}); closing the page returns to the station with the work and the lease kept.
 * A station key always keeps its meaning, and clicks are never JEI's at a station.
 */
final class StationLookups {
    private static boolean childScreenRegistered;

    /** While JEI runs, its lookup keys work at the stations; without it, nothing is looked up. */
    static void runtime(@Nullable IJeiRuntime runtime) {
        if (runtime == null) {
            WorkstationClient.setRecipeLookup(null);
            return;
        }
        registerChildScreen();
        IJeiKeyMappings keys = runtime.getKeyMappings();
        IFocusFactory focuses = runtime.getJeiHelpers().getFocusFactory();
        WorkstationClient.setRecipeLookup((key, scan, stack) -> {
            InputConstants.Key input = InputConstants.getKey(key, scan);
            RecipeIngredientRole role = keys.getShowRecipe().isActiveAndMatches(input) ? RecipeIngredientRole.OUTPUT
                    : keys.getShowUses().isActiveAndMatches(input) ? RecipeIngredientRole.INPUT : null;
            if (role == null) return false;
            runtime.getRecipesGui().show(focuses.createFocus(role, VanillaTypes.ITEM_STACK, stack));
            return true;
        });
    }

    /** JEI's recipe page opens over a Jewelry station as its child. Diegesis keeps the test for the game's lifetime: once. */
    private static void registerChildScreen() {
        if (childScreenRegistered) return;
        childScreenRegistered = true;
        DiegesisClient.registerChildScreen(screen -> screen instanceof IRecipesGui && WorkstationClient.atStation());
    }

    private StationLookups() {}
}
