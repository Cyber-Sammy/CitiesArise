"""Rebuild the authored residential examples without altering existing module recipes."""
from pathlib import Path
import copy
import json

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / 'examples/datapacks/zoning_fixture/data/zoning/content_catalogs/fixture.json'
PROFILE = ROOT / 'examples/datapacks/zoning_fixture/data/cities_arise/settlement_profiles/suburb.json'
data = json.loads(CATALOG.read_text(encoding='utf-8'))


def fill(a, b, material):
    return {'from': a, 'to': b, 'material': material}


def joint(name, kind, at, face, accepts=(), external=False, height=1):
    return dict(id=name, type=kind, at=at, face=face, accepts=list(accepts), external=external, height=height)


def route(name, a, b):
    return {'id': name, 'from': a, 'to': b, 'bodyHeight': 2}


# Room modules own interior arrangements; apartment shells own single shared partitions.
# Room reservations are disjoint and cannot replace the shell's walls or slabs.
for kind, furniture in {
    'living': [('minecraft:oak_stairs[facing=east]', [0, 0, 0]), ('minecraft:bookshelf', [0, 0, 4])],
    'bedroom': [('minecraft:red_bed[facing=south,part=foot]', [0, 0, 1]),
                ('minecraft:red_bed[facing=south,part=head]', [0, 0, 2]), ('minecraft:barrel', [0, 0, 4])],
    'kitchen': [('minecraft:crafting_table', [0, 0, 0]), ('minecraft:smoker', [0, 0, 1]),
                ('minecraft:barrel', [0, 0, 4])],
    'bathroom': [('minecraft:cauldron', [0, 0, 0]), ('minecraft:smooth_quartz', [0, 0, 4])],
}.items():
    data['modules']['residential:room_' + kind] = dict(
        size=[4, 3, 6], tags=['zoning:style', 'residential:room', 'residential:' + kind],
        fills=[fill([0, 0, 0], [3, 2, 5], 'minecraft:air'),
               fill([0, 0, 5 if kind in ['living', 'bedroom'] else 0],
                    [3, 2, 5 if kind in ['living', 'bedroom'] else 0], 'wall'),
               fill([1, 0, 5 if kind in ['living', 'bedroom'] else 0],
                    [1, 1, 5 if kind in ['living', 'bedroom'] else 0], 'minecraft:air')]
              + [fill(at, at, material) for material, at in furniture], joints=[],
        contract={'routes': [route('usable_room', [3, 0, 2], [1, 0, 3])]})

for side in ['left', 'right']:
    edge = 12 if side == 'left' else 0
    face = 'east' if side == 'left' else 'west'
    outer = 0 if side == 'left' else 12
    fills = [fill([0, 0, 0], [12, 4, 14], 'minecraft:air'),
             fill([0, 0, 0], [12, 0, 14], 'floor'), fill([0, 4, 0], [12, 4, 14], 'floor'),
             fill([0, 1, 0], [12, 3, 0], 'wall'), fill([0, 1, 14], [12, 3, 14], 'wall'),
             fill([0, 1, 1], [0, 3, 13], 'wall'), fill([12, 1, 1], [12, 3, 13], 'wall')]
    for x in [5, 7]:
        fills.append(fill([x, 1, 1], [x, 3, 13], 'wall'))
        for z in [3, 7, 11]:
            fills.append(fill([x, 1, z], [x, 2, z], 'minecraft:air'))
    # Cross corridor separates the four rooms and reaches the shared stair hall.
    fills.append(fill([1, 1, 7], [11, 2, 7], 'minecraft:air'))
    fills.append(fill([edge, 1, 7], [edge, 2, 7], 'minecraft:air'))
    for x in [2, 9]:
        for z in [0, 14]:
            fills.append(fill([x, 2, z], [x + 1, 2, z], 'glass'))
            fills.append(fill([x, 1, z], [x + 1, 1, z], 'minecraft:stone_bricks'))
    for z in [3, 11]:
        fills.append(fill([outer, 2, z], [outer, 2, z + 1], 'glass'))
    mounts = []
    for kind, x, z in [('living', 1, 1), ('bedroom', 8, 1), ('kitchen', 1, 8), ('bathroom', 8, 8)]:
        mounts.append(dict(id=kind, at=[x, 1, z], size=[4, 3, 6], pool=['residential:room_' + kind],
                           requiredTags=['residential:room'], optional=False, rotations=[0]))
    data['modules']['residential:apartment_' + side] = dict(size=[13, 5, 15], tags=['zoning:style', 'residential:apartment'],
        fills=fills, joints=[joint('corridor', 'residential:' + side + '_in', [13 if side == 'left' else 0, 1, 7], face,
                                 ['residential:' + side + '_out'], height=2)],
        boundaries=[dict(joint='corridor', role='wall', type='residential:wall', accepts=['residential:wall'],
                         **{'from': [edge, 0, 0], 'to': [edge, 4, 14]})],
        contract={'mounts': mounts, 'routes': [route('entry', [11 if side == 'left' else 1, 1, 7], [6, 1, 7])]
                  + [route('room_' + str(i), [6, 1, 7], [3 if x == 1 else 9, 1, z + 3])
                     for i, (x, z) in enumerate([(1, 1), (8, 1), (1, 8), (8, 8)])]})

