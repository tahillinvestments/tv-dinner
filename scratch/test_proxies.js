const user = 'SGmUC7q2U';
const pass = '4WM9WVsjG';
const targetApi = `http://portal5458.com:8080/player_api.php?username=${user}&password=${pass}&action=get_live_streams`;

const proxies = [
  `https://tv-dinner-proxy.onrender.com/?url=${encodeURIComponent(targetApi)}`,
  `https://corsproxy.io/?${encodeURIComponent(targetApi)}`
];

async function testProxies() {
  for (const px of proxies) {
    console.log(`\nTesting proxy: ${px}`);
    try {
      const res = await fetch(px);
      console.log(`  Status: ${res.status}`);
      if (res.ok) {
        const data = await res.json();
        console.log(`  SUCCESS! Received ${Array.isArray(data) ? data.length : typeof data} streams`);
      } else {
        const errText = await res.text();
        console.log(`  FAIL! Body: ${errText.slice(0, 150)}`);
      }
    } catch (e) {
      console.log(`  ERROR: ${e.message}`);
    }
  }
}

testProxies();
