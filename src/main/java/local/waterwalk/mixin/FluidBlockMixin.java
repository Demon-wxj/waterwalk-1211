package local.waterwalk.mixin;

import local.waterwalk.WaterWalk;
import net.minecraft.block.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.*;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FluidBlock.class)
public abstract class FluidBlockMixin {
    @Inject(method="getCollisionShape", at=@At("HEAD"), cancellable=true)
    private void waterwalk$surface(BlockState state, BlockView world, BlockPos pos, ShapeContext context,
                                   CallbackInfoReturnable<VoxelShape> cir) {
        if (!(context instanceof EntityShapeContext ec) || !(ec.getEntity() instanceof PlayerEntity p)
                || !WaterWalk.active(p)) return;
        double height = WaterWalk.surfaceHeight(world, pos);
        // A one-way surface: do not imprison swimmers below the water or create side walls.
        if (height > 0 && p.getY() >= pos.getY() + height - (p.isOnGround() ? 0.6 : 0.06)) {
            cir.setReturnValue(VoxelShapes.cuboid(0, 0, 0, 1, height, 1));
        }
    }
}
