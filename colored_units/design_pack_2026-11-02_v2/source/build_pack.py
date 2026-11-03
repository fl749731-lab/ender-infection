from pathlib import Path
import json,time,sys
from creatures import build_creatures
from equipment import build_equipment
from laboratory import build_components,complex_scene
from refine_creatures import reinforce
from refine_equipment import refine_gear,refine_lab
from contextlib import nullcontext
from concept_materials import concept_palette
from concept_ecology import build_models as ecology_models
from concept_combat import build_models as combat_models
from concept_bosses import build_models as boss_models

HERE=Path(__file__).resolve().parent
ROOT=HERE.parent/'rebuilt' if HERE.name=='source' else HERE.parents[1]/'outputs'/'blockbench_design_pack_2026-11-02_v2'
def main():
    started=time.time();ROOT.mkdir(parents=True,exist_ok=True)
    concepts={m.key:m for m in ecology_models()+combat_models()+boss_models()}
    creatures=[concepts[m.key] if m.key in concepts else reinforce(m) for m in build_creatures()];gear=[refine_gear(m) for m in build_equipment()];components=[refine_lab(m) for m in build_components()];parts={m.key:m for m in components}
    scenes=[complex_scene(parts,True),complex_scene(parts,False)];models=creatures+gear+components+scenes;manifest=[]
    for m in models:
        with concept_palette() if m.key in concepts else nullcontext():
            data,image,record=m.save(ROOT/m.category)
        record['file']=str(Path(record['file']).relative_to(ROOT)).replace('\\','/');manifest.append(record)
        print(m.key,len(data['elements']),image.size,flush=True)
    payload={'version':'2026-11-02-v2','status':'editable-model-assets; not game-integrated','units_per_block':16,'count':len(manifest),'models':manifest}
    (ROOT/'manifest.json').write_text(json.dumps(payload,ensure_ascii=False,indent=2),encoding='utf-8')
    print('DONE',len(manifest),'models',round(time.time()-started,1),'seconds',ROOT,flush=True)
if __name__=='__main__':main()
