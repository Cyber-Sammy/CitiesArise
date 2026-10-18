"""Generate a standalone, vanilla-material skyline pack and its installable ZIP."""
import copy
import json
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'examples/datapacks/skyline'
SOURCE = ROOT / 'examples/datapacks/zoning_fixture/data'
old = json.loads((SOURCE / 'zoning/content_catalogs/fixture.json').read_text(encoding='utf-8'))
catalog = dict(version=1, modules={}, compositions={}, assets={}, palettes={},
               surfaces=copy.deepcopy(old['surfaces']), materialRules=copy.deepcopy(old['materialRules']))
catalog['surfaceTemplates'] = {'PARCEL_YARD': dict(size=[1, 1, 1], cells=[dict(at=[0, 0, 0], material='minecraft:smooth_stone')])}


def fill(a, b, material):
    return {'from': a, 'to': b, 'material': material}


def joint(name, at, face, external=False):
    return dict(id=name, type='skyline:entry' if external else 'skyline:stairs',
                accepts=['skyline:entrance'] if external else ['skyline:stairs'], at=at, face=face,
                external=external, height=2 if external else 1)


def route(name, a, b):
    return dict(id=name, **{'from': a, 'to': b}, bodyHeight=2)


def floor(w, d, ground=False):
    """One authored floor shell, four large rooms, crossed halls and backed circulation."""
    module = f'skyline:floor_{w}_{d}'
    name = module + ('_ground' if ground else '_upper')
    if name in catalog['compositions']:
        return name
    cx, cz = w // 2, d // 2
    fs = [fill([0, 0, 0], [w-1, 0, d-1], 'floor'),
          fill([0, 4, 0], [w-1, 4, d-1], 'floor')]
    # Recessed two-block windows, projecting piers, and continuous cornice bands.
    for z in [1, d-2]:
        fs += [fill([1, 1, z], [w-2, 3, z], 'wall'), fill([0, 3, z-1 if z == 1 else z+1], [w-1, 3, z-1 if z == 1 else z+1], 'trim')]
        for x in range(3, w-3, 4):
            fs.append(fill([x, 1, z], [x+1, 2, z], 'glass'))
        for x in range(1, w-1, 4):
            fs.append(fill([x, 1, z-1 if z == 1 else z+1], [x, 3, z-1 if z == 1 else z+1], 'trim'))
    for x in [1, w-2]:
        fs += [fill([x, 1, 1], [x, 3, d-2], 'wall'), fill([x-1 if x == 1 else x+1, 3, 0], [x-1 if x == 1 else x+1, 3, d-1], 'trim')]
        for z in range(3, d-3, 4):
            fs.append(fill([x, 1, z], [x, 2, z+1], 'glass'))
        for z in range(1, d-1, 4):
            fs.append(fill([x-1 if x == 1 else x+1, 1, z], [x-1 if x == 1 else x+1, 3, z], 'trim'))
    # Single-block room partitions; three-block central corridors remain unobstructed.
    for x in [cx-2, cx+2]:
        fs.append(fill([x, 1, 2], [x, 3, d-3], 'wall'))
        for z in [cz-4, cz+4]:
            fs.append(fill([x, 1, z], [x, 2, z+1], 'minecraft:air'))
    for z in [cz-2, cz+2]:
        for a, b in [(2, cx-3), (cx+3, w-3)]:
            fs.append(fill([a, 1, z], [b, 3, z], 'wall'))
            fs.append(fill([(a+b)//2, 1, z], [(a+b)//2+1, 2, z], 'minecraft:air'))
    fs.append(fill([2, 1, cz-1], [w-3, 2, cz+1], 'minecraft:air'))
    fs.append(fill([cx, 1, 2], [cx, 2, d-3], 'minecraft:air'))
    # One explicit bypass around the backed ladder connects the rear corridor.
    fs.append(fill([cx+1, 1, cz+2], [cx+1, 2, cz+2], 'minecraft:air'))
    # Support behind every ladder segment, including the shared floor/ceiling slab.
    fs += [fill([cx, 0, cz+1], [cx, 4, cz+1], 'wall'),
           fill([cx-1, 0, cz+1], [cx-1, 4, cz+1], 'minecraft:ladder[facing=west]')]
    # Furnishing stays away from the hall, room doors and arrival landing.
    for x, z, material in [(3, 3, 'minecraft:bookshelf'), (w-4, 3, 'minecraft:crafting_table'),
                           (3, d-4, 'minecraft:barrel'), (w-4, d-4, 'minecraft:smoker')]:
        fs.append(fill([x, 1, z], [x, 1, z], material))
    for x in [cx-5, cx+5]:
        for z in [cz-4, cz+4]:
            fs.append(fill([x, 3, z], [x, 3, z], 'minecraft:sea_lantern'))
    js = [joint('down', [cx-1, 0, cz+1], 'down'), joint('up', [cx-1, 5, cz+1], 'up')]
    fs.append(fill([cx, 1, 0], [cx, 2, 1], 'minecraft:air'))
    js.append(joint('entry', [cx, 1, 0], 'north', True))
    # Only the facade socket belongs to the floor. The complete entrance is a separate module.
    fs.append(fill([cx, 1, d-2], [cx, 2, d-1], 'minecraft:air'))
    js.append(joint('service', [cx, 1, d], 'south', True))
    facade = dict(id='upper_facade', type='skyline:upper_facade', at=[cx, 1, 0], face='north', external=True)
    catalog['modules'][module] = dict(size=[w, 5, d], tags=['skyline:building'], fills=fs, joints=js,
        layouts=[dict(id='lobby', weight=1), dict(id='upper', weight=1, closedJoints=['entry', 'service'],
            addedJoints=[facade], fills=[fill([cx, 1, 1], [cx, 2, 1], 'wall'), fill([cx, 1, d-2], [cx, 2, d-1], 'wall')])],
        contract={'routes': [route('lobby', [cx, 1, 2], [cx, 1, cz]),
                             route('landing', [cx, 1, cz], [cx-2, 1, cz+1])]})
    ports = [dict(id=j['id'], type=j['type'], at=j['at'], face=j['face'], height=j['height'], walkable=True) for j in js if ground or j['id'] not in ['entry', 'service']]
    if not ground:
        ports.append(dict(id='upper_facade', type=facade['type'], at=facade['at'], face='north', walkable=False))
    catalog['compositions'][name] = dict(roots=[module], attachments=[], entranceJoint='entry' if ground else 'down',
        requiredTags=['skyline:building'], minimumModules=1, maximumModules=1, maximumHeight=5,
        floorLayout=dict(height=5, footprint=['#'*w]*d, rooms=[module], rotations=[0], ports=ports,
                         entrance='entry' if ground else 'down', minimumCounts={module: 1}, maximumCounts={module: 1}))
    return name


def roof(w, d, crown):
    name = f'skyline:roof_{w}_{d}_{crown}'
    if name in catalog['modules']:
        return name
    cx, cz = w//2, d//2
    # The top floor already owns its deck. Only the stair-head patch overlaps it.
    fs = [fill([cx-2, 0, cz], [cx, 0, cz+2], 'floor'),
          fill([cx-2, 1, cz+1], [cx-1, 2, cz+1], 'minecraft:air')]
    for z in [0, d-1]:
        fs.append(fill([0, 1, z], [w-1, 1, z], 'trim'))
    for x in [0, w-1]:
        fs.append(fill([x, 1, 0], [x, 1, d-1], 'trim'))
    if crown:
        for x in [1, w-2]:
            for z in [1, d-2]:
                fs.append(fill([x, 1, z], [x, 3, z], 'trim'))
        fs.append(fill([cx, 1, cz-3], [cx, 4, cz-3], 'accent'))
    fs += [fill([cx, 0, cz+1], [cx, 2, cz+1], 'wall'),
           fill([cx-1, 0, cz+1], [cx-1, 1, cz+1], 'minecraft:ladder[facing=west]')]
    catalog['modules'][name] = dict(size=[w, 5, d], tags=['skyline:building'], fills=fs,
        joints=[joint('down', [cx-1, 0, cz+1], 'down')],
        boundaries=[dict(joint='down', role='floor', type='skyline:slab', accepts=['skyline:slab'], priority=10,
                         **{'from': [cx-2, 0, cz], 'to': [cx, 0, cz+2]})],
        contract={'routes': [route('roof_access', [cx-1, 1, cz+1], [cx-2, 1, cz+1])]})
    return name


BUILDINGS = [
    ('crown', [(29, 25, 12), (17, 17, 4)], True),
    ('metropolitan', [(29, 25, 8), (17, 17, 4)], True),
    ('regent', [(29, 25, 14)], False),
    ('terrace', [(29, 25, 6), (17, 17, 4)], False),
    ('townhouse', [(17, 17, 2)], False),
    ('pavilion', [(29, 25, 1)], False),
]
for name, tiers, crown in BUILDINGS:
    sections = []
    for index, (w, d, count) in enumerate(tiers):
        for ground, floors in [(True, 1), (False, count-1)] if index == 0 else [(False, count)]:
            if not floors:
                continue
            slab = dict(type='skyline:slab', accepts=['skyline:slab'])
            sections.append(dict(id=f'tier_{index}_'+('lobby' if ground else 'repeat'), floors=floors,
                floor=floor(w, d, ground), down='down', up='up',
                floorBoundary=dict(slab, priority=10), ceilingBoundary=dict(slab, priority=0)))
    count = sum(t[2] for t in tiers)
    height = count*4 + 5
    identifier = 'skyline:'+name
    catalog['compositions'][identifier] = dict(minimumModules=count+1, maximumModules=count+1, maximumHeight=height,
        buildingSections=dict(bottomCap='minecraft:stone_bricks', topCap='minecraft:stone_bricks', sections=sections,
                              roof=roof(*tiers[-1][:2], crown)))
    catalog['assets'][identifier] = dict(provider='modules', minimumSize=max(tiers[0][:2]), maximumSize=64, height=height,
                                        parameters={'composition': identifier, 'entrances': 'skyline:residential'})

for name, wall, trim, glass, accent in [
    ('limestone', 'smooth_sandstone', 'cut_sandstone', 'light_blue_stained_glass', 'gold_block'),
    ('brick', 'bricks', 'polished_andesite', 'gray_stained_glass', 'copper_block'),
    ('ivory', 'smooth_quartz', 'quartz_pillar', 'light_gray_stained_glass', 'gold_block'),
    ('slate', 'polished_deepslate', 'deepslate_bricks', 'cyan_stained_glass', 'oxidized_copper')]:
    catalog['palettes']['skyline:'+name] = {k: 'minecraft:'+v for k, v in dict(wall=wall, trim=trim, glass=glass, accent=accent, floor='stone_bricks').items()}
    catalog['palettes']['skyline:'+name].update(door_lower='minecraft:oak_door[facing=north,half=lower,hinge=left]',
                                              door_upper='minecraft:oak_door[facing=north,half=upper,hinge=left]')

catalog['materialRules']['operable'] = ['door_lower', 'door_upper']
for name, depth in [('portal', 2), ('canopy', 3)]:
    catalog['modules']['skyline:entrance_'+name] = dict(size=[5, 4, depth], tags=['skyline:entrance'],
        fills=[fill([0, 0, 0], [4, 0, depth-1], 'floor'), fill([0, 1, 0], [4, 2, depth-1], 'minecraft:air'),
               fill([0, 1, 0], [0, 2, depth-1], 'trim'), fill([4, 1, 0], [4, 2, depth-1], 'trim'),
               fill([0, 3, 0], [4, 3, depth-1], 'trim'),
               fill([1, 1, depth-1], [1, 2, depth-1], 'glass'), fill([3, 1, depth-1], [3, 2, depth-1], 'glass'),
               fill([2, 1, depth-1], [2, 1, depth-1], 'door_lower'), fill([2, 2, depth-1], [2, 2, depth-1], 'door_upper')],
        joints=[dict(id='building', type='skyline:entrance', accepts=['skyline:entry'], at=[2, 1, depth], face='south', height=2),
                dict(id='outside', type='skyline:outside', at=[2, 1, 0], face='north', height=2, external=True)],
        contract={'routes': [route('threshold', [2, 1, 0], [2, 1, depth-1])]})
catalog['entranceGroups'] = {'skyline:residential': dict(requests=[
    dict(id='main', role='main', socket='primary', required=True, pathMaterial='floor', capMaterial='wall',
         pool=[dict(module='skyline:entrance_canopy', weight=2), dict(module='skyline:entrance_portal', weight=3)]),
    dict(id='service', role='service', socket='service', required=False, pathMaterial='floor', capMaterial='wall',
         pool=[dict(module='skyline:entrance_portal', weight=1)])])}

profile = json.loads((SOURCE / 'cities_arise/settlement_profiles/suburb.json').read_text(encoding='utf-8'))
p = profile['planning']
profile['survey'] = dict(width=540, depth=540)
p.update(roadWidth=11, parcelWidth=39, parcelDepth=39, buildingMargin=4,
         minimumParcelCount=24, targetParcelCount=80, maximumParcelCount=80, maxEarthworkVolume=1000000,
         maxBuildableSlope=4.0, maxElevationRange=28, preferredMaxCutDepth=8, preferredMaxFillDepth=8,
         maxCutDepth=16, maxFillDepth=16, maxBuildingFoundationDepth=16)
p['districts'].update(targetParcels=4, parcelSizes=[dict(id='skyline:large', width=39, depth=39, buildingMargin=4)], parcelSizeFallback=False)
p['districts']['grid'] = dict(enabled=True, columns=5, rows=4, parcelsAcross=2, parcelsDeep=2)
p['districts']['elevation'].update(preferredDepth=8, maximumTerraces=16, levelChangeCost=160, roadRunPerRise=12)
# Dense paved blocks use retaining faces and bounded slope aprons rather than mandatory shallow grass verges.
p['streets'] = dict(sectionLength=32, centerMaterial='minecraft:yellow_concrete', vergeMaterial='minecraft:grass_block',
                    access=dict(mode='stairs_landing', landingLength=1, stepMaterial='minecraft:stone_brick_stairs'),
                    modules=[dict(id='skyline:standard_sidewalk', weight=6, leftWidth=2, rightWidth=2, material='minecraft:smooth_stone'),
                             dict(id='skyline:wide_sidewalk', weight=2, leftWidth=3, rightWidth=3, material='minecraft:smooth_stone'),
                             dict(id='skyline:asymmetric_sidewalk', weight=1, leftWidth=1, rightWidth=3, material='minecraft:smooth_stone')])
p['terrainTransitions']['buildingAccessRunPerRise'] = 1
p['terrainTransitions']['roadShoulderRadius'] = 0
p['structuralSupports']['maximumVolume'] = 100000
profile['terrainPolicy']['adaptation'].update(maxTerraformArea=128, maxTerraformRelief=8, maxTerraformVolume=1024)
p['districts']['zoning'] = dict(districtTypes=[dict(id='skyline:centre', weight=1, properties={'skyline:purpose': 'towers'})],
    rules=[dict(target='BUILDING_SLOT', every=40, offset=0, properties={'skyline:purpose': 'lowrise'})])
p['buildings'] = dict(catalog='skyline:city',
    pool=[dict(asset='skyline:'+name, weight=1) for name, _, _ in BUILDINGS[:4]],
    palettes=['skyline:'+name for name in ['limestone', 'brick', 'ivory', 'slate']], fallback='skyline:crown',
    rules=[dict(id='skyline:lowrise', match={'properties': {'skyline:purpose': 'lowrise'}},
                pool=[dict(asset='skyline:townhouse', weight=1), dict(asset='skyline:pavilion', weight=1)],
                palettes=['skyline:brick', 'skyline:ivory'], fallback='skyline:townhouse')])
# Urban plazas instead of garden fences; preserve the proven road and terrain settings.
p.pop('parcelLayout', None)

def write(relative, value):
    path = PACK / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2)+'\n', encoding='utf-8')

write('pack.mcmeta', dict(pack=dict(pack_format=48, description='Cities Arise Skyline: broad towers, eleven-block carriageways, modular sidewalks and independent entrance groups')))
write('data/skyline/content_catalogs/city.json', catalog)
write('data/cities_arise/settlement_profiles/suburb.json', profile)
# Every local owner must have the same deterministic start opportunity. Per-owner
# village exclusion would otherwise delete isolated pieces of one accepted city.
write('data/cities_arise/worldgen/structure_set/suburb.json', dict(
    structures=[dict(structure='cities_arise:suburb', weight=1)],
    placement=dict(type='minecraft:random_spread', salt=184736291, spacing=8, separation=7)))
archive = ROOT / 'build/distributions/skyline.zip'
archive.parent.mkdir(parents=True, exist_ok=True)
with ZipFile(archive, 'w', ZIP_DEFLATED) as output:
    for path in sorted(PACK.rglob('*')):
        if path.is_file():
            output.write(path, path.relative_to(PACK).as_posix())
print(archive)
