import gzip, struct, json, sys, collections

class R:
    def __init__(self, data): self.d=data; self.i=0
    def u1(self):
        v=self.d[self.i]; self.i+=1; return v
    def i1(self):
        v=struct.unpack_from('>b',self.d,self.i)[0]; self.i+=1; return v
    def i2(self):
        v=struct.unpack_from('>h',self.d,self.i)[0]; self.i+=2; return v
    def i4(self):
        v=struct.unpack_from('>i',self.d,self.i)[0]; self.i+=4; return v
    def i8(self):
        v=struct.unpack_from('>q',self.d,self.i)[0]; self.i+=8; return v
    def f4(self):
        v=struct.unpack_from('>f',self.d,self.i)[0]; self.i+=4; return v
    def f8(self):
        v=struct.unpack_from('>d',self.d,self.i)[0]; self.i+=8; return v
    def s(self):
        n=struct.unpack_from('>H',self.d,self.i)[0]; self.i+=2
        v=self.d[self.i:self.i+n].decode('utf-8','replace'); self.i+=n; return v
    def payload(self,t):
        if t==1: return self.i1()
        if t==2: return self.i2()
        if t==3: return self.i4()
        if t==4: return self.i8()
        if t==5: return self.f4()
        if t==6: return self.f8()
        if t==7:
            n=self.i4(); v=list(self.d[self.i:self.i+n]); self.i+=n; return v
        if t==8: return self.s()
        if t==9:
            et=self.u1(); n=self.i4(); return [self.payload(et) for _ in range(n)]
        if t==10:
            out={}
            while True:
                tt=self.u1()
                if tt==0: break
                name=self.s()
                out[name]=self.payload(tt)
            return out
        if t==11:
            n=self.i4(); v=list(struct.unpack_from('>%di'%n,self.d,self.i)); self.i+=4*n; return v
        if t==12:
            n=self.i4(); v=list(struct.unpack_from('>%dq'%n,self.d,self.i)); self.i+=8*n; return v
        raise ValueError('tag %d'%t)

def load(path):
    with gzip.open(path,'rb') as f: data=f.read()
    r=R(data); t=r.u1(); name=r.s(); return r.payload(t)

nbt=load(sys.argv[1])
size=nbt['size']
palette=nbt['palette']
print('size(x,y,z) =', size)
print('palette entries =', len(palette))
counts=collections.Counter()
for b in nbt['blocks']:
    counts[b['state']]+=1
print('\n=== 方块统计（按数量）===')
for st,c in counts.most_common():
    p=palette[st]
    name=p['Name'].split(':')[-1]
    props=p.get('Properties',{})
    extra=('['+','.join(f'{k}={v}' for k,v in props.items())+']') if props else ''
    print(f'{c:6d}  {name}{extra}')
# 每层方块数
layers=collections.Counter()
for b in nbt['blocks']:
    layers[b['pos'][1]]+=1
print('\n=== 每层方块数 ===')
for y in sorted(layers): print(f'  y={y:3d}: {layers[y]}')