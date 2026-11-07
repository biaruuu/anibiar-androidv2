"""Generates Kiro's own chat bubble decorations and stickers (transparent PNGs). Pure Pillow, no outside art."""
import math, sys, os
from PIL import Image, ImageDraw, ImageFont

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
S = 4  # supersample

def canvas(w, h):
    return Image.new("RGBA", (w * S, h * S), (0, 0, 0, 0))

def save(im, name, w, h):
    im.resize((w, h), Image.LANCZOS).save(os.path.join(OUT, name + ".png"), optimize=True)

def P(*xs):
    return [x * S for x in xs]

def star_pts(cx, cy, r, ri=None, n=5, rot=-90):
    ri = ri or r * 0.45
    pts = []
    for i in range(n * 2):
        a = math.radians(rot + i * 180 / n)
        rr = r if i % 2 == 0 else ri
        pts.append(((cx + rr * math.cos(a)) * S, (cy + rr * math.sin(a)) * S))
    return pts

def heart(d, cx, cy, s, fill, outline=None):
    pts = []
    for t in range(0, 360, 4):
        a = math.radians(t)
        x = 16 * math.sin(a) ** 3
        y = -(13 * math.cos(a) - 5 * math.cos(2 * a) - 2 * math.cos(3 * a) - math.cos(4 * a))
        pts.append(((cx + x * s) * S, (cy + y * s) * S))
    d.polygon(pts, fill=fill, outline=outline)

# ---------------------------------------------------------------- bubble decorations
def deco_sakura():
    im = canvas(120, 120); d = ImageDraw.Draw(im)
    cx, cy = 60, 60
    for i in range(5):
        a = math.radians(-90 + i * 72)
        px, py = cx + 26 * math.cos(a), cy + 26 * math.sin(a)
        d.ellipse(P(px - 22, py - 22, px + 22, py + 22), fill=(255, 183, 213, 255), outline=(232, 120, 170, 255), width=3 * S)
    d.ellipse(P(cx - 13, cy - 13, cx + 13, cy + 13), fill=(255, 226, 120, 255), outline=(225, 170, 60, 255), width=2 * S)
    for i in range(5):
        a = math.radians(-90 + i * 72 + 36)
        d.ellipse(P(cx + 6 * math.cos(a) - 2, cy + 6 * math.sin(a) - 2, cx + 6 * math.cos(a) + 2, cy + 6 * math.sin(a) + 2), fill=(214, 120, 60, 255))
    save(im, "bub_sakura", 120, 120)

def deco_frog():
    im = canvas(160, 90); d = ImageDraw.Draw(im)
    for cx in (42, 118):
        d.ellipse(P(cx - 34, 8, cx + 34, 76), fill=(30, 168, 100, 255), outline=(18, 110, 66, 255), width=4 * S)
        d.ellipse(P(cx - 22, 20, cx + 22, 64), fill=(255, 255, 255, 255), outline=(18, 110, 66, 255), width=3 * S)
        d.ellipse(P(cx - 10, 30, cx + 12, 58), fill=(20, 24, 30, 255))
        d.ellipse(P(cx - 4, 34, cx + 4, 42), fill=(255, 255, 255, 255))
    save(im, "bub_frog", 160, 90)

def deco_cat():
    im = canvas(150, 80); d = ImageDraw.Draw(im)
    for base, flip in ((12, 1), (138, -1)):
        pts = [(base, 76), (base + 34 * flip, 4), (base + 66 * flip, 76)]
        d.polygon([(x * S, y * S) for x, y in pts], fill=(255, 214, 150, 255), outline=(170, 110, 50, 255))
        d.line([(x * S, y * S) for x, y in pts + [pts[0]]], fill=(170, 110, 50, 255), width=4 * S, joint="curve")
        inner = [(base + 12 * flip, 70), (base + 34 * flip, 26), (base + 54 * flip, 70)]
        d.polygon([(x * S, y * S) for x, y in inner], fill=(255, 168, 190, 255))
    save(im, "bub_cat", 150, 80)

