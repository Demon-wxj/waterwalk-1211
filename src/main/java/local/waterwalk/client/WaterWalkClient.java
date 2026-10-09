package local.waterwalk.client;

import local.waterwalk.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public final class WaterWalkClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ParticleFactoryRegistry.getInstance().register(WaterWalk.RIPPLE,RippleParticle.Factory::new);
        var toggle=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.waterwalk.toggle",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_J,"category.waterwalk"));
        ClientPlayNetworking.registerGlobalReceiver(WaterWalk.State.ID,(packet,ctx)->ctx.client().execute(()->{
            if(ctx.client().player!=null)((WalkState)ctx.client().player).waterwalk$setEnabled(packet.enabled());
        }));
        ClientTickEvents.END_CLIENT_TICK.register(c->{while(toggle.wasPressed()) {
            if(c.player==null||c.currentScreen!=null)continue;
            if(ClientPlayNetworking.canSend(WaterWalk.Toggle.ID))ClientPlayNetworking.send(new WaterWalk.Toggle(true));
            else c.player.sendMessage(Text.literal("当前服务器未安装水上漂 Mod"),true);
        }});
    }
}
