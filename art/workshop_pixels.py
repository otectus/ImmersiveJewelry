"""Native 16px workshop sprites, refined from sources/workshop-16px-atlas.png.

These grids are the production artwork; no export-time downsampling is used.
Short rows have transparent padding on the right. Dots are transparent.
"""
from PIL import Image

STEEL = {'o': '#18212d', '0': '#18212d', '1': '#435262', '2': '#7f96a3', '3': '#bacecf', '4': '#f2f5ed',
         'b': '#a26b20', 'B': '#e0ab39', 'G': '#fff29b'}
BROWN = {'h': '#49251e', 'H': '#944729', 'L': '#d9824c'}
GREEN = {'h': '#123527', 'H': '#2d6845', 'L': '#70a877'}

SPRITES = {
    'jewelers_pliers': (STEEL | BROWN, [
        '................',
        '..........330',
        '.........3440',
        '........34420',
        '........3420',
        '........320..30',
        '.......122..340',
        '......12BG23420',
        '.....LHGBb2220',
        '....LHHhoo120',
        '...LHHho.LHho',
        '..LHHho..HHho',
        '..HHho..LHho',
        '..hho...Hho',
        '........ho',
        '................',
    ]),
    'fine_pliers': (STEEL | GREEN, [
        '................',
        '............4',
        '...........42',
        '..........42',
        '.........42...4',
        '........42...42',
        '.......32...42',
        '......12BG.32',
        '.....LHGBb220',
        '....LHHhoo120',
        '...LHHho.GBho',
        '..LHHho..LHho',
        '..HHho..LHho',
        '..hho...Hho',
        '........ho',
        '................',
    ]),
    'emery_lap': ({'o': '#302d2b', '1': '#595248', '2': '#817668', '3': '#aea08a',
                   '4': '#d6c8ac', '5': '#fff0cf'}, [
        '................',
        '.....111111',
        '...124444321',
        '..12544333421',
        '..25432222331',
        '.1254323322331',
        '.1543211112331',
        '.144321oo12331',
        '.143221oo12321',
        '.1332221122331',
        '.1232322233211',
        '..13233343211',
        '..12333332111',
        '...112222111',
        '.....11111',
        '................',
    ]),
    'diamond_lap': ({'o': '#122733', '1': '#28495d', '2': '#49758c', '3': '#72a6b9',
                     '4': '#ace0df', '5': '#f2fff1'}, [
        '................',
        '.....111111',
        '...124444321',
        '..12544333421',
        '..25433323531',
        '.1254333334331',
        '.1543324423331',
        '.144332oo23331',
        '.143332oo23321',
        '.1332332233531',
        '.1232533334311',
        '..13243333211',
        '..12333332111',
        '...112222111',
        '.....11111',
        '................',
    ]),
    'polishing_compound': (STEEL | {'r': '#612921', 'R': '#aa4224', 's': '#d66435', 'S': '#ffac65'}, [
        '................',
        '......oooooo',
        '.....o344443o',
        '....o421112230',
        '.....o211112230',
        '......o2222221o',
        '...oooooo1111o',
        '..o344333oooo',
        '.o3rRSSssrr330',
        '.o4RSSsRRRrr21o',
        '.o4rssRRRRrr21o',
        '..o3443333321o',
        '..o1244443211o',
        '...o12222211o',
        '....oooooooo',
        '................',
    ]),
    'gem_dust': ({'o': '#392a45', '1': '#65506f', '2': '#967b9e', '3': '#c9afd0', '4': '#f3e2ea'}, [
        '................',
        '................',
        '.......o',
        '......o31',
        '......342o',
        '.....o4311',
        '.....34221',
        '....o342112o',
        '...o24311331',
        '...3432214321',
        '..o43211143211',
        '..342222132211o',
        '.o221133211111',
        '.31.oooo1ooo.31',
        '.o...........o',
        '................',
    ]),
    'design_sketch': ({'o': '#6b4930', 'p': '#ad8150', 'P': '#d4b37b', 'q': '#ebd19b',
                      'Q': '#fff0c5', 'i': '#49463e', 'r': '#8e2c2a', 'R': '#cf5843', 's': '#ffd5a1'}, [
        '................',
        '...pppppppp',
        '..pQQQQQQQQo',
        '..pQQqqqqQQQpo',
        '..pQqqqqqqQpQpo',
        '..pQqqqrqqQPPpo',
        '..pQqqRsRqppppo',
        '..pQqiRrrRiqqpo',
        '..pQiqrRrqqiqpo',
        '..pQiqqqqqqiqpo',
        '..pQqiqqqqiqqpo',
        '..pQqqiiiiqqqpo',
        '.pQQqqqqqqqqPpo',
        '.pPqqqqqPPPPpo',
        '..oppppppoooo',
        '................',
    ]),
    'jewelers_handbook': ({'o': '#20272b', 'h': '#563223', 'H': '#97603d', 'f': '#153a2e',
                          'F': '#2d5740', 'v': '#5d8260', 'b': '#936321', 'B': '#d8a83d',
                          'G': '#fff0a4', 'p': '#ac8d62', 'P': '#e5d2a2',
                          'm': '#62b5a3', 'M': '#d8f6d6', 'r': '#953747'}, [
        '................',
        '...oooooooooo',
        '..oHHFvvvvFBBo',
        '..oHGfFFFFfBGPo',
        '..oHhfFFGFFfPPo',
        '..oHhfFBMBFfPpo',
        '..oHBfGMmGBfBGo',
        '..oHhfFBmBFfGbo',
        '..oHhfFFBFFfPpo',
        '..oHGfFFFFFfPpo',
        '..oHhfFFFFFfPpo',
        '..oHhfFFFFfBPPo',
        '..oHHFffffBGPo',
        '...oPPrPPPPPo',
        '....oorooooo',
        '......r',
    ]),
}
def sprite(name):
    palette, rows = SPRITES[name]
    assert len(rows) == 16 and all(len(row) <= 16 for row in rows), name
    image = Image.new('RGBA', (16, 16))
    pixels = image.load()
    for y, row in enumerate(rows):
        for x, key in enumerate(row):
            if key != '.':
                value = palette[key].lstrip('#')
                pixels[x, y] = tuple(int(value[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
    return image


def atlas():
    image = Image.new('RGBA', (64, 32))
    for i, name in enumerate(SPRITES):
        image.alpha_composite(sprite(name), (i % 4 * 16, i // 4 * 16))
    return image
