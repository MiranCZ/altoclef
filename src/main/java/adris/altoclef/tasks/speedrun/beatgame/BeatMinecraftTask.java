package adris.altoclef.tasks.speedrun.beatgame;

import adris.altoclef.AltoClef;
import adris.altoclef.Debug;
import adris.altoclef.TaskCatalogue;
import adris.altoclef.trackers.BlockScanner;
import adris.altoclef.commands.SetGammaCommand;
import adris.altoclef.multiversion.blockpos.BlockPosVer;
import adris.altoclef.multiversion.versionedfields.Blocks;
import adris.altoclef.tasks.*;
import adris.altoclef.tasks.construction.DestroyBlockTask;
import adris.altoclef.tasks.construction.PlaceBlockNearbyTask;
import adris.altoclef.tasks.construction.PlaceObsidianBucketTask;
import adris.altoclef.tasks.container.*;
import adris.altoclef.tasks.misc.EquipArmorTask;
import adris.altoclef.tasks.misc.PlaceBedAndSetSpawnTask;
import adris.altoclef.tasks.misc.SleepThroughNightTask;
import adris.altoclef.tasks.movement.*;
import adris.altoclef.tasks.resources.*;
import adris.altoclef.tasks.speedrun.*;
import adris.altoclef.tasks.speedrun.beatgame.prioritytask.prioritycalculators.CollectFoodPriorityCalculator;
import adris.altoclef.tasks.speedrun.beatgame.prioritytask.prioritycalculators.DistanceItemPriorityCalculator;
import adris.altoclef.tasks.speedrun.beatgame.prioritytask.prioritycalculators.StaticItemPriorityCalculator;
import adris.altoclef.tasks.speedrun.beatgame.prioritytask.tasks.*;
import adris.altoclef.tasksystem.Task;
import adris.altoclef.trackers.EntityTracker;
import adris.altoclef.trackers.storage.ItemStorageTracker;
import adris.altoclef.util.*;
import adris.altoclef.util.helpers.*;
import adris.altoclef.util.slots.Slot;
import adris.altoclef.util.time.TimerGame;
import baritone.api.utils.input.Input;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.EndPortalFrameBlock;
import net.minecraft.client.gui.screen.CreditsScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.*;
import net.minecraft.world.Difficulty;
import org.apache.commons.lang3.ArrayUtils;
import adris.altoclef.multiversion.versionedfields.Items;
//#if MC <= 12006
//$$ import adris.altoclef.mixins.EntityAccessor;
//#endif

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static adris.altoclef.tasks.resources.CollectMeatTask.COOKABLE_FOODS;
import static net.minecraft.client.MinecraftClient.getInstance;


public class BeatMinecraftTask extends Task {

    private static final Item[] COLLECT_EYE_ARMOR = new Item[]{
            Items.DIAMOND_CHESTPLATE,
            Items.DIAMOND_LEGGINGS,
            Items.DIAMOND_BOOTS
    };

    private static final Item[] COLLECT_IRON_ARMOR = ItemHelper.IRON_ARMORS;
    private static final Item[] COLLECT_EYE_ARMOR_END = ItemHelper.DIAMOND_ARMORS;


    private static final ItemTarget[] COLLECT_EYE_GEAR_MIN = combine(
            ItemTarget.of(Items.DIAMOND_SWORD),
            ItemTarget.of(Items.DIAMOND_PICKAXE)
    );

    private static final int END_PORTAL_FRAME_COUNT = 12;
    private static final double END_PORTAL_BED_SPAWN_RANGE = 8;

    // We don't want curse of binding
    private static final Predicate<ItemStack> noCurseOfBinding = stack -> !EnchantmentHelper.hasAnyEnchantmentsWith(stack, net.minecraft.component.EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE);

    private static BeatMinecraftConfig config;
    private static GoToStrongholdPortalTask locateStrongholdTask;
    private static boolean openingEndPortal = false;

    static {
        ConfigHelper.loadConfig("configs/beat_minecraft.json", BeatMinecraftConfig::new, BeatMinecraftConfig.class, newConfig -> config = newConfig);
    }

    private final UselessItems uselessItems;
    private final HashMap<Item, Integer> cachedEndItemDrops = new HashMap<>();
    // For some reason, after death there's a frame where the game thinks there are NO items in the end.
    private final TimerGame cachedEndItemNothingWaitTime = new TimerGame(10);
    private final Task buildMaterialsTask;
    private final PlaceBedAndSetSpawnTask setBedSpawnTask = new PlaceBedAndSetSpawnTask();
    private final Task getOneBedTask = TaskCatalogue.getItemTask("bed", 1);
    private final Task sleepThroughNightTask = new SleepThroughNightTask();
    private final Task killDragonBedStratsTask = new KillEnderDragonWithBedsTask();
    // End specific dragon breath avoidance
    private final DragonBreathTracker dragonBreathTracker = new DragonBreathTracker();
    private final TimerGame timer1 = new TimerGame(5);
    private final TimerGame timer2 = new TimerGame(35);
    private final TimerGame timer3 = new TimerGame(60);
    private final List<PriorityTask> gatherResources = new LinkedList<>();
    private final TimerGame changedTaskTimer = new TimerGame(3);
    private final TimerGame forcedTaskTimer = new TimerGame(10);
    private final List<BlockPos> blacklistedChests = new LinkedList<>();
    private final TimerGame waterPlacedTimer = new TimerGame(1.5);
    private final TimerGame fortressTimer = new TimerGame(20);
    private final AltoClef mod;
    private PriorityTask lastGather = null;
    private Task lastTask = null;
    private boolean pickupFurnace = false;
    private boolean pickupSmoker = false;
    private boolean pickupCrafting = false;
    private Task rePickupTask = null;
    private Task searchTask = null;
    private boolean hasRods = false;
    private boolean gotToBiome = false;
    private GetRidOfExtraWaterBucketTask getRidOfExtraWaterBucketTask = null;
    private int repeated = 0;
    private boolean gettingPearls = false;
    private SafeNetherPortalTask safeNetherPortalTask;
    private boolean escaped = false;
    private boolean gotToFortress = false;
    private GetWithinRangeOfBlockTask cachedFortressTask = null;
    private boolean resetFortressTask = false;
    private BlockPos prevPos = null;
    private Task goToNetherTask = new DefaultGoToDimensionTask(Dimension.NETHER); // To keep the portal build cache.
    private boolean dragonIsDead = false;
    private BlockPos endPortalCenterLocation;
    private boolean ranStrongholdLocator;
    private boolean endPortalOpened;
    private BlockPos bedSpawnLocation;
    private int cachedFilledPortalFrames = 0;
    // Controls whether we CAN walk on the end portal.
    private boolean enterindEndPortal = false;
    private Task lootTask;
    private boolean collectingEyes;
    private boolean escapingDragonsBreath = false;
    private Task getBedTask;
    private List<TaskChange> taskChanges = new ArrayList<>();
    private PriorityTask prevLastGather = null;
    private BlockPos biomePos = null;

    public BeatMinecraftTask(AltoClef mod) {
        this.mod = mod;

        locateStrongholdTask = new GoToStrongholdPortalTask(config.targetEyes);
        buildMaterialsTask = new GetBuildingMaterialsTask(config.buildMaterialCount);
        uselessItems = new UselessItems(config);

        SetGammaCommand.changeGamma(20d);

        if (mod.getWorld().getDifficulty() != Difficulty.EASY) {
            mod.logWarning("Detected that the difficulty is other than easy!");
            if (mod.getWorld().getDifficulty() == Difficulty.PEACEFUL) {
                mod.logWarning("No mobs spawn on peaceful difficulty, so the bot will not be able to beat the game. Please change it!");
            } else {
                mod.logWarning("This could cause the bot to die sooner, please consider changing it...");
            }
        }

        ItemStorageTracker itemStorage = mod.getItemStorage();

        gatherResources.add(new MineBlockPriorityTask(
                ItemHelper.itemsToBlocks(ItemHelper.LOG), ItemHelper.LOG, MiningRequirement.STONE,
                new DistanceItemPriorityCalculator(1050, 450, 5, 4, 10),

                a -> itemStorage.hasItem(Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE)
                        && itemStorage.getItemCount(ItemHelper.LOG) < 5
        ));

        addOreMiningTasks();
        addCollectFoodTask(mod);

        // gear
        addStoneToolsTasks();
        addPickaxeTasks(mod);
        addDiamondArmorTasks(mod);
        addLootChestsTasks(mod);
        addPickupImportantItemsTask(mod);

        gatherResources.add(new MineBlockPriorityTask(new Block[]{Blocks.GRAVEL}, new Item[]{Items.FLINT}, MiningRequirement.STONE,
                new DistanceItemPriorityCalculator(17500, 7500, 5, 1, 1),
                a -> itemStorage.hasItem(Items.STONE_SHOVEL) && !itemStorage.hasItem(Items.FLINT_AND_STEEL)
        ));

        gatherResources.add(new MineBlockPriorityTask(ItemHelper.itemsToBlocks(ItemHelper.BED), ItemHelper.BED, MiningRequirement.HAND,
                new DistanceItemPriorityCalculator(25_000, 25_000, 5, getTargetBeds(mod), getTargetBeds(mod))
        ));

        gatherResources.add(new CraftItemPriorityTask(200, getRecipeTarget(Items.SHIELD),
                a -> itemStorage.hasItem(Items.IRON_INGOT)
        ));

        gatherResources.add(new CraftItemPriorityTask(300, mod.getCraftingRecipeTracker().getFirstRecipeTarget(Items.BUCKET, 2),
                a -> itemStorage.getItemCount(Items.IRON_INGOT) >= 6)
        );

        gatherResources.add(new CraftItemPriorityTask(100, getRecipeTarget(Items.FLINT_AND_STEEL),
                a -> itemStorage.hasItem(Items.IRON_INGOT) && itemStorage.hasItem(Items.FLINT)
        ));

        gatherResources.add(new CraftItemPriorityTask(330, getRecipeTarget(Items.DIAMOND_SWORD), a -> itemStorage.getItemCount(Items.DIAMOND) >= 2 && StorageHelper.miningRequirementMet(MiningRequirement.DIAMOND)));
        gatherResources.add(new CraftItemPriorityTask(400, getRecipeTarget(Items.GOLDEN_HELMET), a -> itemStorage.getItemCount(Items.GOLD_INGOT) >= 5));

        addSleepTask(mod);

        gatherResources.add(new ActionPriorityTask(a -> {
            Pair<Task, Double> pair = new Pair<>(TaskCatalogue.getItemTask(Items.WATER_BUCKET, 1), Double.NEGATIVE_INFINITY);

            if (itemStorage.hasItem(Items.WATER_BUCKET) || hasItem(mod, Items.WATER_BUCKET))
                return pair;

            Optional<BlockPos> optionalPos = mod.getBlockScanner().getNearestBlock(Blocks.WATER);
            if (optionalPos.isEmpty()) return pair;

            double distance = Math.sqrt(BlockPosVer.getSquaredDistance(optionalPos.get(),mod.getPlayer().getPos()));
            if (distance > 55) return pair;

            pair.setRight(10 / distance * 77.3);

            return pair;
        }, a -> itemStorage.hasItem(Items.BUCKET), false, true, true));

        addSmeltTasks(mod);
        addCookFoodTasks(mod);
    }

    /**
     * Returns the BeatMinecraftConfig instance.
     * If it is not already initialized, it initializes and returns a new instance.
     *
     * @return the BeatMinecraftConfig instance
     */
    public static BeatMinecraftConfig getConfig() {
        if (config == null) {
            Debug.logInternal("Initializing BeatMinecraftConfig");
            config = new BeatMinecraftConfig();
        }
        return config;
    }

    /**
     * Retrieves the frame blocks surrounding the end portal center.
     *
     * @param endPortalCenter the center position of the end portal
     * @return a list of block positions representing the frame blocks
     */
    private static List<BlockPos> getFrameBlocks(AltoClef mod,BlockPos endPortalCenter) {
        List<BlockPos> frameBlocks = new ArrayList<>();

        for (BlockPos pos : mod.getBlockScanner().getKnownLocations(Blocks.END_PORTAL_FRAME)) {
            // distance is arbitrary for now, dont think this can run into any edge cases in a normal mc world
            if (pos.isWithinDistance(endPortalCenter, 20)) {
                frameBlocks.add(pos);
            }
        }

        Debug.logInternal("Frame blocks: " + frameBlocks);

        return frameBlocks;
    }

    /**
     * Combines multiple arrays of ItemTarget objects into a single array.
     *
     * @param targets The arrays of ItemTarget objects to combine.
     * @return The combined array of ItemTarget objects.
     */
    private static ItemTarget[] combine(ItemTarget[]... targets) {
        List<ItemTarget> combinedTargets = new ArrayList<>();

        for (ItemTarget[] targetArray : targets) {
            combinedTargets.addAll(Arrays.asList(targetArray));
        }

        Debug.logInternal("Combined Targets: " + combinedTargets);

        ItemTarget[] combinedArray = combinedTargets.toArray(new ItemTarget[0]);
        Debug.logInternal("Combined Array: " + Arrays.toString(combinedArray));

        return combinedArray;
    }

