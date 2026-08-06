import zipfile
import re

z = zipfile.ZipFile('asoxcg.apk')

for name in ['classes.dex', 'classes2.dex']:
    data = z.read(name)
    
    # Search for player_api references
    apis = re.findall(rb'player_api[^\s"\'<>]*', data)
    print(f"=== {name} player_api matches ===")
    for a in set(apis):
        print(a.decode('utf-8', errors='ignore'))
        
    # Search for User-Agent
    uas = re.findall(rb'User-Agent[^\r\n]*', data, re.IGNORECASE)
    print(f"=== {name} User-Agent matches ===")
    for u in list(set(uas))[:10]:
        print(u.decode('utf-8', errors='ignore'))

    # Search for package names or HTTP clients
    http_matches = re.findall(rb'http[s]?://[^\s"\'<>]+', data)
    print(f"=== {name} sample HTTP URLs ===")
    for h in list(set(http_matches))[:15]:
        print(h.decode('utf-8', errors='ignore'))
