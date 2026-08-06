import zipfile
import re

z = zipfile.ZipFile('asoxcg.apk')
data = z.read('classes2.dex')

# Find class names / strings related to player or xtream or http
matches = re.findall(rb'[a-zA-Z0-9_/]{5,60}\.class', data)
print(f"Total class references: {len(matches)}")
for m in sorted(set(matches)):
    s = m.decode('utf-8', errors='ignore')
    if any(k in s.lower() for k in ['player', 'xtream', 'vlc', 'exo', 'stream', 'http', 'media', 'network', 'proxy', 'aso']):
        print(s)