    /**
     * Checks if the End Portal Frame at the given position is filled with an Eye of Ender.
     *
     * @param mod The AltoClef mod instance.
     * @param pos The position of the End Portal Frame.
     * @return True if the End Portal Frame is filled, false otherwise.
     */
    private static boolean isEndPortalFrameFilled(AltoClef mod, BlockPos pos) {
        if (!mod.getChunkTracker().isChunkLoaded(pos)) {
            Debug.logInternal("Chunk is not loaded");
            return false;
        }


        BlockState blockState = mod.getWorld().getBlockState(pos);
        if (blockState.getBlock() != Blocks.END_PORTAL_FRAME) {
            Debug.logInternal("Block is not an End Portal Frame");
            return false;
        }

        boolean isFilled = blockState.get(EndPortalFrameBlock.EYE);

        Debug.logInternal("End Portal Frame is " + (isFilled ? "filled" : "not filled"));
        return isFilled;
    }

    /**
     * Checks if a task is running eg. task is active and not finished.
     *
     * @param mod  The AltoClef mod.
     * @param task The task to check.
     * @return True if the task is running, false otherwise.
     */
    public static boolean isTaskRunning(AltoClef mod, Task task) {
        if (task == null) {
            Debug.logInternal("Task is null");
            return false;
        }

        boolean taskActive = task.isActive();
        boolean taskFinished = task.isFinished();

        Debug.logInternal("Task is not null");
        Debug.logInternal("Task is " + (taskActive ? "active" : "not active"));
        Debug.logInternal("Task is " + (taskFinished ? "finished" : "not finished"));

        return taskActive && !taskFinished;
    }

    public static void throwAwayItems(AltoClef mod, Item... items) {
        throwAwaySlots(mod, mod.getItemStorage().getSlotsWithItemPlayerInventory(false, items));
    }

