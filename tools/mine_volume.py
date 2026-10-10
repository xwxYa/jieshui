import re, glob, os

os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'h5'))

KEYWORDS = ['定量', '预设', '毫升', '升', '水位', '流量', '出水', '停止', '自动停', '结束接水',
            'unlocked', 'amount', 'flowmeter', 'flowMeter', 'identify', 'machineFunctionId',
            'water/sync', 'endWater', 'bleCmd', 'notifyCharacteristic', 'writeBLECharacteristic']

files = sorted(glob.glob('chunks/*.js')) + sorted(glob.glob('*.js'))
print(f'扫描 {len(files)} 个 js 文件\n')

for kw in KEYWORDS:
    hits = []
    for f in files:
        try:
            s = open(f, encoding='utf-8', errors='ignore').read()
        except Exception:
            continue
        n = s.count(kw)
        if n:
            hits.append((f, n))
    if not hits:
        continue
    total = sum(n for _, n in hits)
    print(f'▶ "{kw}"  共 {total} 处,分布在 {len(hits)} 个文件')
    for f, n in sorted(hits, key=lambda x: -x[1])[:4]:
        print(f'      {n:4d}  {f}')
    print()
