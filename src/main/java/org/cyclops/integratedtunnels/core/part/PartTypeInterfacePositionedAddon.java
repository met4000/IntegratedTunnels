package org.cyclops.integratedtunnels.core.part;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.apache.commons.lang3.tuple.Triple;
import org.cyclops.cyclopscore.network.PacketCodec;
import org.cyclops.integrateddynamics.api.network.INetwork;
import org.cyclops.integrateddynamics.api.network.IPartNetwork;
import org.cyclops.integrateddynamics.api.network.IPositionedAddonsNetwork;
import org.cyclops.integrateddynamics.api.part.IPartContainer;
import org.cyclops.integrateddynamics.api.part.PartPos;
import org.cyclops.integrateddynamics.api.part.PartTarget;
import org.cyclops.integrateddynamics.core.helper.PartHelpers;
import org.cyclops.integrateddynamics.core.part.PartStateBase;
import org.cyclops.integrateddynamics.core.part.PartTypeBase;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * Interface for positioned network addons that do not have a filter.
 * @author rubensworks
 */
public abstract class PartTypeInterfacePositionedAddon<N extends IPositionedAddonsNetwork, T, P extends PartTypeInterfacePositionedAddon<N, T, P, S>, S extends IPartTypeInterfacePositionedAddon.IState<N, T, P, S>>
        extends PartTypeTunnel<P, S>
        implements IPartTypeInterfacePositionedAddon<N, T, P, S> {

    public PartTypeInterfacePositionedAddon(String name) {
        super(name);
    }

    @Override
    public boolean isUpdate(S state) {
        return getConsumptionRate(state) > 0 && org.cyclops.integrateddynamics.GeneralConfig.energyConsumptionMultiplier > 0;
    }

    @Override
    public Optional<MenuProvider> getContainerProvider(PartPos pos) {
        return Optional.of(new MenuProvider() {

            @Override
            public Component getDisplayName() {
                return new TranslatableComponent(getTranslationKey());
            }

            @Nullable
            @Override
            public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player playerEntity) {
                Triple<IPartContainer, PartTypeBase, PartTarget> data = PartHelpers.getContainerPartConstructionData(pos);
                return new ContainerInterfaceSettings(id, playerInventory, new SimpleContainer(0),
                        data.getRight(), Optional.of(data.getLeft()), data.getMiddle());
            }
        });
    }

    @Override
    public void writeExtraGuiData(FriendlyByteBuf packetBuffer, PartPos pos, ServerPlayer player) {
        PacketCodec.write(packetBuffer, pos);
        packetBuffer.writeUtf(this.getUniqueName().toString());
    }

    @Override
    public void onAddingPositionToNetwork(N networkCapability, INetwork network, PartPos pos, int priority, int channelInterface, S state) {
        networkCapability.addPosition(pos, priority, channelInterface);
    }

    @Override
    public void onRemovingPositionFromNetwork(N networkCapability, INetwork network, PartPos pos, S state) {
        networkCapability.removePosition(pos);
    }

    // Methods below copied to PartTypeInterfacePositionedAddonFiltering

    @Override
    public void afterNetworkReAlive(INetwork network, IPartNetwork partNetwork, PartTarget target, S state) {
        super.afterNetworkReAlive(network, partNetwork, target, state);
        addTargetToNetwork(network, target.getTarget(), state.getPriority(), state.getChannelInterface(), state);
    }

    @Override
    public void onNetworkRemoval(INetwork network, IPartNetwork partNetwork, PartTarget target, S state) {
        super.onNetworkRemoval(network, partNetwork, target, state);
        scheduleNetworkObservation(target, state);
        removeTargetFromNetwork(network, target.getTarget(), state);
    }

    @Override
    public void onNetworkAddition(INetwork network, IPartNetwork partNetwork, PartTarget target, S state) {
        super.onNetworkAddition(network, partNetwork, target, state);
        addTargetToNetwork(network, target.getTarget(), state.getPriority(), state.getChannelInterface(), state);
        scheduleNetworkObservation(target, state);
    }

    @Override
    public void onBlockNeighborChange(INetwork network, IPartNetwork partNetwork, PartTarget target, S state, BlockGetter world, Block neighbourBlock, BlockPos neighbourBlockPos) {
        super.onBlockNeighborChange(network, partNetwork, target, state, world, neighbourBlock, neighbourBlockPos);
        if (network != null) {
            updateTargetInNetwork(network, target.getTarget(), state.getPriority(), state.getChannelInterface(), state);
        }
    }

    @Override
    public void setPriorityAndChannel(INetwork network, IPartNetwork partNetwork, PartTarget target, S state, int priority, int channel) {
        // We need to do this because the energy network is not automagically aware of the priority changes,
        // so we have to re-add it.
        removeTargetFromNetwork(network, target.getTarget(), state);
        super.setPriorityAndChannel(network, partNetwork, target, state, priority, channel);
        addTargetToNetwork(network, target.getTarget(), priority, state.getChannelInterface(), state);
    }

    public static abstract class State<N extends IPositionedAddonsNetwork, T, P extends PartTypeInterfacePositionedAddon<N, T, P, S>, S extends State<N, T, P, S>>
            extends PartStateBase<P>
            implements IPartTypeInterfacePositionedAddon.IState<N, T, P, S> {

        private N positionedAddonsNetwork = null;
        private PartPos pos = null;
        private boolean validTargetCapability = false;
        private int channelInterface = 0;

        private INetwork network;
        private IPartNetwork partNetwork;

        @Override
        public void readFromNBT(CompoundTag tag) {
            super.readFromNBT(tag);
            if (tag.contains("channelInterface", Tag.TAG_INT)) {
                this.channelInterface = tag.getInt("channelInterface");
            }
        }

        @Override
        public void writeToNBT(CompoundTag tag) {
            super.writeToNBT(tag);
            tag.putInt("channelInterface", channelInterface);
        }

        @Override
        public void setChannelInterface(int channelInterface) {
            this.channelInterface = channelInterface;
            sendUpdate();
        }

        @Override
        public int getChannelInterface() {
            return channelInterface;
        }

        @Override
        @Nullable
        public N getPositionedAddonsNetwork() {
            return positionedAddonsNetwork;
        }

        @Override
        public void setPositionedAddonsNetwork(N positionedAddonsNetwork) {
            this.positionedAddonsNetwork = positionedAddonsNetwork;
        }

        @Override
        public boolean isValidTargetCapability() {
            return validTargetCapability;
        }

        @Override
        public void setValidTargetCapability(boolean validTargetCapability) {
            this.validTargetCapability = validTargetCapability;
        }

        @Override
        public PartPos getPos() {
            return pos;
        }

        @Override
        public void setPos(PartPos pos) {
            this.pos = pos;
        }

        @Override
        public void setNetworks(@Nullable INetwork network, @Nullable IPartNetwork partNetwork) {
            this.network = network;
            this.partNetwork = partNetwork;
        }

        @Override
        @Nullable
        public INetwork getNetwork() {
            return network;
        }

        @Override
        @Nullable
        public IPartNetwork getPartNetwork() {
            return partNetwork;
        }

        @Override
        public <T2> LazyOptional<T2> getCapability(Capability<T2> capability, INetwork network, IPartNetwork partNetwork, PartTarget target) {
            if (isNetworkAndPositionValid() && capability == getTargetCapability()) {
                return LazyOptional.of(this::getCapabilityInstance).cast();
            }
            return super.getCapability(capability, network, partNetwork, target);
        }
    }

}
