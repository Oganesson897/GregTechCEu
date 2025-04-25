package gregtech.common.metatileentities.multi.multiblockpart;

import gregtech.api.GTValues;
import gregtech.api.capability.DualHandler;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.IControllable;
import gregtech.api.capability.IGhostSlotConfigurable;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.GhostCircuitItemStackHandler;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.capability.impl.NotifiableFluidTank;
import gregtech.api.capability.impl.NotifiableItemStackHandler;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.widgets.GhostCircuitSlotWidget;
import gregtech.api.gui.widgets.SlotWidget;
import gregtech.api.gui.widgets.TankWidget;
import gregtech.api.items.itemhandlers.GTItemStackHandler;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.AbilityInstances;
import gregtech.api.metatileentity.multiblock.IMultiblockAbilityPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.util.GTUtility;
import gregtech.client.renderer.texture.Textures;
import gregtech.client.renderer.texture.cube.SimpleOverlayRenderer;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;

import codechicken.lib.raytracer.CuboidRayTraceResult;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import codechicken.lib.vec.Matrix4;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class MetaTileEntityDualHatch extends MetaTileEntityMultiblockNotifiablePart implements
                                     IMultiblockAbilityPart<IItemHandlerModifiable>,
                                     IControllable,
                                     IGhostSlotConfigurable {

    @Nullable
    protected GhostCircuitItemStackHandler circuitInventory;
    @Nullable
    private IItemHandlerModifiable actualImportItems;
    private DualHandler dualHandler;

    private boolean workingEnabled = true;
    private boolean autoCollapse = false;

    public MetaTileEntityDualHatch(ResourceLocation metaTileEntityId, int tier, boolean isExportHatch) {
        super(metaTileEntityId, tier, isExportHatch);
        initializeInventory();
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityDualHatch(metaTileEntityId, getTier(), isExportHatch);
    }

    @Override
    protected void initializeInventory() {
        super.initializeInventory();
        if (hasGhostCircuitInventory()) {
            circuitInventory = new GhostCircuitItemStackHandler(this);
            actualImportItems = new ItemHandlerList(Arrays.asList(this.importItems, circuitInventory));
        } else {
            actualImportItems = this.importItems;
        }
        dualHandler = new DualHandler(
                isExportHatch ? this.exportItems : this.actualImportItems,
                isExportHatch ? getExportFluids() : getImportFluids(),
                isExportHatch);
    }

    @Override
    public IItemHandlerModifiable getImportItems() {
        return dualHandler;
    }

    protected IFluidTank[] createTanks() {
        int size = 1 + Math.min(GTValues.UHV, getTier());
        IFluidTank[] tanks = new IFluidTank[size];
        for (int index = 0; index < tanks.length; index++) {
            tanks[index] = new NotifiableFluidTank(getTankSize(), null, isExportHatch);
        }
        return tanks;
    }

    protected int getItemSize() {
        int sizeRoot = 1 + Math.min(GTValues.UHV, getTier());
        return sizeRoot * sizeRoot;
    }

    protected int getTankSize() {
        return 8_000 * Math.min(Integer.MAX_VALUE, 1 << getTier());
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return isExportHatch ? new GTItemStackHandler(this, 0) :
                new NotifiableItemStackHandler(this, getItemSize(), null, false);
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return isExportHatch ? new NotifiableItemStackHandler(this, getItemSize(), null, true) :
                new GTItemStackHandler(this, 0);
    }

    @Override
    protected FluidTankList createImportFluidHandler() {
        return isExportHatch ? new FluidTankList(false) : new FluidTankList(false, createTanks());
    }

    @Override
    protected FluidTankList createExportFluidHandler() {
        return isExportHatch ? new FluidTankList(false, createTanks()) : new FluidTankList(false);
    }

    @Override
    public void update() {
        super.update();

        if (!getWorld().isRemote && getOffsetTimer() % 5 == 0) {
            if (workingEnabled) {
                if (isExportHatch) {
                    pushItemsIntoNearbyHandlers(getFrontFacing());
                    pushFluidsIntoNearbyHandlers(getFrontFacing());
                } else {
                    pullItemsFromNearbyHandlers(getFrontFacing());
                    pullFluidsFromNearbyHandlers(getFrontFacing());
                }
            }

            if (autoCollapse()) {
                IItemHandlerModifiable itemHandler = isExportHatch ? getExportItems() : super.getImportItems();
                if (!isAttachedToMultiBlock() || (isExportHatch ? getNotifiedItemOutputList().contains(itemHandler) :
                        getNotifiedItemInputList().contains(itemHandler))) {
                    GTUtility.collapseInventorySlotContents(itemHandler);
                }
            }
        }
    }

    @Override
    public boolean hasGhostCircuitInventory() {
        return !this.isExportHatch;
    }

    @Override
    public void setGhostCircuitConfig(int config) {
        if (this.circuitInventory == null || this.circuitInventory.getCircuitValue() == config) {
            return;
        }
        this.circuitInventory.setCircuitValue(config);
        if (!getWorld().isRemote) {
            markDirty();
        }
    }

    @Override
    public @NotNull List<MultiblockAbility<?>> getAbilities() {
        return isExportHatch ?
                Arrays.asList(MultiblockAbility.EXPORT_FLUIDS, MultiblockAbility.EXPORT_ITEMS) :
                Arrays.asList(MultiblockAbility.IMPORT_FLUIDS, MultiblockAbility.IMPORT_ITEMS);
    }

    @Override
    public void registerAbilities(@NotNull AbilityInstances abilityInstances) {
        abilityInstances.add(dualHandler);
    }

    @Override
    public ModularUI createUI(EntityPlayer player) {
        int rowSize = (int) Math.sqrt(getItemSize());
        boolean hasGhostCircuit = hasGhostCircuitInventory() && circuitInventory != null;

        int backgroundWidth = Math.max(
                9 * 18 + 18 + 14 + 5,   // Player Inv width
                rowSize * 18 + 14 + 18); // Bus Inv width
        int backgroundHeight = 18 + 18 * rowSize + 94;
        int center = backgroundWidth / 2;

        int gridStartX = center - (rowSize * 9) - 10;

        int inventoryStartX = center - 9 - 4 * 18;
        int inventoryStartY = 18 + 18 * rowSize + 12;

        ModularUI.Builder builder = ModularUI.builder(GuiTextures.BACKGROUND, backgroundWidth, backgroundHeight)
                .label(10, 5, getMetaFullName());

        for (int y = 0; y < rowSize; y++) {
            for (int x = 0; x < rowSize; x++) {
                int index = y * rowSize + x;
                IItemHandlerModifiable handler = isExportHatch ? getExportItems() : getImportItems();

                builder.widget(new SlotWidget(handler, index, gridStartX + x * 18, 18 + y * 18, true, !isExportHatch)
                        .setBackgroundTexture(GuiTextures.SLOT));
            }

            IFluidTank tankHandler = dualHandler.getTankAt(y);
            builder.widget(new TankWidget(tankHandler, gridStartX + rowSize * 18, 18 + y * 18, 18, 18)
                    .setBackgroundTexture(GuiTextures.FLUID_SLOT)
                    .setAlwaysShowFull(true).setDrawHoveringText(false).setContainerClicking(true, !isExportHatch));
        }

        if (hasGhostCircuit) {
            int circuitX = rowSize > 6 ? gridStartX + rowSize * 18 + 9 : inventoryStartX + 8 * 18;
            int circuitY = rowSize * 18;

            SlotWidget circuitSlot = new GhostCircuitSlotWidget(circuitInventory, 0, circuitX + 18, circuitY)
                    .setBackgroundTexture(GuiTextures.SLOT, GuiTextures.INT_CIRCUIT_OVERLAY);
            builder.widget(circuitSlot.setConsumer(this::getCircuitSlotTooltip));
        }

        return builder.bindPlayerInventory(player.inventory, GuiTextures.SLOT, inventoryStartX, inventoryStartY)
                .build(getHolder(), player);
    }

    protected void getCircuitSlotTooltip(@NotNull SlotWidget widget) {
        String configString;
        if (circuitInventory == null || circuitInventory.getCircuitValue() == GhostCircuitItemStackHandler.NO_CONFIG) {
            configString = new TextComponentTranslation("gregtech.gui.configurator_slot.no_value").getFormattedText();
        } else {
            configString = String.valueOf(circuitInventory.getCircuitValue());
        }

        widget.setTooltipText("gregtech.gui.configurator_slot.tooltip", configString);
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, Matrix4 translation, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, translation, pipeline);
        SimpleOverlayRenderer renderer = isExportHatch ? Textures.PIPE_OUT_OVERLAY : Textures.PIPE_IN_OVERLAY;
        renderer.renderSided(getFrontFacing(), renderState, translation, pipeline);
        SimpleOverlayRenderer overlay = isExportHatch ? Textures.ITEM_HATCH_OUTPUT_OVERLAY :
                Textures.ITEM_HATCH_INPUT_OVERLAY;
        overlay.renderSided(getFrontFacing(), renderState, translation, pipeline);
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(workingEnabled);
        buf.writeBoolean(autoCollapse);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        workingEnabled = buf.readBoolean();
        autoCollapse = buf.readBoolean();
    }

    @Override
    public void setWorkingEnabled(boolean workingEnabled) {
        this.workingEnabled = workingEnabled;
        World world = getWorld();
        if (world != null && !world.isRemote) {
            writeCustomData(GregtechDataCodes.WORKING_ENABLED, buf -> buf.writeBoolean(workingEnabled));
        }
    }

    @Override
    public boolean isWorkingEnabled() {
        return workingEnabled;
    }

    @SuppressWarnings("DuplicatedCode")
    public void setAutoCollapse(boolean inverted) {
        autoCollapse = inverted;
        if (!getWorld().isRemote) {
            if (autoCollapse) {
                if (isExportHatch) {
                    addNotifiedOutput(getExportItems());
                } else {
                    addNotifiedInput(getImportItems());
                }
            }
            writeCustomData(GregtechDataCodes.TOGGLE_COLLAPSE_ITEMS,
                    packetBuffer -> packetBuffer.writeBoolean(autoCollapse));
            notifyBlockUpdate();
            markDirty();
        }
    }

    public boolean autoCollapse() {
        return autoCollapse;
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if (dataId == GregtechDataCodes.WORKING_ENABLED) {
            workingEnabled = buf.readBoolean();
        } else if (dataId == GregtechDataCodes.TOGGLE_COLLAPSE_ITEMS) {
            autoCollapse = buf.readBoolean();
        }
    }

    @Override
    public boolean onScrewdriverClick(EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                      CuboidRayTraceResult hitResult) {
        setAutoCollapse(!autoCollapse);

        if (!getWorld().isRemote) {
            if (autoCollapse) {
                playerIn.sendStatusMessage(new TextComponentTranslation("gregtech.bus.collapse_true"), true);
            } else {
                playerIn.sendStatusMessage(new TextComponentTranslation("gregtech.bus.collapse_false"), true);
            }
        }
        return true;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);

        data.setBoolean("workingEnabled", workingEnabled);
        data.setBoolean("autoCollapse", autoCollapse);

        if (circuitInventory != null) {
            circuitInventory.write(data);
        }

        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);

        this.workingEnabled = data.getBoolean("workingEnabled");
        this.autoCollapse = data.getBoolean("autoCollapse");

        if (circuitInventory != null) {
            circuitInventory.read(data);
        }
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, @NotNull List<String> tooltip,
                               boolean advanced) {
        if (isExportHatch) {
            tooltip.add(I18n.format("gregtech.machine.item_bus.export.tooltip"));
        } else {
            tooltip.add(I18n.format("gregtech.machine.item_bus.import.tooltip"));
        }
        tooltip.add(I18n.format("gregtech.universal.tooltip.item_storage_capacity", getItemSize()));
        tooltip.add(I18n.format("gregtech.universal.tooltip.fluid_storage_capacity", getTankSize()));
        tooltip.add(I18n.format("gregtech.universal.enabled"));
    }

    @Override
    public void addToolUsages(ItemStack stack, @Nullable World world, List<String> tooltip, boolean advanced) {
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.access_covers"));
        tooltip.add(I18n.format("gregtech.tool_action.screwdriver.auto_collapse"));
        tooltip.add(I18n.format("gregtech.tool_action.wrench.set_facing"));
        super.addToolUsages(stack, world, tooltip, advanced);
    }
}
