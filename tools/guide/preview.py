import sys
from PIL import Image
def on_paper(path, out, scale=1):
    im = Image.open(path).convert('RGBA')
    bg = Image.new('RGBA', im.size, (234, 227, 208, 255))
    bg = Image.alpha_composite(bg, im)
    if scale != 1:
        bg = bg.resize((int(im.width*scale), int(im.height*scale)), Image.LANCZOS)
    bg.save(out)
if __name__ == '__main__':
    on_paper(sys.argv[1], sys.argv[2], float(sys.argv[3]) if len(sys.argv) > 3 else 1)