def deco_duck():
    im = canvas(120, 120); d = ImageDraw.Draw(im)
    d.ellipse(P(14, 62, 100, 112), fill=(255, 222, 70, 255), outline=(214, 160, 20, 255), width=4 * S)
    d.ellipse(P(46, 22, 94, 70), fill=(255, 222, 70, 255), outline=(214, 160, 20, 255), width=4 * S)
    d.ellipse(P(30, 70, 62, 96), fill=(255, 236, 130, 255), outline=(214, 160, 20, 255), width=3 * S)
    d.polygon([(90 * S, 42 * S), (112 * S, 49 * S), (90 * S, 57 * S)], fill=(255, 140, 40, 255), outline=(200, 90, 20, 255))
    d.ellipse(P(66, 36, 76, 46), fill=(30, 30, 36, 255))
    d.ellipse(P(69, 38, 73, 42), fill=(255, 255, 255, 255))
    save(im, "bub_duck", 120, 120)

def deco_stars():
    im = canvas(150, 70); d = ImageDraw.Draw(im)
    for cx, cy, r in ((26, 40, 20), (76, 26, 26), (124, 44, 16)):
        d.polygon(star_pts(cx, cy, r), fill=(255, 224, 90, 255), outline=(230, 170, 30, 255))
    save(im, "bub_stars", 150, 70)

def deco_bubbles():
    im = canvas(150, 90); d = ImageDraw.Draw(im)
    for cx, cy, r in ((30, 54, 22), (74, 30, 28), (118, 56, 16), (100, 20, 9)):
        d.ellipse(P(cx - r, cy - r, cx + r, cy + r), fill=(200, 240, 255, 150), outline=(255, 255, 255, 230), width=3 * S)
        d.arc(P(cx - r * .6, cy - r * .6, cx + r * .6, cy + r * .6), 200, 260, fill=(255, 255, 255, 255), width=3 * S)
    save(im, "bub_bubbles", 150, 90)

def deco_hearts():
    im = canvas(140, 80); d = ImageDraw.Draw(im)
    heart(d, 34, 44, 1.4, (255, 90, 120, 255), (200, 40, 80, 255))
    heart(d, 86, 30, 1.9, (255, 120, 150, 255), (200, 40, 80, 255))
    heart(d, 120, 54, 1.0, (255, 160, 180, 255), (200, 40, 80, 255))
    save(im, "bub_hearts", 140, 80)

def deco_cloud():
    im = canvas(150, 80); d = ImageDraw.Draw(im)
    blobs = [(34, 52, 24), (66, 36, 30), (100, 46, 26), (124, 56, 18)]
    for cx, cy, r in blobs:
        d.ellipse(P(cx - r, cy - r, cx + r, cy + r), fill=(255, 255, 255, 255), outline=(170, 196, 230, 255), width=4 * S)
    for cx, cy, r in blobs:
        d.ellipse(P(cx - r + 4, cy - r + 4, cx + r - 4, cy + r - 4), fill=(255, 255, 255, 255))
    d.rectangle(P(20, 56, 134, 76), fill=(255, 255, 255, 0))
    save(im, "bub_cloud", 150, 80)

# ---------------------------------------------------------------- stickers
YEL = (255, 211, 64, 255); YEL_O = (214, 150, 20, 255); INK = (40, 30, 30, 255)

def face(name, mouth, eyes="dots", extra=None, fill=YEL):
    im = canvas(256, 256); d = ImageDraw.Draw(im)
    d.ellipse(P(16, 16, 240, 240), fill=fill, outline=YEL_O, width=8 * S)
    if eyes == "dots":
        for cx in (88, 168): d.ellipse(P(cx - 14, 92, cx + 14, 132), fill=INK)
    elif eyes == "happy":
        for cx in (88, 168): d.arc(P(cx - 24, 92, cx + 24, 140), 200, 340, fill=INK, width=9 * S)
    elif eyes == "closed":
        for cx in (88, 168): d.arc(P(cx - 24, 96, cx + 24, 136), 20, 160, fill=INK, width=9 * S)
    elif eyes == "hearts":
        for cx in (88, 168): heart(d, cx, 116, 2.2, (235, 50, 90, 255))
    elif eyes == "wide":
        for cx in (88, 168):
            d.ellipse(P(cx - 24, 84, cx + 24, 140), fill=(255, 255, 255, 255), outline=INK, width=5 * S)
            d.ellipse(P(cx - 9, 102, cx + 9, 124), fill=INK)
    elif eyes == "angry":
        for cx, sgn in ((88, 1), (168, -1)):
            d.ellipse(P(cx - 12, 106, cx + 12, 136), fill=INK)
            d.line([(cx - 28 * sgn) * S, 84 * S, (cx + 24 * sgn) * S, 100 * S], fill=INK, width=9 * S)
    mouth(d)
    if extra: extra(d)
    save(im, name, 256, 256)

