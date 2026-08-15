// Xtream Codes VOD Client Service
// Works with both local Xtream Codes emulator and remote Xtream servers

export class XtreamVODClient {
  constructor(baseUrl = '') {
    this.baseUrl = baseUrl || (window.location.origin.includes('localhost') || window.location.origin.includes('127.0.0.1') ? '' : '');
    this.username = localStorage.getItem('xtream_username') || 'LocalUser';
    this.password = localStorage.getItem('xtream_password') || 'password';
  }

  async fetchApi(action, params = {}) {
    const query = new URLSearchParams({
      username: this.username,
      password: this.password,
      action,
      ...params
    });

    const url = `${this.baseUrl}/player_api.php?${query.toString()}`;
    const res = await fetch(url);
    if (!res.ok) {
      throw new Error(`Xtream API error (${action}): ${res.status}`);
    }
    return res.json();
  }

  async getMovieCategories() {
    return this.fetchApi('get_vod_categories');
  }

  async getMovies(categoryId = null) {
    const params = categoryId ? { category_id: categoryId } : {};
    return this.fetchApi('get_vod_streams', params);
  }

  async getMovieInfo(vodId) {
    return this.fetchApi('get_vod_info', { vod_id: vodId });
  }

  async getSeriesCategories() {
    return this.fetchApi('get_series_categories');
  }

  async getSeries(categoryId = null) {
    const params = categoryId ? { category_id: categoryId } : {};
    return this.fetchApi('get_series', params);
  }

  async getSeriesInfo(seriesId) {
    return this.fetchApi('get_series_info', { series_id: seriesId });
  }

  getMovieStreamUrl(streamId, ext = 'mp4') {
    return `${this.baseUrl}/movie/${this.username}/${this.password}/${streamId}.${ext}`;
  }

  getSeriesStreamUrl(streamId, ext = 'mp4') {
    return `${this.baseUrl}/series/${this.username}/${this.password}/${streamId}.${ext}`;
  }
}

export const xtreamVOD = new XtreamVODClient();
