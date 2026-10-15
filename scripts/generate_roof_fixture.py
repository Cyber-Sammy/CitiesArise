"""Author bounded roof modules using existing section terminals and weighted layouts.

Run after other fixture generators. Original floor recipes and parcel dimensions stay intact.
"""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / 'examples/datapacks/zoning_fixture/data/zoning/content_catalogs/fixture.json'
data = json.loads(CATALOG.read_text(encoding='utf-8'))


def fills(cells):
    """Compress horizontal runs without introducing cells in concave voids."""
    result = []
    for y, z in sorted({(y, z) for x, y, z in cells}):
        row = sorted(x for x, yy, zz in cells if yy == y and zz == z)
        start = end = row[0]
        material = cells[start, y, z]
        for x in row[1:] + [row[-1] + 2]:
            if x == end + 1 and cells[x, y, z] == material:
                end = x
                continue
            result.append(dict(**{'from': [start, y, z], 'to': [end, y, z]}, material=material))
            if (x, y, z) in cells:
                start = end = x
                material = cells[x, y, z]
    return result


for name, house in list(data['compositions'].items()):
    if 'buildingSections' not in house:
        continue
    sections = house['buildingSections']
    top = sections['sections'][-1]
    layout = data['compositions'][top['floor']]['floorLayout']
    rows = layout['footprint']
    width, depth, height = len(rows[0]), len(rows), 5
    mask = {(x, z) for z, row in enumerate(rows) for x, c in enumerate(row) if c == '#'}
    port = next(p for p in layout['ports'] if p['id'] == top['up'])
    px, _, pz = port['at']
    slab = top['ceilingBoundary']['type']
    roof_id = 'roofs:' + name.replace(':', '_')
    alternatives = []
    base_cells = None
    for variant in ['terrace', 'parapet'] if name.startswith('residential:') else ['pitched', 'ridge']:
        cells = {(x, y, z): 'floor' if y == 0 else 'minecraft:air'
                 for x, z in mask for y in range(height)}
        for x, z in mask:
            if name.startswith('residential:'):
                edge = any((x + dx, z + dz) not in mask for dx, dz in [(1, 0), (-1, 0), (0, 1), (0, -1)])
                if edge:
                    cells[x, 1, z] = 'minecraft:stone_bricks'
                    cells[x, 2, z] = 'minecraft:stone_bricks' if variant == 'terrace' else 'minecraft:stone_brick_slab'
            else:
                # The stepped shell follows an authored concave footprint without bridging its void.
                distance = min((abs(x - xx) + abs(z - zz) for xx in range(-1, width + 1)
                                for zz in range(-1, depth + 1) if (xx, zz) not in mask))
                peak = min(3, distance)
                # A two-layer shell keeps neighboring steps connected without a solid timber volume.
                for y in range(max(1, peak - 1), peak + 1):
                    cells[x, y, z] = 'minecraft:spruce_planks'
                if variant == 'ridge' and peak == 3 and x == width // 2:
                    cells[x, 4, z] = 'minecraft:stone_brick_slab'
        # A small stair head preserves the shared ladder and a two-block-high landing.
        # Its backing is explicit, including where floor furniture ends at the upper slab.
        for x in range(px - 1, px + 2):
            for z in range(pz - 1, pz + 2):
                if (x, z) not in mask:
                    raise ValueError('Roof stair head must be supported by the top floor')
                for y in [1, 2]:
                    cells[x, y, z] = 'wall' if x == px + 1 or z != pz else 'minecraft:air'
                cells[x, 3, z] = 'minecraft:stone_bricks' if name.startswith('residential:') else 'minecraft:spruce_planks'
        for y in [0, 1]:
            cells[px, y, pz] = 'minecraft:ladder[facing=west]'
            cells[px + 1, y, pz] = 'wall'
        # Sparse exterior air is not a roof volume. Declare only actual structure and access clearance.
        clearance = {(x, y, pz) for x in [px - 1, px] for y in [1, 2]}
        clearance.add((px, 4, pz))
        cells = {p: material for p, material in cells.items()
                 if (material != 'minecraft:air' or p in clearance)
                 and (p[1] != 0 or abs(p[0] - px) <= 1 and abs(p[2] - pz) <= 1)}
        if base_cells is None:
            base_cells = cells
        patch = {p: cells.get(p, 'minecraft:air') for p in base_cells.keys() | cells.keys()
                 if base_cells.get(p) != cells.get(p)}
        alternatives.append(dict(id=variant, weight=1, fills=fills(patch) if patch else []))
    data['modules'][roof_id] = dict(size=[width, height, depth], tags=['zoning:style', 'roofs:terminal'],
        fills=fills(base_cells), layouts=alternatives,
        joints=[dict(id='down', type=port['type'], accepts=[port['type']], at=[px, 0, pz], face='down')],
        boundaries=[dict(joint='down', role='floor', type=slab, accepts=[slab], priority=10,
                         **{'from': [px - 1, 0, pz - 1], 'to': [px + 1, 0, pz + 1]})],
        contract={'routes': [dict(id='roof_landing', **{'from': [px, 1, pz], 'to': [px - 1, 1, pz]}, bodyHeight=2)]})
    # Re-running this generator must not keep increasing the building budgets.
    if not sections.get('roof'):
        house['minimumModules'] += 1
        house['maximumModules'] += 1
        house['maximumHeight'] += height - 1
        data['assets'][name]['height'] += height - 1
    sections['roof'] = roof_id

CATALOG.write_text(json.dumps(data, indent=2) + '\n', encoding='utf-8')
