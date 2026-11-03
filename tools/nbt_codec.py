"""Small read-only Java NBT decoder for reproducible structure validation."""
import gzip,io,struct
from pathlib import Path

def load(path):
 raw=Path(path).read_bytes();data=gzip.decompress(raw) if raw[:2]==b'\x1f\x8b' else raw;f=io.BytesIO(data)
 def read(n):
  b=f.read(n)
  if len(b)!=n:raise ValueError('Truncated NBT')
  return b
 def num(fmt):return struct.unpack('>'+fmt,read(struct.calcsize('>'+fmt)))[0]
 def string():return read(num('H')).decode('utf-8')
 def value(t):
  if t in {1,2,3,4,5,6}:return num({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
  if t==7:return read(num('i'))
  if t==8:return string()
  if t==9:
   child,n=num('B'),num('i');return [value(child) for _ in range(n)]
  if t==10:
   result={}
   while True:
    child=num('B')
    if child==0:return result
    name=string();result[name]=value(child)
  if t in (11,12):return [num('i' if t==11 else 'q') for _ in range(num('i'))]
  raise ValueError('Unsupported NBT '+str(t))
 t=num('B');string();return value(t)
