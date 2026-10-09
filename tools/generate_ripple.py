"""Original analytic ripple artwork. Run to regenerate the packaged RGBA texture."""
from pathlib import Path
import math
from PIL import Image

size = 512
im = Image.new('RGBA', (size, size))
pixels = im.load()
for y in range(size):
    for x in range(size):
        dx, dz = (x+.5-size/2)/(size/2), (y+.5-size/2)/(size/2)
        r, a = math.hypot(dx,dz), math.atan2(dz,dx)
        warp = .0035*math.sin(a*7)+.002*math.sin(a*19+1.2)
        shimmer = .70+.18*math.sin(a*5+.4)+.09*math.sin(a*13)
        crest = math.exp(-((r-.80-warp)/.022)**2)*shimmer
        secondary = math.exp(-((r-.68-warp*.7)/.012)**2)*.48
        trough = math.exp(-((r-.777-warp)/.008)**2)*.30
        glow = math.exp(-((r-.80-warp)/.030)**2)*.07
        alpha = min(1, crest+secondary+trough+glow)
        shade = int(255*(1-.40*trough/max(alpha,.001)))
        pixels[x,y] = (shade,shade,shade,round(alpha*255))
root = Path(__file__).resolve().parents[1]
out = root/'src/main/resources/assets/waterwalk/textures/particle/ripple.png'
out.parent.mkdir(parents=True, exist_ok=True)
# Supersample then downsample: keep the particle atlas below Complementary's 900px
# particle-material detection threshold while retaining smooth narrow crests.
im.resize((256,256),getattr(Image,'Resampling',Image).LANCZOS).save(out)
preview = Image.new('RGBA',(768,768),(66,61,73,255))
preview.alpha_composite(im.resize((700,700)),(34,34))
preview.convert('RGB').save(root/'ripple-texture-preview.png')
print(out)