for level in ['ground', 'upper']:
    root = 'residential:hall_' + level
    fills = [fill([0, 0, 0], [4, 4, 14], 'minecraft:air'),
             fill([0, 0, 0], [4, 0, 14], 'floor'), fill([0, 4, 0], [4, 4, 14], 'floor'),
             fill([0, 1, 0], [4, 3, 0], 'wall'), fill([0, 1, 14], [4, 3, 14], 'wall'),
             fill([0, 1, 1], [0, 3, 13], 'wall'), fill([4, 1, 1], [4, 3, 13], 'wall'),
             fill([3, 0, 3], [3, 4, 3], 'minecraft:ladder[facing=west]')]
    joints = [joint('down', 'variety:stairs', [3, 0, 3], 'down', ['variety:stairs']),
              joint('up', 'variety:stairs', [3, 5, 3], 'up', ['variety:stairs'])]
    boundaries = []
    for side, x, at, face in [('left', 0, 0, 'west'), ('right', 4, 5, 'east')]:
        fills.append(fill([x, 1, 7], [x, 2, 7], 'minecraft:air'))
        joints.append(joint(side, 'residential:' + side + '_out', [at, 1, 7], face,
                            ['residential:' + side + '_in'], height=2))
        boundaries.append(dict(joint=side, role='wall', type='residential:wall', accepts=['residential:wall'],
                               **{'from': [x, 0, 0], 'to': [x, 4, 14]}))
    if level == 'ground':
        fills.append(fill([2, 1, 0], [2, 2, 0], 'minecraft:air'))
        joints.append(joint('entrance', 'zoning:entrance', [2, 1, 0], 'north', external=True, height=2))
    data['modules'][root] = dict(size=[5, 5, 15], tags=['zoning:style'], fills=fills, joints=joints,
        boundaries=boundaries, contract={'routes': [route('stair_hall', [2, 1, 1], [2, 1, 13])]})
    floor = copy.deepcopy(data['compositions']['variety:rectangle_floor_' + level])
    apartments = ['residential:apartment_left', 'residential:apartment_right']
    floor.update(roots=[root], attachments=apartments, minimumModules=3, maximumModules=3)
    layout = floor['floorLayout']
    layout.update(footprint=['#' * 29] * 15, rooms=[root] + apartments,
                  minimumCounts={key: 1 for key in [root] + apartments}, maximumCounts={key: 1 for key in [root] + apartments})
    for port in layout['ports']:
        port['at'][0] = 14 if port['face'] == 'north' else 15
    data['compositions']['residential:floor_' + level] = floor

house = copy.deepcopy(data['compositions']['variety:rectangle_house'])
house.update(minimumModules=18, maximumModules=18, maximumHeight=25, maximumResolvedModules=128)
house['buildingSections']['sections'][0]['floor'] = 'residential:floor_ground'
house['buildingSections']['sections'][1].update(floor='residential:floor_upper', floors=5)
data['compositions']['residential:apartment_block'] = house
data['assets']['residential:apartment_block'] = dict(provider='modules', minimumSize=29, maximumSize=64, height=25,
                                                    parameters={'composition': 'residential:apartment_block'})
data['palettes']['residential:brick'] = dict(data['palettes']['zoning:warm'], wall='minecraft:bricks', floor='minecraft:stone_bricks')
CATALOG.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')

profile = json.loads(PROFILE.read_text(encoding='utf-8'))
profile['survey'] = dict(width=240, depth=240)
p = profile['planning']
p['districts']['parcelSizes'] = [s for s in p['districts']['parcelSizes'] if s['id'] != 'residential:large']
p['districts']['parcelSizes'].append(dict(id='residential:large', width=37, depth=37, buildingMargin=4))
p['districts']['parcelSizeFallback'] = True
p.update(maxElevationRange=14, preferredMaxCutDepth=4, preferredMaxFillDepth=4, maxCutDepth=10,
         maxFillDepth=12, maxBuildingFoundationDepth=10, maxEarthworkVolume=120000)
rules = p['buildings']['rules']
rules[:] = [r for r in rules if r['id'] != 'residential:large']
rules.insert(0, dict(id='residential:large', match={'properties': {'parcel_size': 'residential:large'}},
                     pool=[dict(asset='residential:apartment_block', weight=1)], palettes=['residential:brick'], fallback='zoning:empty'))
PROFILE.write_text(json.dumps(profile, indent=2) + '\n', encoding='utf-8')
