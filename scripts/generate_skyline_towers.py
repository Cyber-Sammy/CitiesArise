"""Regenerate the opt-in Skyline high-rise recipes from its reusable modules.

Run from repository root. Idempotent; authoring data only, no Minecraft/world access.
"""
from pathlib import Path
from copy import deepcopy
import json

ROOT=Path(__file__).resolve().parents[1]/'examples/datapacks/skyline/data'
CAT=ROOT/'skyline/content_catalogs/city.json'
PROFILE=ROOT/'cities_arise/settlement_profiles/suburb.json'
d=json.loads(CAT.read_text(encoding="utf-8"))

# A six-layer module shares its top/bottom layer: five-block storeys, four clear blocks.
# Keep roof and small-architecture modules independent from the floor transformation.
floor_ids=['skyline:floor_29_25','skyline:floor_17_17','skyline:terrace_core','skyline:terrace_north','skyline:terrace_south','skyline:terrace_west','skyline:terrace_east','skyline:mansard_room']
def taller_fills(fills):
    for f in fills:
        low,high=f['from'][1],f['to'][1]
        if low>=4:f['from'][1]+=1
        if high>=3 and (high>low or high>=4):f['to'][1]+=1
        # The facade cornice moves to the top of the fourth interior block.
        if low==high==3 and f['material']=='trim':f['from'][1]=f['to'][1]=4
for mid in floor_ids:
    m=d['modules'][mid]
    if m['size'][1]!=5:continue
    m['size'][1]=6;taller_fills(m.get('fills',[]))
    for j in m.get('joints',[]):
        if j['face']=='up':j['at'][1]=6
    for b in m.get('boundaries',[]):
        for field in ['from','to']:
            if b[field][1]==4:b[field][1]=5
    for layout in m.get('layouts',[]):taller_fills(layout.get('fills',[]))

# Repair legacy upper doorway caps with the original facade pattern rather than a solid stripe.
for mid in ['skyline:floor_29_25','skyline:floor_17_17']:
    m=d['modules'][mid];w,_,depth=m['size'];cx=w//2
    xs={p for start in range(3,w-3,4) for p in (start,start+1)}
    def legacy_front(z,y):
        if z in (0,depth-1):return 'trim' if y==4 or cx in range(1,w-1,4) else 'minecraft:air'
        return 'glass' if y in (1,2) and cx in xs else 'wall'
    upper=next(l for l in m['layouts'] if l['id']=='upper')
    upper['fills']=[{'from':[cx,y,z],'to':[cx,y,z],'material':legacy_front(z,y)}
        for z in (0,1,depth-2,depth-1) for y in range(1,5)]

