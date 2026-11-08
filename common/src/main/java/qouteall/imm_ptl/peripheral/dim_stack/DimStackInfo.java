package qouteall.imm_ptl.peripheral.dim_stack;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.core.portal.global_portals.VerticalConnectingPortal;
import qouteall.q_misc_util.Helper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// will be serialized by GSON
public class DimStackInfo {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Optional independent gate from the End world's bottom plane into a
     * selected layer. This is NOT another dimension-stack entry: it creates
     * only the End -> destination portal, never a reverse portal.
     */
    @Nullable public String endBottomExitTarget = null;
    public int endBottomExitCeilingOffsetBlocks = 32;

    public boolean loop;
    public boolean gravityTransform;
    public List<DimStackEntry> entries;
    
    public DimStackInfo() {
        entries = new ArrayList<>();
        loop = false;
        gravityTransform = false;
    }
    
    public DimStackInfo(List<DimStackEntry> entries, boolean loop, boolean gravityTransform) {
        this.entries = entries;
        this.loop = loop;
        this.gravityTransform = gravityTransform;
    }
    
    public static void initializeFuseViewProperty(Portal portal) {
        // all portals are fuse-view now
        portal.setFuseView(true);
    }
    
    public static void createConnectionBetween(
        DimStackEntry a, DimStackEntry b, boolean gravityChange
    ) {
        ServerLevel fromWorld = McHelper.getServerWorld(a.getDimension());
        ServerLevel toWorld = McHelper.getServerWorld(b.getDimension());
        
        boolean xorFlipped = a.flipped ^ b.flipped;
        
        int fromWorldMinY = McHelper.getMinY(fromWorld);
        if (a.bottomY != null) {
            fromWorldMinY = a.bottomY;
        }
        int fromWorldMaxY = McHelper.getMaxContentYExclusive(fromWorld);
        if (a.topY != null) {
            fromWorldMaxY = a.topY;
        }
        int toWorldMinY = McHelper.getMinY(toWorld);
        if (b.bottomY != null) {
            toWorldMinY = b.bottomY;
        }
        int toWorldMaxY = McHelper.getMaxContentYExclusive(toWorld);
        if (b.topY != null) {
            toWorldMaxY = b.topY;
        }
        
        VerticalConnectingPortal connectingPortal = VerticalConnectingPortal.createConnectingPortal(
            fromWorld,
            a.flipped ? VerticalConnectingPortal.ConnectorType.ceil :
                VerticalConnectingPortal.ConnectorType.floor,
            toWorld,
            b.scale / a.scale,
            xorFlipped,
            b.horizontalRotation - a.horizontalRotation,
            fromWorldMinY, fromWorldMaxY,
            toWorldMinY, toWorldMaxY
        );
        
        VerticalConnectingPortal reverse = PortalAPI.createReversePortal(connectingPortal);
        
        initializeFuseViewProperty(connectingPortal);
        initializeFuseViewProperty(reverse);
        
        if (gravityChange) {
            connectingPortal.setTeleportChangesGravity(true);
            reverse.setTeleportChangesGravity(true);
        }
        
        if (a.connectsNext) {
            PortalAPI.addGlobalPortal(fromWorld, connectingPortal);
        }
        
        if (b.connectsPrevious) {
            PortalAPI.addGlobalPortal(toWorld, reverse);
        }
    }
    