def m_smile(d): d.arc(P(70, 110, 186, 200), 20, 160, fill=INK, width=10 * S)
def m_laugh(d):
    d.chord(P(64, 120, 192, 214), 0, 180, fill=INK)
    d.chord(P(88, 170, 168, 214), 0, 180, fill=(235, 90, 110, 255))
def m_cry(d): d.arc(P(80, 150, 176, 210), 200, 340, fill=INK, width=10 * S)
def m_o(d): d.ellipse(P(108, 158, 148, 206), fill=INK)
def m_flat(d): d.line([84 * S, 176 * S, 172 * S, 176 * S], fill=INK, width=10 * S)
def m_grr(d): d.arc(P(78, 160, 178, 216), 200, 340, fill=INK, width=10 * S)
def tears(d):
    for cx in (80, 176):
        d.polygon([(cx * S, 128 * S), ((cx - 12) * S, 160 * S), ((cx + 12) * S, 160 * S)], fill=(90, 170, 255, 255))
        d.ellipse(P(cx - 12, 150, cx + 12, 176), fill=(90, 170, 255, 255))
def shades(d):
    d.rounded_rectangle(P(52, 84, 120, 128), 12 * S, fill=INK)
    d.rounded_rectangle(P(136, 84, 204, 128), 12 * S, fill=INK)
    d.line([120 * S, 98 * S, 136 * S, 98 * S], fill=INK, width=7 * S)
    d.line([62 * S, 94 * S, 80 * S, 94 * S], fill=(255, 255, 255, 160), width=4 * S)
def zzz(d):
    try: f = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 46 * S)
    except Exception: f = ImageFont.load_default()
    d.text((176 * S, 20 * S), "z", fill=(70, 110, 220, 255), font=f)
    d.text((200 * S, 0), "z", fill=(70, 110, 220, 255), font=f)
def blush(d):
    for cx in (62, 194): d.ellipse(P(cx - 18, 150, cx + 18, 172), fill=(255, 140, 150, 140))

def sticker_like():
    im = canvas(256, 256); d = ImageDraw.Draw(im)
    skin = (255, 211, 64, 255)
    d.rounded_rectangle(P(24, 112, 76, 228), 10 * S, fill=(70, 130, 255, 255), outline=(40, 80, 190, 255), width=6 * S)
    d.rounded_rectangle(P(70, 112, 214, 232), 26 * S, fill=skin, outline=YEL_O, width=7 * S)
    d.rounded_rectangle(P(92, 54, 142, 128), 24 * S, fill=skin, outline=YEL_O, width=7 * S)
    for y in (160, 188): d.line([100 * S, y * S, 196 * S, y * S], fill=YEL_O, width=5 * S)
    save(im, "stk_like", 256, 256)

def sticker_heart():
    im = canvas(256, 256); d = ImageDraw.Draw(im)
    heart(d, 128, 120, 6.4, (255, 70, 110, 255), (190, 30, 70, 255))
    d.ellipse(P(60, 62, 92, 92), fill=(255, 255, 255, 140))
    save(im, "stk_heart", 256, 256)

def sticker_star():
    im = canvas(256, 256); d = ImageDraw.Draw(im)
    d.polygon(star_pts(128, 132, 112, 50), fill=(255, 214, 60, 255), outline=(220, 150, 20, 255))
    d.line([(x * S, y * S) for x, y in [(128, 132), (240, 134)]], fill=(0, 0, 0, 0), width=1)
    save(im, "stk_star", 256, 256)

def sticker_fire():
    im = canvas(256, 256); d = ImageDraw.Draw(im)
    outer = [(128, 14), (176, 84), (212, 120), (222, 166), (200, 218), (128, 244), (56, 218), (34, 166), (52, 116), (84, 88), (100, 50)]
    d.polygon([(x * S, y * S) for x, y in outer], fill=(255, 120, 30, 255), outline=(200, 60, 10, 255))
    inner = [(128, 90), (164, 140), (170, 188), (128, 224), (86, 188), (92, 140)]
    d.polygon([(x * S, y * S) for x, y in inner], fill=(255, 208, 60, 255))
    save(im, "stk_fire", 256, 256)

