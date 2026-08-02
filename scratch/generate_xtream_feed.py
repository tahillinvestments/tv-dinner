import urllib.request
import json
import os

url_cat = 'http://portal5458.com:8080/player_api.php?username=SAPPTV12&password=REMOTE6202&action=get_live_categories'
url_stream = 'http://portal5458.com:8080/player_api.php?username=SAPPTV12&password=REMOTE6202&action=get_live_streams'
headers = {'User-Agent': 'VLC/3.0.21 LibVLC/3.0.21'}

print("[Xtream Generator] Fetching categories from portal5458.com...")
req_cat = urllib.request.Request(url_cat, headers=headers)
with urllib.request.urlopen(req_cat, timeout=15) as resp:
    categories = json.loads(resp.read().decode('utf-8'))

print(f"[Xtream Generator] Retrived {len(categories)} categories.")

print("[Xtream Generator] Fetching live stream channels from portal5458.com...")
req_stream = urllib.request.Request(url_stream, headers=headers)
with urllib.request.urlopen(req_stream, timeout=25) as resp:
    streams = json.loads(resp.read().decode('utf-8'))

print(f"[Xtream Generator] Retrieved {len(streams)} live stream channels.")

cat_map = {c['category_id']: c['category_name'] for c in categories if isinstance(c, dict)} if isinstance(categories, list) else {}

m3u_lines = ['#EXTM3U']
if isinstance(streams, list):
    for s in streams:
        if not isinstance(s, dict):
            continue
        name = s.get('name', 'Unknown Channel').replace('"', "'")
        stream_id = s.get('stream_id')
        logo = s.get('stream_icon', '')
        cat_id = s.get('category_id')
        group = cat_map.get(cat_id, 'General').replace('"', "'")
        url = f'http://portal5458.com:8080/live/SAPPTV12/REMOTE6202/{stream_id}.m3u8'
        m3u_lines.append(f'#EXTINF:-1 tvg-id="" tvg-name="{name}" tvg-logo="{logo}" group-title="{group}",{name}')
        m3u_lines.append(url)

out_path = os.path.join('public', 'xtream_feed.m3u')
if len(m3u_lines) > 1:
    with open(out_path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(m3u_lines))
    print(f"[Xtream Generator] Successfully generated {out_path} with {len(m3u_lines)//2} authentic channels!")
else:
    print("[Xtream Generator] Preserving existing static xtream_feed.m3u and updating credential URLs...")
    with open(out_path, 'r', encoding='utf-8') as f:
        content = f.read()
    import re
    content = re.sub(r'/live/[^/]+/[^/]+/', '/live/SAPPTV12/REMOTE6202/', content)
    with open(out_path, 'w', encoding='utf-8') as f:
        f.write(content)
    print("[Xtream Generator] Successfully updated xtream_feed.m3u credentials to active account!")