# Reusable broad-floor generator. Only explicit circulation air is stored; the
# sparse authoring style matches the original pack and avoids whole-volume air copies.
def module(name,w,h,shape):
    mask={(x,z) for x in range(w) for z in range(h) if not (shape=='L' and x>=19 and z>=15) and not(shape=='U' and 13<=x<w-13 and z>=13)}
    sx,sz=(w//2,h//2+1) if shape=='rectangle' else (9,9)
    entry=w//2;service=8 if shape=='L' else w-9 if shape=='U' else entry
    edges={p for p in mask if any((p[0]+dx,p[1]+dz) not in mask for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)])}
    inner={p for p in mask-edges if any((p[0]+dx,p[1]+dz) in edges for dx,dz in [(1,0),(-1,0),(0,1),(0,-1)])}
    hall=set()
    def line(x1,z1,x2,z2):
        for x in range(min(x1,x2),max(x1,x2)+1):
            for z in range(min(z1,z2),max(z1,z2)+1):
                for dx in [-1,0,1]:
                    for dz in [-1,0,1]:
                        if (x+dx,z+dz) in mask:hall.add((x+dx,z+dz))
    line(entry,0,entry,7);line(min(entry,sx-1),7,max(entry,sx-1),7);line(sx-1,7,sx-1,sz)
    line(service,7,service,h-1);line(min(service,sx-1),7,max(service,sx-1),7)
    def facade(x,z,y):
        if (x,z) in edges:
            return 'trim' if x%4==1 or z%4==1 or y==4 else None
        if (x,z) in inner:
            return 'glass' if y in (2,3) and (x%4 in (2,3) or z%4 in (2,3)) else 'wall'
        return None
    doors={(entry,z) for z in (0,1)}|{(service,z) for z in (h-2,h-1)}
    cells={}
    for x,z in mask:
        cells[x,0,z]=cells[x,5,z]='floor'
        for y in range(1,5):
            material=facade(x,z,y)
            if (x,z) not in edges|inner:
                if x==w//2 or z==h//2:material='wall'
                if (x,z) in hall:material='minecraft:air'
            if (x,z) in doors and y<=2:material='minecraft:air'
            if material is not None:cells[x,y,z]=material
    for y in range(6):cells[sx, y,sz]='minecraft:ladder[facing=west]';cells[sx+1,y,sz]='wall'
    for x,z,mat in [(4,4,'minecraft:bookshelf'),(w-5,4,'minecraft:crafting_table'),(4,h-5,'minecraft:barrel'),(w-5,h-5,'minecraft:smoker')]:
        if (x,z) in mask-edges-inner-hall:cells[x,1,z]=mat
    fills=[]
    for y in range(6):
        for z in range(h):
            x=0
            while x<w:
                material=cells.get((x,y,z))
                if material is None:x+=1;continue
                end=x
                while end+1<w and cells.get((end+1,y,z))==material:end+=1
                fills.append({'from':[x,y,z],'to':[end,y,z],'material':material});x=end+1
    def joint(i,t,at,face,external=False,height=1):return {'id':i,'type':t,'accepts':[t],'at':at,'face':face,'external':external,'height':height}
    joints=[joint('down','skyline:stairs',[sx,0,sz],'down'),joint('up','skyline:stairs',[sx,6,sz],'up'),joint('entry','skyline:entry',[entry,1,0],'north',True,2),joint('service','skyline:entry',[service,1,h],'south',True,2)]
    joints[2]['accepts']=['skyline:entrance'];joints[3]['accepts']=['skyline:entrance']
    # Restore the actual facade palette, including glazing and piers, above the lobby.
    upper={'id':'upper','weight':1,'closedJoints':['entry','service'],'fills':[
        {'from':[x,y,z],'to':[x,y,z],'material':facade(x,z,y) or 'minecraft:air'}
        for x,z in sorted(doors) for y in range(1,5)]}
    d['modules'][name]={'footprint':[''.join('#' if (x,z) in mask else '.' for x in range(w)) for z in range(h)],'size':[w,6,h],'tags':['skyline:building'],'fills':fills,'joints':joints,'layouts':[{'id':'lobby','weight':1},upper],
        'contract':{'routes':[{'id':'lobby','from':[entry,1,2],'to':[sx-1,1,sz],'bodyHeight':2}]}}
    for level in ['ground','upper']:
        ports=[{'id':'down','type':'skyline:stairs','at':[sx,0,sz],'face':'down','height':1,'walkable':True},{'id':'up','type':'skyline:stairs','at':[sx,6,sz],'face':'up','height':1,'walkable':True}]
        if level=='ground':ports.extend([{**j,'walkable':True} for j in joints[2:]])
        d['compositions'][name+'_'+level]={'roots':[name],'attachments':[],'entranceJoint':'entry' if level=='ground' else 'down','requiredTags':['skyline:building'],
            'minimumModules':1,'maximumModules':1,'maximumHeight':6,'floorLayout':{'height':6,'footprint':[''.join('#' if (x,z) in mask else '.' for x in range(w)) for z in range(h)],
            'rooms':[name],'rotations':[0],'ports':ports,'entrance':'entry' if level=='ground' else 'down','minimumCounts':{name:1},'maximumCounts':{name:1},'mode':'fixed',
            'placements':[{'module':name,'at':[0,0,0],'rotation':0,'layout':'lobby' if level=='ground' else 'upper'}]}}
    return sx,sz

sx,sz=module('skyline:floor_35_29',35,29,'rectangle')
module('skyline:floor_l',35,29,'L');module('skyline:floor_u',37,33,'U')
roof=deepcopy(d['modules']['skyline:roof_29_25_False'])
# Stretch the flat parapet while preserving the authored mating/access dimensions.
for f in roof['fills']:
    for key in ['from','to']:
        x,y,z=f[key];f[key]=[x+6 if x>=27 else x+4 if 12<=x<=15 else x,y,z+4 if z>=23 else z+2 if 11<=z<=14 else z]
for j in roof['joints']:j['at']=[sx,0,sz]
for b in roof['boundaries']:b['from']=[sx-1,0,sz-1];b['to']=[sx+1,0,sz+1]
def roof_point(p):
    x,y,z=p
    return [x+6 if x>=27 else x+4 if 12<=x<=15 else x,y,z+4 if z>=23 else z+2 if 11<=z<=14 else z]
for field in ['clearance','supports']:
    if field in roof.get('contract',{}):roof['contract'][field]=[roof_point(p) for p in roof['contract'][field]]
