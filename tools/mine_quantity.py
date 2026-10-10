import re, glob, os

os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'h5'))

files = sorted(glob.glob('chunks/*.js')) + sorted(glob.glob('*.js'))
print(f'扫描 {len(files)} 个文件\n')

for kw in ['quantity', 'params', 'functionName', 'listState', 'stateCmd', 'water/unlock', 'bluetooth/report']:
    print('=' * 78)
    print(f'▶ {kw}')
    total = 0
    for f in files:
        try:
            s = open(f, encoding='utf-8', errors='ignore').read()
        except Exception:
            continue
        hits = list(re.finditer(re.escape(kw), s))
        if not hits:
            continue
        total += len(hits)
        # 只对感兴趣的打印上下文
        if kw in ('quantity', 'params', 'water/unlock'):
            for m in hits[:3]:
                a, b = max(0, m.start() - 220), min(len(s), m.end() + 320)
                print(f'   [{f}]')
                print('      ' + s[a:b].replace('\n', ' '))
                print()
        else:
            print(f'   {len(hits):3d}  {f}')
    print(f'   → 共 {total} 处\n')
