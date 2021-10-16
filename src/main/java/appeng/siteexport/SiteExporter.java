package appeng.siteexport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.mojang.serialization.Lifecycle;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.PrimaryLevelData;

import appeng.core.AppEng;

/**
 * Exports a data package for use by the website.
 */
@Environment(EnvType.CLIENT)
public final class SiteExporter {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final int ICON_DIMENSION = 128;

    private static volatile SceneExportJob job;

    public static void initialize() {
        WorldRenderEvents.AFTER_SETUP.register(context -> {
            continueJob(SceneExportJob::render);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            continueJob(SceneExportJob::tick);
        });

        ClientCommandManager.DISPATCHER.register(
                ClientCommandManager.literal("ae2export").executes(context -> {
                    context.getSource().sendFeedback(new TextComponent("AE2 Site-Export started"));
                    job = null;
                    try {
                        startExport(Minecraft.getInstance(), context.getSource());
                    } catch (Exception e) {
                        LOGGER.error("AE2 site export failed.", e);
                        context.getSource().sendError(new TextComponent(e.toString()));
                        return 0;
                    }
                    return 0;
                }));
    }

    @FunctionalInterface
    interface JobFunction {
        void accept(SceneExportJob job) throws Exception;
    }

    private static void continueJob(JobFunction event) {
        if (job != null) {
            try {
                event.accept(job);
                if (job.isAtEnd()) {
                    job.sendFeedback(new TextComponent("AE2 game data exported to ")
                            .append(new TextComponent("[site-export]")
                                    .withStyle(style -> style
                                            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, "site-export"))
                                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                                    new TextComponent("Click to open export folder")))
                                            .applyFormats(ChatFormatting.UNDERLINE, ChatFormatting.GREEN))));
                    job = null;
                }
            } catch (Exception e) {
                LOGGER.error("AE2 site export failed.", e);
                job.sendError(new TextComponent(e.toString()));
                job = null;
            }
        }
    }

    private static void startExport(Minecraft client, FabricClientCommandSource source) throws Exception {
        if (!client.hasSingleplayerServer()) {
            throw new IllegalStateException("Only run this command from single-player.");
        }

        Path outputFolder = Paths.get("site-export").toAbsolutePath();
        if (Files.isDirectory(outputFolder)) {
            MoreFiles.deleteDirectoryContents(outputFolder, RecursiveDeleteOption.ALLOW_INSECURE);
        } else {
            Files.createDirectories(outputFolder);
        }

        var siteExport = new SiteExportWriter();

        var usedVanillaItems = new HashSet<Item>();
        processRecipes(client, usedVanillaItems, siteExport);

        // Iterate over all Applied Energistics items
        var stacks = new ArrayList<ItemStack>();
        for (Item item : Registry.ITEM) {
            if (getItemId(item).getNamespace().equals(AppEng.MOD_ID)) {
                stacks.add(new ItemStack(item));
            }
        }

        // Also add Vanilla items
        for (Item usedVanillaItem : usedVanillaItems) {
            stacks.add(new ItemStack(usedVanillaItem));
        }

        // All files in this folder will be directly served from the root of the web-site
        Path assetFolder = outputFolder.resolve("public");

        processItems(client, siteExport, stacks, assetFolder);

        Path dataFolder = outputFolder.resolve("data");
        Files.createDirectories(dataFolder);
        siteExport.write(dataFolder.resolve("game-data.json"));

        job = new SceneExportJob(SiteExportScenes.createScenes(), source, assetFolder);
    }

    private static void processItems(Minecraft client,
            SiteExportWriter siteExport,
            List<ItemStack> items,
            Path assetFolder) throws IOException {
        Path iconsFolder = assetFolder.resolve("icons");
        if (Files.exists(iconsFolder)) {
            MoreFiles.deleteRecursively(iconsFolder, RecursiveDeleteOption.ALLOW_INSECURE);
        }

        try (var itemRenderer = new OffScreenRenderer(ICON_DIMENSION, ICON_DIMENSION)) {
            itemRenderer.setupItemRendering();

            for (ItemStack stack : items) {
                String itemId = getItemId(stack.getItem()).toString();
                var iconPath = iconsFolder.resolve(itemId.replace(':', '/') + ".png");
                Files.createDirectories(iconPath.getParent());

                itemRenderer.captureAsPng(() -> {
                    client.getItemRenderer().renderAndDecorateFakeItem(stack, 0, 0);
                }, iconPath);

                String absIconUrl = "/" + assetFolder.relativize(iconPath).toString().replace('\\', '/');
                siteExport.addItem(itemId, stack, absIconUrl);
            }
        }
    }

    private static ResourceLocation getItemId(Item item) {
        return Registry.ITEM.getKey(item);
    }

    private static void processRecipes(Minecraft client, Set<Item> usedVanillaItems, SiteExportWriter siteExport)
            throws Exception {

        // Fake a level in a temporary folder
        var tempLevel = Files.createTempDirectory("templevel");

        var registryAccess = RegistryAccess.builtin();
        var levelStorageSource = new LevelStorageSource(tempLevel, tempLevel, DataFixers.getDataFixer());
        var levelAccess = levelStorageSource.createAccess("siteexport");

        // Save a barebones level.dat
        var levelsettings = MinecraftServer.DEMO_SETTINGS;
        var worldgensettings = WorldGenSettings.demoSettings(registryAccess);
        levelAccess.saveDataTag(registryAccess,
                new PrimaryLevelData(levelsettings, worldgensettings, Lifecycle.stable()));

        try (var stem = client.makeServerStem(registryAccess, Minecraft::loadDataPacks, Minecraft::loadWorldData, false,
                levelAccess)) {
            var recipeManager = stem.serverResources().getRecipeManager();

            dumpRecipes(recipeManager, usedVanillaItems, siteExport);
        } finally {
            MoreFiles.deleteRecursively(tempLevel, RecursiveDeleteOption.ALLOW_INSECURE);
        }
    }

    private static void addVanillaItem(Set<Item> items, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        var item = stack.getItem();
        if ("minecraft".equals(Registry.ITEM.getKey(item).getNamespace())) {
            items.add(item);
        }
    }

    private static void dumpRecipes(RecipeManager recipeManager, Set<Item> vanillaItems, SiteExportWriter siteExport) {

        for (Recipe<?> recipe : recipeManager.getRecipes()) {
            // Only consider our recipes
            if (!recipe.getId().getNamespace().equals(AppEng.MOD_ID)) {
                continue;
            }

            if (recipe instanceof CraftingRecipe craftingRecipe) {
                if (craftingRecipe.isSpecial() || craftingRecipe.getResultItem().isEmpty()) {
                    continue;
                }

                addVanillaItem(vanillaItems, craftingRecipe.getResultItem());

                for (Ingredient ingredient : craftingRecipe.getIngredients()) {
                    for (ItemStack item : ingredient.getItems()) {
                        addVanillaItem(vanillaItems, item);
                    }
                }

                siteExport.addRecipe(craftingRecipe);
            }
        }

    }

}
