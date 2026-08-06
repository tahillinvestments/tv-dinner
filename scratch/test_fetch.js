const creds = [
  { user: 'SGmUC7q2U', pass: '4WM9WVsjG' },
  { user: 'MW2Y2h6e7', pass: '5DwU7wTuA' },
  { user: 'Hn9a6bus9', pass: 'JaKXrfMP7' },
  { user: 'TONE01', pass: 'TV4LIFE' },
  { user: 'SAPPTV12', pass: 'REMOTE6202' },
  { user: 'DAMETV', pass: '2611596317' }
];

const portals = [
  'http://portal5458.com:8080',
  'http://portal5458.com',
];

async function testAll() {
  for (const p of portals) {
    for (const c of creds) {
      console.log(`\nTesting portal: ${p} | user: ${c.user}`);
      const testUrls = [
        `${p}/get.php?username=${c.user}&password=${c.pass}&type=m3u_plus&output=ts`,
        `${p}/get.php?username=${c.user}&password=${c.pass}&type=m3u_plus`,
        `${p}/player_api.php?username=${c.user}&password=${c.pass}`,
        `${p}/xmltv.php?username=${c.user}&password=${c.pass}`
      ];

      for (const u of testUrls) {
        try {
          const res = await fetch(u, { signal: AbortSignal.timeout(4000) });
          console.log(`  URL: ${u} -> Status: ${res.status}`);
          if (res.ok) {
            const text = await res.text();
            console.log(`    SUCCESS! Length: ${text.length}, Preview: ${text.slice(0, 100)}`);
          }
        } catch (e) {
          console.log(`  URL: ${u} -> Error: ${e.message}`);
        }
      }
    }
  }
}

testAll();
