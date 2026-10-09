package local.waterwalk.client;

import local.waterwalk.WaterWalk;
import net.minecraft.client.particle.*;
import net.minecraft.client.render.*;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.util.math.*;

/** Horizontal water decal using the standard lit translucent particle pipeline (including Iris). */
public final class RippleParticle extends SpriteBillboardParticle {
    private final float rotation;
    protected RippleParticle(ClientWorld world,double x,double y,double z,SpriteProvider sprites) {
        super(world,x,y,z);
        setSprite(sprites);maxAge=40;collidesWithWorld=false;
        velocityX=velocityY=velocityZ=0;gravityStrength=0;
        rotation=random.nextFloat()*(float)(Math.PI*2);
        red=0.92f;green=0.97f;blue=1f;
    }
    @Override public void tick() {
        prevPosX=x;prevPosY=y;prevPosZ=z;
        if(++age>=maxAge || WaterWalk.surfaceHeight(world,BlockPos.ofFloored(x,y-0.04,z))<0)markDead();
    }
    @Override public ParticleTextureSheet getType(){return ParticleTextureSheet.PARTICLE_SHEET_TRANSLUCENT;}
    @Override public void buildGeometry(VertexConsumer out,Camera camera,float delta) {
        float t=(age+delta)/maxAge;
        float radius=0.12f+1.35f*(1-(1-t)*(1-t));
        float opacity=0.88f*Math.min(1,t*8)*(1-t);
        Vec3d eye=camera.getPos();
        float px=(float)(x-eye.x),py=(float)(y+0.045-eye.y),pz=(float)(z-eye.z);
        float c=(float)Math.cos(rotation)*radius,s=(float)Math.sin(rotation)*radius;
        int ambient=getBrightness(delta);
        // A modest reflected-light floor keeps thin crests readable at dusk without fullbright.
        int light=(ambient & 0xffff0000) | Math.max(ambient & 0xffff, 12 << 4);
        vertex(out,px-c-s,py,pz+s-c,getMinU(),getMinV(),opacity,light);
        vertex(out,px-c+s,py,pz+s+c,getMinU(),getMaxV(),opacity,light);
        vertex(out,px+c+s,py,pz-s+c,getMaxU(),getMaxV(),opacity,light);
        vertex(out,px+c-s,py,pz-s-c,getMaxU(),getMinV(),opacity,light);
    }
    private void vertex(VertexConsumer v,float x,float y,float z,float u,float w,float a,int light) {
        v.vertex(x,y,z).texture(u,w).color(red,green,blue,a).light(light);
    }
    public record Factory(SpriteProvider sprites) implements ParticleFactory<SimpleParticleType> {
        public Particle createParticle(SimpleParticleType type,ClientWorld world,double x,double y,double z,double vx,double vy,double vz) {
            return new RippleParticle(world,x,y,z,sprites);
        }
    }
}
