package appeng.siteexport;

import appeng.core.AppEng;
import appeng.recipes.handlers.InscriberProcessType;
import appeng.recipes.handlers.InscriberRecipe;
import appeng.siteexport.model.CraftingRecipeJson;
import appeng.siteexport.model.InscriberRecipeJson;
import appeng.siteexport.model.ItemInfoJson;
import appeng.siteexport.model.SiteExportJson;
import appeng.siteexport.model.SmeltingRecipeJson;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.DetectedVersion;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;

public class SiteExportWriter {

    private final SiteExportJson siteExport = new SiteExportJson();

    public SiteExportWriter() {
        siteExport.modVersion = ModVersion.get();
        siteExport.generated = Instant.now().toString();
        siteExport.gameVersion = DetectedVersion.tryDetectVersion().getName();
    }

    public void addItem(String id, ItemStack stack, String iconPath) {
        var itemInfo = new ItemInfoJson();
        itemInfo.id = id;
        itemInfo.icon = iconPath;
        itemInfo.displayName = stack.getHoverName().getString();
        itemInfo.rarity = stack.getRarity().name().toLowerCase(Locale.ROOT);

        siteExport.items.put(itemInfo.id, itemInfo);
    }

    public void addRecipe(CraftingRecipe recipe) {
        var json = new CraftingRecipeJson();
        json.id = recipe.getId().toString();
        json.shapeless = recipe instanceof ShapelessRecipe;

        json.resultItem = Registry.ITEM.getKey(recipe.getResultItem().getItem()).toString();
        json.resultCount = recipe.getResultItem().getCount();

        var ingredients = recipe.getIngredients();
        json.ingredients = new String[9][];
        for (int i = 0; i < json.ingredients.length; i++) {
            if (i < ingredients.size()) {
                json.ingredients[i] = convertIngredient(ingredients.get(i));
            } else {
                json.ingredients[i] = new String[0];
            }
        }

        siteExport.craftingRecipes.put(json.id, json);
    }

    public void addRecipe(InscriberRecipe recipe) {
        var json = new InscriberRecipeJson();
        json.id = recipe.getId().toString();

        json.top = convertIngredient(recipe.getTopOptional());
        json.middle = convertIngredient(recipe.getMiddleInput());
        json.bottom = convertIngredient(recipe.getBottomOptional());
        json.resultItem = Registry.ITEM.getKey(recipe.getResultItem().getItem()).toString();
        json.resultCount = recipe.getResultItem().getCount();
        json.consumesTopAndBottom = recipe.getProcessType() == InscriberProcessType.PRESS;

        siteExport.inscriberRecipes.put(json.id, json);
    }

    public void addRecipe(AbstractCookingRecipe recipe) {
        var json = new SmeltingRecipeJson();
        json.id = recipe.getId().toString();

        json.resultItem = Registry.ITEM.getKey(recipe.getResultItem().getItem()).toString();

        var ingredients = recipe.getIngredients();
        json.ingredient = convertIngredient(ingredients.get(0));

        siteExport.smeltingRecipes.put(json.id, json);
    }

    @NotNull
    private String[] convertIngredient(Ingredient ingredient) {
        return Arrays.stream(ingredient.getItems())
                .map(is -> Registry.ITEM.getKey(is.getItem()))
                .filter(k -> k.getNamespace().equals(AppEng.MOD_ID) || k.getNamespace().equals("minecraft"))
                .map(ResourceLocation::toString)
                .toArray(String[]::new);
    }

    public void write(Path file) throws IOException {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            gson.toJson(siteExport, writer);
        }
    }

}
