import { parseM3U } from '../src/parser.js';

const user = 'SGmUC7q2U';
const pass = '4WM9WVsjG';
const portalUrl = 'http://portal5458.com:8080';

async function testPipeline() {
  console.log('Testing full fetch & parse pipeline...');
  const apiUrl = `${portalUrl}/player_api.php?username=${encodeURIComponent(user)}&password=${encodeURIComponent(pass)}&action=get_live_streams`;
  const catUrl = `${portalUrl}/player_api.php?username=${encodeURIComponent(user)}&password=${encodeURIComponent(pass)}&action=get_live_categories`;

  const [streamsRes, catsRes] = await Promise.all([
    fetch(apiUrl),
    fetch(catUrl)
  ]);

  const streams = await streamsRes.json();
  const cats = await catsRes.json();
  
  let categoriesMap = {};
  if (Array.isArray(cats)) {
    cats.forEach(c => { categoriesMap[c.category_id] = c.category_name; });
  }

  let m3uLines = ['#EXTM3U'];
  streams.forEach(s => {
    const catName = categoriesMap[s.category_id] || 'Live TV';
    const streamUrl = `${portalUrl}/live/${user}/${pass}/${s.stream_id}.ts`;
    m3uLines.push(`#EXTINF:-1 tvg-id="${s.epg_channel_id || ''}" tvg-name="${s.name || ''}" tvg-logo="${s.stream_icon || ''}" group-title="${catName}",${s.name}`);
    m3uLines.push(streamUrl);
  });

  const rawM3U = m3uLines.join('\n');
  const channels = parseM3U(rawM3U);

  console.log('Parsed channel count:', channels.length);
  console.log('Sample channel 0:', channels[0]);
  console.log('Sample channel 100:', channels[100]);
}

testPipeline();