    public void apply(MinecraftServer server) {
        
        if (entries.isEmpty()) {
            McHelper.sendMessageToFirstLoggedPlayer(server, Component.literal(
                "Error: No dimension for dimension stack"
            ));
            return;
        }
        
        for (DimStackEntry entry : entries) {
            if (server.getLevel(entry.getDimension()) == null) {
                McHelper.sendMessageToFirstLoggedPlayer(server, Component.literal(
                    "Failed to apply dimension stack. Missing dimension " + entry.dimensionIdStr
                ).withStyle(ChatFormatting.RED));
                return;
            }
        }
        
        if (!GlobalPortalStorage.getGlobalPortals(McHelper.getServerWorld(entries.get(0).getDimension())).isEmpty()) {
            Helper.err("There are already global portals when initializing dimension stack");
            McHelper.sendMessageToFirstLoggedPlayer(server, Component.literal(
                "Failed to apply dimension stack because there are already global portals when initializing dimension stack"
            ).withStyle(ChatFormatting.RED));
            return;
        }
        
        // Validate the independent End escape before mutating any global
        // portals or the bedrock replacement map. Invalid configs fail closed.
        if (EndBottomExitPolicy.isEnabled(endBottomExitTarget)) {
            if (!EndBottomExitPolicy.isSupportedTarget(endBottomExitTarget)) {
                LOGGER.error("Unsupported End bottom exit target: {}", endBottomExitTarget);
                McHelper.sendMessageToFirstLoggedPlayer(server, Component.literal(
                    "Invalid End bottom exit target: " + endBottomExitTarget
                ).withStyle(ChatFormatting.RED));
                return;
            }
            ServerLevel endWorld = server.getLevel(Level.END);
            ResourceKey<Level> targetKey = EndBottomExitPolicy.OVERWORLD.equals(endBottomExitTarget)
                ? Level.OVERWORLD
                : qouteall.imm_ptl.peripheral.alternate_dimension.AlternateDimensions.SKYLAND;
            ServerLevel targetWorld = server.getLevel(targetKey);
            if (endWorld == null || targetWorld == null) {
                LOGGER.error("End exit cannot be installed; missing source or destination {}", endBottomExitTarget);
                return;
            }
            if (VerticalConnectingPortal.getConnectingPortal(
                endWorld, VerticalConnectingPortal.ConnectorType.floor
            ) != null) {
                LOGGER.error("End floor already contains a vertical global portal; refusing to overwrite it");
                return;
            }
        }

        // check connection conflict
        Map<PortalInfo, List<DimStackEntry>> portalInfoMap = getPortalInfoMap();
        for (Map.Entry<PortalInfo, List<DimStackEntry>> entry : portalInfoMap.entrySet()) {
            PortalInfo key = entry.getKey();
            List<DimStackEntry> value = entry.getValue();
            if (value != null && value.size() > 1) {
                McHelper.sendMessageToFirstLoggedPlayer(server, Component.literal(
                    "Failed to apply dimension stack because of connection conflict. There are multiple connections in the %s of %s"
                        .formatted(key.connectorType, key.dimension.identifier())
                ).withStyle(ChatFormatting.RED));
                return;
            }
        }
        
        Helper.wrapAdjacentAndMap(
            entries.stream(),
            Pair::of
        ).forEach(pair -> {
            DimStackEntry before = pair.getFirst();
            DimStackEntry after = pair.getSecond();
            createConnectionBetween(before, after, gravityTransform);
        });
        
        if (loop) {
            createConnectionBetween(entries.get(entries.size() - 1), entries.get(0), gravityTransform);
        }

        // The End is kept outside the ordinary three-layer graph. Install
        // one global floor portal there, with NO inverse portal in the target.
        if (EndBottomExitPolicy.isEnabled(endBottomExitTarget)) {
            ResourceKey<Level> destinationKey =
                EndBottomExitPolicy.OVERWORLD.equals(endBottomExitTarget)
                    ? Level.OVERWORLD
                    : qouteall.imm_ptl.peripheral.alternate_dimension.AlternateDimensions.SKYLAND;
            ServerLevel endWorld = server.getLevel(Level.END);
            ServerLevel destinationWorld = server.getLevel(destinationKey);
            VerticalConnectingPortal exit = VerticalConnectingPortal.createConnectingPortal(
                endWorld, VerticalConnectingPortal.ConnectorType.floor,
                destinationWorld, 1.0, false, 0,
                McHelper.getMinY(endWorld), McHelper.getMaxContentYExclusive(endWorld),
                McHelper.getMinY(destinationWorld), McHelper.getMaxContentYExclusive(destinationWorld)
            );
            // In particular, avoid placing the Overworld arrival exactly on
            // its existing Skyland ceiling portal. The arrival is still
            // NOT a guaranteed safe surface: staging tests must prepare one.
            exit.setDestination(new Vec3(
                0,
                EndBottomExitPolicy.destinationY(
                    McHelper.getMaxContentYExclusive(destinationWorld),
                    endBottomExitCeilingOffsetBlocks
                ),
                0
            ));
            initializeFuseViewProperty(exit);
            PortalAPI.addGlobalPortal(endWorld, exit);
            LOGGER.info(
                "Installed one-way End floor exit to {} at target Y={} (no reverse portal)",
                endBottomExitTarget, exit.getDestPos().y
            );
        }

        Map<ResourceKey<Level>, BlockState> bedrockReplacementMap = new HashMap<>();
        for (DimStackEntry entry : entries) {
            String bedrockReplacementStr = entry.bedrockReplacementStr;
            
            BlockState bedrockReplacement = parseBlockString(bedrockReplacementStr);
            
            ResourceKey<Level> dimId = entry.getDimension();
            if (bedrockReplacement != null) {
                bedrockReplacementMap.put(dimId, bedrockReplacement);
            }
            ServerLevel world = server.getLevel(dimId);
            Validate.notNull(world, "Missing dimension %s", dimId.identifier());
            GlobalPortalStorage gps = GlobalPortalStorage.get(world);
            gps.bedrockReplacement = bedrockReplacement;
            gps.onDataChanged();
        }
        DimStackManagement.bedrockReplacementMap = bedrockReplacementMap;
        
        McHelper.sendMessageToFirstLoggedPlayer(
            server,
            Component.translatable("imm_ptl.dim_stack_initialized")
        );
    }
    
