package local.waterwalk.mixin;

import local.waterwalk.WalkState;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerStateMixin implements WalkState {
    @Unique private boolean waterwalk$enabled;
    public boolean waterwalk$isEnabled() { return waterwalk$enabled; }
    public void waterwalk$setEnabled(boolean value) { waterwalk$enabled = value; }
    @Inject(method="tick",at=@At("HEAD"))
    private void waterwalk$rise(CallbackInfo ci) {
        local.waterwalk.WaterWalk.assistSurface((PlayerEntity)(Object)this);
    }
}
