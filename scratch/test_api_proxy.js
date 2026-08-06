import http from 'http';

const user = 'SGmUC7q2U';
const pass = '4WM9WVsjG';
const portalUrl = 'http://portal5458.com:8080';
const apiUrl = `${portalUrl}/player_api.php?username=${encodeURIComponent(user)}&password=${encodeURIComponent(pass)}&action=get_live_streams`;

async function testApiProxy() {
  console.log("Testing fetch from /api/proxy url...");
  const encoded = encodeURIComponent(apiUrl);
  console.log("Encoded URL parameter:", encoded);
  
  // Test direct fetch with VLC UA (simulating what vite.config.js / api/proxy.js does)
  const res = await fetch(apiUrl, {
    headers: { 'user-agent': 'VLC/3.0.21 LibVLC/3.0.21' }
  });
  console.log("Direct status:", res.status);
  if (res.ok) {
    const json = await res.json();
    console.log("Channels count:", json.length);
  }
}

testApiProxy();
