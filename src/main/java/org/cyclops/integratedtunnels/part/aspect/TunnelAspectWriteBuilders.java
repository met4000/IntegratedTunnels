package org.cyclops.integratedtunnels.part.aspect;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import org.apache.commons.lang3.tuple.Triple;
import org.cyclops.commoncapabilities.api.ingredient.storage.IIngredientComponentStorage;
import org.cyclops.cyclopscore.datastructure.DimPos;
import org.cyclops.cyclopscore.helper.BlockHelpers;
import org.cyclops.cyclopscore.helper.ItemStackHelpers;
import org.cyclops.cyclopscore.helper.L10NHelpers;
import org.cyclops.integrateddynamics.api.evaluate.EvaluationException;
import org.cyclops.integrateddynamics.api.evaluate.operator.IOperator;
import org.cyclops.integrateddynamics.api.evaluate.variable.IValueType;
import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.network.IPositionedAddonsNetwork;
import org.cyclops.integrateddynamics.api.network.IPositionedAddonsNetworkIngredients;
import org.cyclops.integrateddynamics.api.part.PartPos;
import org.cyclops.integrateddynamics.api.part.PartTarget;
import org.cyclops.integrateddynamics.api.part.aspect.property.IAspectProperties;
import org.cyclops.integrateddynamics.api.part.aspect.property.IAspectPropertyTypeInstance;
import org.cyclops.integrateddynamics.api.part.write.IPartStateWriter;
import org.cyclops.integrateddynamics.api.part.write.IPartTypeWriter;
import org.cyclops.integrateddynamics.core.evaluate.variable.*;
import org.cyclops.integrateddynamics.core.helper.L10NValues;
import org.cyclops.integrateddynamics.core.helper.NetworkHelpers;
import org.cyclops.integrateddynamics.core.helper.PartHelpers;
import org.cyclops.integrateddynamics.core.part.aspect.build.AspectBuilder;
import org.cyclops.integrateddynamics.core.part.aspect.build.IAspectValuePropagator;
import org.cyclops.integrateddynamics.core.part.aspect.build.IAspectWriteActivator;
import org.cyclops.integrateddynamics.core.part.aspect.build.IAspectWriteDeactivator;
import org.cyclops.integrateddynamics.core.part.aspect.property.AspectProperties;
import org.cyclops.integrateddynamics.core.part.aspect.property.AspectPropertyTypeInstance;
import org.cyclops.integrateddynamics.part.aspect.read.AspectReadBuilders;
import org.cyclops.integrateddynamics.part.aspect.write.AspectWriteBuilders;
import org.cyclops.integratedtunnels.Capabilities;
import org.cyclops.integratedtunnels.GeneralConfig;
import org.cyclops.integratedtunnels.IntegratedTunnels;
import org.cyclops.integratedtunnels.api.network.IItemNetwork;
import org.cyclops.integratedtunnels.capability.network.FluidNetworkConfig;
import org.cyclops.integratedtunnels.capability.network.ItemNetworkConfig;
import org.cyclops.integratedtunnels.core.IngredientPredicate;
import org.cyclops.integratedtunnels.core.ItemHandlerWorldEntityExportWrapper;
import org.cyclops.integratedtunnels.core.ItemHandlerWorldEntityImportWrapper;
import org.cyclops.integratedtunnels.core.ItemStoragePlayerWrapper;
import org.cyclops.integratedtunnels.core.TunnelEnergyHelpers;
import org.cyclops.integratedtunnels.core.TunnelFluidHelpers;
import org.cyclops.integratedtunnels.core.TunnelHelpers;
import org.cyclops.integratedtunnels.core.TunnelItemHelpers;
import org.cyclops.integratedtunnels.core.part.PartStatePositionedAddon;
import org.cyclops.integratedtunnels.part.PartStatePlayerSimulator;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Collection of tunnel aspect write builders and value propagators.
 * @author rubensworks
 */
public class TunnelAspectWriteBuilders {

    @Nullable
    public static Entity getEntity(PartPos target, int entityIndex) {
        List<Entity> entities = target.getPos().getWorld().getEntitiesWithinAABB(Entity.class,
                new AxisAlignedBB(target.getPos().getBlockPos()));
        Entity entity = null;
        if (entities.size() > 0 && entityIndex < entities.size()) {
            if (entityIndex == -1) {
                entity = entities.get(target.getPos().getWorld().rand.nextInt(entities.size()));
            } else {
                entity = entities.get(entityIndex);
            }
        }
        return entity;
    }

