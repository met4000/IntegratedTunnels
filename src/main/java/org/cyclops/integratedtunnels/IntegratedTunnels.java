package org.cyclops.integratedtunnels;

import net.minecraft.block.Block;
import net.minecraft.item.ItemGroup;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.Level;
import org.cyclops.cyclopscore.config.ConfigHandler;
import org.cyclops.cyclopscore.infobook.IInfoBookRegistry;
import org.cyclops.cyclopscore.init.ItemGroupMod;
import org.cyclops.cyclopscore.init.ModBaseVersionable;
import org.cyclops.cyclopscore.proxy.IClientProxy;
import org.cyclops.cyclopscore.proxy.ICommonProxy;
import org.cyclops.integrateddynamics.IntegratedDynamics;
import org.cyclops.integrateddynamics.infobook.OnTheDynamicsOfIntegrationBook;
import org.cyclops.integratedtunnels.api.world.IBlockBreakHandlerRegistry;
import org.cyclops.integratedtunnels.api.world.IBlockPlaceHandlerRegistry;
import org.cyclops.integratedtunnels.capability.ingredient.TunnelIngredientComponentCapabilities;
import org.cyclops.integratedtunnels.capability.network.FluidNetworkConfig;
import org.cyclops.integratedtunnels.capability.network.ItemNetworkConfig;
import org.cyclops.integratedtunnels.capability.network.TunnelNetworkCapabilityConstructors;
import org.cyclops.integratedtunnels.core.part.ContainerInterfaceSettingsConfig;
import org.cyclops.integratedtunnels.core.world.BlockBreakHandlerRegistry;
import org.cyclops.integratedtunnels.core.world.BlockBreakHandlers;
import org.cyclops.integratedtunnels.core.world.BlockBreakPlaceRegistry;
import org.cyclops.integratedtunnels.core.world.BlockPlaceHandlers;
import org.cyclops.integratedtunnels.item.ItemDummyPickAxeConfig;
import org.cyclops.integratedtunnels.part.PartTypes;
import org.cyclops.integratedtunnels.part.aspect.TunnelAspects;
import org.cyclops.integratedtunnels.proxy.ClientProxy;
import org.cyclops.integratedtunnels.proxy.CommonProxy;

/**
 * The main mod class of this mod.
 * @author rubensworks (aka kroeserr)
 *
 */
@Mod(Reference.MOD_ID)
public class IntegratedTunnels extends ModBaseVersionable<IntegratedTunnels> {
    
    /**
     * The unique instance of this mod.
     */
    public static IntegratedTunnels _instance;

    public IntegratedTunnels() {
        super(Reference.MOD_ID, (instance) -> _instance = instance);

        // Registries
        getRegistryManager().addRegistry(IBlockBreakHandlerRegistry.class, BlockBreakHandlerRegistry.getInstance());
        getRegistryManager().addRegistry(IBlockPlaceHandlerRegistry.class, BlockBreakPlaceRegistry.getInstance());

        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onRegistriesCreate);
    }

    public void onRegistriesCreate(RegistryEvent.NewRegistry event) {
        TunnelIngredientComponentCapabilities.load();
        TunnelAspects.load();
        PartTypes.load();
        BlockBreakHandlers.load();
        BlockPlaceHandlers.load();
    }

    @Override
    protected void setup(FMLCommonSetupEvent event) {
        super.setup(event);

        MinecraftForge.EVENT_BUS.register(new TunnelNetworkCapabilityConstructors());

        // Initialize info book
        IntegratedDynamics._instance.getRegistryManager().getRegistry(IInfoBookRegistry.class)
                .registerSection(
                        OnTheDynamicsOfIntegrationBook.getInstance(), "info_book.integrateddynamics.manual",
                        "/data/" + Reference.MOD_ID + "/info/tunnels_info.xml");
        IntegratedDynamics._instance.getRegistryManager().getRegistry(IInfoBookRegistry.class)
                .registerSection(
                        OnTheDynamicsOfIntegrationBook.getInstance(), "info_book.integrateddynamics.tutorials",
                        "/data/" + Reference.MOD_ID + "/info/tunnels_tutorials.xml");
    }

    @Override
    public ItemGroup constructDefaultItemGroup() {
        return new ItemGroupMod(this, () -> RegistryEntries.ITEM_PART_INTERFACE);
    }

    @Override
    public void onConfigsRegister(ConfigHandler configHandler) {
        super.onConfigsRegister(configHandler);

        configHandler.addConfigurable(new GeneralConfig());

        configHandler.addConfigurable(new ItemNetworkConfig());
        configHandler.addConfigurable(new FluidNetworkConfig());

        configHandler.addConfigurable(new ItemDummyPickAxeConfig());

        configHandler.addConfigurable(new ContainerInterfaceSettingsConfig());
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected IClientProxy constructClientProxy() {
        return new ClientProxy();
    }

    @Override
    protected ICommonProxy constructCommonProxy() {
        return new CommonProxy();
    }

    /**
     * Log a new info message for this mod.
     * @param message The message to show.
     */
    public static void clog(String message) {
        clog(Level.INFO, message);
    }
    
    /**
     * Log a new message of the given level for this mod.
     * @param level The level in which the message must be shown.
     * @param message The message to show.
     */
    public static void clog(Level level, String message) {
        IntegratedTunnels._instance.getLoggerHelper().log(level, message);
    }
    
}