    public boolean isEffectivelyConnectingPrevious(int index) {
        DimStackEntry entry = entries.get(index);
        if (!entry.connectsPrevious) {
            return false;
        }
        
        if (index == 0) {
            return loop;
        }
        return true;
    }
    
    public boolean isEffectivelyConnectionNext(int index) {
        DimStackEntry entry = entries.get(index);
        if (!entry.connectsNext) {
            return false;
        }
        
        if (index == entries.size() - 1) {
            return loop;
        }
        return true;
    }
    
    public boolean isEffectivelyConnectingCeil(int index) {
        DimStackEntry entry = entries.get(index);
        
        if (!entry.flipped) {
            return isEffectivelyConnectingPrevious(index);
        }
        else {
            return isEffectivelyConnectionNext(index);
        }
    }
    
    public boolean isEffectivelyConnectingFloor(int index) {
        DimStackEntry entry = entries.get(index);
        
        if (!entry.flipped) {
            return isEffectivelyConnectionNext(index);
        }
        else {
            return isEffectivelyConnectingPrevious(index);
        }
    }
    
    public static record PortalInfo(
        ResourceKey<Level> dimension,
        VerticalConnectingPortal.ConnectorType connectorType
    ) {}
    
    public Map<PortalInfo, List<DimStackEntry>> getPortalInfoMap() {
        Map<PortalInfo, List<DimStackEntry>> map = new HashMap<>();
        
        for (int i = 0; i < entries.size(); i++) {
            DimStackEntry entry = entries.get(i);
            
            if (isEffectivelyConnectingCeil(i)) {
                PortalInfo portalInfo = new PortalInfo(
                    entry.getDimension(),
                    VerticalConnectingPortal.ConnectorType.ceil
                );
                map.computeIfAbsent(portalInfo, k -> new ArrayList<>()).add(entry);
            }
            
            if (isEffectivelyConnectingFloor(i)) {
                PortalInfo portalInfo = new PortalInfo(
                    entry.getDimension(),
                    VerticalConnectingPortal.ConnectorType.floor
                );
                map.computeIfAbsent(portalInfo, k -> new ArrayList<>()).add(entry);
            }
        }
        
        return map;
    }
    
    @Nullable
    public static BlockState parseBlockString(@Nullable String str) {
        if (str == null) {
            return null;
        }
        if (str.isEmpty()) {
            return null;
        }
        
        try {
            Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(McHelper.newResourceLocation(str));
            return block.map(Block::defaultBlockState).orElse(null);
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public boolean hasDimension(ResourceKey<Level> dimension) {
        return entries.stream().anyMatch(e -> e.getDimension() == dimension);
    }
}
