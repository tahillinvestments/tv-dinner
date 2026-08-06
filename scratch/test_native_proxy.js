const user = 'SGmUC7q2U';
const pass = '4WM9WVsjG';
const portal = 'http://portal5458.com:8080';

async function testNativeFetch() {
  const apiUrl = `${portal}/player_api.php?username=${user}&password=${pass}&action=get_live_streams`;
  const catUrl = `${portal}/player_api.php?username=${user}&password=${pass}&action=get_live_categories`;

  console.log('Fetching live streams directly with VLC User-Agent...');
  const res = await fetch(apiUrl, {
    headers: { 'user-agent': 'VLC/3.0.21 LibVLC/3.0.21' }
  });

  console.log('Status:', res.status);
  const json = await res.json();
  console.log('Total streams returned for user:', Array.isArray(json) ? json.length : 0);

  const catRes = await fetch(catUrl, {
    headers: { 'user-agent': 'VLC/3.0.21 LibVLC/3.0.21' }
  });
  const catJson = await catRes.json();
  console.log('Total categories returned:', Array.isArray(catJson) ? catJson.length : 0);
}

testNativeFetch();