def text_sticker(name, word, colors, w=256, h=256, size=120, rot=-8):
    im = canvas(w, h); d = ImageDraw.Draw(im)
    try: f = ImageFont.truetype("/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf", size * S)
    except Exception: f = ImageFont.load_default()
    tmp = Image.new("RGBA", (w * S, h * S), (0, 0, 0, 0)); td = ImageDraw.Draw(tmp)
    bb = td.textbbox((0, 0), word, font=f)
    x = (w * S - (bb[2] - bb[0])) // 2 - bb[0]; y = (h * S - (bb[3] - bb[1])) // 2 - bb[1]
    for dx in range(-8, 9, 4):
        for dy in range(-8, 9, 4):
            td.text((x + dx * S // 2, y + dy * S // 2), word, font=f, fill=colors[1])
    td.text((x, y), word, font=f, fill=colors[0])
    tmp = tmp.rotate(rot, resample=Image.BICUBIC, expand=False)
    save(tmp, name, w, h)

def sticker_cat():
    im = canvas(256, 256); d = ImageDraw.Draw(im)
    fur = (255, 196, 120, 255); line = (150, 90, 40, 255)
    for base, f in ((42, 1), (214, -1)):
        pts = [(base, 100), (base + 18 * f, 22), (base + 70 * f, 66)]
        d.polygon([(x * S, y * S) for x, y in pts], fill=fur, outline=line)
        d.polygon([(x * S, y * S) for x, y in [(base + 14 * f, 90), (base + 22 * f, 44), (base + 52 * f, 68)]], fill=(255, 170, 190, 255))
    d.ellipse(P(26, 56, 230, 236), fill=fur, outline=line, width=7 * S)
    for cx in (92, 164): d.ellipse(P(cx - 12, 120, cx + 12, 156), fill=INK)
    d.polygon([(118 * S, 158 * S), (138 * S, 158 * S), (128 * S, 170 * S)], fill=(240, 110, 130, 255))
    d.arc(P(98, 164, 128, 190), 10, 170, fill=INK, width=5 * S); d.arc(P(128, 164, 158, 190), 10, 170, fill=INK, width=5 * S)
    for sgn in (-1, 1):
        for dy in (-8, 8):
            d.line([(128 + sgn * 44) * S, (172 + dy) * S, (128 + sgn * 100) * S, (172 + dy * 2) * S], fill=line, width=4 * S)
    save(im, "stk_cat", 256, 256)

def sticker_ghost():
    im = canvas(256, 256); d = ImageDraw.Draw(im)
    body = [(40, 230), (40, 112)]
    d.ellipse(P(40, 22, 216, 198), fill=(250, 250, 255, 255), outline=(150, 150, 200, 255), width=7 * S)
    d.rectangle(P(40, 110, 216, 220), fill=(250, 250, 255, 255))
    d.line([40 * S, 110 * S, 40 * S, 226 * S], fill=(150, 150, 200, 255), width=7 * S)
    d.line([216 * S, 110 * S, 216 * S, 226 * S], fill=(150, 150, 200, 255), width=7 * S)
    for i, x in enumerate(range(40, 216, 44)):
        d.pieslice(P(x, 200, x + 44, 246), 0, 180, fill=(250, 250, 255, 255), outline=(150, 150, 200, 255), width=6 * S)
    for cx in (96, 160): d.ellipse(P(cx - 11, 92, cx + 11, 128), fill=INK)
    d.ellipse(P(112, 140, 144, 172), fill=INK)
    save(im, "stk_ghost", 256, 256)

for fn in (deco_sakura, deco_frog, deco_cat, deco_duck, deco_stars, deco_bubbles, deco_hearts, deco_cloud):
    fn()
face("stk_smile", m_smile, "dots", blush)
face("stk_laugh", m_laugh, "happy")
face("stk_cry", m_cry, "dots", tears)
face("stk_love", m_smile, "hearts")
face("stk_cool", m_smile, "dots", shades)
face("stk_angry", m_grr, "angry", None, (255, 120, 80, 255))
face("stk_sleep", m_flat, "closed", zzz)
face("stk_wow", m_o, "wide")
sticker_like(); sticker_heart(); sticker_star(); sticker_fire(); sticker_cat(); sticker_ghost()
text_sticker("stk_yes", "Yes!", ((255, 150, 30, 255), (120, 50, 0, 255)), 256, 256, 110)
text_sticker("stk_no", "NO", ((255, 70, 90, 255), (110, 10, 30, 255)), 256, 256, 120)
text_sticker("stk_ok", "OK", ((80, 200, 120, 255), (10, 80, 40, 255)), 256, 256, 120, 6)
text_sticker("stk_lol", "LOL", ((90, 160, 255, 255), (10, 40, 110, 255)), 256, 256, 96, -6)
print("done")
