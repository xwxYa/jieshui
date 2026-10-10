import re, glob, os

os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'h5'))

files = sorted(glob.glob('pages_water*.js') + glob.glob('deviceStatus*.js'))
print(f'找到 {len(files)} 个饮水机相关 chunk\n')

for f in files:
    s = open(f, encoding='utf-8', errors='ignore').read()
    print('=' * 74)
    print(f'{f}   ({len(s)} 字节)')

    paths = sorted(set(re.findall(r'["\'](/(?:[a-zA-Z][a-zA-Z0-9_]*/)*[a-zA-Z][a-zA-Z0-9_]*)["\']', s)))
    api = [p for p in paths if not p.startswith(('/pages/', '/static/', '/images/', '/obj/'))]
    print('\n  ▸ 接口路径:')
    for p in api:
        print('      ', p)

    cn = sorted(set(re.findall(r'["\']([^"\']{2,50}[\u4e00-\u9fa5][^"\']{0,50})["\']', s)))
    print('\n  ▸ 中文文案:')
    for t in cn[:70]:
        print('      ', t)
    print()