    public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_BLACKLIST =
            new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.blacklist.name");
    public static final IAspectPropertyTypeInstance<ValueTypeInteger, ValueTypeInteger.ValueInteger> PROP_CHANNEL =
            new AspectPropertyTypeInstance<>(ValueTypes.INTEGER, "aspect.aspecttypes.integrateddynamics.integer.channel.name");
    public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_ROUNDROBIN =
            new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.roundrobin.name");
    public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_EXACTAMOUNT =
            new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.exactamount.name");
    public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_EMPTYISANY =
            new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.emptyisany.name");
    public static final IAspectProperties PROPERTIES_CHANNEL = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
            PROP_CHANNEL,
            PROP_ROUNDROBIN
    ));
    static {
        PROPERTIES_CHANNEL.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
        PROPERTIES_CHANNEL.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
    }

    public static final class Energy {

        public static final IAspectWriteActivator ACTIVATOR = createPositionedNetworkAddonActivator(
                () -> Capabilities.NETWORK_ENERGY, CapabilityEnergy.ENERGY);
        public static final IAspectWriteDeactivator DEACTIVATOR = createPositionedNetworkAddonDeactivator(
                () -> Capabilities.NETWORK_ENERGY, CapabilityEnergy.ENERGY);

        public static final AspectBuilder<ValueTypeBoolean.ValueBoolean, ValueTypeBoolean, Triple<PartTarget, IAspectProperties, Boolean>>
                BUILDER_BOOLEAN = AspectWriteBuilders.BUILDER_BOOLEAN.byMod(IntegratedTunnels._instance)
                .appendActivator(ACTIVATOR).appendDeactivator(DEACTIVATOR)
                .appendKind("energy").handle(AspectWriteBuilders.PROP_GET_BOOLEAN).withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueTypeInteger.ValueInteger, ValueTypeInteger, Triple<PartTarget, IAspectProperties, Integer>>
                BUILDER_INTEGER = AspectWriteBuilders.BUILDER_INTEGER.byMod(IntegratedTunnels._instance)
                .appendActivator(ACTIVATOR).appendDeactivator(DEACTIVATOR)
                .appendKind("energy").handle(AspectWriteBuilders.PROP_GET_INTEGER).withProperties(PROPERTIES_CHANNEL);

        public static final Predicate<ValueTypeInteger.ValueInteger> VALIDATOR_INTEGER_MAXRATE =
                input -> input.getRawValue() <= org.cyclops.integrateddynamics.GeneralConfig.energyRateLimit;

        public static final IAspectPropertyTypeInstance<ValueTypeInteger, ValueTypeInteger.ValueInteger> PROP_RATE =
                new AspectPropertyTypeInstance<>(ValueTypes.INTEGER, "aspect.aspecttypes.integratedtunnels.integer.energy.rate.name",
                        AspectReadBuilders.VALIDATOR_INTEGER_POSITIVE.and(VALIDATOR_INTEGER_MAXRATE));
        public static final IAspectProperties PROPERTIES_RATE = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                PROP_CHANNEL,
                PROP_ROUNDROBIN,
                PROP_RATE,
                PROP_EXACTAMOUNT
        ));
        public static final IAspectProperties PROPERTIES = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                PROP_CHANNEL,
                PROP_ROUNDROBIN,
                PROP_EXACTAMOUNT
        ));
        static {
            PROPERTIES_RATE.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_RATE.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
            PROPERTIES_RATE.setValue(PROP_RATE, ValueTypeInteger.ValueInteger.of(1000));
            PROPERTIES_RATE.setValue(PROP_EXACTAMOUNT, ValueTypeBoolean.ValueBoolean.of(false));

            PROPERTIES.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
            PROPERTIES.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES.setValue(PROP_EXACTAMOUNT, ValueTypeBoolean.ValueBoolean.of(false));
        }

        public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, Boolean>, Triple<PartTarget, IAspectProperties, Integer>>
                PROP_GETRATE = input -> Triple.of(input.getLeft(), input.getMiddle(), input.getRight() ? input.getMiddle().getValue(PROP_RATE).getRawValue() : 0);
        public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, Integer>, IEnergyTarget>
                PROP_ENERGYTARGET = input -> IEnergyTarget.ofTile(input.getLeft(), input.getMiddle(), input.getRight());
        public static final IAspectValuePropagator<IEnergyTarget, Void>
                PROP_EXPORT = input -> {
            if (input.hasValidTarget() && input.getAmount() != 0) {
                input.preTransfer();
                TunnelEnergyHelpers.moveEnergy(input.getEnergyChannel(), input.getStorage(), input.getAmount(), input.isExactAmount());
                input.postTransfer();
            }
            return null;
        };
        public static final IAspectValuePropagator<IEnergyTarget, Void>
                PROP_IMPORT = input -> {
            if (input.hasValidTarget() && input.getAmount() != 0) {
                input.preTransfer();
                TunnelEnergyHelpers.moveEnergy(input.getStorage(), input.getEnergyChannel(), input.getAmount(), input.isExactAmount());
                input.postTransfer();
            }
            return null;
        };

    }

    public static final class Item {

        public static final IAspectWriteActivator ACTIVATOR = createPositionedNetworkAddonActivator(
                () -> ItemNetworkConfig.CAPABILITY, CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);
        public static final IAspectWriteDeactivator DEACTIVATOR = createPositionedNetworkAddonDeactivator(
                () -> ItemNetworkConfig.CAPABILITY, CapabilityItemHandler.ITEM_HANDLER_CAPABILITY);

        public static final AspectBuilder<ValueTypeBoolean.ValueBoolean, ValueTypeBoolean, Triple<PartTarget, IAspectProperties, Boolean>>
                BUILDER_BOOLEAN = AspectWriteBuilders.BUILDER_BOOLEAN.byMod(IntegratedTunnels._instance)
                .appendActivator(ACTIVATOR).appendDeactivator(DEACTIVATOR)
                .appendKind("item").handle(AspectWriteBuilders.PROP_GET_BOOLEAN).withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueTypeInteger.ValueInteger, ValueTypeInteger, Triple<PartTarget, IAspectProperties, Integer>>
                BUILDER_INTEGER = AspectWriteBuilders.BUILDER_INTEGER.byMod(IntegratedTunnels._instance)
                .appendActivator(ACTIVATOR).appendDeactivator(DEACTIVATOR)
                .appendKind("item").handle(AspectWriteBuilders.PROP_GET_INTEGER).withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueObjectTypeItemStack.ValueItemStack, ValueObjectTypeItemStack, Triple<PartTarget, IAspectProperties, ItemStack>>
                BUILDER_ITEMSTACK = AspectWriteBuilders.BUILDER_ITEMSTACK.byMod(IntegratedTunnels._instance)
                .appendActivator(ACTIVATOR).appendDeactivator(DEACTIVATOR)
                .appendKind("item").handle(AspectWriteBuilders.PROP_GET_ITEMSTACK).withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueTypeList.ValueList, ValueTypeList, Triple<PartTarget, IAspectProperties, ValueTypeList.ValueList>>
                BUILDER_LIST = AspectWriteBuilders.BUILDER_LIST.byMod(IntegratedTunnels._instance)
                .appendActivator(ACTIVATOR).appendDeactivator(DEACTIVATOR)
                .appendKind("item").withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueTypeOperator.ValueOperator, ValueTypeOperator, Triple<PartTarget, IAspectProperties, ValueTypeOperator.ValueOperator>>
                BUILDER_OPERATOR = AspectWriteBuilders.BUILDER_OPERATOR.byMod(IntegratedTunnels._instance)
                .appendActivator(ACTIVATOR).appendDeactivator(DEACTIVATOR)
                .appendKind("item").withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueTypeNbt.ValueNbt, ValueTypeNbt, Triple<PartTarget, IAspectProperties, NBTTagCompound>>
                BUILDER_NBT = AspectWriteBuilders.BUILDER_NBT.byMod(IntegratedTunnels._instance)
                .appendActivator(ACTIVATOR).appendDeactivator(DEACTIVATOR)
                .appendKind("item").handle(AspectWriteBuilders.PROP_GET_NBT).withProperties(PROPERTIES_CHANNEL);

        public static final IAspectPropertyTypeInstance<ValueTypeInteger, ValueTypeInteger.ValueInteger> PROP_RATE =
                new AspectPropertyTypeInstance<>(ValueTypes.INTEGER, "aspect.aspecttypes.integratedtunnels.integer.item.rate.name",
                        AspectReadBuilders.VALIDATOR_INTEGER_POSITIVE);
        public static final IAspectPropertyTypeInstance<ValueTypeInteger, ValueTypeInteger.ValueInteger> PROP_SLOT =
                new AspectPropertyTypeInstance<>(ValueTypes.INTEGER, "aspect.aspecttypes.integratedtunnels.integer.item.slot.name");
        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_CHECK_STACKSIZE =
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.item.checkstacksize.name");
        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_CHECK_DAMAGE =
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.item.checkdamage.name");
        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_CHECK_NBT =
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.item.checknbt.name");
        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_NBT_SUBSET =
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.item.nbtsubset.name");
        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_NBT_SUPERSET =
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.item.nbtsuperset.name");
        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_NBT_REQUIRE=
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.item.nbtrequire.name");
        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_NBT_RECURSIVE=
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.item.nbtrecursive.name");
        public static final IAspectProperties PROPERTIES_RATESLOT = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                PROP_CHANNEL,
                PROP_ROUNDROBIN,
                PROP_RATE,
                PROP_EXACTAMOUNT,
                PROP_SLOT
        ));
        public static final IAspectProperties PROPERTIES_SLOT = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                PROP_CHANNEL,
                PROP_ROUNDROBIN,
                PROP_EXACTAMOUNT,
                PROP_SLOT
        ));
        public static final IAspectProperties PROPERTIES_RATE = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                PROP_CHANNEL,
                PROP_ROUNDROBIN,
                PROP_RATE,
                PROP_EXACTAMOUNT
        ));
        public static final IAspectProperties PROPERTIES_RATESLOTCHECKS = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                PROP_CHANNEL,
                PROP_ROUNDROBIN,
                PROP_BLACKLIST,
                PROP_RATE,
                PROP_EXACTAMOUNT,
                PROP_SLOT,
                PROP_CHECK_STACKSIZE,
                PROP_CHECK_DAMAGE,
                PROP_CHECK_NBT,
                PROP_EMPTYISANY
        ));
        public static final IAspectProperties PROPERTIES_RATESLOTCHECKSLIST = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                PROP_CHANNEL,
                PROP_ROUNDROBIN,
                PROP_BLACKLIST,
                PROP_RATE,
                PROP_EXACTAMOUNT,
                PROP_SLOT,
                PROP_CHECK_STACKSIZE,
                PROP_CHECK_DAMAGE,
                PROP_CHECK_NBT
        ));
        public static final IAspectProperties PROPERTIES_NBT = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                PROP_CHANNEL,
                PROP_ROUNDROBIN,
                PROP_BLACKLIST,
                PROP_RATE,
                PROP_EXACTAMOUNT,
                PROP_SLOT,
                PROP_NBT_SUBSET,
                PROP_NBT_SUPERSET,
                PROP_NBT_REQUIRE,
                PROP_NBT_RECURSIVE
        ));
        static {
            PROPERTIES_RATESLOT.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
            PROPERTIES_RATESLOT.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_RATESLOT.setValue(PROP_RATE, ValueTypeInteger.ValueInteger.of(64));
            PROPERTIES_RATESLOT.setValue(PROP_EXACTAMOUNT, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_RATESLOT.setValue(PROP_SLOT, ValueTypeInteger.ValueInteger.of(-1));

            PROPERTIES_SLOT.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
            PROPERTIES_SLOT.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_SLOT.setValue(PROP_EXACTAMOUNT, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_SLOT.setValue(PROP_SLOT, ValueTypeInteger.ValueInteger.of(-1));

            PROPERTIES_RATE.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
            PROPERTIES_RATE.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_RATE.setValue(PROP_RATE, ValueTypeInteger.ValueInteger.of(64));
            PROPERTIES_RATE.setValue(PROP_EXACTAMOUNT, ValueTypeBoolean.ValueBoolean.of(false));

            PROPERTIES_RATESLOTCHECKS.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
            PROPERTIES_RATESLOTCHECKS.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_RATESLOTCHECKS.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_RATESLOTCHECKS.setValue(PROP_RATE, ValueTypeInteger.ValueInteger.of(64));
            PROPERTIES_RATESLOTCHECKS.setValue(PROP_EXACTAMOUNT, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_RATESLOTCHECKS.setValue(PROP_SLOT, ValueTypeInteger.ValueInteger.of(-1));
            PROPERTIES_RATESLOTCHECKS.setValue(PROP_CHECK_STACKSIZE, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_RATESLOTCHECKS.setValue(PROP_CHECK_DAMAGE, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_RATESLOTCHECKS.setValue(PROP_CHECK_NBT, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_RATESLOTCHECKS.setValue(PROP_EMPTYISANY, ValueTypeBoolean.ValueBoolean.of(true));

            PROPERTIES_RATESLOTCHECKSLIST.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
            PROPERTIES_RATESLOTCHECKSLIST.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_RATESLOTCHECKSLIST.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_RATESLOTCHECKSLIST.setValue(PROP_RATE, ValueTypeInteger.ValueInteger.of(64));
            PROPERTIES_RATESLOTCHECKSLIST.setValue(PROP_EXACTAMOUNT, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_RATESLOTCHECKSLIST.setValue(PROP_SLOT, ValueTypeInteger.ValueInteger.of(-1));
            PROPERTIES_RATESLOTCHECKSLIST.setValue(PROP_CHECK_STACKSIZE, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_RATESLOTCHECKSLIST.setValue(PROP_CHECK_DAMAGE, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_RATESLOTCHECKSLIST.setValue(PROP_CHECK_NBT, ValueTypeBoolean.ValueBoolean.of(true));

            PROPERTIES_NBT.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
            PROPERTIES_NBT.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_NBT.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_NBT.setValue(PROP_RATE, ValueTypeInteger.ValueInteger.of(64));
            PROPERTIES_NBT.setValue(PROP_EXACTAMOUNT, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_NBT.setValue(PROP_SLOT, ValueTypeInteger.ValueInteger.of(-1));
            PROPERTIES_NBT.setValue(PROP_NBT_SUBSET, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_NBT.setValue(PROP_NBT_SUPERSET, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_NBT.setValue(PROP_NBT_REQUIRE, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_NBT.setValue(PROP_NBT_RECURSIVE, ValueTypeBoolean.ValueBoolean.of(true));
        }

        public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, Boolean>, Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<ItemStack, Integer>>>
                PROP_BOOLEAN_ITEMPREDICATE = input -> {
            IAspectProperties properties = input.getMiddle();
            int amount = input.getRight() ? input.getMiddle().getValue(PROP_RATE).getRawValue() : 0;
            boolean exactAmount = properties.getValue(PROP_EXACTAMOUNT).getRawValue();
            IngredientPredicate<ItemStack, Integer> itemStackMatcher = input.getRight() ? TunnelItemHelpers.matchAll(amount, exactAmount) : TunnelItemHelpers.MATCH_NONE;
            int transferHash = input.getRight() ? 1 : 0;
            int slot = input.getMiddle().getValue(PROP_SLOT).getRawValue();
            return Triple.of(input.getLeft(), input.getMiddle(), ChanneledTargetInformation.of(itemStackMatcher, transferHash, slot));
        };
        public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, Integer>, Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<ItemStack, Integer>>>
                PROP_INTEGER_ITEMPREDICATE = input -> {
            IAspectProperties properties = input.getMiddle();
            int amount = input.getRight();
            boolean exactAmount = properties.getValue(PROP_EXACTAMOUNT).getRawValue();
            IngredientPredicate<ItemStack, Integer> itemStackMatcher = TunnelItemHelpers.matchAll(amount, exactAmount);
            int transferHash = input.getRight();
            int slot = properties.getValue(PROP_SLOT).getRawValue();
            return Triple.of(input.getLeft(), input.getMiddle(), ChanneledTargetInformation.of(itemStackMatcher, transferHash, slot));
        };
        public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, Integer>, Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<ItemStack, Integer>>>
                PROP_INTEGER_SLOT_ITEMPREDICATE = input -> {
            IAspectProperties properties = input.getMiddle();
            int amount = input.getRight() >= -1 ? properties.getValue(PROP_RATE).getRawValue() : 0;
            boolean exactAmount = properties.getValue(PROP_EXACTAMOUNT).getRawValue();
            IngredientPredicate<ItemStack, Integer> itemStackMatcher = TunnelItemHelpers.matchAll(amount, exactAmount);
            int transferHash = input.getRight() > 0 ? 1 : 0;
            int slot = input.getRight();
            return Triple.of(input.getLeft(), input.getMiddle(), ChanneledTargetInformation.of(itemStackMatcher, transferHash, slot));
        };
        public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, ItemStack>, Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<ItemStack, Integer>>>
                PROP_ITEMSTACK_ITEMPREDICATE = input -> {
            IAspectProperties properties = input.getMiddle();
            boolean checkStackSize = properties.getValue(PROP_CHECK_STACKSIZE).getRawValue();
            boolean checkDamage = properties.getValue(PROP_CHECK_DAMAGE).getRawValue();
            boolean checkNbt = properties.getValue(PROP_CHECK_NBT).getRawValue();
            boolean blacklist = properties.getValue(PROP_BLACKLIST).getRawValue();
            IngredientPredicate.EmptyBehaviour emptyBehaviour = IngredientPredicate.EmptyBehaviour.fromBoolean(properties.getValue(PROP_EMPTYISANY).getRawValue());
            boolean exactAmount = properties.getValue(PROP_EXACTAMOUNT).getRawValue();
            int amount = properties.getValue(PROP_RATE).getRawValue();
            ItemStack prototype = TunnelItemHelpers.prototypeWithCount(input.getRight(), amount);

            IngredientPredicate<ItemStack, Integer> itemStackMatcher = TunnelItemHelpers.matchItemStack(prototype, true, checkStackSize, checkDamage, checkNbt, blacklist, exactAmount, emptyBehaviour);
            int transferHash = ItemStackHelpers.getItemStackHashCode(input.getRight());
            int slot = properties.getValue(PROP_SLOT).getRawValue();
            return Triple.of(input.getLeft(), input.getMiddle(), ChanneledTargetInformation.of(itemStackMatcher, transferHash, slot));
        };
        public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, ValueTypeList.ValueList>, Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<ItemStack, Integer>>>
                PROP_ITEMSTACKLIST_ITEMPREDICATE = input -> {
            ValueTypeList.ValueList list = input.getRight();
            if (list.getRawValue().getValueType() != ValueTypes.OBJECT_ITEMSTACK) {
                throw new EvaluationException(new L10NHelpers.UnlocalizedString(L10NValues.VALUETYPE_ERROR_INVALIDLISTVALUETYPE,
                        ValueTypes.OBJECT_ITEMSTACK, list.getRawValue().getValueType()).localize());
            }
            IAspectProperties properties = input.getMiddle();
            boolean checkStackSize = properties.getValue(PROP_CHECK_STACKSIZE).getRawValue();
            boolean checkDamage = properties.getValue(PROP_CHECK_DAMAGE).getRawValue();
            boolean checkNbt = properties.getValue(PROP_CHECK_NBT).getRawValue();
            boolean blacklist = properties.getValue(PROP_BLACKLIST).getRawValue();
            boolean exactAmount = properties.getValue(PROP_EXACTAMOUNT).getRawValue();
            int amount = properties.getValue(PROP_RATE).getRawValue();

            IngredientPredicate<ItemStack, Integer> itemStackMatcher = TunnelItemHelpers.matchItemStacks(list.getRawValue(), true, checkStackSize, checkDamage, checkNbt, blacklist, amount, exactAmount);
            int transferHash = list.getRawValue().hashCode();
            int slot = properties.getValue(PROP_SLOT).getRawValue();
            return Triple.of(input.getLeft(), input.getMiddle(), ChanneledTargetInformation.of(itemStackMatcher, transferHash, slot));
        };
        public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, ValueTypeOperator.ValueOperator>, Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<ItemStack, Integer>>>
                PROP_ITEMSTACKPREDICATE_ITEMPREDICATE = input -> {
            IOperator predicate = input.getRight().getRawValue();
            if (predicate.getInputTypes().length == 1
                    && ValueHelpers.correspondsTo(predicate.getInputTypes()[0], ValueTypes.OBJECT_ITEMSTACK)
                    && ValueHelpers.correspondsTo(predicate.getOutputType(), ValueTypes.BOOLEAN)) {
                IAspectProperties properties = input.getMiddle();
                int amount = properties.getValue(PROP_RATE).getRawValue();
                boolean exactAmount = properties.getValue(PROP_EXACTAMOUNT).getRawValue();
                IngredientPredicate<ItemStack, Integer> itemStackMatcher = TunnelItemHelpers.matchPredicateItem(input.getLeft(), predicate, amount, exactAmount);
                int transferHash = predicate.hashCode();
                int slot = properties.getValue(PROP_SLOT).getRawValue();
                return Triple.of(input.getLeft(), input.getMiddle(), ChanneledTargetInformation.of(itemStackMatcher, transferHash, slot));
            } else {
                String current = ValueTypeOperator.getSignature(predicate);
                String expected = ValueTypeOperator.getSignature(new IValueType[]{ValueTypes.OBJECT_ITEMSTACK}, ValueTypes.BOOLEAN);
                throw new EvaluationException(new L10NHelpers.UnlocalizedString(L10NValues.ASPECT_ERROR_INVALIDTYPE,
                        expected, current).localize());
            }
        };
        public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, NBTTagCompound>, Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<ItemStack, Integer>>>
                PROP_NBT_ITEMPREDICATE = input -> {
            NBTTagCompound tag = input.getRight();
            IAspectProperties properties = input.getMiddle();
            int amount = properties.getValue(PROP_RATE).getRawValue();
            boolean exactAmount = properties.getValue(PROP_EXACTAMOUNT).getRawValue();
            int transferHash = tag.toString().hashCode();
            int slot = properties.getValue(PROP_SLOT).getRawValue();
            boolean subset = properties.getValue(PROP_NBT_SUBSET).getRawValue();
            boolean superset = properties.getValue(PROP_NBT_SUPERSET).getRawValue();
            boolean requireNbt = properties.getValue(PROP_NBT_REQUIRE).getRawValue();
            boolean recursive = properties.getValue(PROP_NBT_RECURSIVE).getRawValue();
            boolean blacklist = properties.getValue(PROP_BLACKLIST).getRawValue();
            IngredientPredicate<ItemStack, Integer> itemStackMatcher = TunnelItemHelpers.matchNbt(tag, subset, superset, requireNbt, recursive, blacklist, amount, exactAmount);
            return Triple.of(input.getLeft(), input.getMiddle(), ChanneledTargetInformation.of(itemStackMatcher, transferHash, slot));
        };
        public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, IBlockState>, Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<ItemStack, Integer>>>
                PROP_BLOCK_ITEMPREDICATE = input -> {
            IAspectProperties properties = input.getMiddle();

            boolean blacklist = properties.getValue(PROP_BLACKLIST).getRawValue();
            IngredientPredicate.EmptyBehaviour emptyBehaviour = IngredientPredicate.EmptyBehaviour.fromBoolean(properties.getValue(PROP_EMPTYISANY).getRawValue());
            int amount = properties.getValue(PROP_RATE).getRawValue();
            boolean exactAmount = false;
            ItemStack prototype = TunnelItemHelpers.prototypeWithCount(BlockHelpers.getItemStackFromBlockState(input.getRight()), amount);
            IngredientPredicate<ItemStack, Integer> itemStackMatcher = TunnelItemHelpers.matchItemStack(prototype, true, false, true, false, blacklist, exactAmount, emptyBehaviour);
            int transferHash = ItemStackHelpers.getItemStackHashCode(prototype);
            int slot = properties.getValue(PROP_SLOT).getRawValue();
            return Triple.of(input.getLeft(), input.getMiddle(), ChanneledTargetInformation.of(itemStackMatcher, transferHash, slot));
        };
        public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, ValueTypeList.ValueList>, Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<ItemStack, Integer>>>
                PROP_BLOCKLIST_ITEMPREDICATE = input -> {
            ValueTypeList.ValueList list = input.getRight();
            if (list.getRawValue().getValueType() != ValueTypes.OBJECT_BLOCK) {
                throw new EvaluationException(new L10NHelpers.UnlocalizedString(L10NValues.VALUETYPE_ERROR_INVALIDLISTVALUETYPE,
                        ValueTypes.OBJECT_BLOCK, list.getRawValue().getValueType()).localize());
            }
            IAspectProperties properties = input.getMiddle();
            boolean blacklist = properties.getValue(PROP_BLACKLIST).getRawValue();
            int amount = 1;
            boolean exactAmount = false;

            IngredientPredicate<ItemStack, Integer> itemStackMatcher = TunnelItemHelpers.matchBlocks(list.getRawValue(), true, false, true, false, blacklist, amount, exactAmount);
            int transferHash = list.getRawValue().hashCode();
            int slot = properties.getValue(PROP_SLOT).getRawValue();
            return Triple.of(input.getLeft(), input.getMiddle(), ChanneledTargetInformation.of(itemStackMatcher, transferHash, slot));
        };
        public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, ValueTypeOperator.ValueOperator>, Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<ItemStack, Integer>>>
                PROP_BLOCKPREDICATE_ITEMPREDICATE = input -> {
            IOperator predicate = input.getRight().getRawValue();
            if (predicate.getInputTypes().length == 1
                    && ValueHelpers.correspondsTo(predicate.getInputTypes()[0], ValueTypes.OBJECT_BLOCK)
                    && ValueHelpers.correspondsTo(predicate.getOutputType(), ValueTypes.BOOLEAN)) {
                IAspectProperties properties = input.getMiddle();
                int amount = 1;
                boolean exactAmount = false;
                IngredientPredicate<ItemStack, Integer> itemStackMatcher = TunnelItemHelpers.matchPredicateBlock(input.getLeft(), predicate, amount, exactAmount);
                int transferHash = predicate.hashCode();
                int slot = properties.getValue(PROP_SLOT).getRawValue();
                return Triple.of(input.getLeft(), input.getMiddle(), ChanneledTargetInformation.of(itemStackMatcher, transferHash, slot));
            } else {
                String current = ValueTypeOperator.getSignature(predicate);
                String expected = ValueTypeOperator.getSignature(new IValueType[]{ValueTypes.OBJECT_BLOCK}, ValueTypes.BOOLEAN);
                throw new EvaluationException(new L10NHelpers.UnlocalizedString(L10NValues.ASPECT_ERROR_INVALIDTYPE,
                        expected, current).localize());
            }
        };

        public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<ItemStack, Integer>>, IItemTarget>
                PROP_ITEMTARGET = input -> IItemTarget.ofCapabilityProvider(input.getRight().getTransferHash(), input.getLeft(),
                input.getMiddle(), input.getRight().getIngredientPredicate(), input.getRight().getSlot());

        public static final IAspectValuePropagator<IItemTarget, Void>
                PROP_EXPORT = input -> {
            if (input.hasValidTarget()) {
                input.preTransfer();
                TunnelHelpers.moveSingleStateOptimized(
                        input.getConnectionHash(),
                        input.getItemChannel(), -1,
                        input.getStorage(), input.getSlot(),
                        input.getItemStackMatcher());
                input.postTransfer();
            }
            return null;
        };
        public static final IAspectValuePropagator<IItemTarget, Void>
                PROP_IMPORT = input -> {
            if (input.hasValidTarget()) {
                input.preTransfer();
                TunnelHelpers.moveSingleStateOptimized(
                        input.getConnectionHash(),
                        input.getStorage(), input.getSlot(),
                        input.getItemChannel(), -1,
                        input.getItemStackMatcher());
                input.postTransfer();
            }
            return null;
        };

    }

    public static final class Fluid {

        public static final IAspectWriteActivator ACTIVATOR = createPositionedNetworkAddonActivator(
            () -> FluidNetworkConfig.CAPABILITY,
        CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY);
        public static final IAspectWriteDeactivator DEACTIVATOR = createPositionedNetworkAddonDeactivator(
            () -> FluidNetworkConfig.CAPABILITY,
        CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY);

        public static final AspectBuilder<ValueTypeBoolean.ValueBoolean, ValueTypeBoolean, Triple<PartTarget, IAspectProperties, Boolean>>
                BUILDER_BOOLEAN = AspectWriteBuilders.BUILDER_BOOLEAN.byMod(IntegratedTunnels._instance)
                .appendActivator(ACTIVATOR).appendDeactivator(DEACTIVATOR)
                .appendKind("fluid").handle(AspectWriteBuilders.PROP_GET_BOOLEAN).withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueTypeInteger.ValueInteger, ValueTypeInteger, Triple<PartTarget, IAspectProperties, Integer>>
                BUILDER_INTEGER = AspectWriteBuilders.BUILDER_INTEGER.byMod(IntegratedTunnels._instance)
                .appendActivator(ACTIVATOR).appendDeactivator(DEACTIVATOR)
                .appendKind("fluid").handle(AspectWriteBuilders.PROP_GET_INTEGER).withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueObjectTypeFluidStack.ValueFluidStack, ValueObjectTypeFluidStack, Triple<PartTarget, IAspectProperties, FluidStack>>
                BUILDER_FLUIDSTACK = AspectWriteBuilders.BUILDER_FLUIDSTACK.byMod(IntegratedTunnels._instance)
                .appendActivator(ACTIVATOR).appendDeactivator(DEACTIVATOR)
                .appendKind("fluid").handle(AspectWriteBuilders.PROP_GET_FLUIDSTACK).withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueTypeList.ValueList, ValueTypeList, Triple<PartTarget, IAspectProperties, ValueTypeList.ValueList>>
                BUILDER_LIST = AspectWriteBuilders.BUILDER_LIST.byMod(IntegratedTunnels._instance)
                .appendActivator(ACTIVATOR).appendDeactivator(DEACTIVATOR)
                .appendKind("fluid").withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueTypeOperator.ValueOperator, ValueTypeOperator, Triple<PartTarget, IAspectProperties, ValueTypeOperator.ValueOperator>>
                BUILDER_OPERATOR = AspectWriteBuilders.BUILDER_OPERATOR.byMod(IntegratedTunnels._instance)
                .appendActivator(ACTIVATOR).appendDeactivator(DEACTIVATOR)
                .appendKind("fluid").withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueTypeNbt.ValueNbt, ValueTypeNbt, Triple<PartTarget, IAspectProperties, NBTTagCompound>>
                BUILDER_NBT = AspectWriteBuilders.BUILDER_NBT.byMod(IntegratedTunnels._instance)
                .appendActivator(ACTIVATOR).appendDeactivator(DEACTIVATOR)
                .appendKind("fluid").handle(AspectWriteBuilders.PROP_GET_NBT).withProperties(PROPERTIES_CHANNEL);

        public static final Predicate<ValueTypeInteger.ValueInteger> VALIDATOR_INTEGER_MAXRATE =
                input -> input.getRawValue() <= GeneralConfig.fluidRateLimit;

        public static final IAspectPropertyTypeInstance<ValueTypeInteger, ValueTypeInteger.ValueInteger> PROP_RATE =
                new AspectPropertyTypeInstance<>(ValueTypes.INTEGER, "aspect.aspecttypes.integratedtunnels.integer.fluid.rate.name",
                        AspectReadBuilders.VALIDATOR_INTEGER_POSITIVE.and(VALIDATOR_INTEGER_MAXRATE));
        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_CHECK_AMOUNT =
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.fluid.checkamount.name");
        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_CHECK_NBT =
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.fluid.checknbt.name");
        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_NBT_SUBSET =
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.fluid.nbtsubset.name");
        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_NBT_SUPERSET =
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.fluid.nbtsuperset.name");
        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_NBT_REQUIRE=
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.fluid.nbtrequire.name");
        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_NBT_RECURSIVE=
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.fluid.nbtrecursive.name");

        public static final IAspectProperties PROPERTIES = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                PROP_CHANNEL,
                PROP_EXACTAMOUNT
        ));
        public static final IAspectProperties PROPERTIES_RATE = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                PROP_CHANNEL,
                PROP_ROUNDROBIN,
                PROP_RATE,
                PROP_EXACTAMOUNT
        ));
        public static final IAspectProperties PROPERTIES_RATECHECKS = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                PROP_CHANNEL,
                PROP_ROUNDROBIN,
                PROP_BLACKLIST,
                PROP_RATE,
                PROP_EXACTAMOUNT,
                PROP_CHECK_AMOUNT,
                PROP_CHECK_NBT
        ));
        public static final IAspectProperties PROPERTIES_RATECHECKSLIST = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                PROP_CHANNEL,
                PROP_ROUNDROBIN,
                PROP_BLACKLIST,
                PROP_RATE,
                PROP_EXACTAMOUNT,
                PROP_CHECK_AMOUNT,
                PROP_CHECK_NBT
        ));
        public static final IAspectProperties PROPERTIES_NBT = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                PROP_CHANNEL,
                PROP_RATE,
                PROP_NBT_SUBSET,
                PROP_NBT_SUPERSET,
                PROP_NBT_REQUIRE,
                PROP_NBT_RECURSIVE
        ));
        static {
            PROPERTIES.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
            PROPERTIES.setValue(PROP_EXACTAMOUNT, ValueTypeBoolean.ValueBoolean.of(false));

            PROPERTIES_RATE.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
            PROPERTIES_RATE.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_RATE.setValue(PROP_RATE, ValueTypeInteger.ValueInteger.of(1000));
            PROPERTIES_RATE.setValue(PROP_EXACTAMOUNT, ValueTypeBoolean.ValueBoolean.of(false));

            PROPERTIES_RATECHECKS.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
            PROPERTIES_RATECHECKS.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_RATECHECKS.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_RATECHECKS.setValue(PROP_RATE, ValueTypeInteger.ValueInteger.of(1000));
            PROPERTIES_RATECHECKS.setValue(PROP_EXACTAMOUNT, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_RATECHECKS.setValue(PROP_CHECK_AMOUNT, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_RATECHECKS.setValue(PROP_CHECK_NBT, ValueTypeBoolean.ValueBoolean.of(true));

            PROPERTIES_RATECHECKSLIST.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
            PROPERTIES_RATECHECKSLIST.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_RATECHECKSLIST.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_RATECHECKSLIST.setValue(PROP_RATE, ValueTypeInteger.ValueInteger.of(1000));
            PROPERTIES_RATECHECKSLIST.setValue(PROP_EXACTAMOUNT, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_RATECHECKSLIST.setValue(PROP_CHECK_NBT, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_RATECHECKSLIST.setValue(PROP_CHECK_AMOUNT, ValueTypeBoolean.ValueBoolean.of(false));

            PROPERTIES_NBT.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
            PROPERTIES_NBT.setValue(PROP_RATE, ValueTypeInteger.ValueInteger.of(1000));
            PROPERTIES_NBT.setValue(PROP_NBT_SUBSET, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_NBT.setValue(PROP_NBT_SUPERSET, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_NBT.setValue(PROP_NBT_REQUIRE, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_NBT.setValue(PROP_NBT_RECURSIVE, ValueTypeBoolean.ValueBoolean.of(true));
        }

        public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, Boolean>, Triple<PartTarget, IAspectProperties, Integer>>
                PROP_BOOLEAN_GETRATE = input -> Triple.of(input.getLeft(), input.getMiddle(), input.getRight() ? input.getMiddle().getValue(PROP_RATE).getRawValue() : 0);

        public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, Integer>, Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<FluidStack, Integer>>>
                PROP_INTEGER_FLUIDPREDICATE = input -> Triple.of(input.getLeft(), input.getMiddle(), ChanneledTargetInformation.of(TunnelFluidHelpers.matchAll(input.getRight(), false), input.getRight(), -1));
        public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, FluidStack>, Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<FluidStack, Integer>>>
                PROP_FLUIDSTACK_FLUIDPREDICATE = input -> {
            IAspectProperties properties = input.getMiddle();
            int rate = properties.getValue(PROP_RATE).getRawValue();
            boolean exactAmount = properties.getValue(PROP_EXACTAMOUNT).getRawValue();
            boolean checkAmount = properties.getValue(PROP_CHECK_AMOUNT).getRawValue();
            boolean checkNbt = properties.getValue(PROP_CHECK_NBT).getRawValue();
            boolean blacklist = properties.getValue(PROP_BLACKLIST).getRawValue();
            int transferHash = input.getRight().hashCode();
            return Triple.of(input.getLeft(), input.getMiddle(),
                    ChanneledTargetInformation.of(TunnelFluidHelpers.matchFluidStack(TunnelFluidHelpers.prototypeWithCount(input.getRight(), rate), true, checkAmount, checkNbt, blacklist, exactAmount),
                            transferHash, -1));
        };
        public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, ValueTypeList.ValueList>, Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<FluidStack, Integer>>>
                PROP_FLUIDSTACKLIST_FLUIDPREDICATE = input -> {
            ValueTypeList.ValueList<ValueObjectTypeFluidStack, ValueObjectTypeFluidStack.ValueFluidStack> list = input.getRight();
            if (list.getRawValue().getValueType() != ValueTypes.OBJECT_FLUIDSTACK) {
                throw new EvaluationException(new L10NHelpers.UnlocalizedString(L10NValues.VALUETYPE_ERROR_INVALIDLISTVALUETYPE,
                        ValueTypes.OBJECT_FLUIDSTACK, list.getRawValue().getValueType()).localize());
            }
            IAspectProperties properties = input.getMiddle();
            int rate = properties.getValue(PROP_RATE).getRawValue();
            boolean exactAmount = properties.getValue(PROP_EXACTAMOUNT).getRawValue();
            boolean checkAmount = properties.getValue(PROP_CHECK_AMOUNT).getRawValue();
            boolean checkNbt = properties.getValue(PROP_CHECK_NBT).getRawValue();
            boolean blacklist = properties.getValue(PROP_BLACKLIST).getRawValue();
            int transferHash = list.getRawValue().hashCode();
            return Triple.of(input.getLeft(), input.getMiddle(),
                    ChanneledTargetInformation.of(TunnelFluidHelpers.matchFluidStacks(list.getRawValue(), true, checkAmount, checkNbt, blacklist, rate, exactAmount),
                            transferHash, -1));
        };
        public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, ValueTypeOperator.ValueOperator>, Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<FluidStack, Integer>>>
                PROP_FLUIDSTACKPREDICATE_FLUIDPREDICATE = input -> {
            IOperator predicate = input.getRight().getRawValue();
            if (predicate.getInputTypes().length == 1
                    && ValueHelpers.correspondsTo(predicate.getInputTypes()[0], ValueTypes.OBJECT_FLUIDSTACK)
                    && ValueHelpers.correspondsTo(predicate.getOutputType(), ValueTypes.BOOLEAN)) {
                IAspectProperties properties = input.getMiddle();
                int rate = properties.getValue(PROP_RATE).getRawValue();
                boolean exactAmount = properties.getValue(PROP_EXACTAMOUNT).getRawValue();
                int transferHash = predicate.hashCode();
                return Triple.of(input.getLeft(), input.getMiddle(),
                        ChanneledTargetInformation.of(TunnelFluidHelpers.matchPredicate(input.getLeft(), predicate, rate, exactAmount),
                                transferHash, -1));
            } else {
                String current = ValueTypeOperator.getSignature(predicate);
                String expected = ValueTypeOperator.getSignature(new IValueType[]{ValueTypes.OBJECT_FLUIDSTACK}, ValueTypes.BOOLEAN);
                throw new EvaluationException(new L10NHelpers.UnlocalizedString(L10NValues.ASPECT_ERROR_INVALIDTYPE,
                        expected, current).localize());
            }
        };
        public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, NBTTagCompound>, Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<FluidStack, Integer>>>
                PROP_NBT_FLUIDPREDICATE = input -> {
            IAspectProperties properties = input.getMiddle();
            NBTTagCompound tag = input.getRight();
            int rate = properties.getValue(PROP_RATE).getRawValue();
            boolean exactAmount = properties.getValue(PROP_EXACTAMOUNT).getRawValue();
            boolean subset = properties.getValue(PROP_NBT_SUBSET).getRawValue();
            boolean superset = properties.getValue(PROP_NBT_SUPERSET).getRawValue();
            boolean requireNbt = properties.getValue(PROP_NBT_REQUIRE).getRawValue();
            boolean recursive = properties.getValue(PROP_NBT_RECURSIVE).getRawValue();
            boolean blacklist = properties.getValue(PROP_BLACKLIST).getRawValue();
            int transferHash = tag.hashCode();
            IngredientPredicate<FluidStack, Integer> fluidStackMatcher = TunnelFluidHelpers.matchNbt(tag, subset, superset, requireNbt, recursive, blacklist, rate, exactAmount);
            return Triple.of(input.getLeft(), input.getMiddle(), ChanneledTargetInformation.of(fluidStackMatcher, transferHash, -1));
        };
        public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<FluidStack, Integer>>, IFluidTarget>
                PROP_FLUIDTARGET = input -> IFluidTarget.ofCapabilityProvider(input.getRight().getTransferHash(),
                input.getLeft(), input.getMiddle(), input.getRight().getIngredientPredicate());

        public static final IAspectValuePropagator<IFluidTarget, Void>
                PROP_EXPORT = input -> {
            if (input.hasValidTarget()) {
                input.preTransfer();
                TunnelHelpers.moveSingleStateOptimized(input.getConnectionHash(), input.getFluidChannel(), -1, input.getStorage(), -1, input.getFluidStackMatcher());
                input.postTransfer();
            }
            return null;
        };
        public static final IAspectValuePropagator<IFluidTarget, Void>
                PROP_IMPORT = input -> {
            if (input.hasValidTarget()) {
                input.preTransfer();
                TunnelHelpers.moveSingleStateOptimized(input.getConnectionHash(), input.getStorage(), -1, input.getFluidChannel(), -1, input.getFluidStackMatcher());
                input.postTransfer();
            }
            return null;
        };

    }

    public static final class World {

        public static final AspectBuilder<ValueObjectTypeBlock.ValueBlock, ValueObjectTypeBlock, Triple<PartTarget, IAspectProperties, ValueObjectTypeBlock.ValueBlock>>
                BUILDER_BLOCK_BASE = AspectWriteBuilders.getValue(AspectBuilder.forWriteType(ValueTypes.OBJECT_BLOCK)).withProperties(PROPERTIES_CHANNEL);

        public static final AspectBuilder<ValueTypeBoolean.ValueBoolean, ValueTypeBoolean, Triple<PartTarget, IAspectProperties, Boolean>>
                BUILDER_BOOLEAN = AspectWriteBuilders.BUILDER_BOOLEAN.byMod(IntegratedTunnels._instance)
                .appendKind("world").handle(AspectWriteBuilders.PROP_GET_BOOLEAN).withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueTypeInteger.ValueInteger, ValueTypeInteger, Triple<PartTarget, IAspectProperties, Integer>>
                BUILDER_INTEGER = AspectWriteBuilders.BUILDER_INTEGER.byMod(IntegratedTunnels._instance)
                .appendKind("world").handle(AspectWriteBuilders.PROP_GET_INTEGER).withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueObjectTypeItemStack.ValueItemStack, ValueObjectTypeItemStack, Triple<PartTarget, IAspectProperties, ItemStack>>
                BUILDER_ITEMSTACK = AspectWriteBuilders.BUILDER_ITEMSTACK.byMod(IntegratedTunnels._instance)
                .appendKind("world").handle(AspectWriteBuilders.PROP_GET_ITEMSTACK).withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueObjectTypeBlock.ValueBlock, ValueObjectTypeBlock, Triple<PartTarget, IAspectProperties, IBlockState>>
                BUILDER_BLOCK = BUILDER_BLOCK_BASE.byMod(IntegratedTunnels._instance)
                .appendKind("world").handle(AspectWriteBuilders.PROP_GET_BLOCK).withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueObjectTypeFluidStack.ValueFluidStack, ValueObjectTypeFluidStack, Triple<PartTarget, IAspectProperties, FluidStack>>
                BUILDER_FLUIDSTACK = AspectWriteBuilders.BUILDER_FLUIDSTACK.byMod(IntegratedTunnels._instance)
                .appendKind("world").handle(AspectWriteBuilders.PROP_GET_FLUIDSTACK).withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueTypeList.ValueList, ValueTypeList, Triple<PartTarget, IAspectProperties, ValueTypeList.ValueList>>
                BUILDER_LIST = AspectWriteBuilders.BUILDER_LIST.byMod(IntegratedTunnels._instance)
                .appendKind("world").withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueTypeOperator.ValueOperator, ValueTypeOperator, Triple<PartTarget, IAspectProperties, ValueTypeOperator.ValueOperator>>
                BUILDER_OPERATOR = AspectWriteBuilders.BUILDER_OPERATOR.byMod(IntegratedTunnels._instance)
                .appendKind("world").withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueTypeNbt.ValueNbt, ValueTypeNbt, Triple<PartTarget, IAspectProperties, NBTTagCompound>>
                BUILDER_NBT = AspectWriteBuilders.BUILDER_NBT.byMod(IntegratedTunnels._instance)
                .appendKind("world").handle(AspectWriteBuilders.PROP_GET_NBT).withProperties(PROPERTIES_CHANNEL);

        public static final Predicate<ValueTypeDouble.ValueDouble> VALIDATOR_DOUBLE_ANGLE =
                input -> input.getRawValue() >= -180D && input.getRawValue() <= 180F;
        public static final Predicate<ValueTypeDouble.ValueDouble> VALIDATOR_DOUBLE_OFFSET =
                input -> input.getRawValue() >= 0D && input.getRawValue() <= 1F;

        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_BLOCK_UPDATE =
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.world.blockupdate.name");
        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_HAND_RIGHT =
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.world.righthand.name");
        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_SILK_TOUCH =
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.world.silktouch.name");
        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_IGNORE_REPLACABLE =
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.world.ignorereplacable.name");
        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_BREAK_ON_NO_DROPS =
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.world.breaknodrops.name");
        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_IGNORE_PICK_UP_DELAY =
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.world.ignorepickupdelay.name");
        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_DISPENSE =
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.world.dispense.name");
        public static final IAspectPropertyTypeInstance<ValueTypeDouble, ValueTypeDouble.ValueDouble> PROP_OFFSET_X =
                new AspectPropertyTypeInstance<>(ValueTypes.DOUBLE, "aspect.aspecttypes.integratedtunnels.double.world.offsetx.name", VALIDATOR_DOUBLE_OFFSET);
        public static final IAspectPropertyTypeInstance<ValueTypeDouble, ValueTypeDouble.ValueDouble> PROP_OFFSET_Y =
                new AspectPropertyTypeInstance<>(ValueTypes.DOUBLE, "aspect.aspecttypes.integratedtunnels.double.world.offsety.name", VALIDATOR_DOUBLE_OFFSET);
        public static final IAspectPropertyTypeInstance<ValueTypeDouble, ValueTypeDouble.ValueDouble> PROP_OFFSET_Z =
                new AspectPropertyTypeInstance<>(ValueTypes.DOUBLE, "aspect.aspecttypes.integratedtunnels.double.world.offsetz.name", VALIDATOR_DOUBLE_OFFSET);
        public static final IAspectPropertyTypeInstance<ValueTypeInteger, ValueTypeInteger.ValueInteger> PROP_LIFESPAN =
                new AspectPropertyTypeInstance<>(ValueTypes.INTEGER, "aspect.aspecttypes.integratedtunnels.boolean.world.lifespan.name", AspectReadBuilders.VALIDATOR_INTEGER_POSITIVE);
        public static final IAspectPropertyTypeInstance<ValueTypeInteger, ValueTypeInteger.ValueInteger> PROP_DELAY_BEFORE_PICKUP =
                new AspectPropertyTypeInstance<>(ValueTypes.INTEGER, "aspect.aspecttypes.integratedtunnels.boolean.world.delaybeforepickup.name", AspectReadBuilders.VALIDATOR_INTEGER_POSITIVE);
        public static final IAspectPropertyTypeInstance<ValueTypeDouble, ValueTypeDouble.ValueDouble> PROP_VELOCITY =
                new AspectPropertyTypeInstance<>(ValueTypes.DOUBLE, "aspect.aspecttypes.integratedtunnels.double.world.velocity.name",
                        input -> input.getRawValue() >= 0 && input.getRawValue() <= 25D);
        public static final IAspectPropertyTypeInstance<ValueTypeDouble, ValueTypeDouble.ValueDouble> PROP_YAW =
                new AspectPropertyTypeInstance<>(ValueTypes.DOUBLE, "aspect.aspecttypes.integratedtunnels.double.world.yaw.name", VALIDATOR_DOUBLE_ANGLE);
        public static final IAspectPropertyTypeInstance<ValueTypeDouble, ValueTypeDouble.ValueDouble> PROP_PITCH =
                new AspectPropertyTypeInstance<>(ValueTypes.DOUBLE, "aspect.aspecttypes.integratedtunnels.double.world.pitch.name", VALIDATOR_DOUBLE_ANGLE);
        public static final IAspectPropertyTypeInstance<ValueTypeInteger, ValueTypeInteger.ValueInteger> PROPERTY_ENTITYINDEX =
                new AspectPropertyTypeInstance<>(ValueTypes.INTEGER, "aspect.aspecttypes.integratedtunnels.integer.entityindex.name");

        public static final class Energy {

            public static final IAspectProperties PROPERTIES = TunnelAspectWriteBuilders.Energy.PROPERTIES_RATE.clone();
            static {
                PROPERTIES.setValue(World.PROPERTY_ENTITYINDEX, ValueTypeInteger.ValueInteger.of(0));
            }
            public static final IAspectProperties PROPERTIES_ENTITY = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                    PROP_CHANNEL,
                    PROP_EXACTAMOUNT,
                    World.PROPERTY_ENTITYINDEX
            ));
            static {
                PROPERTIES_ENTITY.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
                PROPERTIES_ENTITY.setValue(PROP_EXACTAMOUNT, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ENTITY.setValue(World.PROPERTY_ENTITYINDEX, ValueTypeInteger.ValueInteger.of(0));
            }

            public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, Integer>, IEnergyTarget>
                    PROP_ENTITY_ENERGYTARGET = input -> {
                PartTarget partTarget = input.getLeft();
                IAspectProperties properties = input.getMiddle();
                int amount = input.getRight();
                int entityIndex = properties.getValue(World.PROPERTY_ENTITYINDEX).getRawValue();

                PartPos target = partTarget.getTarget();
                Entity entity = Iterables.get(target.getPos().getWorld().getEntitiesWithinAABB(Entity.class,
                        new AxisAlignedBB(target.getPos().getBlockPos())), entityIndex, null);
                return IEnergyTarget.ofEntity(partTarget, entity, properties, amount);
            };
        }

        public static final class Item {

            public static final IAspectProperties PROPERTIES_ENTITYITEM_PICK_UP_NORATE = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                    PROP_CHANNEL,
                    PROP_ROUNDROBIN,
                    PROP_EXACTAMOUNT,
                    TunnelAspectWriteBuilders.Item.PROP_CHECK_STACKSIZE,
                    TunnelAspectWriteBuilders.Item.PROP_CHECK_DAMAGE,
                    TunnelAspectWriteBuilders.Item.PROP_CHECK_NBT,
                    PROP_IGNORE_PICK_UP_DELAY
            ));
            public static final IAspectProperties PROPERTIES_ENTITYITEM_PLACE_NORATE_NOCHECKS = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                    PROP_CHANNEL,
                    PROP_ROUNDROBIN,
                    PROP_EXACTAMOUNT,
                    PROP_DISPENSE,
                    PROP_OFFSET_X,
                    PROP_OFFSET_Y,
                    PROP_OFFSET_Z,
                    PROP_LIFESPAN,
                    PROP_DELAY_BEFORE_PICKUP,
                    PROP_VELOCITY,
                    PROP_YAW,
                    PROP_PITCH
            ));
            static {
                PROPERTIES_ENTITYITEM_PICK_UP_NORATE.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
                PROPERTIES_ENTITYITEM_PICK_UP_NORATE.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ENTITYITEM_PICK_UP_NORATE.setValue(PROP_EXACTAMOUNT, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ENTITYITEM_PICK_UP_NORATE.setValue(TunnelAspectWriteBuilders.Item.PROP_CHECK_STACKSIZE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ENTITYITEM_PICK_UP_NORATE.setValue(TunnelAspectWriteBuilders.Item.PROP_CHECK_DAMAGE, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ENTITYITEM_PICK_UP_NORATE.setValue(TunnelAspectWriteBuilders.Item.PROP_CHECK_NBT, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ENTITYITEM_PICK_UP_NORATE.setValue(PROP_IGNORE_PICK_UP_DELAY, ValueTypeBoolean.ValueBoolean.of(true));

                PROPERTIES_ENTITYITEM_PLACE_NORATE_NOCHECKS.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
                PROPERTIES_ENTITYITEM_PLACE_NORATE_NOCHECKS.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ENTITYITEM_PLACE_NORATE_NOCHECKS.setValue(PROP_EXACTAMOUNT, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ENTITYITEM_PLACE_NORATE_NOCHECKS.setValue(PROP_DISPENSE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ENTITYITEM_PLACE_NORATE_NOCHECKS.setValue(PROP_OFFSET_X, ValueTypeDouble.ValueDouble.of(0.5D));
                PROPERTIES_ENTITYITEM_PLACE_NORATE_NOCHECKS.setValue(PROP_OFFSET_Y, ValueTypeDouble.ValueDouble.of(0.5D));
                PROPERTIES_ENTITYITEM_PLACE_NORATE_NOCHECKS.setValue(PROP_OFFSET_Z, ValueTypeDouble.ValueDouble.of(0.5D));
                PROPERTIES_ENTITYITEM_PLACE_NORATE_NOCHECKS.setValue(PROP_LIFESPAN, ValueTypeInteger.ValueInteger.of(6000));
                PROPERTIES_ENTITYITEM_PLACE_NORATE_NOCHECKS.setValue(PROP_DELAY_BEFORE_PICKUP, ValueTypeInteger.ValueInteger.of(10));
                PROPERTIES_ENTITYITEM_PLACE_NORATE_NOCHECKS.setValue(PROP_VELOCITY, ValueTypeDouble.ValueDouble.of(0.1D));
                PROPERTIES_ENTITYITEM_PLACE_NORATE_NOCHECKS.setValue(PROP_YAW, ValueTypeDouble.ValueDouble.of(0D));
                PROPERTIES_ENTITYITEM_PLACE_NORATE_NOCHECKS.setValue(PROP_PITCH, ValueTypeDouble.ValueDouble.of(0D));
            }
            public static final IAspectProperties PROPERTIES_ENTITYITEM_PICK_UP = PROPERTIES_ENTITYITEM_PICK_UP_NORATE.clone();
            public static final IAspectProperties PROPERTIES_ENTITYITEM_PLACE_NORATE = PROPERTIES_ENTITYITEM_PLACE_NORATE_NOCHECKS.clone();
            public static final IAspectProperties PROPERTIES_ENTITYITEM_PLACE_NOCHECKS = PROPERTIES_ENTITYITEM_PLACE_NORATE_NOCHECKS.clone();
            public static final IAspectProperties PROPERTIES_ENTITYITEM_PLACE_NBT = PROPERTIES_ENTITYITEM_PLACE_NORATE_NOCHECKS.clone();
            public static final IAspectProperties PROPERTIES_ENTITYITEM_PLACE = PROPERTIES_ENTITYITEM_PLACE_NORATE_NOCHECKS.clone();
            public static final IAspectProperties PROPERTIES_ENTITYITEM_PICK_UP_NOCHECKS = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                    PROP_CHANNEL,
                    PROP_ROUNDROBIN,
                    PROP_EXACTAMOUNT,
                    PROP_IGNORE_PICK_UP_DELAY,
                    TunnelAspectWriteBuilders.Item.PROP_RATE
            ));
            public static final IAspectProperties PROPERTIES_ENTITYITEM_PICK_UP_NORATE_NOCHECKS = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                    PROP_CHANNEL,
                    PROP_ROUNDROBIN,
                    PROP_EXACTAMOUNT,
                    PROP_IGNORE_PICK_UP_DELAY
            ));
            public static final IAspectProperties PROPERTIES_ENTITYITEM_PICK_UP_NBT = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                    PROP_CHANNEL,
                    PROP_ROUNDROBIN,
                    PROP_BLACKLIST,
                    PROP_IGNORE_PICK_UP_DELAY,
                    TunnelAspectWriteBuilders.Item.PROP_RATE
            ));
            static {
                PROPERTIES_ENTITYITEM_PICK_UP.setValue(TunnelAspectWriteBuilders.Item.PROP_RATE, ValueTypeInteger.ValueInteger.of(64));
                PROPERTIES_ENTITYITEM_PICK_UP.setValue(TunnelAspectWriteBuilders.Item.PROP_CHECK_STACKSIZE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ENTITYITEM_PICK_UP.setValue(TunnelAspectWriteBuilders.Item.PROP_CHECK_DAMAGE, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ENTITYITEM_PICK_UP.setValue(TunnelAspectWriteBuilders.Item.PROP_CHECK_NBT, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ENTITYITEM_PICK_UP.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ENTITYITEM_PICK_UP.setValue(PROP_EMPTYISANY, ValueTypeBoolean.ValueBoolean.of(true));

                PROPERTIES_ENTITYITEM_PICK_UP_NOCHECKS.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
                PROPERTIES_ENTITYITEM_PICK_UP_NOCHECKS.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ENTITYITEM_PICK_UP_NOCHECKS.setValue(PROP_EXACTAMOUNT, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ENTITYITEM_PICK_UP_NOCHECKS.setValue(PROP_IGNORE_PICK_UP_DELAY, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ENTITYITEM_PICK_UP_NOCHECKS.setValue(TunnelAspectWriteBuilders.Item.PROP_RATE, ValueTypeInteger.ValueInteger.of(64));

                PROPERTIES_ENTITYITEM_PICK_UP_NORATE_NOCHECKS.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
                PROPERTIES_ENTITYITEM_PICK_UP_NORATE_NOCHECKS.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ENTITYITEM_PICK_UP_NORATE_NOCHECKS.setValue(PROP_EXACTAMOUNT, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ENTITYITEM_PICK_UP_NORATE_NOCHECKS.setValue(PROP_IGNORE_PICK_UP_DELAY, ValueTypeBoolean.ValueBoolean.of(true));

                PROPERTIES_ENTITYITEM_PICK_UP_NBT.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
                PROPERTIES_ENTITYITEM_PICK_UP_NBT.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ENTITYITEM_PICK_UP_NBT.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ENTITYITEM_PICK_UP_NBT.setValue(PROP_IGNORE_PICK_UP_DELAY, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ENTITYITEM_PICK_UP_NBT.setValue(TunnelAspectWriteBuilders.Item.PROP_RATE, ValueTypeInteger.ValueInteger.of(64));
                PROPERTIES_ENTITYITEM_PICK_UP_NBT.setValue(TunnelAspectWriteBuilders.Item.PROP_NBT_SUBSET, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ENTITYITEM_PICK_UP_NBT.setValue(TunnelAspectWriteBuilders.Item.PROP_NBT_SUPERSET, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ENTITYITEM_PICK_UP_NBT.setValue(TunnelAspectWriteBuilders.Item.PROP_NBT_REQUIRE, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ENTITYITEM_PICK_UP_NBT.setValue(TunnelAspectWriteBuilders.Item.PROP_NBT_RECURSIVE, ValueTypeBoolean.ValueBoolean.of(true));

                PROPERTIES_ENTITYITEM_PLACE.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
                PROPERTIES_ENTITYITEM_PLACE.setValue(TunnelAspectWriteBuilders.Item.PROP_RATE, ValueTypeInteger.ValueInteger.of(64));
                PROPERTIES_ENTITYITEM_PLACE.setValue(TunnelAspectWriteBuilders.Item.PROP_CHECK_STACKSIZE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ENTITYITEM_PLACE.setValue(TunnelAspectWriteBuilders.Item.PROP_CHECK_DAMAGE, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ENTITYITEM_PLACE.setValue(TunnelAspectWriteBuilders.Item.PROP_CHECK_NBT, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ENTITYITEM_PLACE.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ENTITYITEM_PLACE.setValue(PROP_EMPTYISANY, ValueTypeBoolean.ValueBoolean.of(true));

                PROPERTIES_ENTITYITEM_PLACE_NORATE.setValue(TunnelAspectWriteBuilders.Item.PROP_CHECK_STACKSIZE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ENTITYITEM_PLACE_NORATE.setValue(TunnelAspectWriteBuilders.Item.PROP_CHECK_DAMAGE, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ENTITYITEM_PLACE_NORATE.setValue(TunnelAspectWriteBuilders.Item.PROP_CHECK_NBT, ValueTypeBoolean.ValueBoolean.of(true));

                PROPERTIES_ENTITYITEM_PLACE_NOCHECKS.setValue(TunnelAspectWriteBuilders.Item.PROP_RATE, ValueTypeInteger.ValueInteger.of(64));

                PROPERTIES_ENTITYITEM_PLACE_NBT.setValue(TunnelAspectWriteBuilders.Item.PROP_RATE, ValueTypeInteger.ValueInteger.of(64));
                PROPERTIES_ENTITYITEM_PLACE_NBT.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ENTITYITEM_PLACE_NBT.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ENTITYITEM_PLACE_NBT.setValue(TunnelAspectWriteBuilders.Item.PROP_NBT_SUBSET, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ENTITYITEM_PLACE_NBT.setValue(TunnelAspectWriteBuilders.Item.PROP_NBT_SUPERSET, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ENTITYITEM_PLACE_NBT.setValue(TunnelAspectWriteBuilders.Item.PROP_NBT_REQUIRE, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ENTITYITEM_PLACE_NBT.setValue(TunnelAspectWriteBuilders.Item.PROP_NBT_RECURSIVE, ValueTypeBoolean.ValueBoolean.of(true));
            }
            public static final IAspectProperties PROPERTIES_ENTITYITEM_PICK_UPLIST = PROPERTIES_ENTITYITEM_PICK_UP.clone();
            public static final IAspectProperties PROPERTIES_ENTITYITEM_PLACELIST = PROPERTIES_ENTITYITEM_PLACE.clone();
            static {
                PROPERTIES_ENTITYITEM_PICK_UPLIST.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));

                PROPERTIES_ENTITYITEM_PLACELIST.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));
            }
            public static final IAspectProperties PROPERTIES_RATESLOT = TunnelAspectWriteBuilders.Item.PROPERTIES_RATESLOT.clone();
            public static final IAspectProperties PROPERTIES_SLOT = TunnelAspectWriteBuilders.Item.PROPERTIES_SLOT.clone();
            public static final IAspectProperties PROPERTIES_RATESLOTCHECKS = TunnelAspectWriteBuilders.Item.PROPERTIES_RATESLOTCHECKS.clone();
            public static final IAspectProperties PROPERTIES_NBT = TunnelAspectWriteBuilders.Item.PROPERTIES_NBT.clone();
            static {
                PROPERTIES_RATESLOT.setValue(World.PROPERTY_ENTITYINDEX, ValueTypeInteger.ValueInteger.of(0));

                PROPERTIES_SLOT.setValue(World.PROPERTY_ENTITYINDEX, ValueTypeInteger.ValueInteger.of(0));

                PROPERTIES_RATESLOTCHECKS.setValue(World.PROPERTY_ENTITYINDEX, ValueTypeInteger.ValueInteger.of(0));
                PROPERTIES_RATESLOTCHECKS.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_RATESLOTCHECKS.setValue(PROP_EMPTYISANY, ValueTypeBoolean.ValueBoolean.of(true));

                PROPERTIES_NBT.setValue(World.PROPERTY_ENTITYINDEX, ValueTypeInteger.ValueInteger.of(0));
            }
            public static final IAspectProperties PROPERTIES_RATESLOTCHECKSLIST = PROPERTIES_RATESLOTCHECKS.clone();
            static {
                PROPERTIES_RATESLOTCHECKSLIST.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));
            }

            public static <T> IAspectValuePropagator<Triple<PartTarget, IAspectProperties, T>, Triple<PartTarget, IAspectProperties, T>>
            ignoreStackSize() {
                return input -> {
                    IAspectProperties aspectProperties = input.getMiddle().clone();
                    aspectProperties.setValue(TunnelAspectWriteBuilders.Item.PROP_CHECK_STACKSIZE, ValueTypeBoolean.ValueBoolean.of(false));
                    aspectProperties.setValue(TunnelAspectWriteBuilders.Item.PROP_RATE, ValueTypeInteger.ValueInteger.of(1));
                    return Triple.of(input.getLeft(), aspectProperties, input.getRight());
                };
            }
            public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, Boolean>, IItemTarget>
                    PROP_BOOLEAN_ITEMTARGET = input -> IItemTarget.ofBlock(input.getRight() ? 1 : 0,
                    input.getLeft(), input.getMiddle(),
                    input.getRight() ? TunnelItemHelpers.matchAll(64, false) : TunnelItemHelpers.MATCH_NONE,
                    input.getMiddle().getValue(TunnelAspectWriteBuilders.Item.PROP_SLOT).getRawValue());

            public static IAspectValuePropagator<Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<ItemStack, Integer>>, IItemTarget>
            newPropEntityItemItemTarget(final boolean doImport) {
                return input -> {
                    PartTarget partTarget = input.getLeft();
                    IAspectProperties properties = input.getMiddle();
                    int transferHash = input.getRight().getTransferHash();
                    IngredientPredicate<ItemStack, Integer> itemStackMatcher = input.getRight().getIngredientPredicate();

                    PartPos center = partTarget.getCenter();
                    PartPos target = partTarget.getTarget();
                    INetwork network = IChanneledTarget.getNetworkChecked(center);
                    IIngredientComponentStorage<ItemStack, Integer> itemStorage;
                    if (doImport) {
                        boolean ignorePickupDelay = properties.getValue(PROP_IGNORE_PICK_UP_DELAY).getRawValue();
                        itemStorage = new ItemHandlerWorldEntityImportWrapper((WorldServer) target.getPos().getWorld(),
                                target.getPos().getBlockPos(), target.getSide(), ignorePickupDelay
                        );
                    } else {
                        double offsetX = properties.getValue(PROP_OFFSET_X).getRawValue();
                        double offsetY = properties.getValue(PROP_OFFSET_Y).getRawValue();
                        double offsetZ = properties.getValue(PROP_OFFSET_Z).getRawValue();
                        int lifespan = properties.getValue(PROP_LIFESPAN).getRawValue();
                        int delayBeforePickup = properties.getValue(PROP_DELAY_BEFORE_PICKUP).getRawValue();
                        EnumFacing facing = center.getSide();
                        double velocity = properties.getValue(PROP_VELOCITY).getRawValue();
                        double yaw = properties.getValue(PROP_YAW).getRawValue();
                        double pitch = properties.getValue(PROP_PITCH).getRawValue();
                        boolean dispense = properties.getValue(PROP_DISPENSE).getRawValue();
                        int channel = properties.getValue(PROP_CHANNEL).getRawValue();
                        itemStorage = new ItemHandlerWorldEntityExportWrapper(
                                (WorldServer) target.getPos().getWorld(),
                                target.getPos().getBlockPos(), offsetX, offsetY, offsetZ,
                                lifespan, delayBeforePickup, facing, velocity, yaw, pitch,
                                dispense, network.getCapability(ItemNetworkConfig.CAPABILITY).getChannel(channel)
                        );
                    }
                    return IItemTarget.ofStorage(transferHash, network, partTarget, properties, itemStackMatcher, itemStorage, -1);
                };
            }

            public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<ItemStack, Integer>>, IItemTarget>
                    PROP_ENTITYITEM_ITEMTARGET_IMPORT = newPropEntityItemItemTarget(true);
            public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<ItemStack, Integer>>, IItemTarget>
                    PROP_ENTITYITEM_ITEMTARGET_EXPORT = newPropEntityItemItemTarget(false);

            public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<ItemStack, Integer>>, IItemTarget>
                    PROP_ENTITY_ITEMTARGET = input -> {
                PartTarget partTarget = input.getLeft();
                IAspectProperties properties = input.getMiddle();
                int transferHash = input.getRight().getTransferHash();
                IngredientPredicate<ItemStack, Integer> itemStackMatcher = input.getRight().getIngredientPredicate();
                int entityIndex = properties.getValue(World.PROPERTY_ENTITYINDEX).getRawValue();

                PartPos target = partTarget.getTarget();
                Entity entity = Iterables.get(target.getPos().getWorld().getEntitiesWithinAABB(Entity.class,
                        new AxisAlignedBB(target.getPos().getBlockPos())), entityIndex, null);
                return IItemTarget.ofEntity(transferHash, partTarget, entity, properties, itemStackMatcher,  -1);
            };

        }

        public static final class Fluid {

            public static final IAspectProperties PROPERTIES_UPDATE = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                    PROP_CHANNEL,
                    PROP_ROUNDROBIN,
                    PROP_BLOCK_UPDATE,
                    PROP_IGNORE_REPLACABLE
            ));
            public static final IAspectProperties PROPERTIES_FLUID_UPDATE = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                    PROP_CHANNEL,
                    PROP_ROUNDROBIN,
                    PROP_BLACKLIST,
                    TunnelAspectWriteBuilders.Fluid.PROP_CHECK_NBT,
                    PROP_BLOCK_UPDATE,
                    PROP_IGNORE_REPLACABLE
            ));
            public static final IAspectProperties PROPERTIES_FLUIDLIST_UPDATE = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                    PROP_CHANNEL,
                    PROP_ROUNDROBIN,
                    PROP_BLACKLIST,
                    TunnelAspectWriteBuilders.Fluid.PROP_CHECK_NBT,
                    PROP_BLOCK_UPDATE,
                    PROP_IGNORE_REPLACABLE
            ));
            public static final IAspectProperties PROPERTIES_FLUID = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                    PROP_CHANNEL,
                    PROP_ROUNDROBIN,
                    TunnelAspectWriteBuilders.Fluid.PROP_CHECK_NBT
            ));
            public static final IAspectProperties PROPERTIES_FLUIDLIST = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                    PROP_CHANNEL,
                    PROP_ROUNDROBIN,
                    PROP_BLACKLIST,
                    TunnelAspectWriteBuilders.Fluid.PROP_CHECK_NBT
            ));
            public static final IAspectProperties PROPERTIES_NBT_UPDATE = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                    PROP_CHANNEL,
                    PROP_BLOCK_UPDATE,
                    PROP_IGNORE_REPLACABLE,
                    TunnelAspectWriteBuilders.Fluid.PROP_NBT_SUBSET,
                    TunnelAspectWriteBuilders.Fluid.PROP_NBT_SUPERSET,
                    TunnelAspectWriteBuilders.Fluid.PROP_NBT_REQUIRE,
                    TunnelAspectWriteBuilders.Fluid.PROP_NBT_RECURSIVE
            ));
            public static final IAspectProperties PROPERTIES_NBT = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                    PROP_CHANNEL,
                    TunnelAspectWriteBuilders.Fluid.PROP_NBT_SUBSET,
                    TunnelAspectWriteBuilders.Fluid.PROP_NBT_SUPERSET,
                    TunnelAspectWriteBuilders.Fluid.PROP_NBT_REQUIRE,
                    TunnelAspectWriteBuilders.Fluid.PROP_NBT_RECURSIVE
            ));
            public static final IAspectProperties PROPERTIES_RATE = TunnelAspectWriteBuilders.Fluid.PROPERTIES_RATE.clone();
            public static final IAspectProperties PROPERTIES_RATECHECKS = TunnelAspectWriteBuilders.Fluid.PROPERTIES_RATECHECKS.clone();
            public static final IAspectProperties PROPERTIES_RATECHECKSLIST = TunnelAspectWriteBuilders.Fluid.PROPERTIES_RATECHECKSLIST.clone();

            static {
                PROPERTIES_UPDATE.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
                PROPERTIES_UPDATE.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_UPDATE.setValue(PROP_BLOCK_UPDATE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_UPDATE.setValue(PROP_IGNORE_REPLACABLE, ValueTypeBoolean.ValueBoolean.of(false));

                PROPERTIES_FLUID_UPDATE.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
                PROPERTIES_FLUID_UPDATE.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_FLUID_UPDATE.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_FLUID_UPDATE.setValue(TunnelAspectWriteBuilders.Fluid.PROP_CHECK_NBT, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_FLUID_UPDATE.setValue(PROP_BLOCK_UPDATE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_FLUID_UPDATE.setValue(PROP_IGNORE_REPLACABLE, ValueTypeBoolean.ValueBoolean.of(false));

                PROPERTIES_FLUIDLIST_UPDATE.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
                PROPERTIES_FLUIDLIST_UPDATE.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_FLUIDLIST_UPDATE.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_FLUIDLIST_UPDATE.setValue(TunnelAspectWriteBuilders.Fluid.PROP_CHECK_NBT, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_FLUIDLIST_UPDATE.setValue(PROP_BLOCK_UPDATE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_FLUIDLIST_UPDATE.setValue(PROP_IGNORE_REPLACABLE, ValueTypeBoolean.ValueBoolean.of(false));

                PROPERTIES_FLUID.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
                PROPERTIES_FLUID.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_FLUID.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_FLUID.setValue(TunnelAspectWriteBuilders.Fluid.PROP_CHECK_NBT, ValueTypeBoolean.ValueBoolean.of(true));

                PROPERTIES_FLUIDLIST.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
                PROPERTIES_FLUIDLIST.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_FLUIDLIST.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_FLUIDLIST.setValue(TunnelAspectWriteBuilders.Fluid.PROP_CHECK_NBT, ValueTypeBoolean.ValueBoolean.of(true));

                PROPERTIES_RATE.setValue(World.PROPERTY_ENTITYINDEX, ValueTypeInteger.ValueInteger.of(0));

                PROPERTIES_RATECHECKS.setValue(World.PROPERTY_ENTITYINDEX, ValueTypeInteger.ValueInteger.of(0));
                PROPERTIES_RATECHECKS.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));

                PROPERTIES_RATECHECKSLIST.setValue(World.PROPERTY_ENTITYINDEX, ValueTypeInteger.ValueInteger.of(0));

                PROPERTIES_NBT_UPDATE.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
                PROPERTIES_NBT_UPDATE.setValue(PROP_BLOCK_UPDATE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_NBT_UPDATE.setValue(PROP_IGNORE_REPLACABLE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_NBT_UPDATE.setValue(TunnelAspectWriteBuilders.Fluid.PROP_NBT_SUBSET, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_NBT_UPDATE.setValue(TunnelAspectWriteBuilders.Fluid.PROP_NBT_SUPERSET, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_NBT_UPDATE.setValue(TunnelAspectWriteBuilders.Fluid.PROP_NBT_REQUIRE, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_NBT_UPDATE.setValue(TunnelAspectWriteBuilders.Fluid.PROP_NBT_RECURSIVE, ValueTypeBoolean.ValueBoolean.of(true));

                PROPERTIES_NBT.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
                PROPERTIES_NBT.setValue(TunnelAspectWriteBuilders.Fluid.PROP_NBT_SUBSET, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_NBT.setValue(TunnelAspectWriteBuilders.Fluid.PROP_NBT_SUPERSET, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_NBT.setValue(TunnelAspectWriteBuilders.Fluid.PROP_NBT_REQUIRE, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_NBT.setValue(TunnelAspectWriteBuilders.Fluid.PROP_NBT_RECURSIVE, ValueTypeBoolean.ValueBoolean.of(true));
            }

            public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, Boolean>, IFluidTarget>
                    PROP_BOOLEAN_FLUIDTARGET = input -> IFluidTarget.ofCapabilityProvider(
                            input.getRight() ? 1 : 0,
                    input.getLeft(),
                    input.getMiddle(),
                    input.getRight() ? TunnelFluidHelpers
                            .matchAll(net.minecraftforge.fluids.Fluid.BUCKET_VOLUME, false)
                            : TunnelFluidHelpers.MATCH_NONE);
            public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, FluidStack>, IFluidTarget>
                    PROP_FLUIDSTACK_FLUIDTARGET = input -> {
                IAspectProperties properties = input.getMiddle();
                boolean checkNbt = properties.getValue(TunnelAspectWriteBuilders.Fluid.PROP_CHECK_NBT).getRawValue();
                boolean blacklist = properties.getValue(PROP_BLACKLIST).getRawValue();
                int transferHash = input.getRight().hashCode();
                return IFluidTarget.ofBlock(transferHash, input.getLeft(), input.getMiddle(),
                        TunnelFluidHelpers.matchFluidStack(input.getRight(), true, false, checkNbt, blacklist, true));
            };
            public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, ValueTypeList.ValueList>, IFluidTarget>
                    PROP_FLUIDSTACKLIST_FLUIDTARGET = input -> {
                ValueTypeList.ValueList list = input.getRight();
                if (list.getRawValue().getValueType() != ValueTypes.OBJECT_FLUIDSTACK) {
                    throw new EvaluationException(new L10NHelpers.UnlocalizedString(L10NValues.VALUETYPE_ERROR_INVALIDLISTVALUETYPE,
                            ValueTypes.OBJECT_FLUIDSTACK, list.getRawValue().getValueType()).localize());
                }
                IAspectProperties properties = input.getMiddle();
                boolean checkNbt = properties.getValue(TunnelAspectWriteBuilders.Fluid.PROP_CHECK_NBT).getRawValue();
                boolean blacklist = properties.getValue(PROP_BLACKLIST).getRawValue();
                int transferHash = list.getRawValue().hashCode();
                IngredientPredicate<FluidStack, Integer> fluidStackPredicate = TunnelFluidHelpers.matchFluidStacks(list.getRawValue(), false, false, checkNbt, blacklist, net.minecraftforge.fluids.Fluid.BUCKET_VOLUME, true);
                return IFluidTarget.ofBlock(transferHash, input.getLeft(), input.getMiddle(), fluidStackPredicate);
            };
            public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, ValueTypeOperator.ValueOperator>, IFluidTarget>
                    PROP_FLUIDSTACKPREDICATE_FLUIDTARGET = input -> {
                IOperator predicate = input.getRight().getRawValue();
                if (predicate.getInputTypes().length == 1
                        && ValueHelpers.correspondsTo(predicate.getInputTypes()[0], ValueTypes.OBJECT_FLUIDSTACK)
                        && ValueHelpers.correspondsTo(predicate.getOutputType(), ValueTypes.BOOLEAN)) {
                    int transferHash = predicate.hashCode();
                    return IFluidTarget.ofBlock(transferHash, input.getLeft(), input.getMiddle(),
                            TunnelFluidHelpers.matchPredicate(input.getLeft(), predicate,
                                    net.minecraftforge.fluids.Fluid.BUCKET_VOLUME, true));
                } else {
                    String current = ValueTypeOperator.getSignature(predicate);
                    String expected = ValueTypeOperator.getSignature(new IValueType[]{ValueTypes.OBJECT_FLUIDSTACK}, ValueTypes.BOOLEAN);
                    throw new EvaluationException(new L10NHelpers.UnlocalizedString(L10NValues.ASPECT_ERROR_INVALIDTYPE,
                            expected, current).localize());
                }
            };
            public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, NBTTagCompound>, IFluidTarget>
                    PROP_NBT_FLUIDTARGET = input -> {
                IAspectProperties properties = input.getMiddle();
                NBTTagCompound tag = input.getRight();
                boolean subset = properties.getValue(TunnelAspectWriteBuilders.Fluid.PROP_NBT_SUBSET).getRawValue();
                boolean superset = properties.getValue(TunnelAspectWriteBuilders.Fluid.PROP_NBT_SUPERSET).getRawValue();
                boolean requireNbt = properties.getValue(TunnelAspectWriteBuilders.Fluid.PROP_NBT_REQUIRE).getRawValue();
                boolean recursive = properties.getValue(TunnelAspectWriteBuilders.Fluid.PROP_NBT_RECURSIVE).getRawValue();
                boolean blacklist = properties.getValue(PROP_BLACKLIST).getRawValue();
                int transferHash = tag.hashCode();
                IngredientPredicate<FluidStack, Integer> fluidStackMatcher = TunnelFluidHelpers.matchNbt(tag, subset, superset, requireNbt, recursive, blacklist, net.minecraftforge.fluids.Fluid.BUCKET_VOLUME, true);
                return IFluidTarget.ofBlock(transferHash, input.getLeft(), input.getMiddle(), fluidStackMatcher);
            };

            public static final IAspectValuePropagator<IFluidTarget, Void>
                    PROP_FLUIDSTACK_EXPORT = input -> {
                PartPos target = input.getPartTarget().getTarget();
                final DimPos pos = target.getPos();
                if (pos.isLoaded() && input.getChanneledNetwork() != null) {
                    IIngredientComponentStorage<FluidStack, Integer> fluidChannel = input.getFluidChannel();
                    input.preTransfer();
                    TunnelFluidHelpers.placeFluids(input.getConnectionHash(), fluidChannel, pos.getWorld(), pos.getBlockPos(),
                            input.getFluidStackMatcher(), input.getProperties().getValue(PROP_BLOCK_UPDATE).getRawValue(),
                            input.getProperties().getValue(PROP_IGNORE_REPLACABLE).getRawValue());
                    input.postTransfer();
                }
                return null;

            };

            public static final IAspectValuePropagator<IFluidTarget, Void>
                    PROP_FLUIDSTACK_IMPORT = input -> {
                PartPos target = input.getPartTarget().getTarget();
                final DimPos pos = target.getPos();
                if (pos.isLoaded() && input.getChanneledNetwork() != null) {
                    IIngredientComponentStorage<FluidStack, Integer> fluidChannel = input.getFluidChannel();
                    input.preTransfer();
                    TunnelFluidHelpers.pickUpFluids(input.getConnectionHash(), target.getPos().getWorld(),
                            target.getPos().getBlockPos(), target.getSide(), fluidChannel, input.getFluidStackMatcher());
                    input.postTransfer();
                }
                return null;
            };

            public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<FluidStack, Integer>>, IFluidTarget>
                    PROP_ENTITY_FLUIDTARGET = input -> {
                PartTarget partTarget = input.getLeft();
                IAspectProperties properties = input.getMiddle();
                IngredientPredicate<FluidStack, Integer> fluidStackPredicate = input.getRight().getIngredientPredicate();
                int entityIndex = properties.getValue(World.PROPERTY_ENTITYINDEX).getRawValue();

                PartPos target = partTarget.getTarget();
                Entity entity = Iterables.get(target.getPos().getWorld().getEntitiesWithinAABB(Entity.class,
                        new AxisAlignedBB(target.getPos().getBlockPos())), entityIndex, null);
                int transferHash = input.getRight().getTransferHash();
                return IFluidTarget.ofEntity(transferHash, partTarget, entity, properties, fluidStackPredicate);
            };

        }

        public static final class Block {

            public static final IAspectProperties PROPERTIES_ITEM_PLACE_NOCHECKS = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                    PROP_CHANNEL,
                    PROP_ROUNDROBIN,
                    PROP_BLOCK_UPDATE,
                    PROP_HAND_RIGHT,
                    PROP_IGNORE_REPLACABLE
            ));
            public static final IAspectProperties PROPERTIES_ITEM_PLACE = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                    PROP_CHANNEL,
                    PROP_ROUNDROBIN,
                    PROP_BLACKLIST,
                    PROP_EMPTYISANY,
                    TunnelAspectWriteBuilders.Item.PROP_CHECK_DAMAGE,
                    TunnelAspectWriteBuilders.Item.PROP_CHECK_NBT,
                    PROP_BLOCK_UPDATE,
                    PROP_HAND_RIGHT,
                    PROP_IGNORE_REPLACABLE
            ));
            public static final IAspectProperties PROPERTIES_ITEM_PLACE_NBT = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                    PROP_CHANNEL,
                    PROP_ROUNDROBIN,
                    PROP_BLACKLIST,
                    PROP_BLOCK_UPDATE,
                    PROP_HAND_RIGHT,
                    PROP_IGNORE_REPLACABLE,
                    TunnelAspectWriteBuilders.Item.PROP_NBT_SUBSET,
                    TunnelAspectWriteBuilders.Item.PROP_NBT_SUPERSET,
                    TunnelAspectWriteBuilders.Item.PROP_NBT_REQUIRE,
                    TunnelAspectWriteBuilders.Item.PROP_NBT_RECURSIVE
            ));
            public static final IAspectProperties PROPERTIES_ITEM_PICK_UP_NOCHECKS = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                    PROP_CHANNEL,
                    PROP_ROUNDROBIN,
                    PROP_BLOCK_UPDATE,
                    PROP_HAND_RIGHT,
                    PROP_SILK_TOUCH,
                    PROP_IGNORE_REPLACABLE,
                    PROP_BREAK_ON_NO_DROPS
            ));
            public static final IAspectProperties PROPERTIES_ITEM_PICK_UP = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                    PROP_CHANNEL,
                    PROP_ROUNDROBIN,
                    PROP_BLACKLIST,
                    PROP_EMPTYISANY,
                    TunnelAspectWriteBuilders.Item.PROP_CHECK_DAMAGE,
                    TunnelAspectWriteBuilders.Item.PROP_CHECK_NBT,
                    PROP_BLOCK_UPDATE,
                    PROP_HAND_RIGHT,
                    PROP_SILK_TOUCH,
                    PROP_IGNORE_REPLACABLE,
                    PROP_BREAK_ON_NO_DROPS
            ));
            public static final IAspectProperties PROPERTIES_ITEM_PICK_UP_NBT = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                    PROP_CHANNEL,
                    PROP_ROUNDROBIN,
                    PROP_BLACKLIST,
                    PROP_BLOCK_UPDATE,
                    PROP_HAND_RIGHT,
                    PROP_SILK_TOUCH,
                    PROP_IGNORE_REPLACABLE,
                    PROP_BREAK_ON_NO_DROPS,
                    TunnelAspectWriteBuilders.Item.PROP_NBT_SUBSET,
                    TunnelAspectWriteBuilders.Item.PROP_NBT_SUPERSET,
                    TunnelAspectWriteBuilders.Item.PROP_NBT_REQUIRE,
                    TunnelAspectWriteBuilders.Item.PROP_NBT_RECURSIVE
            ));
            public static final IAspectProperties PROPERTIES_BLOCK_PLACE = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                    PROP_CHANNEL,
                    PROP_ROUNDROBIN,
                    PROP_BLOCK_UPDATE,
                    PROP_HAND_RIGHT,
                    PROP_IGNORE_REPLACABLE
            ));
            public static final IAspectProperties PROPERTIES_BLOCK_PICK_UP = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                    PROP_CHANNEL,
                    PROP_ROUNDROBIN,
                    PROP_BLOCK_UPDATE,
                    PROP_HAND_RIGHT,
                    PROP_SILK_TOUCH,
                    PROP_IGNORE_REPLACABLE,
                    PROP_BREAK_ON_NO_DROPS
            ));

            static {
                PROPERTIES_ITEM_PLACE_NOCHECKS.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
                PROPERTIES_ITEM_PLACE_NOCHECKS.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PLACE_NOCHECKS.setValue(PROP_BLOCK_UPDATE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PLACE_NOCHECKS.setValue(PROP_HAND_RIGHT, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ITEM_PLACE_NOCHECKS.setValue(PROP_IGNORE_REPLACABLE, ValueTypeBoolean.ValueBoolean.of(false));

                PROPERTIES_ITEM_PLACE.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
                PROPERTIES_ITEM_PLACE.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PLACE.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PLACE.setValue(PROP_EMPTYISANY, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PLACE.setValue(TunnelAspectWriteBuilders.Item.PROP_CHECK_DAMAGE, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ITEM_PLACE.setValue(TunnelAspectWriteBuilders.Item.PROP_CHECK_NBT, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ITEM_PLACE.setValue(PROP_BLOCK_UPDATE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PLACE.setValue(PROP_HAND_RIGHT, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ITEM_PLACE.setValue(PROP_IGNORE_REPLACABLE, ValueTypeBoolean.ValueBoolean.of(false));

                PROPERTIES_ITEM_PLACE_NBT.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
                PROPERTIES_ITEM_PLACE_NBT.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PLACE_NBT.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PLACE_NBT.setValue(PROP_BLOCK_UPDATE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PLACE_NBT.setValue(PROP_HAND_RIGHT, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ITEM_PLACE_NBT.setValue(PROP_IGNORE_REPLACABLE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PLACE_NBT.setValue(TunnelAspectWriteBuilders.Item.PROP_NBT_SUBSET, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ITEM_PLACE_NBT.setValue(TunnelAspectWriteBuilders.Item.PROP_NBT_SUPERSET, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ITEM_PLACE_NBT.setValue(TunnelAspectWriteBuilders.Item.PROP_NBT_REQUIRE, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ITEM_PLACE_NBT.setValue(TunnelAspectWriteBuilders.Item.PROP_NBT_RECURSIVE, ValueTypeBoolean.ValueBoolean.of(true));

                PROPERTIES_ITEM_PICK_UP_NOCHECKS.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
                PROPERTIES_ITEM_PICK_UP_NOCHECKS.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PICK_UP_NOCHECKS.setValue(PROP_BLOCK_UPDATE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PICK_UP_NOCHECKS.setValue(PROP_HAND_RIGHT, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ITEM_PICK_UP_NOCHECKS.setValue(PROP_SILK_TOUCH, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PICK_UP_NOCHECKS.setValue(PROP_IGNORE_REPLACABLE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PICK_UP_NOCHECKS.setValue(PROP_BREAK_ON_NO_DROPS, ValueTypeBoolean.ValueBoolean.of(true));

                PROPERTIES_ITEM_PICK_UP.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
                PROPERTIES_ITEM_PICK_UP.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PICK_UP.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PICK_UP.setValue(PROP_EMPTYISANY, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PICK_UP.setValue(TunnelAspectWriteBuilders.Item.PROP_CHECK_DAMAGE, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ITEM_PICK_UP.setValue(TunnelAspectWriteBuilders.Item.PROP_CHECK_NBT, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ITEM_PICK_UP.setValue(PROP_BLOCK_UPDATE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PICK_UP.setValue(PROP_HAND_RIGHT, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ITEM_PICK_UP.setValue(PROP_SILK_TOUCH, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PICK_UP.setValue(PROP_IGNORE_REPLACABLE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PICK_UP.setValue(PROP_BREAK_ON_NO_DROPS, ValueTypeBoolean.ValueBoolean.of(true));

                PROPERTIES_ITEM_PICK_UP_NBT.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
                PROPERTIES_ITEM_PICK_UP_NBT.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PICK_UP_NBT.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PICK_UP_NBT.setValue(PROP_BLOCK_UPDATE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PICK_UP_NBT.setValue(PROP_HAND_RIGHT, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ITEM_PICK_UP_NBT.setValue(PROP_SILK_TOUCH, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PICK_UP_NBT.setValue(PROP_IGNORE_REPLACABLE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_ITEM_PICK_UP_NBT.setValue(PROP_BREAK_ON_NO_DROPS, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ITEM_PICK_UP_NBT.setValue(TunnelAspectWriteBuilders.Item.PROP_NBT_SUBSET, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ITEM_PICK_UP_NBT.setValue(TunnelAspectWriteBuilders.Item.PROP_NBT_SUPERSET, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ITEM_PICK_UP_NBT.setValue(TunnelAspectWriteBuilders.Item.PROP_NBT_REQUIRE, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_ITEM_PICK_UP_NBT.setValue(TunnelAspectWriteBuilders.Item.PROP_NBT_RECURSIVE, ValueTypeBoolean.ValueBoolean.of(true));
                
                PROPERTIES_BLOCK_PLACE.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
                PROPERTIES_BLOCK_PLACE.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_BLOCK_PLACE.setValue(PROP_BLOCK_UPDATE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_BLOCK_PLACE.setValue(PROP_HAND_RIGHT, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_BLOCK_PLACE.setValue(PROP_IGNORE_REPLACABLE, ValueTypeBoolean.ValueBoolean.of(false));
                
                PROPERTIES_BLOCK_PICK_UP.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
                PROPERTIES_BLOCK_PICK_UP.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_BLOCK_PICK_UP.setValue(PROP_BLOCK_UPDATE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_BLOCK_PICK_UP.setValue(PROP_HAND_RIGHT, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_BLOCK_PICK_UP.setValue(PROP_SILK_TOUCH, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_BLOCK_PICK_UP.setValue(PROP_IGNORE_REPLACABLE, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_BLOCK_PICK_UP.setValue(PROP_BREAK_ON_NO_DROPS, ValueTypeBoolean.ValueBoolean.of(true));
            }
            public static final IAspectProperties PROPERTIES_ITEM_PLACELIST = PROPERTIES_ITEM_PLACE.clone();
            public static final IAspectProperties PROPERTIES_ITEM_PICK_UPLIST = PROPERTIES_ITEM_PICK_UP.clone();
            public static final IAspectProperties PROPERTIES_BLOCK_PLACELIST = PROPERTIES_BLOCK_PLACE.clone();
            public static final IAspectProperties PROPERTIES_BLOCK_PICK_UPLIST = PROPERTIES_BLOCK_PICK_UP.clone();
            public static final IAspectProperties PROPERTIES_BLOCK_PLACEBLOCK = PROPERTIES_BLOCK_PLACE.clone();
            public static final IAspectProperties PROPERTIES_BLOCK_PICK_UPBLOCK = PROPERTIES_BLOCK_PICK_UP.clone();
            static {
                PROPERTIES_ITEM_PLACELIST.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));

                PROPERTIES_ITEM_PICK_UPLIST.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));

                PROPERTIES_BLOCK_PLACELIST.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));

                PROPERTIES_BLOCK_PICK_UPLIST.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));

                PROPERTIES_BLOCK_PLACEBLOCK.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_BLOCK_PLACEBLOCK.setValue(PROP_EMPTYISANY, ValueTypeBoolean.ValueBoolean.of(true));
                PROPERTIES_BLOCK_PICK_UPBLOCK.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));
                PROPERTIES_BLOCK_PICK_UPBLOCK.setValue(PROP_EMPTYISANY, ValueTypeBoolean.ValueBoolean.of(true));
            }

            public static final IAspectValuePropagator<IItemTarget, Void>
                    PROP_ITEMBLOCK_EXPORT = input -> {
                PartPos target = input.getPartTarget().getTarget();
                IItemNetwork itemNetwork = input.getChanneledNetwork();
                if (target.getPos().isLoaded() && itemNetwork != null) {
                    EnumHand hand = input.getProperties().getValue(PROP_HAND_RIGHT).getRawValue()
                            ? EnumHand.MAIN_HAND : EnumHand.OFF_HAND;
                    boolean blockUpdate = input.getProperties().getValue(PROP_BLOCK_UPDATE).getRawValue();
                    boolean ignoreReplacable = input.getProperties().getValue(PROP_IGNORE_REPLACABLE).getRawValue();
                    TunnelItemHelpers.placeItems(input.getConnectionHash(), input.getItemChannel(),
                            target.getPos().getWorld(), target.getPos().getBlockPos(), target.getSide(),
                            input.getItemStackMatcher(), hand, blockUpdate, ignoreReplacable);
                }
                return null;

            };

            public static final IAspectValuePropagator<IItemTarget, Void>
                    PROP_ITEMBLOCK_IMPORT = input -> {
                PartPos target = input.getPartTarget().getTarget();
                IItemNetwork itemNetwork = input.getChanneledNetwork();
                if (target.getPos().isLoaded() && itemNetwork != null) {
                    EnumHand hand = input.getProperties().getValue(PROP_HAND_RIGHT).getRawValue()
                            ? EnumHand.MAIN_HAND : EnumHand.OFF_HAND;
                    boolean blockUpdate = input.getProperties().getValue(PROP_BLOCK_UPDATE).getRawValue();
                    boolean ignoreReplacable = input.getProperties().getValue(PROP_IGNORE_REPLACABLE).getRawValue();
                    int fortune = 0;
                    boolean silkTouch = input.getProperties().getValue(PROP_SILK_TOUCH).getRawValue();
                    boolean breakOnNoDrops = input.getProperties().getValue(PROP_BREAK_ON_NO_DROPS).getRawValue();
                    TunnelItemHelpers.pickUpItems(input.getConnectionHash(),
                            target.getPos().getWorld(), target.getPos().getBlockPos(), target.getSide(),
                            input.getItemChannel(), input.getItemStackMatcher(), hand, blockUpdate, ignoreReplacable,
                            fortune, silkTouch, breakOnNoDrops);
                }
                return null;

            };

        }

    }

    public static final class Player {

        public static final AspectBuilder<ValueTypeBoolean.ValueBoolean, ValueTypeBoolean, Triple<PartTarget, IAspectProperties, Boolean>>
                BUILDER_BOOLEAN = AspectWriteBuilders.BUILDER_BOOLEAN.byMod(IntegratedTunnels._instance)
                .appendKind("player").handle(AspectWriteBuilders.PROP_GET_BOOLEAN).withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueTypeInteger.ValueInteger, ValueTypeInteger, Triple<PartTarget, IAspectProperties, Integer>>
                BUILDER_INTEGER = AspectWriteBuilders.BUILDER_INTEGER.byMod(IntegratedTunnels._instance)
                .appendKind("player").handle(AspectWriteBuilders.PROP_GET_INTEGER).withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueObjectTypeItemStack.ValueItemStack, ValueObjectTypeItemStack, Triple<PartTarget, IAspectProperties, ItemStack>>
                BUILDER_ITEMSTACK = AspectWriteBuilders.BUILDER_ITEMSTACK.byMod(IntegratedTunnels._instance)
                .appendKind("player").handle(AspectWriteBuilders.PROP_GET_ITEMSTACK).withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueObjectTypeFluidStack.ValueFluidStack, ValueObjectTypeFluidStack, Triple<PartTarget, IAspectProperties, FluidStack>>
                BUILDER_FLUIDSTACK = AspectWriteBuilders.BUILDER_FLUIDSTACK.byMod(IntegratedTunnels._instance)
                .appendKind("player").handle(AspectWriteBuilders.PROP_GET_FLUIDSTACK).withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueTypeList.ValueList, ValueTypeList, Triple<PartTarget, IAspectProperties, ValueTypeList.ValueList>>
                BUILDER_LIST = AspectWriteBuilders.BUILDER_LIST.byMod(IntegratedTunnels._instance)
                .appendKind("player").withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueTypeOperator.ValueOperator, ValueTypeOperator, Triple<PartTarget, IAspectProperties, ValueTypeOperator.ValueOperator>>
                BUILDER_OPERATOR = AspectWriteBuilders.BUILDER_OPERATOR.byMod(IntegratedTunnels._instance)
                .appendKind("player").withProperties(PROPERTIES_CHANNEL);
        public static final AspectBuilder<ValueTypeNbt.ValueNbt, ValueTypeNbt, Triple<PartTarget, IAspectProperties, NBTTagCompound>>
                BUILDER_NBT = AspectWriteBuilders.BUILDER_NBT.byMod(IntegratedTunnels._instance)
                .appendKind("player").handle(AspectWriteBuilders.PROP_GET_NBT).withProperties(PROPERTIES_CHANNEL);

        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_RIGHT_CLICK =
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.player.rightclick.name");
        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_CONTINUOUS_CLICK =
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.player.continuousclick.name");
        public static final IAspectPropertyTypeInstance<ValueTypeBoolean, ValueTypeBoolean.ValueBoolean> PROP_SNEAK =
                new AspectPropertyTypeInstance<>(ValueTypes.BOOLEAN, "aspect.aspecttypes.integratedtunnels.boolean.player.sneak.name");

        public static final IAspectProperties PROPERTIES_CLICK_EMPTY = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                PROP_CHANNEL,
                PROP_ROUNDROBIN,
                PROP_RIGHT_CLICK,
                World.PROP_HAND_RIGHT,
                PROP_CONTINUOUS_CLICK,
                PROP_SNEAK,
                World.PROPERTY_ENTITYINDEX,
                World.PROP_OFFSET_X,
                World.PROP_OFFSET_Y,
                World.PROP_OFFSET_Z
        ));
        public static final IAspectProperties PROPERTIES_CLICK_SIMPLE = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                PROP_CHANNEL,
                PROP_ROUNDROBIN,
                PROP_RIGHT_CLICK,
                World.PROP_HAND_RIGHT,
                PROP_CONTINUOUS_CLICK,
                PROP_SNEAK,
                Item.PROP_RATE,
                World.PROPERTY_ENTITYINDEX,
                World.PROP_OFFSET_X,
                World.PROP_OFFSET_Y,
                World.PROP_OFFSET_Z
        ));
        public static final IAspectProperties PROPERTIES_CLICK_NORATE = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                PROP_CHANNEL,
                PROP_ROUNDROBIN,
                PROP_RIGHT_CLICK,
                World.PROP_HAND_RIGHT,
                PROP_CONTINUOUS_CLICK,
                PROP_SNEAK,
                World.PROPERTY_ENTITYINDEX,
                World.PROP_OFFSET_X,
                World.PROP_OFFSET_Y,
                World.PROP_OFFSET_Z
        ));
        public static final IAspectProperties PROPERTIES_CLICK = new AspectProperties(ImmutableList.<IAspectPropertyTypeInstance>of(
                PROP_CHANNEL,
                PROP_ROUNDROBIN,
                PROP_BLACKLIST,
                PROP_RIGHT_CLICK,
                PROP_EMPTYISANY,
                World.PROP_HAND_RIGHT,
                PROP_CONTINUOUS_CLICK,
                PROP_SNEAK,
                Item.PROP_CHECK_STACKSIZE,
                Item.PROP_CHECK_DAMAGE,
                Item.PROP_CHECK_NBT,
                Item.PROP_RATE,
                World.PROPERTY_ENTITYINDEX,
                World.PROP_OFFSET_X,
                World.PROP_OFFSET_Y,
                World.PROP_OFFSET_Z
        ));
        static {
            PROPERTIES_CLICK_EMPTY.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
            PROPERTIES_CLICK_EMPTY.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_CLICK_EMPTY.setValue(PROP_RIGHT_CLICK, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_CLICK_EMPTY.setValue(World.PROP_HAND_RIGHT, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_CLICK_EMPTY.setValue(PROP_CONTINUOUS_CLICK, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_CLICK_EMPTY.setValue(PROP_SNEAK, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_CLICK_EMPTY.setValue(World.PROPERTY_ENTITYINDEX, ValueTypeInteger.ValueInteger.of(-1));
            PROPERTIES_CLICK_EMPTY.setValue(World.PROP_OFFSET_X, ValueTypeDouble.ValueDouble.of(0.5D));
            PROPERTIES_CLICK_EMPTY.setValue(World.PROP_OFFSET_Y, ValueTypeDouble.ValueDouble.of(0.5D));
            PROPERTIES_CLICK_EMPTY.setValue(World.PROP_OFFSET_Z, ValueTypeDouble.ValueDouble.of(0.5D));

            PROPERTIES_CLICK_SIMPLE.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
            PROPERTIES_CLICK_SIMPLE.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_CLICK_SIMPLE.setValue(PROP_RIGHT_CLICK, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_CLICK_SIMPLE.setValue(World.PROP_HAND_RIGHT, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_CLICK_SIMPLE.setValue(PROP_CONTINUOUS_CLICK, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_CLICK_SIMPLE.setValue(PROP_SNEAK, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_CLICK_SIMPLE.setValue(Item.PROP_RATE, ValueTypeInteger.ValueInteger.of(1));
            PROPERTIES_CLICK_SIMPLE.setValue(World.PROPERTY_ENTITYINDEX, ValueTypeInteger.ValueInteger.of(-1));
            PROPERTIES_CLICK_SIMPLE.setValue(World.PROP_OFFSET_X, ValueTypeDouble.ValueDouble.of(0.5D));
            PROPERTIES_CLICK_SIMPLE.setValue(World.PROP_OFFSET_Y, ValueTypeDouble.ValueDouble.of(0.5D));
            PROPERTIES_CLICK_SIMPLE.setValue(World.PROP_OFFSET_Z, ValueTypeDouble.ValueDouble.of(0.5D));

            PROPERTIES_CLICK_NORATE.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
            PROPERTIES_CLICK_NORATE.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_CLICK_NORATE.setValue(PROP_RIGHT_CLICK, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_CLICK_NORATE.setValue(World.PROP_HAND_RIGHT, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_CLICK_NORATE.setValue(PROP_CONTINUOUS_CLICK, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_CLICK_NORATE.setValue(PROP_SNEAK, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_CLICK_NORATE.setValue(World.PROPERTY_ENTITYINDEX, ValueTypeInteger.ValueInteger.of(-1));
            PROPERTIES_CLICK_NORATE.setValue(World.PROP_OFFSET_X, ValueTypeDouble.ValueDouble.of(0.5D));
            PROPERTIES_CLICK_NORATE.setValue(World.PROP_OFFSET_Y, ValueTypeDouble.ValueDouble.of(0.5D));
            PROPERTIES_CLICK_NORATE.setValue(World.PROP_OFFSET_Z, ValueTypeDouble.ValueDouble.of(0.5D));

            PROPERTIES_CLICK.setValue(PROP_CHANNEL, ValueTypeInteger.ValueInteger.of(IPositionedAddonsNetworkIngredients.DEFAULT_CHANNEL));
            PROPERTIES_CLICK.setValue(PROP_ROUNDROBIN, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_CLICK.setValue(PROP_RIGHT_CLICK, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_CLICK.setValue(PROP_EMPTYISANY, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_CLICK.setValue(World.PROP_HAND_RIGHT, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_CLICK.setValue(PROP_CONTINUOUS_CLICK, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_CLICK.setValue(PROP_SNEAK, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_CLICK.setValue(Item.PROP_CHECK_STACKSIZE, ValueTypeBoolean.ValueBoolean.of(false));
            PROPERTIES_CLICK.setValue(Item.PROP_CHECK_DAMAGE, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_CLICK.setValue(Item.PROP_CHECK_NBT, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_CLICK.setValue(Item.PROP_RATE, ValueTypeInteger.ValueInteger.of(1));
            PROPERTIES_CLICK.setValue(World.PROPERTY_ENTITYINDEX, ValueTypeInteger.ValueInteger.of(-1));
            PROPERTIES_CLICK.setValue(World.PROP_OFFSET_X, ValueTypeDouble.ValueDouble.of(0.5D));
            PROPERTIES_CLICK.setValue(World.PROP_OFFSET_Y, ValueTypeDouble.ValueDouble.of(0.5D));
            PROPERTIES_CLICK.setValue(World.PROP_OFFSET_Z, ValueTypeDouble.ValueDouble.of(0.5D));
        }
        public static final IAspectProperties PROPERTIES_CLICKLIST = PROPERTIES_CLICK.clone();
        public static final IAspectProperties PROPERTIES_CLICK_NBT = PROPERTIES_CLICK_SIMPLE.clone();
        static {
            PROPERTIES_CLICKLIST.setValue(PROP_BLACKLIST, ValueTypeBoolean.ValueBoolean.of(false));

            PROPERTIES_CLICK_NBT.setValue(TunnelAspectWriteBuilders.Item.PROP_NBT_SUBSET, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_CLICK_NBT.setValue(TunnelAspectWriteBuilders.Item.PROP_NBT_SUPERSET, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_CLICK_NBT.setValue(TunnelAspectWriteBuilders.Item.PROP_NBT_REQUIRE, ValueTypeBoolean.ValueBoolean.of(true));
            PROPERTIES_CLICK_NBT.setValue(TunnelAspectWriteBuilders.Item.PROP_NBT_RECURSIVE, ValueTypeBoolean.ValueBoolean.of(true));
        }

        public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, Boolean>, Void>
                PROP_CLICK_EMPTY = input -> {
            if (input.getRight()) {
                PartTarget partTarget = input.getLeft();
                IAspectProperties properties = input.getMiddle();
                EnumHand hand = properties.getValue(World.PROP_HAND_RIGHT).getRawValue()
                        ? EnumHand.MAIN_HAND : EnumHand.OFF_HAND;
                boolean rightClick = properties.getValue(PROP_RIGHT_CLICK).getRawValue();
                boolean continuousClick = properties.getValue(PROP_CONTINUOUS_CLICK).getRawValue();
                boolean sneak = properties.getValue(PROP_SNEAK).getRawValue();
                int entityIndex = properties.getValue(World.PROPERTY_ENTITYINDEX).getRawValue();
                double offsetX = properties.getValue(World.PROP_OFFSET_X).getRawValue();
                double offsetY = properties.getValue(World.PROP_OFFSET_Y).getRawValue();
                double offsetZ = properties.getValue(World.PROP_OFFSET_Z).getRawValue();
                int channel = properties.getValue(PROP_CHANNEL).getRawValue();

                PartPos center = partTarget.getCenter();
                PartPos target = partTarget.getTarget();
                INetwork network = IChanneledTarget.getNetworkChecked(center);
                PartStatePlayerSimulator partState = (PartStatePlayerSimulator) PartHelpers.getPart(center).getState();

                IIngredientComponentStorage<ItemStack, Integer> storage = new ItemStoragePlayerWrapper(partState.getPlayer(),
                        (WorldServer) target.getPos().getWorld(), target.getPos().getBlockPos(),
                        offsetX, offsetY, offsetZ, target.getSide(), hand,
                        rightClick, sneak, continuousClick, entityIndex, network.getCapability(ItemNetworkConfig.CAPABILITY).getChannel(channel));
                storage.insert(ItemStack.EMPTY, false);
            }
            return null;
        };

        public static final IAspectValuePropagator<Triple<PartTarget, IAspectProperties, ChanneledTargetInformation<ItemStack, Integer>>, IItemTarget>
                PROP_ITEMTARGET_CLICK = input -> {
            PartTarget partTarget = input.getLeft();
            IAspectProperties properties = input.getMiddle();
            int transferHash = input.getRight().getTransferHash();
            IngredientPredicate<ItemStack, Integer> itemStackMatcher = input.getRight().getIngredientPredicate();
            EnumHand hand = input.getMiddle().getValue(World.PROP_HAND_RIGHT).getRawValue()
                    ? EnumHand.MAIN_HAND : EnumHand.OFF_HAND;
            boolean rightClick = input.getMiddle().getValue(PROP_RIGHT_CLICK).getRawValue();
            boolean continuousClick = properties.getValue(PROP_CONTINUOUS_CLICK).getRawValue();
            boolean sneak = properties.getValue(PROP_SNEAK).getRawValue();
            int entityIndex = properties.getValue(World.PROPERTY_ENTITYINDEX).getRawValue();
            double offsetX = properties.getValue(World.PROP_OFFSET_X).getRawValue();
            double offsetY = properties.getValue(World.PROP_OFFSET_Y).getRawValue();
            double offsetZ = properties.getValue(World.PROP_OFFSET_Z).getRawValue();
            int channel = properties.getValue(PROP_CHANNEL).getRawValue();

            PartPos center = partTarget.getCenter();
            PartPos target = partTarget.getTarget();
            INetwork network = IChanneledTarget.getNetworkChecked(center);
            PartStatePlayerSimulator partState = (PartStatePlayerSimulator) PartHelpers.getPart(center).getState();

            IIngredientComponentStorage<ItemStack, Integer> storage = new ItemStoragePlayerWrapper(partState.getPlayer(),
                    (WorldServer) target.getPos().getWorld(), target.getPos().getBlockPos(),
                    offsetX, offsetY, offsetZ, target.getSide(), hand,
                    rightClick, sneak, continuousClick, entityIndex, network.getCapability(ItemNetworkConfig.CAPABILITY).getChannel(channel));
            return IItemTarget.ofStorage(transferHash, network, partTarget, properties,
                    itemStackMatcher, storage, -1);
        };

    }

    public static <N extends IPositionedAddonsNetwork, T> IAspectWriteActivator
    createPositionedNetworkAddonActivator(final Supplier<Capability<N>> networkCapability, final Capability<T> targetCapability) {
        return new IAspectWriteActivator() {
            @Override
            public <P extends IPartTypeWriter<P, S>, S extends IPartStateWriter<P>> void onActivate(P partType, PartTarget target, S state) {
                state.addVolatileCapability(targetCapability, (T) state);
                DimPos pos = target.getCenter().getPos();
                INetwork network = NetworkHelpers.getNetwork(pos.getWorld(), pos.getBlockPos(), target.getCenter().getSide());
                if (network != null && network.hasCapability(networkCapability.get())) {
                    ((PartStatePositionedAddon<?, N>) state).setPositionedAddonsNetwork(network.getCapability(networkCapability.get()));

                    // Notify target neighbour
                    DimPos originPos = target.getCenter().getPos();
                    DimPos targetPos = target.getTarget().getPos();
                    targetPos.getWorld().neighborChanged(targetPos.getBlockPos(),
                            targetPos.getWorld().getBlockState(targetPos.getBlockPos()).getBlock(), originPos.getBlockPos());
                }
            }
        };
    }

    public static <N extends IPositionedAddonsNetwork, T> IAspectWriteDeactivator
    createPositionedNetworkAddonDeactivator(final Supplier<Capability<N>> networkCapability, final Capability<T> targetCapability) {
        return new IAspectWriteDeactivator() {
            @Override
            public <P extends IPartTypeWriter<P, S>, S extends IPartStateWriter<P>> void onDeactivate(P partType, PartTarget target, S state) {
                state.removeVolatileCapability(targetCapability);
                DimPos pos = target.getCenter().getPos();
                INetwork network = NetworkHelpers.getNetwork(pos.getWorld(), pos.getBlockPos(), target.getCenter().getSide());
                if (network != null && network.hasCapability(networkCapability.get())) {
                    ((PartStatePositionedAddon<?, N>) state).setPositionedAddonsNetwork(network.getCapability(networkCapability.get()));

                    // Notify target neighbour
                    DimPos originPos = target.getCenter().getPos();
                    DimPos targetPos = target.getTarget().getPos();
                    targetPos.getWorld().neighborChanged(targetPos.getBlockPos(),
                            targetPos.getWorld().getBlockState(targetPos.getBlockPos()).getBlock(), originPos.getBlockPos());
                }
            }
        };
    }

}
