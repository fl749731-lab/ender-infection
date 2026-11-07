"""Author purpose-specific institute furniture groups with the structure MCP."""
from hashlib import sha256
import json
from pathlib import Path
from mcp_stdio_client import McpClient

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'tools/institute_architecture'


def designs():
    result = {}
    def make(name, size=(5, 4, 4)):
        cells = {}
        def box(x, y, z, X, Y, Z, state):
            for a in range(x, X + 1):
                for b in range(y, Y + 1):
                    for c in range(z, Z + 1): cells[a, b, c] = state
        box(0, 0, 0, size[0]-1, 0, size[2]-1, 'light_gray_concrete')
        result[name] = (size, cells)
        return box
    ns = 'ender_infection:'
    b = make('office_desk', (5, 4, 5))
    b(0, 1, 1, 4, 1, 1, 'smooth_quartz_slab[type=top,waterlogged=false]')
    for x in (0, 2, 4): b(x, 1, 1, x, 1, 1, ns+'lab_freezer[facing=south]')
    for x in (1, 3):
        b(x, 2, 1, x, 2, 1, ns+'lab_monitor[facing=south]')
        b(x, 1, 3, x, 1, 3, ns+'lab_chair[facing=north]')
    b(2, 2, 1, 2, 3, 1, 'light_blue_stained_glass')
    for name, material in (('supply_rack',ns+'lab_shelf'), ('cold_bank',ns+'lab_freezer'), ('archive_stack','bookshelf')):
        b = make(name)
        for z, face in ((1,'north'),(2,'south')):
            b(0, 1, z, 4, 3 if name=='archive_stack' else 2, z,
              material if name=='archive_stack' else material+'[facing='+face+']')
        b(0, 3, 1, 4, 3, 2, 'smooth_quartz_slab[type=bottom,waterlogged=false]')
    for name, bed in (('medical_bay',ns+'lab_medical_bed'),('bunk_pair','gray_bed')):
        b = make(name,(5,4,5))
        for x in (1,3):
            for z, part in ((1,'head'),(2,'foot')):
                b(x,1,z,x,1,z,bed+'[facing=north,part='+part+',occupied=false]')
        for x in (0,4):
            b(x,1,1,x,1,1,ns+'lab_freezer[facing=south]')
            if name=='medical_bay':b(x,2,1,x,2,1,ns+'lab_monitor[facing=south]')
        if name=='medical_bay': b(2,1,1,2,2,2,'white_stained_glass')
    b = make('dining_table',(5,3,5))
    b(2,1,1,2,1,3,'smooth_quartz_slab[type=top,waterlogged=false]')
    for x,face in ((1,'east'),(3,'west')):
        for z in (1,3): b(x,1,z,x,1,z,ns+'lab_chair[facing='+face+']')
    for name, soil, plant in (('planter_bed','grass_block','azalea'),('fungus_bed','mycelium','brown_mushroom')):
        b = make(name)
        b(0,1,1,4,1,2,'smooth_quartz')
        b(1,1,1,3,1,2,soil)
        b(1,2,1,3,2,2,plant)
    b = make('pump_skid')
    for x in (0,2,4):b(x,1,1,x,1,1,ns+'lab_cooling_pump[facing=north]')
    for x in (0,4):b(x,1,2,x,1,2,ns+'lab_storage_crate[facing=south]')
    b(0,2,2,4,2,2,ns+'lab_pipe[facing=east]')
    b = make('terminal_bank')
    b(0,1,1,4,1,1,ns+'lab_freezer[facing=north]')
    for x in (0,2,4):b(x,2,1,x,2,1,ns+'lab_monitor[facing=north]')
    b(0,1,2,4,3,2,'gray_concrete')
    b(1,2,2,3,2,2,'cyan_stained_glass')
    b = make('crate_pallet')
    for z,face in ((1,'north'),(2,'south')):
        for x in (0,1,3,4): b(x,1,z,x,2,z,ns+'lab_storage_crate[facing='+face+']')
    b = make('mother_chamber',(12,7,7))
    b(0,0,0,8,0,6,'polished_deepslate')
    for y in range(1,6):
        b(0,y,0,8,y,0,'purple_stained_glass')
        b(0,y,6,8,y,6,'purple_stained_glass')
        b(0,y,1,0,y,5,'purple_stained_glass')
        b(8,y,1,8,y,5,'purple_stained_glass')
    for x in (0,8):
        for z in (0,6):b(x,1,z,x,6,z,'polished_deepslate')
    b(0,6,0,8,6,6,'gray_concrete')
    for x in (2,6):b(x,6,3,x,6,3,'sea_lantern')
    b(10,1,3,10,1,3,'polished_deepslate')
    b(10,2,3,10,2,3,ns+'rift_anchor[active=false,suppressed=false]')
    b(8,2,3,8,2,3,'amethyst_block')
    for x in (9,10):b(x,0,3,x,0,3,'purple_concrete')
    return result


def create():
    receipt = {'target':'NeoForge Minecraft 1.21.1','shared_source_data_version':3465,'assets':[]}
    with McpClient('minecraft-structure') as client:
        for name,(size,cells) in designs().items():
            client.call('new_structure',name=name,size_x=size[0],size_y=size[1],size_z=size[2],minecraft_version='1.20.1')
            client.call('set_blocks',name=name,blocks=[dict(x=x,y=y,z=z,state=state) for (x,y,z),state in cells.items()])
            path=DEST/(name+'.nbt');client.call('save_structure',name=name,path=str(path))
            receipt['assets'].append({'file':path.relative_to(ROOT).as_posix(),'sha256':sha256(path.read_bytes()).hexdigest()})
            print('Authored '+name,flush=True)
        receipt['server']=client.server.get('serverInfo');receipt['calls']=client.calls
        for call in receipt['calls']:
            if 'path' in call['arguments']:call['arguments']['path']=Path(call['arguments']['path']).relative_to(ROOT).as_posix()
    (DEST/'mcp-furnishings.json').write_text(json.dumps(receipt,indent=2)+'\n',encoding='utf8')


if __name__=='__main__':create()
