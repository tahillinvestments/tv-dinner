const user = 'SGmUC7q2U';
const pass = '4WM9WVsjG';
const portal = 'http://portal5458.com:8080';

async function testApi() {
  console.log('Testing player_api.php live streams...');
  try {
    const res = await fetch(`${portal}/player_api.php?username=${user}&password=${pass}&action=get_live_streams`);
    console.log('Live streams status:', res.status);
    const json = await res.json();
    console.log('Is array:', Array.isArray(json));
    console.log('Count:', Array.isArray(json) ? json.length : Object.keys(json));
    if (Array.isArray(json) && json.length > 0) {
      console.log('Sample channel:', json[0]);
    }
  } catch (e) {
    console.error('Error fetching live streams:', e);
  }

  console.log('\nTesting player_api.php live categories...');
  try {
    const res = await fetch(`${portal}/player_api.php?username=${user}&password=${pass}&action=get_live_categories`);
    console.log('Categories status:', res.status);
    const json = await res.json();
    console.log('Is array:', Array.isArray(json));
    console.log('Count:', Array.isArray(json) ? json.length : Object.keys(json));
    if (Array.isArray(json) && json.length > 0) {
      console.log('Sample category:', json[0]);
    }
  } catch (e) {
    console.error('Error fetching categories:', e);
  }
}

testApi();
