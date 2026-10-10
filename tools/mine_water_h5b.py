import re, os

os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'h5'))

s = open('pages_waterMachine.85d8df0d.js', encoding='utf-8', errors='ignore').read()

print('=== 该页面所有 $post / $get 调用 ===')
calls = sorted(set(re.findall(r'\$post\(\s*["\']([^"\']+)["\']', s)))
for c in calls:
    print('   POST', c)
calls = sorted(set(re.findall(r'\$get\(\s*["\']([^"\']+)["\']', s)))
for c in calls:
    print('   GET ', c)

print()
print('=== checkChannelStatus 完整实现 ===')
i = s.find('checkChannelStatus')
print(s[max(0, i - 60): i + 1500])

print()
print('=== 解锁相关方法 ===')
for kw in ['async unlock', 'unlockHandle', 'getUnlock', 'doUnlock', 'unlockApi']:
    j = s.find(kw)
    if j > 0:
        print(f'--- {kw} ---')
        print(s[j: j + 900])
        print()
