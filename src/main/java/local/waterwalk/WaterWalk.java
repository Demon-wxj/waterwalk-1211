package local.waterwalk;

import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.*;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.BlockView;
import static net.minecraft.server.command.CommandManager.literal;

public final class WaterWalk implements ModInitializer {
    public static final SimpleParticleType RIPPLE = FabricParticleTypes.simple();
    public static Identifier id(String path) { return Identifier.of("waterwalk", path); }
    public static boolean enabled(PlayerEntity p) { return ((WalkState)p).waterwalk$isEnabled(); }
    public static boolean active(PlayerEntity p) {
        return enabled(p) && p.isAlive() && !p.isSpectator() && !p.hasVehicle()
                && !p.getAbilities().flying && !p.isFallFlying() && !p.isSneaking();
    }
    public static double surfaceHeight(BlockView world, BlockPos pos) {
        var fluid = world.getFluidState(pos);
        if (!fluid.isIn(FluidTags.WATER) || world.getFluidState(pos.up()).isIn(FluidTags.WATER)) return -1;
        return Math.min(1, fluid.getHeight(world,pos) + 0.016);
    }
    public static void assistSurface(PlayerEntity p) {
        if (!active(p) || !p.isTouchingWater()) return;
        // Near the surface, lift smoothly out of swimming. Deep water still requires swimming up.
        for (int dy=0;dy<=2;dy++) {
            BlockPos pos=p.getBlockPos().up(dy);
            double h=surfaceHeight(p.getWorld(),pos), gap=pos.getY()+h-p.getY();
            if (h>0 && gap>0 && gap<=1.6) {
                p.setSwimming(false);
                var velocity=p.getVelocity();
                p.setVelocity(velocity.x,Math.max(velocity.y,0.22),velocity.z);
                return;
            }
        }
    }
    public record Toggle(boolean request) implements CustomPayload {
        public static final Id<Toggle> ID = new Id<>(id("toggle_v1"));
        public static final PacketCodec<RegistryByteBuf,Toggle> CODEC = PacketCodec.of((p,b)->b.writeBoolean(p.request), b->new Toggle(b.readBoolean()));
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    public record State(boolean enabled) implements CustomPayload {
        public static final Id<State> ID = new Id<>(id("state_v1"));
        public static final PacketCodec<RegistryByteBuf,State> CODEC = PacketCodec.of((p,b)->b.writeBoolean(p.enabled), b->new State(b.readBoolean()));
        public Id<? extends CustomPayload> getId() { return ID; }
    }
    private static final Map<UUID,Integer> REQUESTS = new HashMap<>();
    private static final Map<UUID,Stride> STRIDES = new HashMap<>();
    private static final class Stride {
        Vec3d last; double distance; int foot, lastTick; Object world; boolean grounded;
        Stride(ServerPlayerEntity p) { last=p.getPos(); world=p.getWorld(); lastTick=p.age; grounded=p.isOnGround(); }
    }
    public static void set(ServerPlayerEntity p, boolean value) {
        ((WalkState)p).waterwalk$setEnabled(value);
        STRIDES.remove(p.getUuid());
        sync(p);
        p.sendMessage(Text.literal(value ? "水上漂：开" : "水上漂：关"), true);
    }
    private static void sync(ServerPlayerEntity p) {
        if (ServerPlayNetworking.canSend(p, State.ID)) ServerPlayNetworking.send(p,new State(enabled(p)));
    }
    @Override public void onInitialize() {
        Registry.register(Registries.PARTICLE_TYPE,id("ripple"),RIPPLE);
        PayloadTypeRegistry.playC2S().register(Toggle.ID,Toggle.CODEC);
        PayloadTypeRegistry.playS2C().register(State.ID,State.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Toggle.ID,(packet,ctx)->ctx.server().execute(()->{
            var p=ctx.player(); int tick=ctx.server().getTicks();
            if (!packet.request || !p.isAlive() || tick-REQUESTS.getOrDefault(p.getUuid(),-100)<5) return;
            REQUESTS.put(p.getUuid(),tick); set(p,!enabled(p));
        }));
        CommandRegistrationCallback.EVENT.register((dispatcher,access,env)->dispatcher.register(literal("waterwalk")
            .executes(c->{var p=c.getSource().getPlayerOrThrow();set(p,!enabled(p));return 1;})
            .then(literal("on").executes(c->{set(c.getSource().getPlayerOrThrow(),true);return 1;}))
            .then(literal("off").executes(c->{set(c.getSource().getPlayerOrThrow(),false);return 1;}))));
        ServerPlayConnectionEvents.JOIN.register((h,s,server)->sync(h.player));
        ServerPlayConnectionEvents.DISCONNECT.register((h,s)->{REQUESTS.remove(h.player.getUuid());STRIDES.remove(h.player.getUuid());});
        ServerPlayerEvents.AFTER_RESPAWN.register((old,p,alive)->{STRIDES.remove(p.getUuid());sync(p);});
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((p,origin,destination)->{
            STRIDES.remove(p.getUuid());sync(p);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s->{REQUESTS.clear();STRIDES.clear();});
        ServerTickEvents.END_SERVER_TICK.register(s->s.getPlayerManager().getPlayerList().forEach(WaterWalk::step));
    }
    private static void step(ServerPlayerEntity p) {
        if (!active(p)) { STRIDES.remove(p.getUuid()); return; }
        BlockPos pos=rippleSurface(p,p.getX(),p.getZ());
        if(pos==null){STRIDES.remove(p.getUuid());return;}
        double surface=surfaceHeight(p.getWorld(),pos);
        // Only continue an airborne trail after contact with the water; flying over it is not a step.
        // Position relative to the surface is stable when stationary; packet onGround can flicker.
        boolean contact=Math.abs(p.getY()-(pos.getY()+surface))<=0.09;
        if(!contact && !STRIDES.containsKey(p.getUuid()))return;
        Stride stride=STRIDES.computeIfAbsent(p.getUuid(),k->new Stride(p));
        double moved=p.getPos().subtract(stride.last).horizontalLength();
        stride.last=p.getPos();
        if (stride.world!=p.getWorld() || moved>2) { STRIDES.remove(p.getUuid()); return; }
        stride.distance+=moved;
        boolean impact=contact!=stride.grounded;
        stride.grounded=contact;
        // Keep stride state through a normal jump. Rings stay on the water below the feet.
        // Takeoff and landing also produce a ring, including a jump in place.
        if (!impact && (stride.distance<(p.isSprinting()?0.85:0.62) || p.age-stride.lastTick<4)) return;
        stride.distance=0;stride.lastTick=p.age;
        double side=(stride.foot++%2==0 ? -0.13 : 0.13), yaw=Math.toRadians(p.getYaw());
        double x=p.getX()+Math.cos(yaw)*side, z=p.getZ()+Math.sin(yaw)*side;
        BlockPos foot=rippleSurface(p,x,z);
        if(foot==null)return;
        double h=surfaceHeight(p.getWorld(),foot);
        if(h<0)return;
        p.getServerWorld().spawnParticles(RIPPLE,x,foot.getY()+h+0.008,z,1,0,0,0,0);
    }
    private static BlockPos rippleSurface(ServerPlayerEntity p,double x,double z){
        BlockPos top=BlockPos.ofFloored(x,p.getY()-0.05,z);
        for(int dy=0;dy<=2;dy++){
            BlockPos pos=top.down(dy);
            double h=surfaceHeight(p.getWorld(),pos),gap=p.getY()-(pos.getY()+h);
            if(h>0 && gap>=-0.09 && gap<=1.6 && p.getWorld().getBlockState(pos.up()).isAir())return pos;
            if(!p.getWorld().getBlockState(pos).isAir() && h<0)return null;
        }
        return null;
    }
}
