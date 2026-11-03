"""Apply prepared geometry inside Blockbench through MCP, with native Undo/save.

Canonical files are saved by Blockbench, never by the proposal generator.
The spitter already has bespoke native edits and is deliberately excluded.
"""
import argparse
import json
from pathlib import Path
import shutil

from client import BlockbenchMCP
from review_models import NAMES, ROOT, MODELS, data


def refine(names):
    client = BlockbenchMCP()
    log = ROOT / 'ender_infection_colored/verification/blockbench_native_edits.json'
    records = json.loads(log.read_text('utf-8')) if log.exists() else {}
    for name in names:
        if name == 'rift_throat_spitter':
            continue
        client.call('set_mode', {'mode_id':'edit'})
        source = MODELS / (name + '.bbmodel')
        backup = ROOT / 'ender_infection_colored/build/before-mcp-edit'
        backup.mkdir(parents=True, exist_ok=True)
        for suffix in ('.bbmodel', '.png'):
            path = source.with_suffix(suffix)
            if not (backup/path.name).exists():
                shutil.copy2(path, backup/path.name)
        proposal = ROOT / 'ender_infection_colored/build/mcp-proposals' / source.name
        code = '''(()=>{
          const sourcePath=SOURCE, draftPath=DRAFT;
          let original, draft;
          Blockbench.read([sourcePath,draftPath],{readtype:'text',errorbox:false},files=>{
            original=JSON.parse(files[0].content);draft=JSON.parse(files[1].content);
          });
          if(!draft||!original)throw Error('Could not read the models');
          const existing=ModelProject.all.find(p=>p.save_path.replaceAll('\\\\','/')===sourcePath);
          if(existing){if(!existing.saved)throw Error('Unsaved project: '+existing.name);existing.select();}
          else Codecs.project.load(original,{path:sourcePath});
          if(Project.name!==NAME)throw Error('Wrong active project');
          const texture=Texture.all[0], groups=new Map(Group.all.map(g=>[g.uuid,g]));
          const owners=new Map();
          function collect(g){if(!groups.has(g.uuid))throw Error('Missing bone '+g.name);
            for(const child of g.children){if(typeof child==='string')owners.set(child,g.uuid);else collect(child);}}
          draft.outliner.forEach(collect);
          const elements=Cube.all.slice();
          Undo.initEdit({elements,textures:[texture],outliner:true});
          Cube.all.slice().forEach(c=>c.remove());
          texture.uv_width=draft.resolution.width;texture.uv_height=draft.resolution.height;
          texture.fromDataURL(draft.textures[0].source);
          Project.texture_width=draft.resolution.width;Project.texture_height=draft.resolution.height;
          for(const entry of draft.elements){
            const e=JSON.parse(JSON.stringify(entry));
            for(const face of Object.values(e.faces))face.texture=texture.uuid;
            const c=new Cube(e).addTo(groups.get(owners.get(e.uuid))).init();elements.push(c);
          }
          Undo.finishEdit('Refine concept silhouette, layered armor and organic sacs via MCP');
          Canvas.updateAll();
          const bounds=new THREE.Box3().setFromObject(Project.model_3d);
          return {project:Project.name,path:Project.save_path,cubes:Cube.all.length,bones:Group.all.length,
            atlas:[texture.width,texture.height],undo:Undo.history.slice(-1).map(h=>h.action),
            bounds:{min:bounds.min.toArray(),max:bounds.max.toArray()}};
        })()'''.replace('SOURCE',json.dumps(source.as_posix())).replace('DRAFT',json.dumps(proposal.as_posix())).replace('NAME',json.dumps(name))
        result=data(client.call('risky_eval',{'code':code}))
        client.call('trigger_action',{'action':'save_project','confirmDialog':False})
        saved=data(client.call('risky_eval',{'code':'(()=>({name:Project.name,saved:Project.saved,path:Project.save_path}))()'}))
        assert saved['saved'] and saved['name']==name
        result['saved']=saved
        records[name]=result
        log.write_text(json.dumps(records,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print('MCP_NATIVE_SAVED',name,result['cubes'],flush=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('names',nargs='*')
    args=parser.parse_args()
    names=args.names or [name for name in NAMES if name!='rift_throat_spitter']
    assert set(names)<=set(NAMES)
    refine(names)