for route in roof.get('contract',{}).get('routes',[]):
    for field in ['from','to']:route[field]=roof_point(route[field])
roof['size']=[35,5,29];d['modules']['skyline:roof_35_29']=roof

# Update all existing reusable floor descriptors, including authored terraces/mansards.
for c in d['compositions'].values():
    if 'floorLayout' in c and c['floorLayout']['height']==5:
        c['floorLayout']['height']=c['maximumHeight']=6
        for port in c['floorLayout']['ports']:
            if port['face']=='up':port['at'][1]=6

def section(i,count,floor):
    boundary={'type':'skyline:slab','accepts':['skyline:slab'],'priority':10}
    return {'id':i,'floors':count,'floor':floor,'down':'down','up':'up','floorBoundary':boundary,'ceilingBoundary':{**boundary,'priority':0}}
def tower(name,tiers,roof='',extra_modules=0):
    parts=[];total=0
    for i,(floors,floor) in enumerate(tiers):
        if i==0:
            parts.append(section('lobby',1,floor+'_ground'));floors-=1
        if floors:parts.append(section('section_'+str(i),floors,floor+'_upper'))
        total+=floors+(1 if i==0 else 0)
    count=total+(1 if roof else 0)+extra_modules
    h=total*5+(5 if roof else 1)
    cfg={'minimumModules':count,'maximumModules':count,'maximumResolvedModules':max(64,count),'maximumHeight':h,'buildingSections':{'bottomCap':'minecraft:stone_bricks','topCap':'minecraft:stone_bricks','sections':parts}}
    if roof:cfg['buildingSections']['roof']=roof
    d['compositions']['skyline:'+name]=cfg
    d['assets']['skyline:'+name]={'provider':'modules','minimumSize':35 if tiers[0][1]=='skyline:floor_35_29' else 37 if tiers[0][1]=='skyline:floor_u' else 35 if tiers[0][1]=='skyline:floor_l' else 29,'maximumSize':64,'height':h,'parameters':{'composition':'skyline:'+name,'entrances':'skyline:residential'}}

tower('crown',[(30,'skyline:floor_35_29'),(12,'skyline:floor_29_25'),(8,'skyline:floor_17_17')],'skyline:roof_17_17_True')
tower('metropolitan',[(30,'skyline:floor_35_29'),(10,'skyline:floor_29_25')],'skyline:roof_29_25_False')
tower('regent',[(20,'skyline:floor_35_29')],'skyline:roof_35_29')
tower('slender',[(24,'skyline:floor_29_25')],'skyline:roof_29_25_False')
tower('loft',[(28,'skyline:floor_l')])
tower('courtyard',[(36,'skyline:floor_u')])
tower('landmark',[(25,'skyline:floor_35_29'),(12,'skyline:floor_29_25'),(8,'skyline:floor_17_17')],'skyline:roof_17_17_True')
terrace=d['compositions']['skyline:terrace'];terrace['buildingSections']['sections'][1]['floors']=25
# lobby +25 base +transition +4 upper +mansard =32; transition has five structural rooms.
terrace['minimumModules']=terrace['maximumModules']=36;terrace['maximumResolvedModules']=64;terrace['maximumHeight']=161;d['assets']['skyline:terrace']['height']=161
for name,count in [('townhouse',2),('pavilion',1)]:
    d['compositions']['skyline:'+name]['maximumHeight']=count*5+5;d['assets']['skyline:'+name]['height']=count*5+5
CAT.write_text(json.dumps(d,indent=2)+'\n',encoding='utf-8')
p=json.loads(PROFILE.read_text(encoding="utf-8"));p['survey']={'width':768,'depth':768};cfg=p['planning'];cfg.update({'targetParcelCount':96,'maximumParcelCount':96,'minimumParcelCount':32,'parcelWidth':47,'parcelDepth':47,'buildingMargin':4,'maxEarthworkVolume':2000000})
cfg['districts']['grid'].update({'columns':6,'rows':4});cfg['districts']['parcelSizes'][0].update({'width':47,'depth':47})
cfg['buildings']['pool']=[{'asset':'skyline:'+name,'weight':1} for name in ['crown','metropolitan','regent','terrace','slender','loft','courtyard','landmark']]
cfg['buildings']['rules']=[];cfg['districts']['zoning']['rules']=[]
PROFILE.write_text(json.dumps(p,indent=2)+'\n',encoding='utf-8')
print('Skyline: 768x768, 96 plots, 20-50 storeys, 5-block step')
