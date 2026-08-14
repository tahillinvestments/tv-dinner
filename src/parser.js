/**
 * Utility class to parse and load M3U IPTV playlists.
 */
export async function fetchAndParseM3U(url) {
  try {
    const response = await fetch(url);
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    const text = await response.text();
    return parseM3U(text);
  } catch (error) {
    console.error("Failed to fetch M3U playlist:", error);
    throw error;
  }
}

/**
 * Parses the raw content of an M3U file into a structured array of channel objects.
 * @param {string} rawContent 
 * @returns {Array<Object>}
 */
export function parseM3U(rawContent) {
  const lines = rawContent.split(/\r?\n/);
  const channels = [];
  let currentMetadata = null;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    
    if (line.startsWith('#EXTINF:')) {
      currentMetadata = parseExtInf(line);
    } else if (line && !line.startsWith('#')) {
      // This is the URL line for the preceding metadata
      if (currentMetadata) {
        currentMetadata.url = line;
        // Extract numeric stream_id from Xtream URL: /live/user/pass/<id>.m3u8, /<id>.ts, or query params
        const streamIdMatch = line.match(/\/(\d+)(?:\.(?:m3u8|ts|mp4))?(?:$|\?)/) || line.match(/[?&]stream_id=(\d+)/);
        if (streamIdMatch) {
          currentMetadata.stream_id = streamIdMatch[1];
        }
        channels.push(currentMetadata);
        currentMetadata = null;
      }
    }
  }

  return channels;
}

/**
 * Parses metadata out of a single #EXTINF line.
 * Example line:
 * #EXTINF:-1 tvg-id="CNN.us" tvg-name="CNN" tvg-logo="https://example.com/cnn.png" group-title="News",CNN US HD
 */
function parseExtInf(line) {
  const metadata = {
    id: '',
    name: '',
    logo: '',
    group: 'General', // Default group if not specified
    url: '',
    epg_channel_id: '',
    tvg_id: '',
    tvg_name: '',
    stream_id: ''
  };

  // Find the comma that separates tags from the channel display name
  const commaIndex = line.lastIndexOf(',');
  if (commaIndex !== -1) {
    metadata.name = line.substring(commaIndex + 1).trim();
  }

  // Helper function to extract attribute values using regex
  const extractAttribute = (attribute) => {
    // Regex matches attribute="value"
    const regex = new RegExp(`${attribute}=\"([^\"]*)\"`, 'i');
    const match = line.match(regex);
    return match ? match[1].trim() : null;
  };

  const tvgId = extractAttribute('tvg-id') || extractAttribute('tvg_id') || extractAttribute('epg-id') || '';
  const tvgName = extractAttribute('tvg-name') || extractAttribute('tvg_name') || '';
  const tvgLogo = extractAttribute('tvg-logo') || extractAttribute('tvg_logo') || extractAttribute('logo') || '';
  const groupTitle = extractAttribute('group-title') || extractAttribute('group_title') || 'General';

  metadata.id = tvgId || tvgName || metadata.name.toLowerCase().replace(/\s+/g, '-');
  metadata.epg_channel_id = tvgId || '';
  metadata.tvg_id = tvgId || '';
  metadata.tvg_name = tvgName || '';
  metadata.logo = tvgLogo;
  metadata.group = (!groupTitle || groupTitle.trim() === '') ? 'General' : groupTitle.trim();

  return metadata;
}
