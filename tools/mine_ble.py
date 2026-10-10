import re, os

os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'h5'))
s = open('app.js', encoding='utf-8', errors='ignore').read()
print(f'app.js 大小 {len(s)} 字节\n')

def ctx(kw, before=260, after=420, limit=3):
    print('=' * 78)
    print(f'▶ {kw}')
    for m in list(re.finditer(re.escape(kw), s))[:limit]:
        a, b = max(0, m.start() - before), min(len(s), m.end() + after)
        print('   ' + s[a:b].replace('\n', ' '))
        print('   ' + '-' * 70)
    print()

for kw in ['bleCmd', '毫升', '升']:
    ctx(kw, limit=3 if kw == 'bleCmd' else 4)
