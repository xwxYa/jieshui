import re, os

os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'h5'))
s = open('app.js', encoding='utf-8', errors='ignore').read()

def ctx(kw, before=300, after=700, limit=2):
    hits = list(re.finditer(re.escape(kw), s))
    print('=' * 78)
    print(f'▶ {kw}   ({len(hits)} 处)')
    for m in hits[:limit]:
        a, b = max(0, m.start() - before), min(len(s), m.end() + after)
        print('   ' + s[a:b].replace('\n', ' '))
        print('   ' + '-' * 72)
    print()

for kw in ['bleSecretUp', 'encryption/cmd', 'machine/water/sync', 'MachinePrePay', 'getChargList']:
    ctx(kw, limit=2)
