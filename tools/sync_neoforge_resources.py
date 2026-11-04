"""Keep visuals shared with Forge; convert loader and 1.21 data-pack paths."""
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'ender_infection_colored/src/main/resources'
DEST=ROOT/'ender_infection_neoforge/build/generated/sharedResources'
RENAMES={'advancements':'advancement','loot_tables':'loot_table','predicates':'predicate','recipes':'recipe',
         'structures':'structure','item_modifiers':'item_modifier','functions':'function'}
TAG_NAMES={'blocks':'block','items':'item','fluids':'fluid','entity_types':'entity_type','game_events':'game_event'}

def write_if_changed(path,content):
    if path.is_file() and path.read_bytes()==content:return False
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_bytes(content)
    return True

def target(rel):
    parts=list(rel.parts)
    if len(parts)>2 and parts[0]=='data':
        parts[2]=RENAMES.get(parts[2],parts[2])
        if parts[2]=='forge':parts[2]='neoforge'
        if parts[2]=='tags' and len(parts)>3:parts[3]=TAG_NAMES.get(parts[3],parts[3])
    return Path(*parts)

def transform(obj,recipe=False):
    if isinstance(obj,list):return [transform(x,recipe) for x in obj]
    if not isinstance(obj,dict):
        if not isinstance(obj,str):return obj
        # Vanilla renamed this sprite in 1.20.3. Keep full-ID matching so
        # grass_block, tall_grass and the mod's vegetation IDs are unaffected.
        if obj=='minecraft:block/grass':return 'minecraft:block/short_grass'
        return obj.replace('forge:','neoforge:')
    out={k:transform(v,recipe) for k,v in obj.items()}
    if recipe and isinstance(out.get('result'),dict) and 'item' in out['result']:out['result']['id']=out['result'].pop('item')
    # 1.21 DisplayInfo decodes its icon with ItemStack.STRICT_CODEC (id,
    # count, components); the 1.20 `item` key rejects the whole advancement.
    if 'criteria' in out and isinstance(out.get('display'),dict):
        icon=out['display'].get('icon')
        if isinstance(icon,dict) and 'item' in icon:icon['id']=icon.pop('item')
    # MatchTool -> ItemPredicate now has a map of ItemSubPredicates.
    # Keeping the old enchantments field silently makes silk-touch match any
    # tool. EnchantmentPredicate itself also renamed enchantment -> enchantments.
    if out.get('condition')=='minecraft:match_tool' and isinstance(out.get('predicate'),dict):
        predicate=out['predicate']
        for legacy,component in (('enchantments','minecraft:enchantments'),('stored_enchantments','minecraft:stored_enchantments')):
            if legacy in predicate:
                entries=predicate.pop(legacy)
                for entry in entries:
                    if 'enchantment' in entry:entry['enchantments']=entry.pop('enchantment')
                predicate.setdefault('predicates',{})[component]=entries
    return out

def main():
    DEST.mkdir(parents=True,exist_ok=True);written=set();count=0;updated=0
    for file in SOURCE.rglob('*'):
        if not file.is_file():continue
        rel=file.relative_to(SOURCE)
        if rel.as_posix() in ('META-INF/mods.toml','pack.mcmeta'):continue
        output=DEST/target(rel);output.parent.mkdir(parents=True,exist_ok=True)
        if file.suffix=='.json':
            obj=transform(json.loads(file.read_text('utf-8')),len(rel.parts)>2 and rel.parts[2]=='recipes')
            updated+=write_if_changed(output,(json.dumps(obj,ensure_ascii=False,separators=(',',':'))+'\n').encode('utf-8'))
        else:updated+=write_if_changed(output,file.read_bytes())
        written.add(output.resolve());count+=1
    pack=DEST/'pack.mcmeta';updated+=write_if_changed(pack,(json.dumps({'pack':{'pack_format':34,'supported_formats':{'min_inclusive':34,'max_inclusive':48},'description':'Ender Infection shared assets / NeoForge 1.21.1'}})+'\n').encode('utf-8'));written.add(pack.resolve())
    # Only obsolete generated files inside this task's explicit build directory.
    base=DEST.resolve()
    for file in DEST.rglob('*'):
        if file.is_file() and file.resolve() not in written:
            assert file.resolve().is_relative_to(base);file.unlink()
    print(json.dumps({'shared_resources':count,'updated_files':updated,'destination':str(DEST)}))

if __name__=='__main__':main()
