import sys, collections
exec(open(r'E:\mc\dist\spore_lab\parse.py').read().split("nbt=load")[0])
for name in sys.argv[1:]:
    try:
        nbt=load(name)
    except Exception as e:
        print(name,'读取失败',e); continue
    size=nbt['size']; palette=nbt['palette']
    counts=collections.Counter()
    for b in nbt['blocks']:
        n=palette[b['state']]['Name'].split(':')[-1]
        if n!='air': counts[n]+=1
    total=sum(counts.values())
    print(f'### {name.split(chr(92))[-1]}  size={size}  非空气={total}')
    print('   top:', ', '.join(f'{k}×{v}' for k,v in counts.most_common(10)))