    public static void throwAwaySlots(AltoClef mod, List<Slot> slots) {
        for (Slot slot : slots) {
            if (Slot.isCursor(slot)) {
              /*  if (!mod.getControllerExtras().isBreakingBlock()) {
                    LookHelper.randomOrientation(mod);
                }*/
                mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, SlotActionType.PICKUP);
            } else {
                mod.getSlotHandler().clickSlot(slot, 0, SlotActionType.PICKUP);
            }
        }
    }

    public static boolean hasItem(AltoClef mod, Item item) {
        ClientPlayerEntity player = mod.getPlayer();
        PlayerInventory inv = player.getInventory();
        List<DefaultedList<ItemStack>> combinedInventory = List.of(inv.main, inv.armor, inv.offHand);

        for (List<ItemStack> list : combinedInventory) {
            for (ItemStack itemStack : list) {
                if (itemStack.getItem().equals(item)) return true;
            }
        }

        return false;
    }

    //TODO move to ItemHelper
    public static int getCountWithCraftedFromOre(AltoClef mod, Item item) {
        ItemStorageTracker itemStorage = mod.getItemStorage();

        if (item == Items.COAL) {
            return itemStorage.getItemCount(item);
        } else if (item == Items.RAW_IRON) {
            int count = itemStorage.getItemCount(Items.RAW_IRON, Items.IRON_INGOT);
            count += itemStorage.getItemCount(Items.BUCKET, Items.WATER_BUCKET, Items.LAVA_BUCKET, Items.AXOLOTL_BUCKET, Items.POWDER_SNOW_BUCKET) * 3;
            count += hasItem(mod, Items.SHIELD) ? 1 : 0;
            count += hasItem(mod, Items.FLINT_AND_STEEL) ? 1 : 0;

            count += hasItem(mod, Items.IRON_SWORD) ? 2 : 0;
            count += hasItem(mod, Items.IRON_PICKAXE) ? 3 : 0;

            count += hasItem(mod, Items.IRON_HELMET) ? 5 : 0;
            count += hasItem(mod, Items.IRON_CHESTPLATE) ? 8 : 0;
            count += hasItem(mod, Items.IRON_LEGGINGS) ? 7 : 0;
            count += hasItem(mod, Items.IRON_BOOTS) ? 4 : 0;

            return count;
        } else if (item == Items.RAW_GOLD) {
            int count = itemStorage.getItemCount(Items.RAW_GOLD, Items.GOLD_INGOT);
            count += hasItem(mod, Items.GOLDEN_PICKAXE) ? 3 : 0;

            count += hasItem(mod, Items.GOLDEN_HELMET) ? 5 : 0;
            count += hasItem(mod, Items.GOLDEN_CHESTPLATE) ? 8 : 0;
            count += hasItem(mod, Items.GOLDEN_LEGGINGS) ? 7 : 0;
            count += hasItem(mod, Items.GOLDEN_BOOTS) ? 4 : 0;

            return count;
        } else if (item == Items.DIAMOND) {
            int count = itemStorage.getItemCount(Items.DIAMOND);
            count += hasItem(mod, Items.DIAMOND_SWORD) ? 2 : 0;
            count += hasItem(mod, Items.DIAMOND_PICKAXE) ? 3 : 0;

            count += hasItem(mod, Items.DIAMOND_HELMET) ? 5 : 0;
            count += hasItem(mod, Items.DIAMOND_CHESTPLATE) ? 8 : 0;
            count += hasItem(mod, Items.DIAMOND_LEGGINGS) ? 7 : 0;
            count += hasItem(mod, Items.DIAMOND_BOOTS) ? 4 : 0;

            return count;
        }

        throw new IllegalStateException("Invalid ore item: " + item);
    }

    private static Block[] mapOreItemToBlocks(Item item) {
        if (item.equals(Items.RAW_IRON)) {
            return new Block[]{Blocks.DEEPSLATE_IRON_ORE, Blocks.IRON_ORE};
        } else if (item.equals(Items.RAW_GOLD)) {
            return new Block[]{Blocks.DEEPSLATE_GOLD_ORE, Blocks.GOLD_ORE};
        } else if (item.equals(Items.DIAMOND)) {
            return new Block[]{Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.DIAMOND_ORE};
        } else if (item.equals(Items.COAL)) {
            return new Block[]{Blocks.DEEPSLATE_COAL_ORE, Blocks.COAL_ORE};
        }

        throw new IllegalStateException("Invalid ore: " + item);
    }

    private void addSleepTask(AltoClef mod) {
        boolean[] skipNight = new boolean[]{false};

        gatherResources.add(new ActionPriorityTask(a -> new PlaceBedAndSetSpawnTask(),
                () -> {
                    if (!WorldHelper.canSleep()) {
                        skipNight[0] = false;
                        return Double.NEGATIVE_INFINITY;
                    }

                    if (lastTask instanceof PlaceBedAndSetSpawnTask && lastTask.isFinished()) {
                        skipNight[0] = true;
                        mod.log("Failed to sleep :(");
                        mod.log("Skipping night");
                    }
                    if (skipNight[0]) return Double.NEGATIVE_INFINITY;

                    Optional<BlockPos> pos = mod.getBlockScanner().getNearestBlock(ItemHelper.itemsToBlocks(ItemHelper.BED));
                    if (pos.isPresent() && pos.get().isWithinDistance(mod.getPlayer().getPos(), 30)) return 1_000_000;

                    return Double.NEGATIVE_INFINITY;
                }
        ));
    }

    // FIXME again, this is stupid.. but without a rewrite I cant use CraftingRecipeTracker due to the fact some recipes arent catalogued :')
    private RecipeTarget getRecipeTarget(Item item) {
        ResourceTask task = TaskCatalogue.getItemTask(item, 1);
        if (task instanceof CraftInTableTask craftInTableTask) {
            return craftInTableTask.getRecipeTargets()[0];
        } else if (task instanceof CraftInInventoryTask craftInInventoryTask) {
            return craftInInventoryTask.getRecipeTarget();
        }

        throw new IllegalStateException("Item isn't cataloged");
        //  return mod.getCraftingRecipeTracker().getFirstRecipeTarget(item, 1);
    }

    private void addPickupImportantItemsTask(AltoClef mod) {
        List<Item> importantItems = List.of(Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.GOLDEN_HELMET, Items.DIAMOND_SWORD,
                Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, Items.FLINT_AND_STEEL);

        gatherResources.add(new ActionPriorityTask(mod1 -> {
            Pair<Task, Double> pair = new Pair<>(null, 0d);

            for (Item item : importantItems) {
                if (item == Items.IRON_PICKAXE && mod1.getItemStorage().hasItem(Items.DIAMOND_PICKAXE)) continue;

                if (!mod1.getItemStorage().hasItem(item) && mod1.getEntityTracker().itemDropped(item)) {
                    pair.setLeft(new PickupDroppedItemTask(item, 1));
                    pair.setRight(8000d);

                    return pair;
                }
            }

            return pair;
        }));
    }

    //TODO add some checks for how many food we already have, how hungry we are etc...
    private void addCookFoodTasks(AltoClef mod) {
        gatherResources.add(new ActionPriorityTask(a -> {
            Pair<Task, Double> pair = new Pair<>(null, Double.NEGATIVE_INFINITY);

            int rawFoodCount = a.getItemStorage().getItemCount(ItemHelper.RAW_FOODS);
            int readyFoodCount = a.getItemStorage().getItemCount(ItemHelper.COOKED_FOODS) + a.getItemStorage().getItemCount(Items.BREAD);

            double priority = rawFoodCount >= 8 ? 450 : rawFoodCount * 25;

            if (lastTask instanceof SmeltInSmokerTask) {
                priority = Double.POSITIVE_INFINITY;
            }

            if (readyFoodCount > 5 && priority < Double.POSITIVE_INFINITY) {
                // always smelt the food at some point
                // smelt all at once to waste as little coal as possible
                priority = 0.01;
            }

            for (CollectMeatTask.CookableFoodTarget cookable : COOKABLE_FOODS) {
                int rawCount = a.getItemStorage().getItemCount(cookable.getRaw());
                if (rawCount == 0) continue;

                int toSmelt = rawCount + a.getItemStorage().getItemCount(cookable.getCooked());

                SmeltTarget target = new SmeltTarget(new ItemTarget(cookable.cookedFood, toSmelt), new ItemTarget(cookable.rawFood, rawCount));

                pair.setLeft(new SmeltInSmokerTask(target));
                pair.setRight(priority);

                return pair;
            }

            return pair;
        }, a -> StorageHelper.miningRequirementMet(MiningRequirement.STONE), true, false, false));
    }

    private void addSmeltTasks(AltoClef mod) {
        ItemStorageTracker itemStorage = mod.getItemStorage();

        gatherResources.add(new ActionPriorityTask(a -> {
            Pair<Task, Double> pair = new Pair<>(null, Double.NEGATIVE_INFINITY);

            boolean hasSufficientPickaxe = itemStorage.hasItem(Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE);

            int neededIron = 11;
            if (itemStorage.hasItem(Items.FLINT_AND_STEEL)) {
                neededIron--;
            }
            if (hasItem(mod, Items.SHIELD)) {
                neededIron--;
            }
            if (hasSufficientPickaxe) {
                neededIron -= 3;
            }

            neededIron -= Math.min(itemStorage.getItemCount(Items.BUCKET, Items.WATER_BUCKET, Items.LAVA_BUCKET), 2) * 3;


            int count = itemStorage.getItemCount(Items.RAW_IRON);
            int includedCount = count + itemStorage.getItemCount(Items.IRON_INGOT);

            if ((!hasSufficientPickaxe && includedCount >= 3) || (!hasItem(mod, Items.SHIELD) && includedCount >= 1) || includedCount >= neededIron) {
                int toSmelt = Math.min(includedCount, neededIron);
                if (toSmelt <= 0) return pair;

                pair.setLeft(new SmeltInFurnaceTask(new SmeltTarget(new ItemTarget(Items.IRON_INGOT, toSmelt), new ItemTarget(Items.RAW_IRON, toSmelt))));
                pair.setRight(350d);
                return pair;
            }


            return pair;
        }, a -> itemStorage.hasItem(Items.RAW_IRON), true, false, false));

        gatherResources.add(new ActionPriorityTask(
                a -> new SmeltInFurnaceTask(new SmeltTarget(new ItemTarget(Items.GOLD_INGOT, 5), new ItemTarget(Items.RAW_GOLD, 5))),
                () -> 140, a -> itemStorage.getItemCount(Items.RAW_GOLD, Items.GOLD_INGOT) >= 5 && !itemStorage.hasItem(Items.GOLDEN_HELMET),
                true, true, false
        ));

    }

    private void addLootChestsTasks(AltoClef mod) {
        // TODO lower priority is player already has most of the items
        gatherResources.add(new ActionPriorityTask(a -> {
            Pair<Task, Double> pair = new Pair<>(null, Double.NEGATIVE_INFINITY);

            Optional<BlockPos> chest = locateClosestUnopenedChest(mod);
            if (chest.isEmpty()) {
                return pair;
            }

            double dst = Math.sqrt(BlockPosVer.getSquaredDistance(chest.get(),mod.getPlayer().getPos()));
            pair.setRight(30d / dst * 175);
            pair.setLeft(new GetToBlockTask(chest.get().up()));

            return pair;
        }, a -> true, false, false, true));


        gatherResources.add(new ActionPriorityTask(m -> {
            Pair<Task, Double> pair = new Pair<>(null, Double.NEGATIVE_INFINITY);

            Optional<BlockPos> chest = locateClosestUnopenedChest(mod);
            if (chest.isEmpty()) return pair;

            if (LookHelper.cleanLineOfSight(mod.getPlayer(), chest.get(), 10) && chest.get().isWithinDistance(mod.getPlayer().getEyePos(), 5)) {
                pair.setLeft(new LootContainerTask(chest.get(), lootableItems(mod), noCurseOfBinding));
                pair.setRight(Double.POSITIVE_INFINITY);
            }

            return pair;
        }, a -> true, true, false, true));

    }

    private void addCollectFoodTask(AltoClef mod) {
        List<Item> food = new LinkedList<>(ItemHelper.cookableFoodMap.values());
        food.addAll(ItemHelper.cookableFoodMap.keySet());
        food.addAll(List.of(Items.WHEAT, Items.BREAD));

        gatherResources.add(new ResourcePriorityTask(
                new CollectFoodPriorityCalculator(mod, config.foodUnits),
                a -> StorageHelper.miningRequirementMet(MiningRequirement.STONE)
                        && mod.getItemStorage().hasItem(Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD)
                        && CollectFoodTask.calculateFoodPotential(mod) < config.foodUnits,

                new CollectFoodTask(config.foodUnits), ItemTarget.of(food.toArray(new Item[]{}))
        ));

        gatherResources.add(new ActionPriorityTask(mod12 -> {
            Pair<Task, Double> pair = new Pair<>(null, 0d);

            pair.setLeft(TaskCatalogue.getItemTask(Items.WHEAT, mod12.getItemStorage().getItemCount(Items.HAY_BLOCK) * 9 + mod12.getItemStorage().getItemCount(Items.WHEAT)));

            pair.setRight(10d);
            if (StorageHelper.calculateInventoryFoodScore() < 5) {
                pair.setRight(270d);
            }

            return pair;
        }, a -> mod.getItemStorage().hasItem(Items.HAY_BLOCK)));

        gatherResources.add(new ActionPriorityTask(mod1 -> {
            Pair<Task, Double> pair = new Pair<>(null, 0d);

            pair.setLeft(TaskCatalogue.getItemTask("bread", mod1.getItemStorage().getItemCount(Items.WHEAT) / 3 + mod1.getItemStorage().getItemCount(Items.BREAD)));

            pair.setRight(5d);
            if (StorageHelper.calculateInventoryFoodScore() < 5) {
                pair.setRight(250d);
            }

            return pair;
        }, a -> mod.getItemStorage().getItemCount(Items.WHEAT) >= 3));
    }

    private void addOreMiningTasks() {
        gatherResources.add(getOrePriorityTask(Items.COAL, MiningRequirement.STONE, 1050, 250, 5, 4, 7));
        gatherResources.add(getOrePriorityTask(Items.RAW_IRON, MiningRequirement.STONE, 1050, 250, 5, 11, 11));
        gatherResources.add(getOrePriorityTask(Items.RAW_GOLD, MiningRequirement.IRON, 1050, 250, 5, 5, 5));
        gatherResources.add(getOrePriorityTask(Items.DIAMOND, MiningRequirement.IRON, 1050, 250, 5, 27, 30));
    }

    private PriorityTask getOrePriorityTask(Item item, MiningRequirement requirement, int multiplier, int unneededMultiplier, int unneededThreshold, int minCount, int maxCount) {
        Block[] blocks = mapOreItemToBlocks(item);

        return new MineBlockPriorityTask(blocks, new Item[]{item}, requirement,
                new DistanceOrePriorityCalculator(item, multiplier, unneededMultiplier, unneededThreshold, minCount, maxCount));
    }

    /**
     * Adds stone tools not including pickaxe
     */
    private void addStoneToolsTasks() {
        gatherResources.add(new ResourcePriorityTask(StaticItemPriorityCalculator.of(520),
                altoClef -> StorageHelper.miningRequirementMet(MiningRequirement.STONE),
                true, true, false,
                ItemTarget.of(Items.STONE_AXE, Items.STONE_SWORD, Items.STONE_SHOVEL, Items.STONE_HOE)
        ));

        gatherResources.add(new CraftItemPriorityTask(300, getRecipeTarget(Items.STONE_SWORD),
                a -> StorageHelper.miningRequirementMet(MiningRequirement.STONE)
                        && !mod.getItemStorage().hasItem(Items.DIAMOND_SWORD, Items.IRON_SWORD)
        ));

        gatherResources.add(new CraftItemPriorityTask(300, getRecipeTarget(Items.STONE_AXE),
                a -> StorageHelper.miningRequirementMet(MiningRequirement.STONE)
                        && !mod.getItemStorage().hasItem(Items.DIAMOND_AXE, Items.IRON_AXE)
        ));
    }

    /**
     * adds tasks for CRAFTING diamond armor from already obtained diamonds.
     * the helmet isnt crafted because we have a golden one
     */

    private void addDiamondArmorTasks(AltoClef mod) {
        gatherResources.add(new CraftItemPriorityTask(350, getRecipeTarget(Items.DIAMOND_CHESTPLATE), a -> mod.getItemStorage().getItemCount(Items.DIAMOND) >= 8));
        gatherResources.add(new CraftItemPriorityTask(300, getRecipeTarget(Items.DIAMOND_LEGGINGS), a -> mod.getItemStorage().getItemCount(Items.DIAMOND) >= 7));
        gatherResources.add(new CraftItemPriorityTask(220, getRecipeTarget(Items.DIAMOND_BOOTS), a -> mod.getItemStorage().getItemCount(Items.DIAMOND) >= 5));
    }

    private void addPickaxeTasks(AltoClef mod) {
        gatherResources.add(new ResourcePriorityTask(StaticItemPriorityCalculator.of(400),
                a -> !(mod.getItemStorage().hasItem(Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE)),
                ItemTarget.of(Items.WOODEN_PICKAXE)));

        gatherResources.add(new RecraftableItemPriorityTask(410, 10_000, getRecipeTarget(Items.STONE_PICKAXE),
                a -> {
                    List<Slot> list = mod.getItemStorage().getSlotsWithItemPlayerInventory(false);
                    boolean hasSafeIronPick = false;
                    for (Slot slot : list) {
                        if (slot.getInventorySlot() == -1) continue;
                        ItemStack stack = mod.getPlayer().getInventory().getStack(slot.getInventorySlot());
                        if (!StorageHelper.shouldSaveStack(mod, Blocks.STONE, stack) && stack.getItem().equals(Items.IRON_PICKAXE)) {
                            hasSafeIronPick = true;
                            break;
                        }
                    }

                    return StorageHelper.miningRequirementMet(MiningRequirement.WOOD) && !mod.getItemStorage().hasItem(Items.STONE_PICKAXE) && !hasSafeIronPick && !mod.getItemStorage().hasItem(Items.DIAMOND_PICKAXE);
                }));

        gatherResources.add(new CraftItemPriorityTask(420, getRecipeTarget(Items.IRON_PICKAXE),
                a -> !mod.getItemStorage().hasItem(Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE) && mod.getItemStorage().getItemCount(Items.IRON_INGOT) >= 3));


        gatherResources.add(new CraftItemPriorityTask(430, getRecipeTarget(Items.DIAMOND_PICKAXE), a -> mod.getItemStorage().getItemCount(Items.DIAMOND) >= 3));
    }

    /**
     * Checks if the task is finished.
     *
     * @return True if the task is finished, false otherwise.
     */
    @Override
    public boolean isFinished() {
        if (getInstance().currentScreen instanceof CreditsScreen) {
            Debug.logInternal("isFinished - Current screen is CreditsScreen");
            return true;
        }

        if (WorldHelper.getCurrentDimension() == Dimension.OVERWORLD && dragonIsDead) {
            Debug.logInternal("isFinished - Dragon is dead in the Overworld");
            return true;
        }

        Debug.logInternal("isFinished - Returning false");
        return false;
    }

    /**
     * Checks if the mod needs building materials.
     *
     * @param mod The AltoClef mod instance.
     * @return True if building materials are needed, false otherwise.
     */
    private boolean needsBuildingMaterials(AltoClef mod) {
        int materialCount = StorageHelper.getBuildingMaterialCount();
        boolean shouldForce = isTaskRunning(mod, buildMaterialsTask);

        // Check if the material count is below the minimum required count
        // or if the build materials task should be forced.
        if (materialCount < config.minBuildMaterialCount || shouldForce) {
            Debug.logInternal("Building materials needed: " + materialCount);
            Debug.logInternal("Force build materials: " + shouldForce);
            return true;
        } else {
            Debug.logInternal("Building materials not needed");
            return false;
        }
    }

    /**
     * Updates the cached end items based on the dropped items in the entity tracker.
     *
     * @param mod The AltoClef mod instance.
     */
    private void updateCachedEndItems(AltoClef mod) {
        List<ItemEntity> droppedItems = mod.getEntityTracker().getDroppedItems();

        // If there are no dropped items and the cache wait time has not elapsed, return.
        if (droppedItems.isEmpty() && !cachedEndItemNothingWaitTime.elapsed()) {
            Debug.logInternal("No dropped items and cache wait time not elapsed.");
            return;
        }

        // Reset the cache wait time and clear the cached end item drops.
        cachedEndItemNothingWaitTime.reset();
        cachedEndItemDrops.clear();

        for (ItemEntity entity : droppedItems) {
            Item item = entity.getStack().getItem();
            int count = entity.getStack().getCount();

            cachedEndItemDrops.put(item, cachedEndItemDrops.getOrDefault(item, 0) + count);

            Debug.logInternal("Added dropped item: " + item + " with count: " + count);
        }
    }

    /**
     * Retrieves a list of lootable items based on certain conditions.
     *
     * @param mod The AltoClef mod instance.
     * @return The list of lootable items.
     */
    private List<Item> lootableItems(AltoClef mod) {
        List<Item> lootable = new ArrayList<>();

        // Add initial lootable items
        lootable.add(Items.APPLE);
        lootable.add(Items.GOLDEN_APPLE);
        lootable.add(Items.ENCHANTED_GOLDEN_APPLE);
        lootable.add(Items.GOLDEN_CARROT);
        lootable.add(Items.OBSIDIAN);
        lootable.add(Items.STICK);
        lootable.add(Items.COAL);
        lootable.addAll(Arrays.stream(ItemHelper.LOG).toList());

        lootable.add(Items.BREAD);

        // Check if golden helmet is equipped or available in inventory
        boolean isGoldenHelmetEquipped = StorageHelper.isArmorEquipped(Items.GOLDEN_HELMET);
        boolean hasGoldenHelmet = mod.getItemStorage().hasItemInventoryOnly(Items.GOLDEN_HELMET);

        if (!mod.getItemStorage().hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE)) {
            lootable.add(Items.IRON_PICKAXE);
        }
        if (mod.getItemStorage().getItemCount(Items.BUCKET, Items.WATER_BUCKET, Items.LAVA_BUCKET) < 2) {
            lootable.add(Items.BUCKET);
        }

        // Check if there are enough gold ingots
        boolean hasEnoughGoldIngots = mod.getItemStorage().getItemCountInventoryOnly(Items.GOLD_INGOT) >= 5;

        // Add golden helmet if not equipped or available in inventory
        if (!isGoldenHelmetEquipped && !hasGoldenHelmet) {
            lootable.add(Items.GOLDEN_HELMET);
        }


        if ((!hasEnoughGoldIngots && !isGoldenHelmetEquipped && !hasGoldenHelmet) || config.barterPearlsInsteadOfEndermanHunt) {
            lootable.add(Items.GOLD_INGOT);
        }

        // Add flint and steel and fire charge if not available in inventory
        if (!mod.getItemStorage().hasItemInventoryOnly(Items.FLINT_AND_STEEL)) {
            lootable.add(Items.FLINT_AND_STEEL);
            if (!mod.getItemStorage().hasItemInventoryOnly(Items.FIRE_CHARGE)) {
                lootable.add(Items.FIRE_CHARGE);
            }
        }

        // Add iron ingot if neither bucket nor water bucket is available in inventory
        if (!mod.getItemStorage().hasItemInventoryOnly(Items.BUCKET) && !mod.getItemStorage().hasItemInventoryOnly(Items.WATER_BUCKET)) {
            lootable.add(Items.IRON_INGOT);
        }

        // Add diamond if item targets for eye gear are not met in inventory
        if (!StorageHelper.itemTargetsMetInventory(COLLECT_EYE_GEAR_MIN)) {
            lootable.add(Items.DIAMOND);
        }

        // Add flint if not available in inventory
        if (!mod.getItemStorage().hasItemInventoryOnly(Items.FLINT)) {
            lootable.add(Items.FLINT);
        }

        Debug.logInternal("Lootable items: " + lootable); // Logging statement

        return lootable;
    }

    /**
     * Overrides the onStop method.
     * Performs necessary cleanup and logging when the task is interrupted or stopped.
     *
     * @param interruptTask The task that interrupted the current task.
     */
    @Override
    protected void onStop(Task interruptTask) {
        mod.getExtraBaritoneSettings().canWalkOnEndPortal(false);

        mod.getBehaviour().pop();

        Debug.logInternal("Stopped onStop method");
        Debug.logInternal("canWalkOnEndPortal set to false");
        Debug.logInternal("Behaviour popped");
        Debug.logInternal("Stopped tracking BED blocks");
        Debug.logInternal("Stopped tracking TRACK_BLOCKS");
    }

    /**
     * Check if the given task is equal to this BeatMinecraftTask.
     *
     * @param other The task to compare.
     * @return True if the tasks are equal, false otherwise.
     */
    @Override
    protected boolean isEqual(Task other) {
        boolean isSameTask = other instanceof BeatMinecraftTask;

        if (!isSameTask)
            Debug.logInternal("The 'other' task is not of type BeatMinecraftTask");

        return isSameTask;
    }

    /**
     * Returns a debug string for the object.
     *
     * @return The debug string.
     */
    @Override
    protected String toDebugString() {
        return "Beating the game (Miran version).";
    }

    /**
     * Checks if the end portal has been found.
     *
     * @param mod             The instance of the AltoClef mod.
     * @param endPortalCenter The center position of the end portal.
     * @return True if the end portal has been found, false otherwise.
     */
    private boolean endPortalFound(AltoClef mod, BlockPos endPortalCenter) {
        if (endPortalCenter == null) {
            Debug.logInternal("End portal center is null");
            return false;
        }
        return true;

        /*if (endPortalOpened(mod, endPortalCenter)) {
            Debug.logInternal("End portal is already opened");
            return true;
          }

        List<BlockPos> frameBlocks = getFrameBlocks(endPortalCenter);

        for (BlockPos frame : frameBlocks) {
            // Check if the frame block is a valid end portal frame
            if (mod.getBlockTracker().blockIsValid(frame, Blocks.END_PORTAL_FRAME)) {
                Debug.logInternal("Found valid end portal frame at " + frame.toString());
                return true;
            }
        }

        Debug.logInternal("No valid end portal frame found");
        return false;*/
    }

    /**
     * Checks if the end portal is opened.
     *
     * @param mod             The AltoClef mod instance.
     * @param endPortalCenter The center position of the end portal.
     * @return True if the end portal is opened, false otherwise.
     */
    private boolean endPortalOpened(AltoClef mod, BlockPos endPortalCenter) {
        if (endPortalOpened && endPortalCenter != null) {
            BlockScanner blockTracker = mod.getBlockScanner();

            if (blockTracker != null) {
                boolean isValid = blockTracker.isBlockAtPosition(endPortalCenter, Blocks.END_PORTAL);

                Debug.logInternal("End Portal is " + (isValid ? "valid" : "invalid"));
                return isValid;
            }
        }

        Debug.logInternal("End Portal is not opened yet");
        return false;
    }

    /**
     * Checks if the bed spawn location is near the given end portal center.
     *
     * @param mod             The AltoClef mod instance.
     * @param endPortalCenter The center position of the end portal.
     * @return True if the bed spawn location is near the end portal, false otherwise.
     */
    private boolean spawnSetNearPortal(AltoClef mod, BlockPos endPortalCenter) {
        if (bedSpawnLocation == null) {
            Debug.logInternal("Bed spawn location is null");
            return false;
        }

        BlockScanner blockTracker = mod.getBlockScanner();
        boolean isValid = blockTracker.isBlockAtPosition(bedSpawnLocation, ItemHelper.itemsToBlocks(ItemHelper.BED));

        Debug.logInternal("Spawn set near portal: " + isValid);

        return isValid;
    }

    /**
     * Finds the closest unopened chest.
     *
     * @param mod The AltoClef mod instance.
     * @return An Optional containing the closest BlockPos of the unopened chest, or empty if not found.
     */
    private Optional<BlockPos> locateClosestUnopenedChest(AltoClef mod) {
        if (!WorldHelper.getCurrentDimension().equals(Dimension.OVERWORLD)) {
            return Optional.empty();
        }

        // Find the nearest tracking block position
        return mod.getBlockScanner().getNearestBlock(blockPos -> {
            if (blacklistedChests.contains(blockPos)) return false;

            boolean isUnopenedChest = WorldHelper.isUnopenedChest(blockPos);
            boolean isWithinDistance = mod.getPlayer().getBlockPos().isWithinDistance(blockPos, 150);
            boolean isLootableChest = canBeLootablePortalChest(mod, blockPos);

            // TODO make more sophisticated
            //dont open spawner chests
            Optional<BlockPos> nearestSpawner = mod.getBlockScanner().getNearestBlock(WorldHelper.toVec3d(blockPos), Blocks.SPAWNER);
            if (nearestSpawner.isPresent() && nearestSpawner.get().isWithinDistance(blockPos, 6)) {
                blacklistedChests.add(blockPos);
                return false;
            }

            // TODO use shipwreck finder instead

            Box box = new Box(blockPos.getX() - 5, blockPos.getY() - 5, blockPos.getZ() - 5,
                    blockPos.getX() + 5, blockPos.getY() + 5, blockPos.getZ() + 5);

            Stream<BlockState> states = BlockPos.stream(box).map(pos -> mod.getWorld().getBlockState(pos));

            if (states.anyMatch((state) -> state.getBlock().equals(Blocks.WATER))) {
                blacklistedChests.add(blockPos);
                return false;
            }

            Debug.logInternal("isUnopenedChest: " + isUnopenedChest);
            Debug.logInternal("isWithinDistance: " + isWithinDistance);
            Debug.logInternal("isLootableChest: " + isLootableChest);

            return isUnopenedChest && isWithinDistance && isLootableChest;
        }, Blocks.CHEST);
    }

    /**
     * This method is called when the mod starts.
     * It performs several tasks to set up the mod.
     */
    @Override
    protected void onStart() {
        resetTimers();
        mod.getBehaviour().push();
        addThrowawayItemsWarning(mod);
        addProtectedItems(mod);
        allowWalkingOnEndPortal(mod);
        avoidDragonBreath(mod);
        avoidBreakingBed(mod);

        mod.getBehaviour().avoidBlockBreaking((pos) -> mod.getWorld().getBlockState(pos).getBlock().equals(Blocks.NETHER_PORTAL));
    }

    /**
     * Resets the timers.
     */
    private void resetTimers() {
        timer1.reset();
        timer2.reset();
        timer3.reset();
    }

    /**
     * Adds a warning message if certain conditions are not met.
     *
     * @param mod The AltoClef mod instance.
     */
    private void addThrowawayItemsWarning(AltoClef mod) {
        // Warning message tail that will be appended to the warning message.
        String settingsWarningTail = "in \".minecraft/altoclef_settings.json\". @gamer may break if you don't add this! (sorry!)";

        // Check if "end_stone" is not part of the "throwawayItems" list and log a warning.
        if (!ArrayUtils.contains(mod.getModSettings().getThrowawayItems(mod), Items.END_STONE)) {
            Debug.logWarning("\"end_stone\" is not part of your \"throwawayItems\" list " + settingsWarningTail);
        }

        // Check if "throwawayUnusedItems" is not set to true and log a warning.
        if (!mod.getModSettings().shouldThrowawayUnusedItems()) {
            Debug.logWarning("\"throwawayUnusedItems\" is not set to true " + settingsWarningTail);
        }
    }

    /**
     * Adds protected items to the behaviour of the given AltoClef instance.
     *
     * @param mod The AltoClef instance.
     */
    private void addProtectedItems(AltoClef mod) {
        mod.getBehaviour().addProtectedItems(Items.ENDER_EYE, Items.BLAZE_ROD, Items.BLAZE_POWDER, Items.ENDER_PEARL, Items.CRAFTING_TABLE, Items.IRON_INGOT, Items.WATER_BUCKET, Items.FLINT_AND_STEEL, Items.SHIELD, Items.SHEARS, Items.BUCKET, Items.GOLDEN_HELMET, Items.SMOKER, Items.FURNACE);

        // Add protected items using helper classes
        mod.getBehaviour().addProtectedItems(ItemHelper.BED);
        mod.getBehaviour().addProtectedItems(ItemHelper.IRON_ARMORS);
        mod.getBehaviour().addProtectedItems(ItemHelper.LOG);

        Debug.logInternal("Protected items added successfully.");
    }

    /**
     * Allows the player to walk on an end portal block.
     *
     * @param mod The AltoClef mod instance.
     */
    private void allowWalkingOnEndPortal(AltoClef mod) {
        mod.getBehaviour().allowWalkingOn(blockPos -> {
            if (enterindEndPortal && (mod.getChunkTracker().isChunkLoaded(blockPos))) {
                BlockState blockState = mod.getWorld().getBlockState(blockPos);
                boolean isEndPortal = blockState.getBlock() == Blocks.END_PORTAL;
                if (isEndPortal) {
                    Debug.logInternal("Walking on End Portal at " + blockPos.toString());
                }
                return isEndPortal;

            }
            return false;
        });
    }

    /**
     * Avoids walking through dragon breath in the End dimension.
     *
     * @param mod The AltoClef mod instance.
     */
    private void avoidDragonBreath(AltoClef mod) {
        mod.getBehaviour().avoidWalkingThrough(blockPos -> {
            Dimension currentDimension = WorldHelper.getCurrentDimension();
            boolean isEndDimension = currentDimension == Dimension.END;
            boolean isTouchingDragonBreath = dragonBreathTracker.isTouchingDragonBreath(blockPos);

            if (isEndDimension && !escapingDragonsBreath && isTouchingDragonBreath) {
                Debug.logInternal("Avoiding dragon breath at blockPos: " + blockPos);
                return true;
            } else {
                return false;
            }
        });
    }

    /**
     * Avoid breaking the bed by adding a behavior to avoid breaking specific block positions.
     *
     * @param mod The AltoClef mod instance.
     */
    private void avoidBreakingBed(AltoClef mod) {
        mod.getBehaviour().avoidBlockBreaking(blockPos -> {
            if (bedSpawnLocation != null) {
                // Get the head and foot positions of the bed
                BlockPos bedHead = WorldHelper.getBedHead(bedSpawnLocation);
                BlockPos bedFoot = WorldHelper.getBedFoot(bedSpawnLocation);

                boolean shouldAvoidBreaking = blockPos.equals(bedHead) || blockPos.equals(bedFoot);

                if (shouldAvoidBreaking) {
                    Debug.logInternal("Avoiding breaking bed at block position: " + blockPos);
                }

                return shouldAvoidBreaking;
            }

            return false;
        });
    }

    private void blackListDangerousBlock(AltoClef mod, Block block) {
        Optional<BlockPos> nearestTracking = mod.getBlockScanner().getNearestBlock(block);

        if (nearestTracking.isPresent()) {
            Iterable<Entity> entities = mod.getWorld().getEntities();
            for (Entity entity : entities) {

                if (mod.getBlockScanner().isUnreachable(nearestTracking.get()) || !(entity instanceof HostileEntity))
                    continue;

                if (mod.getPlayer().squaredDistanceTo(entity.getPos()) < 150 && nearestTracking.get().isWithinDistance(entity.getPos(), 30)) {

                    Debug.logMessage("Blacklisting dangerous " + block.toString());
                    mod.getBlockScanner().requestBlockUnreachable(nearestTracking.get(), 0);
                }
            }
        }
    }

    @Override
    protected Task onTick() {
        ItemStorageTracker itemStorage = mod.getItemStorage();

        double blockPlacementPenalty = 10;
        if (StorageHelper.getNumberOfThrowawayBlocks(mod) > 128) {
            blockPlacementPenalty = 5;
        } else if (StorageHelper.getNumberOfThrowawayBlocks(mod) > 64) {
            blockPlacementPenalty = 7.5;
        }

        mod.getClientBaritoneSettings().blockPlacementPenalty.value = blockPlacementPenalty;

        if (mod.getPlayer().getMainHandStack().getItem() instanceof EnderEyeItem && !openingEndPortal) {
            List<ItemStack> itemStacks = itemStorage.getItemStacksPlayerInventory(true);
            for (ItemStack itemStack : itemStacks) {
                Item item = itemStack.getItem();
                if (item instanceof SwordItem) {
                    mod.getSlotHandler().forceEquipItem(item);
                }
            }
        }


        boolean shouldSwap = false;
        boolean hasInHotbar = false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mod.getPlayer().getInventory().getStack(i);

            //FIXME do some more general approach
            if (stack.getItem().equals(Items.IRON_PICKAXE) && StorageHelper.shouldSaveStack(mod, Blocks.STONE, stack)) {
                shouldSwap = true;
            }
            if (stack.getItem().equals(Items.STONE_PICKAXE)) {
                hasInHotbar = true;
            }
        }

        if (shouldSwap && !hasInHotbar) {
            if (itemStorage.hasItem(Items.STONE_PICKAXE)) {
                mod.getSlotHandler().forceEquipItem(Items.STONE_PICKAXE);
            }
        }


        boolean eyeGearSatisfied = StorageHelper.isArmorEquippedAll(COLLECT_EYE_ARMOR);
        boolean ironGearSatisfied = StorageHelper.isArmorEquippedAll(COLLECT_IRON_ARMOR);

        if (itemStorage.hasItem(Items.DIAMOND_PICKAXE)) {
            mod.getBehaviour().setBlockBreakAdditionalPenalty(1.2);
        } else {
            mod.getBehaviour().setBlockBreakAdditionalPenalty(mod.getClientBaritoneSettings().blockBreakAdditionalPenalty.defaultValue);
        }
        Predicate<Task> isCraftingTableTask = task -> {
            if (task instanceof DoStuffInContainerTask cont) {
                return cont.getContainerTarget().matches(Items.CRAFTING_TABLE);
            }
            return false;
        };
        List<BlockPos> craftingTables = mod.getBlockScanner().getKnownLocations(Blocks.CRAFTING_TABLE);
        for (BlockPos craftingTable : craftingTables) {
            if (itemStorage.hasItem(Items.CRAFTING_TABLE) && !thisOrChildSatisfies(isCraftingTableTask) && (!mod.getBlockScanner().isUnreachable(craftingTable))) {
                Debug.logMessage("Blacklisting extra crafting table.");
                mod.getBlockScanner().requestBlockUnreachable(craftingTable, 0);

            }
            if (!mod.getBlockScanner().isUnreachable(craftingTable)) {
                BlockState craftingTablePosUp = mod.getWorld().getBlockState(craftingTable.up(2));
                if (mod.getEntityTracker().entityFound(WitchEntity.class)) {
                    Optional<Entity> witch = mod.getEntityTracker().getClosestEntity(WitchEntity.class);
                    if (witch.isPresent() && (craftingTable.isWithinDistance(witch.get().getPos(), 15))) {
                        Debug.logMessage("Blacklisting witch crafting table.");
                        mod.getBlockScanner().requestBlockUnreachable(craftingTable, 0);

                    }
                }
                if (craftingTablePosUp.getBlock() == Blocks.WHITE_WOOL) {
                    Debug.logMessage("Blacklisting pillage crafting table.");
                    mod.getBlockScanner().requestBlockUnreachable(craftingTable, 0);
                }
            }
        }
        List<BlockPos> smokers = mod.getBlockScanner().getKnownLocations(Blocks.SMOKER);

        for (BlockPos smoker : smokers) {
            if (itemStorage.hasItem(Items.SMOKER) && !mod.getBlockScanner().isUnreachable(smoker)) {
                Debug.logMessage("Blacklisting extra smoker.");
                mod.getBlockScanner().requestBlockUnreachable(smoker, 0);
            }
        }

        List<BlockPos> furnaces = mod.getBlockScanner().getKnownLocations(Blocks.FURNACE);

        for (BlockPos furnace : furnaces) {
            if (itemStorage.hasItem(Items.FURNACE) && !goToNetherTask.isActive() && !ranStrongholdLocator && !mod.getBlockScanner().isUnreachable(furnace)) {
                Debug.logMessage("Blacklisting extra furnace.");
                mod.getBlockScanner().requestBlockUnreachable(furnace, 0);
            }
        }

        List<BlockPos> logs = mod.getBlockScanner().getKnownLocations(ItemHelper.itemsToBlocks(ItemHelper.LOG));

        for (BlockPos log : logs) {
            Iterable<Entity> entities = mod.getWorld().getEntities();
            for (Entity entity : entities) {
                if (entity instanceof PillagerEntity && !mod.getBlockScanner().isUnreachable(log) && log.isWithinDistance(entity.getPos(), 40)) {
                    Debug.logMessage("Blacklisting pillage log.");
                    mod.getBlockScanner().requestBlockUnreachable(log, 0);
                }
            }
            if (log.getY() < 62 && !mod.getBlockScanner().isUnreachable(log) && !ironGearSatisfied && !eyeGearSatisfied) {
                Debug.logMessage("Blacklisting dangerous log.");
                mod.getBlockScanner().requestBlockUnreachable(log, 0);
            }
        }


        if (!ironGearSatisfied && !eyeGearSatisfied) {
            blackListDangerousBlock(mod, Blocks.DEEPSLATE_COAL_ORE);
            blackListDangerousBlock(mod, Blocks.COAL_ORE);
            blackListDangerousBlock(mod, Blocks.DEEPSLATE_IRON_ORE);
            blackListDangerousBlock(mod, Blocks.IRON_ORE);
        }

        List<Block> ancientCityBlocks = List.of(Blocks.DEEPSLATE_BRICKS, Blocks.SCULK, Blocks.SCULK_VEIN, Blocks.SCULK_SENSOR, Blocks.SCULK_SHRIEKER, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.CRACKED_DEEPSLATE_BRICKS, Blocks.SOUL_LANTERN, Blocks.DEEPSLATE_TILES, Blocks.POLISHED_DEEPSLATE);
        final int radius = 5;
        for (BlockPos pos : mod.getBlockScanner().getKnownLocations(ItemHelper.itemsToBlocks(ItemHelper.WOOL))) {

            searchLoop:
            for (int x = -radius; x < radius; x++) {
                for (int y = -radius; y < radius; y++) {
                    for (int z = -radius; z < radius; z++) {
                        BlockPos p = pos.add(x,y,z);
                        Block block = mod.getWorld().getBlockState(p).getBlock();

                        if (ancientCityBlocks.contains(block)) {
                            Debug.logMessage("Blacklisting ancient city wool " + pos);
                            mod.getBlockScanner().requestBlockUnreachable(pos, 0);
                            break searchLoop;
                        }
                    }
                }
            }
        }

        if (locateStrongholdTask.isActive() && WorldHelper.getCurrentDimension() == Dimension.OVERWORLD && !mod.getClientBaritone().getExploreProcess().isActive() && timer1.elapsed()) {
            timer1.reset();
        }
        if ((getOneBedTask != null && getOneBedTask.isActive() || (sleepThroughNightTask.isActive() && !itemStorage.hasItem(ItemHelper.BED))) && getBedTask == null && !mod.getClientBaritone().getExploreProcess().isActive() && timer3.elapsed()) {
            timer3.reset();
        }

        //armor quipping logic
        if (WorldHelper.getCurrentDimension() != Dimension.END && itemStorage.hasItem(Items.SHIELD) && !itemStorage.hasItemInOffhand(Items.SHIELD)) {
            return new EquipArmorTask(Items.SHIELD);
        }

        if (WorldHelper.getCurrentDimension() == Dimension.NETHER) {
            if (itemStorage.hasItem(Items.GOLDEN_HELMET)) {
                return new EquipArmorTask(Items.GOLDEN_HELMET);
            } else if (itemStorage.hasItem(Items.DIAMOND_HELMET) && !hasItem(mod, Items.GOLDEN_HELMET)) {
                return new EquipArmorTask(Items.DIAMOND_HELMET);
            }
        } else {
            if (itemStorage.hasItem(Items.DIAMOND_HELMET)) {
                return new EquipArmorTask(Items.DIAMOND_HELMET);
            }
        }

        if (itemStorage.hasItem(Items.DIAMOND_CHESTPLATE)) {
            return new EquipArmorTask(Items.DIAMOND_CHESTPLATE);
        }
        if (itemStorage.hasItem(Items.DIAMOND_LEGGINGS)) {
            return new EquipArmorTask(Items.DIAMOND_LEGGINGS);
        }
        if (itemStorage.hasItem(Items.DIAMOND_BOOTS)) {
            return new EquipArmorTask(Items.DIAMOND_BOOTS);
        }


        if (!StorageHelper.isBigCraftingOpen() && !StorageHelper.isFurnaceOpen() && !StorageHelper.isSmokerOpen() && !StorageHelper.isBlastFurnaceOpen() && !StorageHelper.isChestOpen()) {
            //can cause the bot to get stuck
            if (itemStorage.getItemCount(Items.FURNACE) > 1) {
                return new PlaceBlockNearbyTask(Blocks.FURNACE);
            }
            if (itemStorage.getItemCount(Items.CRAFTING_TABLE) > 1) {
                return new PlaceBlockNearbyTask(Blocks.CRAFTING_TABLE);
            }
            throwAwayItems(mod, Items.SAND, Items.RED_SAND);
            throwAwayItems(mod, Items.TORCH);
            throwAwayItems(mod, uselessItems.uselessItems);


            if (itemStorage.hasItem(Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE)) {
                throwAwayItems(mod, Items.WOODEN_PICKAXE);
            }
            if (itemStorage.hasItem(Items.DIAMOND_PICKAXE)) {
                throwAwayItems(mod, Items.IRON_PICKAXE, Items.STONE_PICKAXE);
            }

            if (itemStorage.hasItem(Items.DIAMOND_SWORD)) {
                throwAwayItems(mod, Items.STONE_SWORD, Items.IRON_SWORD);
            }

            if (itemStorage.hasItem(Items.GOLDEN_HELMET)) {
                throwAwayItems(mod, Items.RAW_GOLD, Items.GOLD_INGOT);
            }

            if (itemStorage.hasItem(Items.FLINT) || itemStorage.hasItem(Items.FLINT_AND_STEEL)) {
                throwAwayItems(mod, Items.GRAVEL);
            }
            if (itemStorage.hasItem(Items.FLINT_AND_STEEL)) {
                throwAwayItems(mod, Items.FLINT);
            }
            if (isTaskRunning(mod, getRidOfExtraWaterBucketTask)) {
                return getRidOfExtraWaterBucketTask;
            }
            if (itemStorage.getItemCount(Items.WATER_BUCKET) > 1) {
                getRidOfExtraWaterBucketTask = new GetRidOfExtraWaterBucketTask();
                return getRidOfExtraWaterBucketTask;
            }
            if (itemStorage.getItemCount(Items.FLINT_AND_STEEL) > 1) {
                throwAwayItems(mod, Items.FLINT_AND_STEEL);
            }
            if (itemStorage.getItemCount(ItemHelper.BED) > getTargetBeds(mod) && !endPortalFound(mod, endPortalCenterLocation) && WorldHelper.getCurrentDimension() != Dimension.END) {
                throwAwayItems(mod, ItemHelper.BED);
            }
        }


        /*
        if in the overworld:
          if end portal found:
            if end portal opened:
              @make sure we have iron gear and enough beds to kill the dragon first, considering whether that gear was dropped in the end
              @enter end portal
            else if we have enough eyes of ender:
              @fill in the end portal
          else if we have enough eyes of ender:
            @locate the end portal
          else:
            if we don't have diamond gear:
              if we have no food:
                @get a little bit of food
              @get diamond gear
            @go to the nether
        if in the nether:
          if we don't have enough blaze rods:
            @kill blazes till we do
          else if we don't have enough pearls:
            @kill enderman till we do
          else:
            @leave the nether
        if in the end:
          if we have a bed:
            @do bed strats
          else:
            @just hit the dragon normally
         */

        // By default, don't walk over end portals.
        enterindEndPortal = false;

        // End stuff.
        if (WorldHelper.getCurrentDimension() == Dimension.END) {
            if (!mod.getWorld().isChunkLoaded(0, 0)) {
                setDebugState("Waiting for chunks to load");
                return null;
            }

            // If we have bed, do bed strats, otherwise punk normally.
            updateCachedEndItems(mod);
            // Grab beds
            if (mod.getEntityTracker().itemDropped(ItemHelper.BED) && (needsBeds(mod) || WorldHelper.getCurrentDimension() == Dimension.END))
                return new PickupDroppedItemTask(new ItemTarget(ItemHelper.BED), true);
            // Grab tools
            if (!itemStorage.hasItem(Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE)) {
                if (mod.getEntityTracker().itemDropped(Items.IRON_PICKAXE))
                    return new PickupDroppedItemTask(Items.IRON_PICKAXE, 1);
                if (mod.getEntityTracker().itemDropped(Items.DIAMOND_PICKAXE))
                    return new PickupDroppedItemTask(Items.DIAMOND_PICKAXE, 1);
            }
            if (!itemStorage.hasItem(Items.WATER_BUCKET) && mod.getEntityTracker().itemDropped(Items.WATER_BUCKET))
                return new PickupDroppedItemTask(Items.WATER_BUCKET, 1);
            // Grab armor
            for (Item armorCheck : COLLECT_EYE_ARMOR_END) {
                if (!StorageHelper.isArmorEquipped(armorCheck)) {
                    if (itemStorage.hasItem(armorCheck)) {
                        setDebugState("Equipping armor.");
                        return new EquipArmorTask(armorCheck);
                    }
                    if (mod.getEntityTracker().itemDropped(armorCheck)) {
                        return new PickupDroppedItemTask(armorCheck, 1);
                    }
                }
            }
            // Dragons breath avoidance
            dragonBreathTracker.updateBreath(mod);
            for (BlockPos playerIn : WorldHelper.getBlocksTouchingPlayer()) {
                if (dragonBreathTracker.isTouchingDragonBreath(playerIn)) {
                    setDebugState("ESCAPE dragons breath");
                    escapingDragonsBreath = true;
                    return dragonBreathTracker.getRunAwayTask();
                }
            }
            escapingDragonsBreath = false;

            // If we find an ender portal, just GO to it!!!
            if (mod.getBlockScanner().anyFound(Blocks.END_PORTAL)) {
                setDebugState("WOOHOO");
                dragonIsDead = true;
                enterindEndPortal = true;
                if (!mod.getExtraBaritoneSettings().isCanWalkOnEndPortal()) {
                    mod.getExtraBaritoneSettings().canWalkOnEndPortal(true);
                }
                return new DoToClosestBlockTask(blockPos -> new GetToBlockTask(blockPos.up()), (pos) -> Math.abs(pos.getX()) + Math.abs(pos.getZ()) <= 1, Blocks.END_PORTAL);
            }
            if (itemStorage.hasItem(ItemHelper.BED) || mod.getBlockScanner().anyFound(ItemHelper.itemsToBlocks(ItemHelper.BED))) {
                setDebugState("Bed strats");
                return killDragonBedStratsTask;
            }
            setDebugState("No beds, regular strats.");
            return new KillEnderDragonTask();
        } else {
            // We're not in the end so reset our "end cache" timer
            cachedEndItemNothingWaitTime.reset();
        }

        // Check for end portals. Always.
        if (!endPortalOpened(mod, endPortalCenterLocation) && WorldHelper.getCurrentDimension() == Dimension.OVERWORLD) {
            Optional<BlockPos> endPortal = mod.getBlockScanner().getNearestBlock(Blocks.END_PORTAL);
            if (endPortal.isPresent()) {
                endPortalCenterLocation = endPortal.get();
                endPortalOpened = true;
            } else {
                // TODO: Test that this works, for some reason the bot gets stuck near the stronghold and it keeps "Searching" for the portal
                endPortalCenterLocation = doSimpleSearchForEndPortal(mod);
            }
        }
        if (isTaskRunning(mod, rePickupTask)) {
            return rePickupTask;
        }


        // Portable crafting table.
        // If we're NOT using our crafting table right now and there's one nearby, grab it.
        if (!endPortalOpened && WorldHelper.getCurrentDimension() != Dimension.END && config.rePickupCraftingTable && !itemStorage.hasItem(Items.CRAFTING_TABLE) && !thisOrChildSatisfies(isCraftingTableTask) && (mod.getBlockScanner().anyFound(blockPos -> WorldHelper.canBreak(blockPos) && WorldHelper.canReach(blockPos), Blocks.CRAFTING_TABLE) || mod.getEntityTracker().itemDropped(Items.CRAFTING_TABLE)) && pickupCrafting) {
            setDebugState("Picking up the crafting table while we are at it.");
            return new MineAndCollectTask(Items.CRAFTING_TABLE, 1, new Block[]{Blocks.CRAFTING_TABLE}, MiningRequirement.HAND);
        }
        if (config.rePickupSmoker && !endPortalOpened && WorldHelper.getCurrentDimension() != Dimension.END && !itemStorage.hasItem(Items.SMOKER) && (mod.getBlockScanner().anyFound(blockPos -> WorldHelper.canBreak(blockPos) && WorldHelper.canReach(blockPos), Blocks.SMOKER) || mod.getEntityTracker().itemDropped(Items.SMOKER)) && pickupSmoker) {
            setDebugState("Picking up the smoker while we are at it.");
            rePickupTask = new MineAndCollectTask(Items.SMOKER, 1, new Block[]{Blocks.SMOKER}, MiningRequirement.WOOD);
            return rePickupTask;
        }
        if (config.rePickupFurnace && !endPortalOpened && WorldHelper.getCurrentDimension() != Dimension.END && !itemStorage.hasItem(Items.FURNACE) && (mod.getBlockScanner().anyFound(blockPos -> WorldHelper.canBreak(blockPos) && WorldHelper.canReach(blockPos), Blocks.FURNACE) || mod.getEntityTracker().itemDropped(Items.FURNACE)) && !goToNetherTask.isActive() && !ranStrongholdLocator && pickupFurnace) {
            setDebugState("Picking up the furnace while we are at it.");
            rePickupTask = new MineAndCollectTask(Items.FURNACE, 1, new Block[]{Blocks.FURNACE}, MiningRequirement.WOOD);
            return rePickupTask;
        }
        pickupFurnace = false;
        pickupSmoker = false;
        pickupCrafting = false;

        // Sleep through night.
        if (config.sleepThroughNight && !endPortalOpened && WorldHelper.getCurrentDimension() == Dimension.OVERWORLD) {
            if (WorldHelper.canSleep()) {
                if (timer2.elapsed()) {
                    timer2.reset();
                }

                if (timer2.getDuration() >= 30 && !mod.getPlayer().isSleeping()) {
                    if (mod.getEntityTracker().itemDropped(ItemHelper.BED) && needsBeds(mod)) {
                        setDebugState("Resetting sleep through night task.");
                        return new PickupDroppedItemTask(new ItemTarget(ItemHelper.BED), true);
                    }
                    if (anyBedsFound(mod)) {
                        setDebugState("Resetting sleep through night task.");
                        return new DoToClosestBlockTask(DestroyBlockTask::new, ItemHelper.itemsToBlocks(ItemHelper.BED));
                    }
                }

                setDebugState("Sleeping through night");
                return sleepThroughNightTask;
            }
            if (!itemStorage.hasItem(ItemHelper.BED) && (mod.getBlockScanner().anyFound(blockPos -> WorldHelper.canBreak(blockPos), ItemHelper.itemsToBlocks(ItemHelper.BED)) || isTaskRunning(mod, getOneBedTask))) {
                setDebugState("Getting one bed to sleep in at night.");
                return getOneBedTask;
            }
        }

        // Do we need more eyes?
        boolean needsEyes = !endPortalOpened(mod, endPortalCenterLocation) && WorldHelper.getCurrentDimension() != Dimension.END;

        int filledPortalFrames = getFilledPortalFrames(mod, endPortalCenterLocation);
        int eyesNeededMin = needsEyes ? config.minimumEyes - filledPortalFrames : 0;
        int eyesNeeded = needsEyes ? config.targetEyes - filledPortalFrames : 0;

        int eyes = itemStorage.getItemCount(Items.ENDER_EYE);
        if (eyes < eyesNeededMin || (!ranStrongholdLocator && collectingEyes && eyes < eyesNeeded)) {
            collectingEyes = true;
            return getEyesOfEnderTask(mod, eyesNeeded);
        } else {
            collectingEyes = false;
        }

        // make new pickaxe if old one breaks
        if (itemStorage.getItemCount(Items.DIAMOND) >= 3 && !itemStorage.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE)) {
            return TaskCatalogue.getItemTask(Items.DIAMOND_PICKAXE, 1);
        } else if (itemStorage.getItemCount(Items.IRON_INGOT) >= 3 && !itemStorage.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE)) {
            return TaskCatalogue.getItemTask(Items.IRON_PICKAXE, 1);
        } else if (!itemStorage.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, Items.STONE_PICKAXE)) {
            return TaskCatalogue.getItemTask(Items.STONE_PICKAXE, 1);
        }
        if (!itemStorage.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, Items.STONE_PICKAXE, Items.WOODEN_PICKAXE)) {
            return TaskCatalogue.getItemTask(Items.WOODEN_PICKAXE, 1);
        }

        // We have eyes. Locate our portal + enter.
        if (WorldHelper.getCurrentDimension() == Dimension.OVERWORLD) {
            if (itemStorage.hasItem(Items.DIAMOND_PICKAXE)) {
                Item[] throwGearItems = {Items.STONE_SWORD, Items.STONE_PICKAXE, Items.IRON_SWORD, Items.IRON_PICKAXE};
                List<Slot> ironArmors = itemStorage.getSlotsWithItemPlayerInventory(true, COLLECT_IRON_ARMOR);
                List<Slot> throwGears = itemStorage.getSlotsWithItemPlayerInventory(true, throwGearItems);
                if (!StorageHelper.isBigCraftingOpen() && !StorageHelper.isFurnaceOpen() && !StorageHelper.isSmokerOpen() && !StorageHelper.isBlastFurnaceOpen() && (itemStorage.hasItem(Items.FLINT_AND_STEEL) || itemStorage.hasItem(Items.FIRE_CHARGE))) {

                    for (Slot throwGear : throwGears) {
                        if (Slot.isCursor(throwGear)) {
                            if (!mod.getControllerExtras().isBreakingBlock()) {
                                LookHelper.randomOrientation();
                            }
                            mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, SlotActionType.PICKUP);
                        } else {
                            mod.getSlotHandler().clickSlot(throwGear, 0, SlotActionType.PICKUP);
                        }
                    }


                    for (Slot ironArmor : ironArmors) {
                        if (Slot.isCursor(ironArmor)) {
                            if (!mod.getControllerExtras().isBreakingBlock()) {
                                LookHelper.randomOrientation();
                            }
                            mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, SlotActionType.PICKUP);
                        } else {
                            mod.getSlotHandler().clickSlot(ironArmor, 0, SlotActionType.PICKUP);
                        }
                    }

                }
            }
            ranStrongholdLocator = true;
            // Get beds before starting our portal location.
            if (WorldHelper.getCurrentDimension() == Dimension.OVERWORLD && needsBeds(mod)) {
                setDebugState("Getting beds before stronghold search.");
                if (!mod.getClientBaritone().getExploreProcess().isActive() && timer1.elapsed()) {
                    timer1.reset();
                }
                getBedTask = getBedTask(mod);
                return getBedTask;
            } else {
                getBedTask = null;
            }
            if (!itemStorage.hasItem(Items.WATER_BUCKET)) {
                setDebugState("Getting water bucket.");
                return TaskCatalogue.getItemTask(Items.WATER_BUCKET, 1);
            }
            if (!itemStorage.hasItem(Items.FLINT_AND_STEEL)) {
                setDebugState("Getting flint and steel.");
                return TaskCatalogue.getItemTask(Items.FLINT_AND_STEEL, 1);
            }
            if (needsBuildingMaterials(mod)) {
                setDebugState("Collecting building materials.");
                return buildMaterialsTask;
            }

            if (!endPortalFound(mod, endPortalCenterLocation)) {
                // Portal Location
                setDebugState("Locating End Portal...");
                return locateStrongholdTask;
            }

            // WE FOUND END PORTAL AND SHOULD HAVE ALL THE NECESSARY STUFF
            // Destroy silverfish spawner
            if (StorageHelper.miningRequirementMetInventory(MiningRequirement.WOOD)) {
                Optional<BlockPos> silverfish = mod.getBlockScanner().getNearestBlock(blockPos -> (WorldHelper.getSpawnerEntity(blockPos) instanceof SilverfishEntity)
                        , Blocks.SPAWNER);

                if (silverfish.isPresent()) {
                    setDebugState("Breaking silverfish spawner.");
                    return new DestroyBlockTask(silverfish.get());
                }
            }
            if (endPortalOpened(mod, endPortalCenterLocation)) {
                openingEndPortal = false;
                if (needsBuildingMaterials(mod)) {
                    setDebugState("Collecting building materials.");
                    return buildMaterialsTask;
                }
                if (config.placeSpawnNearEndPortal && itemStorage.hasItem(ItemHelper.BED) && (!spawnSetNearPortal(mod, endPortalCenterLocation))) {
                    setDebugState("Setting spawn near end portal");
                    return setSpawnNearPortalTask(mod);

                }
                // We're as ready as we'll ever be, hop into the portal!
                setDebugState("Entering End");
                enterindEndPortal = true;
                if (!mod.getExtraBaritoneSettings().isCanWalkOnEndPortal()) {
                    mod.getExtraBaritoneSettings().canWalkOnEndPortal(true);
                }
                return new DoToClosestBlockTask(blockPos -> new GetToBlockTask(blockPos.up()), Blocks.END_PORTAL);
            } else {
                if (!itemStorage.hasItem(Items.OBSIDIAN)) {
                    if (mod.getBlockScanner().anyFoundWithinDistance(10, Blocks.OBSIDIAN) || mod.getEntityTracker().itemDropped(Items.OBSIDIAN)) {
                        if (!itemStorage.hasItem(Items.WATER_BUCKET)) {
                            return new CollectBucketLiquidTask.CollectWaterBucketTask(1);
                        }
                        if (!waterPlacedTimer.elapsed()) {
                            setDebugState("waitin " + waterPlacedTimer.getDuration());
                            return null;
                        }
                        return TaskCatalogue.getItemTask(Items.OBSIDIAN, 1);
                    } else {
                        if (repeated > 2 && !itemStorage.hasItem(Items.WATER_BUCKET)) {
                            return new CollectBucketLiquidTask.CollectWaterBucketTask(1);
                        }
                        if (waterPlacedTimer.elapsed()) {
                            if (!itemStorage.hasItem(Items.WATER_BUCKET)) {
                                repeated++;
                                waterPlacedTimer.reset();
                                return null;
                            } else {
                                repeated = 0;
                            }

                            return new PlaceObsidianBucketTask(
                                    mod.getBlockScanner().getNearestBlock(WorldHelper.toVec3d(endPortalCenterLocation), (blockPos) -> !blockPos.isWithinDistance(endPortalCenterLocation, 8), Blocks.LAVA).get());
                        }
                        setDebugState(waterPlacedTimer.getDuration() + "");
                        return null;
                    }
                }
                // Open the portal! (we have enough eyes, do it)
                setDebugState("Opening End Portal");
                openingEndPortal = true;
                return new DoToClosestBlockTask(blockPos -> new InteractWithBlockTask(Items.ENDER_EYE, blockPos), blockPos -> !isEndPortalFrameFilled(mod, blockPos), Blocks.END_PORTAL_FRAME);
            }
        } else if (WorldHelper.getCurrentDimension() == Dimension.NETHER) {
            Item[] throwGearItems = {Items.STONE_SWORD, Items.STONE_PICKAXE, Items.IRON_SWORD, Items.IRON_PICKAXE};
            List<Slot> ironArmors = itemStorage.getSlotsWithItemPlayerInventory(true, COLLECT_IRON_ARMOR);
            List<Slot> throwGears = itemStorage.getSlotsWithItemPlayerInventory(true, throwGearItems);
            if (!StorageHelper.isBigCraftingOpen() && !StorageHelper.isFurnaceOpen() && !StorageHelper.isSmokerOpen() && !StorageHelper.isBlastFurnaceOpen() && (itemStorage.hasItem(Items.FLINT_AND_STEEL) || itemStorage.hasItem(Items.FIRE_CHARGE))) {

                for (Slot throwGear : throwGears) {
                    if (Slot.isCursor(throwGear)) {
                        if (!mod.getControllerExtras().isBreakingBlock()) {
                            LookHelper.randomOrientation();
                        }
                        mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, SlotActionType.PICKUP);
                    } else {
                        mod.getSlotHandler().clickSlot(throwGear, 0, SlotActionType.PICKUP);
                    }
                }


                for (Slot ironArmor : ironArmors) {
                    if (Slot.isCursor(ironArmor)) {
                        if (!mod.getControllerExtras().isBreakingBlock()) {
                            LookHelper.randomOrientation();
                        }
                        mod.getSlotHandler().clickSlot(Slot.UNDEFINED, 0, SlotActionType.PICKUP);
                    } else {
                        mod.getSlotHandler().clickSlot(ironArmor, 0, SlotActionType.PICKUP);
                    }
                }

            }
            // Portal Location
            setDebugState("Locating End Portal...");
            return locateStrongholdTask;
        }
        return null;
    }

    /**
     * Sets the spawn point near the portal.
     *
     * @param mod The AltoClef mod instance.
     * @return The task to set the spawn point near the portal.
     */
    private Task setSpawnNearPortalTask(AltoClef mod) {
        if (setBedSpawnTask.isSpawnSet()) {
            bedSpawnLocation = setBedSpawnTask.getBedSleptPos();
        } else {
            bedSpawnLocation = null;
        }

        if (isTaskRunning(mod, setBedSpawnTask)) {
            setDebugState("Setting spawnpoint now.");
            return setBedSpawnTask;
        }

        // Check if the player is within range of the portal
        if (WorldHelper.inRangeXZ(mod.getPlayer(), WorldHelper.toVec3d(endPortalCenterLocation), END_PORTAL_BED_SPAWN_RANGE)) {
            return setBedSpawnTask;
        } else {
            setDebugState("Approaching portal (to set spawnpoint)");
            return new GetToXZTask(endPortalCenterLocation.getX(), endPortalCenterLocation.getZ());
        }
    }

    /**
     * Returns a Task to handle Blaze Rods based on the given count.
     *
     * @param mod   The AltoClef mod instance.
     * @param count The desired count of Blaze Rods.
     * @return A Task to handle Blaze Rods.
     */
    private Task getBlazeRodsTask(AltoClef mod, int count) {
        EntityTracker entityTracker = mod.getEntityTracker();

        if (entityTracker.itemDropped(Items.BLAZE_ROD)) {
            Debug.logInternal("Blaze Rod dropped, picking it up.");
            return new PickupDroppedItemTask(Items.BLAZE_ROD, 1);
        } else if (entityTracker.itemDropped(Items.BLAZE_POWDER)) {
            Debug.logInternal("Blaze Powder dropped, picking it up.");
            return new PickupDroppedItemTask(Items.BLAZE_POWDER, 1);
        } else {
            Debug.logInternal("No Blaze Rod or Blaze Powder dropped, collecting Blaze Rods.");
            return new CollectBlazeRodsTask(count);
        }
    }

    /**
     * Returns a Task to obtain Ender Pearls.
     *
     * @param mod   The mod instance.
     * @param count The desired number of Ender Pearls.
     * @return The Task to obtain Ender Pearls.
     */
    private Task getEnderPearlTask(AltoClef mod, int count) {
        if (mod.getEntityTracker().itemDropped(Items.ENDER_PEARL)) {
            return new PickupDroppedItemTask(Items.ENDER_PEARL, 1);
        }

        // Check if we should barter Pearls instead of hunting Endermen.
        if (config.barterPearlsInsteadOfEndermanHunt) {
            // Check if Golden Helmet is not equipped, and equip it.
            if (!StorageHelper.isArmorEquipped(Items.GOLDEN_HELMET)) {
                return new EquipArmorTask(Items.GOLDEN_HELMET);
            }
            // Trade with Piglins for Ender Pearls.
            return new TradeWithPiglinsTask(32, Items.ENDER_PEARL, count);
        }

        boolean endermanFound = mod.getEntityTracker().entityFound(EndermanEntity.class);
        boolean pearlDropped = mod.getEntityTracker().itemDropped(Items.ENDER_PEARL);

        // Check if we have found an Enderman or Ender Pearl and have enough Twisting Vines.
        if (endermanFound || pearlDropped) {
            Optional<Entity> toKill = mod.getEntityTracker().getClosestEntity(EndermanEntity.class);
            if (toKill.isPresent() && mod.getEntityTracker().isEntityReachable(toKill.get())) {
                return new KillEndermanTask(count);
            }
        }

        // Search for Ender Pearls within the warped forest biome.
        setDebugState("Waiting for endermen to spawn... ");
        return null;
    }

    /**
     * Calculates the target number of beds based on the configuration settings.
     *
     * @param mod The AltoClef mod instance.
     * @return The target number of beds.
     */
    private int getTargetBeds(AltoClef mod) {
        // Check if spawn needs to be set near the end portal
        boolean needsToSetSpawn = config.placeSpawnNearEndPortal && (!spawnSetNearPortal(mod, endPortalCenterLocation) && !isTaskRunning(mod, setBedSpawnTask));

        // Calculate the number of beds in the end
        int bedsInEnd = Arrays.stream(ItemHelper.BED).mapToInt(bed -> cachedEndItemDrops.getOrDefault(bed, 0)).sum();

        // Calculate the target number of beds
        int targetBeds = config.requiredBeds + (needsToSetSpawn ? 1 : 0) - bedsInEnd;

        // Output debug information
        Debug.logInternal("needsToSetSpawn: " + needsToSetSpawn);
        Debug.logInternal("bedsInEnd: " + bedsInEnd);
        Debug.logInternal("targetBeds: " + targetBeds);

        return targetBeds;
    }

    /**
     * Checks if the player needs to acquire more beds.
     *
     * @param mod The instance of the AltoClef mod.
     * @return True if the player needs more beds, false otherwise.
     */
    private boolean needsBeds(AltoClef mod) {
        // Calculate the total number of end items obtained from breaking beds
        int totalEndItems = 0;
        for (Item bed : ItemHelper.BED) {
            totalEndItems += cachedEndItemDrops.getOrDefault(bed, 0);
        }

        // Get the current number of beds in the player's inventory
        int itemCount = mod.getItemStorage().getItemCount(ItemHelper.BED);

        // Get the target number of beds to have
        int targetBeds = getTargetBeds(mod);

        // Log the values for debugging purposes
        Debug.logInternal("Total End Items: " + totalEndItems);
        Debug.logInternal("Item Count: " + itemCount);
        Debug.logInternal("Target Beds: " + targetBeds);

        // Check if the player needs to acquire more beds
        boolean needsBeds = (itemCount + totalEndItems) < targetBeds;

        // Log the result for debugging purposes
        Debug.logInternal("Needs Beds: " + needsBeds);

        // Return whether the player needs more beds
        return needsBeds;
    }

    /**
     * Retrieves a task to obtain the desired number of beds.
     *
     * @param mod The AltoClef mod instance.
     * @return The task to obtain the beds.
     */
    private Task getBedTask(AltoClef mod) {
        int targetBeds = getTargetBeds(mod);
        if (!mod.getItemStorage().hasItem(Items.SHEARS) && !anyBedsFound(mod)) {
            Debug.logInternal("Getting shears.");
            return TaskCatalogue.getItemTask(Items.SHEARS, 1);
        }
        Debug.logInternal("Getting beds.");
        return TaskCatalogue.getItemTask("bed", targetBeds);
    }

    /**
     * Checks if any beds are found in the game.
     *
     * @param mod The AltoClef mod instance.
     * @return true if beds are found either in blocks or entities, false otherwise.
     */
    private boolean anyBedsFound(AltoClef mod) {
        // Get the block and entity trackers from the mod instance.
        BlockScanner blockTracker = mod.getBlockScanner();
        EntityTracker entityTracker = mod.getEntityTracker();

        // Check if any beds are found in blocks.
        boolean bedsFoundInBlocks = blockTracker.anyFound(ItemHelper.itemsToBlocks(ItemHelper.BED));

        // Check if any beds are dropped by entities.
        boolean bedsFoundInEntities = entityTracker.itemDropped(ItemHelper.BED);

        // Log a message if beds are found in blocks.
        if (bedsFoundInBlocks) {
            Debug.logInternal("Beds found in blocks");
        }

        // Log a message if beds are found in entities.
        if (bedsFoundInEntities) {
            Debug.logInternal("Beds found in entities");
        }

        // Return true if beds are found either in blocks or entities.
        return bedsFoundInBlocks || bedsFoundInEntities;
    }

    /**
     * Searches for the position of an end portal frame by averaging the known locations of the frames.
     * Returns the center position of the frames if enough frames are found, otherwise returns null.
     *
     * @param mod The AltoClef instance.
     * @return The position of the end portal frame, or null if not enough frames are found.
     */

    // FIXME note that this doesnt work correctly and only returns a postion that is WITHIN the end portal, not its center -MiranCZ
    private BlockPos doSimpleSearchForEndPortal(AltoClef mod) {
        List<BlockPos> frames = mod.getBlockScanner().getKnownLocations(Blocks.END_PORTAL_FRAME);

        if (frames.size() >= END_PORTAL_FRAME_COUNT) {
            // Calculate the average position of the frames.
            Vec3d average = frames.stream().reduce(Vec3d.ZERO, (accum, bpos) -> accum.add((int) Math.round(bpos.getX() + 0.5), (int) Math.round(bpos.getY() + 0.5), (int) Math.round(bpos.getZ() + 0.5)), Vec3d::add).multiply(1d / frames.size());

            // Log the average position.
            mod.log("Average Position: " + average);

            return new BlockPos(new Vec3i((int) average.x, (int) average.y, (int) average.z));
        }

        // Log that there are not enough frames.
        Debug.logInternal("Not enough frames");

        return null;
    }

    /**
     * Returns the number of filled portal frames around the end portal center.
     *
     * @param mod             The AltoClef mod instance.
     * @param endPortalCenter The center position of the end portal.
     * @return The number of filled portal frames.
     */
    private int getFilledPortalFrames(AltoClef mod, BlockPos endPortalCenter) {
        if (endPortalCenter == null) {
            return 0;
        }

        // Get all the frame blocks around the end portal center.
        List<BlockPos> frameBlocks = getFrameBlocks(mod,endPortalCenter);

        // Check if all the frame blocks are loaded.
        if (frameBlocks.stream().allMatch(blockPos -> mod.getChunkTracker().isChunkLoaded(blockPos))) {
            // Calculate the sum of filled frames using a stream and mapToInt.
            cachedFilledPortalFrames = frameBlocks.stream().mapToInt(blockPos -> {
                boolean isFilled = isEndPortalFrameFilled(mod, blockPos);
                // Log whether the frame is filled or not.
                if (isFilled) {
                    Debug.logInternal("Portal frame at " + blockPos + " is filled.");
                } else {
                    Debug.logInternal("Portal frame at " + blockPos + " is not filled.");
                }
                return isFilled ? 1 : 0;
            }).sum();
        }

        return cachedFilledPortalFrames;
    }

    /**
     * Checks if a chest at the given block position can be looted.
     *
     * @param mod      The instance of the mod.
     * @param blockPos The block position of the chest to check.
     * @return True if the chest can be looted as a portal chest, false otherwise.
     */
    private boolean canBeLootablePortalChest(AltoClef mod, BlockPos blockPos) {
        // Check if the block above is water or if the y-coordinate is below 50
        return mod.getWorld().getBlockState(blockPos.up()).getBlock() != Blocks.WATER && blockPos.getY() >= 50;

       /* // Define the minimum and maximum positions to scan for NETHERRACK blocks
        BlockPos minPos = blockPos.add(-4, -2, -4);
        BlockPos maxPos = blockPos.add(4, 2, 4);

        // Log the scanning region
        Debug.logInternal("Scanning region from " + minPos + " to " + maxPos);

        // Scan the region defined by minPos and maxPos
        for (BlockPos checkPos : WorldHelper.scanRegion(mod, minPos, maxPos)) {
            // Check if the block at checkPos is NETHERRACK
            if (mod.getWorld().getBlockState(checkPos).getBlock() == Blocks.NETHERRACK) {
                return true;
            }
        }

        // Log that the blockPos is added to the list of not ruined portal chests
        Debug.logInternal("Adding blockPos " + blockPos + " to the list of not ruined portal chests");

        // Add the blockPos to the list of not ruined portal chests
        notRuinedPortalChests.add(blockPos);

        return false;*/
    }

    private Task getEyesOfEnderTask(AltoClef mod, int targetEyes) {
        if (mod.getEntityTracker().itemDropped(Items.ENDER_EYE)) {
            setDebugState("Picking up Dropped Eyes");
            return new PickupDroppedItemTask(Items.ENDER_EYE, targetEyes);
        }

        int eyeCount = mod.getItemStorage().getItemCount(Items.ENDER_EYE);
        int blazePowderCount = mod.getItemStorage().getItemCount(Items.BLAZE_POWDER);
        int blazeRodCount = mod.getItemStorage().getItemCount(Items.BLAZE_ROD);

        int blazeRodTarget = (int) Math.ceil(((double) targetEyes - eyeCount - blazePowderCount) / 2.0);
        int enderPearlTarget = targetEyes - eyeCount;

        boolean needsBlazeRods = blazeRodCount < blazeRodTarget;
        boolean needsBlazePowder = eyeCount + blazePowderCount < targetEyes;
        boolean needsEnderPearls = mod.getItemStorage().getItemCount(Items.ENDER_PEARL) < enderPearlTarget;

        if (needsBlazePowder && !needsBlazeRods) {
            // We have enough blaze rods.
            setDebugState("Crafting blaze powder");
            return TaskCatalogue.getItemTask(Items.BLAZE_POWDER, targetEyes - eyeCount);
        }

        if (!needsBlazePowder && !needsEnderPearls) {
            // Craft ender eyes
            setDebugState("Crafting Ender Eyes");
            return TaskCatalogue.getItemTask(Items.ENDER_EYE, targetEyes);
        }


        // Get blaze rods + pearls...
        switch (WorldHelper.getCurrentDimension()) {
            case OVERWORLD -> {
                PriorityTask toGather = null;
                double maxPriority = 0;

                if (!gatherResources.isEmpty()) {
                    if (!forcedTaskTimer.elapsed() && isTaskRunning(mod, lastTask) && lastGather != null && lastGather.calculatePriority(mod) > 0) {
                        return lastTask;
                    }

                    if (!changedTaskTimer.elapsed() && lastTask != null && !lastGather.bypassForceCooldown && isTaskRunning(mod, lastTask)) {
                        return lastTask;
                    }
                    if (isTaskRunning(mod, lastTask) && lastGather != null && lastGather.shouldForce()) {
                        return lastTask;
                    }

                    for (PriorityTask gatherResource : gatherResources) {
                        double priority = gatherResource.calculatePriority(mod);

                        if (priority > maxPriority) {
                            maxPriority = priority;
                            toGather = gatherResource;
                        }
                    }
                }
                if (toGather != null) {
                    boolean sameTask = lastGather == toGather;

                    setDebugState("Priority: " + String.format(Locale.US, "%.2f", maxPriority) + ", " + toGather);
                    if (!sameTask && prevLastGather == toGather && lastTask != null && lastGather.calculatePriority(mod) > 0 && isTaskRunning(mod, lastTask)) {
                        mod.logWarning("might be stuck or switching too much, forcing current resource for a bit more");
                        changedTaskTimer.reset();
                        prevLastGather = null; //do not force infinitely, 3 sec should be enough I hope
                        setDebugState("Priority: FORCED, " + lastGather);
                        return lastTask;
                    }


                    if (sameTask && toGather.canCache()) {
                        return lastTask;
                    }
                    if (!sameTask) {
                        taskChanges.add(0, new TaskChange(lastGather, toGather, mod.getPlayer().getBlockPos()));
                    }

                    if (taskChanges.size() >= 3 && !sameTask) {
                        TaskChange t1 = taskChanges.get(0);
                        TaskChange t2 = taskChanges.get(1);
                        TaskChange t3 = taskChanges.get(2);

                        if (t1.original == t2.interrupt && t1.pos.isWithinDistance(t3.pos, 5) && t3.original == t1.interrupt) {
                            forcedTaskTimer.reset();
                            mod.logWarning("Probably stuck! Forcing timer...");
                            taskChanges.clear();
                            return lastTask;
                        }
                        if (taskChanges.size() > 3) {
                            taskChanges.remove(taskChanges.size() - 1);
                        }
                    }


                    prevLastGather = lastGather;
                    lastGather = toGather;

                    Task task = toGather.getTask(mod);


                    if (!sameTask) {
                        if (lastTask instanceof SmeltInFurnaceTask && !(task instanceof SmeltInFurnaceTask) && !mod.getItemStorage().hasItem(Items.FURNACE)) {
                            pickupFurnace = true;
                            lastGather = null;
                            lastTask = null;
                            StorageHelper.closeScreen();
                            return null;
                        } else if (lastTask instanceof SmeltInSmokerTask && !(task instanceof SmeltInSmokerTask) && !mod.getItemStorage().hasItem(Items.SMOKER)) {
                            pickupSmoker = true;
                            lastGather = null;
                            lastTask = null;
                            StorageHelper.closeScreen();
                            return null;
                            // TODO implement crafting
                        } else if (lastTask != null && task != null && !toGather.needCraftingOnStart(mod)) {
                            pickupCrafting = true;
                            lastGather = null;
                            lastTask = null;
                            StorageHelper.closeScreen();
                            return null;
                        }
                    }

                    lastTask = task;

                    changedTaskTimer.reset();
                    return task;
                }


                if (needsBuildingMaterials(mod)) {
                    setDebugState("Collecting building materials.");
                    return buildMaterialsTask;
                }
                // Then go to the nether.
                setDebugState("Going to Nether");

                // make new pickaxe if old one breaks
                // TODO refactor duplicated code
                ItemStorageTracker itemStorage = mod.getItemStorage();
                if (itemStorage.getItemCount(Items.DIAMOND) >= 3 && !itemStorage.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE)) {
                    return TaskCatalogue.getItemTask(Items.DIAMOND_PICKAXE, 1);
                } else if (itemStorage.getItemCount(Items.IRON_INGOT) >= 3 && !itemStorage.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE)) {
                    return TaskCatalogue.getItemTask(Items.IRON_PICKAXE, 1);
                } else if (!itemStorage.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, Items.STONE_PICKAXE)) {
                    return TaskCatalogue.getItemTask(Items.STONE_PICKAXE, 1);
                }
                if (!itemStorage.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, Items.STONE_PICKAXE, Items.WOODEN_PICKAXE)) {
                    return TaskCatalogue.getItemTask(Items.WOODEN_PICKAXE, 1);
                }

                // DO NOT INTERRUPT GOING TO NETHER
                gatherResources.clear();

                //prevents from getting stuck
                if (!(lastTask instanceof DefaultGoToDimensionTask)) {
                    goToNetherTask = new DefaultGoToDimensionTask(Dimension.NETHER);
                }
                lastTask = goToNetherTask;
                return goToNetherTask;
            }
            case NETHER -> {
                if (isTaskRunning(mod, safeNetherPortalTask)) {
                    return safeNetherPortalTask;
                }

                if (mod.getPlayer().getPortalCooldown() != 0 && safeNetherPortalTask == null) {
                    safeNetherPortalTask = new SafeNetherPortalTask();
                    return safeNetherPortalTask;
                }

                mod.getInputControls().release(Input.MOVE_FORWARD);
                mod.getInputControls().release(Input.MOVE_LEFT);
                mod.getInputControls().release(Input.SNEAK);

                BlockPos pos = mod.getPlayer().getSteppingPos();
                if (!escaped && mod.getWorld().getBlockState(pos).getBlock().equals(Blocks.SOUL_SAND) &&
                        (mod.getWorld().getBlockState(pos.east()).getBlock().equals(Blocks.OBSIDIAN) ||
                                mod.getWorld().getBlockState(pos.west()).getBlock().equals(Blocks.OBSIDIAN) ||
                                mod.getWorld().getBlockState(pos.south()).getBlock().equals(Blocks.OBSIDIAN) ||
                                mod.getWorld().getBlockState(pos.north()).getBlock().equals(Blocks.OBSIDIAN))) {

                    LookHelper.lookAt(mod, pos);
                    mod.getInputControls().hold(Input.CLICK_LEFT);
                    return null;
                }
                if (!escaped) {
                    escaped = true;
                    mod.getInputControls().release(Input.CLICK_LEFT);
                }


                // make new pickaxe if old one breaks
                ItemStorageTracker itemStorage = mod.getItemStorage();
                if (itemStorage.getItemCount(Items.DIAMOND) >= 3 && !itemStorage.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE)) {
                    return TaskCatalogue.getItemTask(Items.DIAMOND_PICKAXE, 1);
                } else if (itemStorage.getItemCount(Items.IRON_INGOT) >= 3 && !itemStorage.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE)) {
                    return TaskCatalogue.getItemTask(Items.IRON_PICKAXE, 1);
                } else if (!itemStorage.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, Items.STONE_PICKAXE)) {
                    return TaskCatalogue.getItemTask(Items.STONE_PICKAXE, 1);
                }
                if (!itemStorage.hasItem(Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, Items.STONE_PICKAXE, Items.WOODEN_PICKAXE)) {
                    return TaskCatalogue.getItemTask(Items.WOODEN_PICKAXE, 1);
                }

                if (mod.getItemStorage().getItemCount(Items.BLAZE_ROD) * 2 + mod.getItemStorage().getItemCount(Items.BLAZE_POWDER) + mod.getItemStorage().getItemCount(Items.ENDER_EYE) >= 14) {
                    hasRods = true;
                }

                double rodDistance = mod.getBlockScanner().distanceToClosest(Blocks.NETHER_BRICKS);
                double pearlDistance = mod.getBlockScanner().distanceToClosest(Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT, Blocks.WARPED_HYPHAE, Blocks.WARPED_NYLIUM);

                if (pearlDistance == Double.POSITIVE_INFINITY && rodDistance == Double.POSITIVE_INFINITY) {
                    setDebugState("Neither fortress or warped forest found... wandering");
                    if (isTaskRunning(mod, searchTask)) {
                        return searchTask;
                    }

                    searchTask = new SearchChunkForBlockTask(Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT, Blocks.WARPED_HYPHAE, Blocks.WARPED_NYLIUM, Blocks.NETHER_BRICKS);
                    return searchTask;
                }

                if ((rodDistance < pearlDistance && !hasRods && !gettingPearls) || !needsEnderPearls) {
                    if (!gotToFortress) {
                        if (mod.getBlockScanner().anyFoundWithinDistance(5, Blocks.NETHER_BRICKS)) {
                            gotToFortress = true;
                        } else {
                            if (!mod.getBlockScanner().anyFound(Blocks.NETHER_BRICKS)) {
                                setDebugState("Searching for fortress");
                                return new TimeoutWanderTask();
                            }

                            if (WorldHelper.inRangeXZ(mod.getPlayer().getPos(),
                                    WorldHelper.toVec3d(mod.getBlockScanner().getNearestBlock(Blocks.NETHER_BRICKS).get()), 2)) {

                                setDebugState("trying to get to fortress");
                                return new GetToBlockTask(mod.getBlockScanner().getNearestBlock(Blocks.NETHER_BRICKS).get());
                            }

                            setDebugState("Getting close to fortress");

                            if ((cachedFortressTask != null && !fortressTimer.elapsed() &&
                                    mod.getPlayer().getPos().distanceTo(WorldHelper.toVec3d(cachedFortressTask.blockPos)) - 1 > prevPos.getManhattanDistance(cachedFortressTask.blockPos) / 2d
                            ) || !mod.getClientBaritone().getPathingBehavior().isSafeToCancel()) {
                                if (cachedFortressTask != null) {
                                    mod.log(mod.getPlayer().getPos().distanceTo(WorldHelper.toVec3d(cachedFortressTask.blockPos)) + " : " + prevPos.getManhattanDistance(cachedFortressTask.blockPos) / 2);
                                    return cachedFortressTask;
                                }
                            }

                            // 'isEqual' is fucking me up here, so I have to reset the task
                            if (resetFortressTask) {
                                resetFortressTask = false;
                                return null;
                            }
                            resetFortressTask = true;

                            fortressTimer.reset();
                            mod.log("new");

                            prevPos = mod.getPlayer().getBlockPos();

                            BlockPos p = mod.getBlockScanner().getNearestBlock(Blocks.NETHER_BRICKS).get();
                            int distance = (int) (mod.getPlayer().getPos().distanceTo(WorldHelper.toVec3d(p)) / 2);
                            if (cachedFortressTask != null) {
                                // prevents from getting stuck in place
                                distance = Math.min(cachedFortressTask.range - 1, distance);
                            }
                            if (distance < 0) {
                                gotToFortress = true;
                            } else {
                                cachedFortressTask = new GetWithinRangeOfBlockTask(p, distance);
                                return cachedFortressTask;
                            }
                        }
                    }
                    setDebugState("Getting Blaze Rods");
                    return getBlazeRodsTask(mod, blazeRodTarget);
                }


                if (!mod.getBlockScanner().anyFound(Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT, Blocks.WARPED_HYPHAE, Blocks.WARPED_NYLIUM)) {
                    return new TimeoutWanderTask();
                }

                if (!gotToBiome && (biomePos == null || !WorldHelper.inRangeXZ(mod.getPlayer(), biomePos, 30) || !mod.getClientBaritone().getPathingBehavior().isSafeToCancel())) {
                    if (biomePos != null) {
                        setDebugState("Going to biome");

                        return new GetWithinRangeOfBlockTask(biomePos, 20);
                    } else {
                        gettingPearls = true;
                        setDebugState("Getting Ender Pearls");
                        Optional<BlockPos> closestBlock = mod.getBlockScanner().getNearestBlock(Blocks.TWISTING_VINES, Blocks.TWISTING_VINES_PLANT, Blocks.WARPED_HYPHAE, Blocks.WARPED_NYLIUM);

                        if (closestBlock.isPresent()) {
                            biomePos = closestBlock.get();
                        } else {
                            setDebugState("biome not found, wandering");
                        }
                        return new TimeoutWanderTask();
                    }
                } else {
                    gotToBiome = true;
                }

                return getEnderPearlTask(mod, enderPearlTarget);

            }
            case END -> throw new UnsupportedOperationException("You're in the end. Don't collect eyes here.");
        }
        return null;
    }

    private record TaskChange(PriorityTask original, PriorityTask interrupt, BlockPos pos) {
    }

    private class DistanceOrePriorityCalculator extends DistanceItemPriorityCalculator {

        private final Item oreItem;

        public DistanceOrePriorityCalculator(Item oreItem, double multiplier, double unneededMultiplier, double unneededDistanceThreshold, int minCount, int maxCount) {
            super(multiplier, unneededMultiplier, unneededDistanceThreshold, minCount, maxCount);
            this.oreItem = oreItem;
        }

        @Override
        public void update(int count) {
            super.update(getCountWithCraftedFromOre(mod, oreItem));
        }

    }


}
