(() => {
  'use strict';
  const $ = s => document.querySelector(s);
  const HOME = 'eol:home';
  const PARTITION = 'persist:eol';

  const ENGINES = {
    google: {name: 'Google',          url: 'https://www.google.com/search?q=%s'},
    ddg:    {name: 'DuckDuckGo',      url: 'https://duckduckgo.com/?q=%s'},
    bing:   {name: 'Bing',            url: 'https://www.bing.com/search?q=%s'},
    wiki:   {name: 'Wikipedia',       url: 'https://it.wikipedia.org/w/index.php?search=%s'},
    images: {name: 'Google Immagini', url: 'https://www.google.com/search?tbm=isch&q=%s'},
    video:  {name: 'YouTube',         url: 'https://www.youtube.com/results?search_query=%s'},
    news:   {name: 'Google Notizie',  url: 'https://www.google.com/search?tbm=nws&q=%s'},
    maps:   {name: 'Google Maps',     url: 'https://www.google.com/maps/search/%s'}
  };
  const CATS = [['Web','google'],['Immagini','images'],['Video','video'],['Notizie','news'],['Mappe','maps']];
  const TILES = {
    'Esplora':   [['🔎','Google','https://www.google.com/'],['▶️','YouTube','https://www.youtube.com/'],['📚','Wikipedia','https://it.wikipedia.org/'],['🗺️','Mappe','https://www.openstreetmap.org/']],
    'Retro':     [['🕰️','Wayback Machine','https://web.archive.org/'],['🕹️','Internet Arcade','https://archive.org/details/internetarcade'],['🏚️','Neocities','https://neocities.org/browse'],['🏀','Space Jam 1996','https://www.spacejam.com/1996/']],
    'Svago':     [['📻','Radio Garden','https://radio.garden/'],['🎬','IMDb','https://www.imdb.com/'],['👾','Reddit','https://www.reddit.com/'],['🎮','Twitch','https://www.twitch.tv/']],
    'Strumenti': [['🌍','Traduttore','https://translate.google.com/'],['✉️','Gmail','https://mail.google.com/'],['💬','WhatsApp Web','https://web.whatsapp.com/'],['📖','Wikizionario','https://it.wiktionary.org/']]
  };

  /* ---------- dati salvati ---------- */
  let S = {
    favs: [{t:'Google', u:'https://www.google.com/'}, {t:'Wikipedia', u:'https://it.wikipedia.org/'}, {t:'Wayback Machine', u:'https://web.archive.org/'}],
    hist: [], theme: 'eol', engine: 'google', tm: false, visits: 0
  };
  try { Object.assign(S, JSON.parse(localStorage.getItem('eol-explorer') || '{}')); } catch (e) {}
  const save = () => { try { localStorage.setItem('eol-explorer', JSON.stringify(S)); } catch (e) {} };

  let tabs = [], cur = null, uid = 0, panelMode = null;
  let homeCat = ENGINES[S.engine] ? S.engine : 'google', homeTab = 'Esplora';

  const host = u => { try { return new URL(u).hostname.replace(/^www\./, ''); } catch (e) { return u; } };
  const status = m => { $('#stat').textContent = m; };

  /* ---------- schede ---------- */
  function newTab(url, activate = true){
    const t = {id: ++uid, wv: null, home: true, url: '', title: 'Pagina iniziale', icon: '', loading: false, ready: false, error: null, zoom: 0};
    tabs.push(t);
    if (activate || !cur) cur = t;
    if (url && url !== HOME) navigate(t, url); else refresh();
    return t;
  }
  function switchTab(id){ const t = tabs.find(x => x.id === id); if (t){ cur = t; closeFind(); refresh(); } }
  function closeTab(id){
    const i = tabs.findIndex(t => t.id === id); if (i < 0) return;
    const t = tabs[i]; if (t.wv) t.wv.remove();
    tabs.splice(i, 1);
    if (!tabs.length){ cur = null; newTab(); return; }
    if (cur === t) cur = tabs[Math.min(i, tabs.length - 1)];
    refresh();
  }
  function makeView(t, src){
    const wv = document.createElement('webview');
    wv.setAttribute('partition', PARTITION);
    wv.setAttribute('allowpopups', '');
    wv.setAttribute('src', src);
    t.wv = wv;
    wv.addEventListener('dom-ready', () => { t.ready = true; try { wv.setZoomLevel(t.zoom); } catch (e) {} refresh(); });
    wv.addEventListener('did-start-loading', () => { t.loading = true; t.error = null; refresh(); });
    wv.addEventListener('did-stop-loading', () => { t.loading = false; if (t === cur) status('Fatto'); refresh(); });
    wv.addEventListener('did-navigate', e => { t.url = e.url; t.error = null; addHistory(e.url); refresh(); });
    wv.addEventListener('did-navigate-in-page', e => { if (e.isMainFrame){ t.url = e.url; refresh(); } });
    wv.addEventListener('page-title-updated', e => { t.title = e.title || host(t.url); updateHistoryTitle(t.url, t.title); refresh(); });
    wv.addEventListener('page-favicon-updated', e => { t.icon = (e.favicons && e.favicons[0]) || ''; refresh(); });
    wv.addEventListener('did-fail-load', e => {
      if (!e.isMainFrame || e.errorCode === -3) return;
      t.error = {desc: e.errorDescription || 'Errore di rete', url: e.validatedURL || t.url}; refresh();
    });
    wv.addEventListener('update-target-url', e => { if (t === cur) status(e.url || 'Fatto'); });
    wv.addEventListener('found-in-page', e => {
      if (t === cur && e.result) $('#fcount').textContent = e.result.matches ? (e.result.activeMatchOrdinal + '/' + e.result.matches) : '0';
    });
    wv.addEventListener('enter-html-full-screen', () => { document.body.classList.add('htmlfs'); window.eol.setFullscreen(true); });
    wv.addEventListener('leave-html-full-screen', () => { document.body.classList.remove('htmlfs'); window.eol.setFullscreen(false); });
    $('#stage').appendChild(wv);
  }
  function loadSrc(t, src){
    if (!t.wv) makeView(t, src);
    else if (t.ready){ t.wv.loadURL(src).catch(() => {}); }
    else t.wv.setAttribute('src', src);
  }
  function navigate(t, url){
    if (!url || url === HOME){ t.home = true; refresh(); return; }
    t.home = false; t.url = url; t.title = host(url); t.icon = ''; t.error = null;
    const src = (S.tm && !/web\.archive\.org/.test(url)) ? 'https://web.archive.org/web/1999/' + url : url;
    loadSrc(t, src);
    if (t === cur) status('Apertura di ' + host(url) + '…');
    refresh();
  }
  function resolve(text, engineKey){
    text = (text || '').trim(); if (!text) return null;
    if (text === HOME || text === 'about:home') return HOME;
    if (/^[a-z][a-z0-9+.-]*:\/\//i.test(text)) return text;
    if (/^(localhost|\d{1,3}(\.\d{1,3}){3})(:\d+)?(\/\S*)?$/i.test(text)) return 'http://' + text;
    if (/^[\w-]+(\.[\w-]+)+(:\d+)?(\/\S*)?$/i.test(text)) return 'https://' + text;
    return ENGINES[engineKey || S.engine].url.replace('%s', encodeURIComponent(text));
  }
  function openInput(text, engineKey){ const u = resolve(text, engineKey); if (u) navigate(cur, u); }

  const canBack = t => t.wv && !t.home;
  const canFwd = t => (t.home && !!t.wv) || (t.wv && t.ready && !t.home && t.wv.canGoForward());
  function back(){
    if (!cur.wv || cur.home) return;
    if (cur.ready && cur.wv.canGoBack()) cur.wv.goBack(); else { cur.home = true; refresh(); }
  }
  function forward(){
    if (cur.home && cur.wv){ cur.home = false; refresh(); }
    else if (cur.wv && cur.ready && cur.wv.canGoForward()) cur.wv.goForward();
  }
  function reload(){ if (cur.wv && !cur.home && cur.ready){ cur.error = null; cur.wv.reload(); } else if (cur.home) drawHome(); }
  function stop(){ if (cur.wv && cur.ready) cur.wv.stop(); }
  function zoom(delta){
    if (!cur.wv || cur.home) return;
    cur.zoom = delta === 0 ? 0 : Math.max(-3, Math.min(5, cur.zoom + delta));
    try { cur.wv.setZoomLevel(cur.zoom); } catch (e) {}
    refresh();
  }
  function addFav(){
    if (cur.home || !cur.url) return status('La pagina iniziale non si può aggiungere ai preferiti');
    if (!S.favs.some(f => f.u === cur.url)) S.favs.push({t: cur.title, u: cur.url});
    save(); status('Aggiunto ai preferiti: ' + cur.title); drawHome(); if (panelMode === 'fav') drawPanel();
  }
  function addHistory(u){
    if (!/^https?:/i.test(u)) return;
    S.hist = [{t: host(u), u}].concat(S.hist.filter(h => h.u !== u)).slice(0, 150); save();
    if (panelMode === 'hist') drawPanel();
  }
  function updateHistoryTitle(u, title){ const h = S.hist.find(x => x.u === u); if (h){ h.t = title; save(); } }

  /* ---------- ricerca nella pagina ---------- */
  function openFind(){
    if (!cur.wv || cur.home) return;
    $('#find').classList.add('open'); $('#fq').focus(); $('#fq').select();
  }
  function closeFind(){
    $('#find').classList.remove('open'); $('#fcount').textContent = '';
    tabs.forEach(t => { if (t.wv && t.ready) try { t.wv.stopFindInPage('clearSelection'); } catch (e) {} });
  }
  function findNext(forward){
    const q = $('#fq').value; if (!q || !cur.wv || !cur.ready) return;
    cur.wv.findInPage(q, {forward, findNext: true});
  }

  /* ---------- disegno interfaccia ---------- */
  function refresh(){
    if (!cur) return;
    const bar = $('#tabs'); bar.textContent = '';
    tabs.forEach(t => {
      const d = document.createElement('div'); d.className = 'tab raised' + (t === cur ? ' active' : ''); d.title = t.title;
      if (t.icon && !t.home){ const im = document.createElement('img'); im.src = t.icon; im.alt = ''; im.onerror = () => { im.replaceWith(document.createTextNode('🌐')); }; d.appendChild(im); }
      else { const s = document.createElement('span'); s.textContent = t.home ? '🏠' : '🌐'; d.appendChild(s); }
      const tt = document.createElement('span'); tt.className = 'tt'; tt.textContent = t.home ? 'Pagina iniziale' : t.title;
      const x = document.createElement('button'); x.className = 'x'; x.textContent = '✕'; x.setAttribute('aria-label', 'Chiudi scheda');
      x.onclick = e => { e.stopPropagation(); closeTab(t.id); };
      d.append(tt, x);
      d.onclick = () => switchTab(t.id);
      d.onauxclick = e => { if (e.button === 1) closeTab(t.id); };
      bar.appendChild(d);
    });
    const plus = document.createElement('button'); plus.id = 'newtab'; plus.className = 'btn'; plus.textContent = '+'; plus.title = 'Nuova scheda (Ctrl+T)';
    plus.onclick = () => newTab(); bar.appendChild(plus);

    tabs.forEach(t => { if (t.wv) t.wv.classList.toggle('hidden', !(t === cur && !t.home)); });
    const h = $('#home'); if (h) h.style.display = cur.home ? 'block' : 'none';
    const e = $('#err');
    e.classList.toggle('open', !!cur.error && !cur.home);
    if (cur.error){ $('#errmsg').textContent = cur.error.desc; $('#errurl').textContent = cur.error.url; }

    if (document.activeElement !== $('#url')) $('#url').value = cur.home ? '' : cur.url;
    const title = (cur.home ? 'Pagina iniziale' : cur.title) + ' - EOL Explorer';
    $('#ttl').textContent = title; document.title = title;
    $('#back').disabled = !canBack(cur); $('#fwd').disabled = !canFwd(cur);
    $('#stop').disabled = !cur.loading;
    $('#throb').classList.toggle('busy', cur.loading);
    $('#sec').textContent = cur.home ? '🏠 Locale' : (/^https:/.test(cur.url) ? '🔒 Connessione sicura' : '🌐 Internet');
    $('#zoomst').textContent = Math.round(Math.pow(1.2, cur.zoom) * 100) + '%';
    $('#panelBtn').classList.toggle('on', !!panelMode);
    document.querySelectorAll('.rb').forEach(b => b.classList.toggle('on', b.dataset.p === panelMode));
  }

  function row(icon, label, onclick, onDel){
    const r = document.createElement('div'); r.className = 'row';
    const i = document.createElement('span'); i.textContent = icon;
    const t = document.createElement('span'); t.className = 'rt'; t.textContent = label; t.title = label;
    r.append(i, t); r.onclick = onclick;
    if (onDel){ const d = document.createElement('button'); d.className = 'rd'; d.textContent = '✕'; d.setAttribute('aria-label', 'Rimuovi');
      d.onclick = e => { e.stopPropagation(); onDel(); }; r.appendChild(d); }
    return r;
  }
  function openPanel(mode){ panelMode = panelMode === mode ? null : mode; drawPanel(); refresh(); }
  function closePanel(){ panelMode = null; drawPanel(); refresh(); }
  function drawPanel(){
    $('#panel').classList.toggle('open', !!panelMode);
    if (!panelMode) return;
    const b = $('#pbody'); b.textContent = '';
    $('#ptitle').textContent = {fav: 'Preferiti', hist: 'Cronologia', chan: 'Canali'}[panelMode];
    if (panelMode === 'fav'){
      if (!S.favs.length){ const d = document.createElement('div'); d.className = 'empty'; d.textContent = 'Nessun preferito. Apri una pagina e premi Ctrl+D.'; b.appendChild(d); }
      S.favs.forEach((f, i) => b.appendChild(row('⭐', f.t, () => navigate(cur, f.u), () => { S.favs.splice(i, 1); save(); drawPanel(); drawHome(); })));
    } else if (panelMode === 'hist'){
      if (!S.hist.length){ const d = document.createElement('div'); d.className = 'empty'; d.textContent = 'La cronologia è vuota.'; b.appendChild(d); }
      S.hist.forEach(h => b.appendChild(row('🕘', h.t, () => navigate(cur, h.u))));
    } else {
      Object.entries(TILES).forEach(([g, items]) => {
        const h = document.createElement('div'); h.className = 'grp'; h.textContent = g; b.appendChild(h);
        items.forEach(([ic, name, u]) => b.appendChild(row(ic, name, () => navigate(cur, u))));
      });
    }
  }

  /* ---------- pagina iniziale ---------- */
  function drawHome(){
    if (!$('#home')){
      $('#stage').appendChild($('#homeTpl').content.cloneNode(true));
      $('#hform').onsubmit = e => { e.preventDefault(); const q = $('#hq').value; $('#hq').value = ''; openInput(q, homeCat); };
      $('#tmBtn').onclick = () => toggleTM();
      const h = new Date().getHours();
      $('#greet').textContent = h < 12 ? 'Buongiorno' : (h < 18 ? 'Buon pomeriggio' : 'Buonasera');
      $('#hdate').textContent = new Date().toLocaleDateString('it-IT', {weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'});
    }
    const cats = $('#cats'); cats.textContent = '';
    CATS.forEach(([n, k]) => { const b = document.createElement('button'); b.type = 'button'; b.textContent = n; b.className = homeCat === k ? 'on' : '';
      b.onclick = () => { homeCat = k; drawHome(); $('#hq').focus(); }; cats.appendChild(b); });
    const tb = $('#htabs'); tb.textContent = '';
    Object.keys(TILES).forEach(n => { const b = document.createElement('button'); b.textContent = n; b.className = homeTab === n ? 'on' : '';
      b.onclick = () => { homeTab = n; drawHome(); }; tb.appendChild(b); });
    const tl = $('#htiles'); tl.textContent = '';
    TILES[homeTab].forEach(([ic, n, u]) => {
      const a = document.createElement('div'); a.className = 'tile raised';
      const i = document.createElement('b'); i.textContent = ic; const s = document.createElement('span'); s.textContent = n;
      a.append(i, s); a.onclick = () => navigate(cur, u); tl.appendChild(a);
    });
    const hf = $('#hfavs'); hf.textContent = '';
    if (!S.favs.length) hf.textContent = 'Ancora nessuno.';
    S.favs.slice(0, 6).forEach(f => hf.appendChild(row('⭐', f.t, () => navigate(cur, f.u))));
    $('#tmBtn').textContent = S.tm ? '⏪ Macchina del tempo: ATTIVA (disattiva)' : '⏪ Attiva la Macchina del tempo';
    $('#visits').textContent = String(S.visits).padStart(6, '0');
    if (cur) refresh();
  }
  function toggleTM(){
    S.tm = !S.tm; save(); drawHome();
    status(S.tm ? 'Macchina del tempo attiva: i siti si aprono come nel 1999' : 'Macchina del tempo disattivata');
  }

  /* ---------- menu ---------- */
  function setTheme(t){ S.theme = t; document.documentElement.dataset.theme = t === 'eol' ? '' : t; save(); }
  const ck = v => v ? '✓ ' : '';
  const MENUS = () => ({
    'File': [
      ['Nuova scheda', 'Ctrl+T', () => newTab()],
      ['Chiudi scheda', 'Ctrl+W', () => closeTab(cur.id)]
    ],
    'Modifica': [
      ['Copia indirizzo', '', () => { try { navigator.clipboard.writeText(cur.home ? '' : cur.url); status('Indirizzo copiato'); } catch (e) {} }],
      ['Seleziona indirizzo', 'Ctrl+L', () => { $('#url').focus(); $('#url').select(); }],
      ['Trova nella pagina', 'Ctrl+F', openFind]
    ],
    'Visualizza': [
      ['Aggiorna', 'F5', reload],
      ['Pagina iniziale', 'Alt+Home', () => navigate(cur, HOME)],
      '-',
      ['Ingrandisci', 'Ctrl++', () => zoom(0.5)],
      ['Riduci', 'Ctrl+-', () => zoom(-0.5)],
      ['Zoom normale', 'Ctrl+0', () => zoom(0)],
      '-',
      ['Schermo intero', 'F11', () => SHORTCUTS.fullscreen()]
    ],
    'Preferiti': [
      ['Aggiungi ai preferiti', 'Ctrl+D', addFav],
      ['Mostra preferiti', '', () => { panelMode = null; openPanel('fav'); }],
      ['Mostra cronologia', 'Ctrl+H', () => { panelMode = null; openPanel('hist'); }]
    ],
    'Strumenti': [
      [ck(S.tm) + 'Macchina del tempo (web del 1999)', '', toggleTM],
      '-',
      [ck(S.theme === 'eol') + 'Tema EOL', '', () => setTheme('eol')],
      [ck(S.theme === 'classic') + 'Tema Classic 98', '', () => setTheme('classic')],
      [ck(S.theme === 'aqua') + 'Tema Aqua 2000', '', () => setTheme('aqua')],
      [ck(S.theme === 'night') + 'Tema Notte neon', '', () => setTheme('night')],
      '-',
      ...['google', 'ddg', 'bing', 'wiki'].map(k => [ck(S.engine === k) + 'Cerca con ' + ENGINES[k].name, '', () => { S.engine = k; homeCat = k; save(); drawHome(); status('Motore predefinito: ' + ENGINES[k].name); }]),
      '-',
      ['Strumenti per sviluppatori', 'F12', () => { if (cur.wv && cur.ready) cur.wv.openDevTools(); }],
      ['Cancella cronologia', '', () => { S.hist = []; save(); drawPanel(); status('Cronologia cancellata'); }]
    ],
    '?': [
      ['Informazioni su EOL Explorer', '', () => alertBox('EOL Explorer', 'EOL Explorer 1.0<br><br>Il browser con l\'aspetto degli anni \'90 e il motore di oggi (Chromium).')]
    ]
  });
  function alertBox(title, html){
    const o = document.createElement('div');
    o.style.cssText = 'position:fixed;inset:0;z-index:90;background:rgba(0,0,0,.35);display:grid;place-items:center;padding:16px';
    o.innerHTML = '<div class="dlgbox raised" style="width:min(380px,100%)"><div class="dt"></div><div class="db"></div><div class="df"><button class="btn">OK</button></div></div>';
    o.querySelector('.dt').textContent = title; o.querySelector('.db').innerHTML = html;
    o.querySelector('button').onclick = () => o.remove();
    document.body.appendChild(o); o.querySelector('button').focus();
  }
  const pop = $('#pop');
  const hideMenu = () => { pop.style.display = 'none'; pop.dataset.for = ''; };
  function showMenu(btn, items){
    pop.textContent = '';
    items.forEach(it => {
      if (it === '-') return pop.appendChild(document.createElement('hr'));
      const d = document.createElement('div'); const a = document.createElement('span'); a.textContent = it[0];
      const k = document.createElement('small'); k.textContent = it[1]; d.append(a, k);
      d.onclick = () => { hideMenu(); it[2](); }; pop.appendChild(d);
    });
    pop.style.display = 'block';
    const r = btn.getBoundingClientRect();
    pop.style.left = Math.max(4, Math.min(r.left, innerWidth - pop.offsetWidth - 4)) + 'px';
    pop.style.top = r.bottom + 'px';
  }
  Object.keys(MENUS()).forEach(name => {
    const b = document.createElement('button'); b.className = 'btn flat'; b.textContent = name;
    b.onclick = e => { e.stopPropagation(); const open = pop.dataset.for === name; hideMenu(); if (!open){ showMenu(b, MENUS()[name]); pop.dataset.for = name; } };
    $('#menubar').appendChild(b);
  });
  document.addEventListener('click', hideMenu);

  /* ---------- eventi ---------- */
  $('#back').onclick = back; $('#fwd').onclick = forward; $('#stop').onclick = stop; $('#reload').onclick = reload;
  $('#homeBtn').onclick = () => navigate(cur, HOME);
  $('#panelBtn').onclick = () => openPanel(panelMode || 'fav');
  $('#pclose').onclick = closePanel;
  document.querySelectorAll('.rb').forEach(b => b.onclick = () => openPanel(b.dataset.p));
  $('#addr').onsubmit = e => { e.preventDefault(); const v = $('#url').value; $('#url').blur(); openInput(v); };
  $('#sbox').onsubmit = e => { e.preventDefault(); const v = $('#sq').value; $('#sq').value = ''; openInput(v); };
  $('#url').addEventListener('focus', e => e.target.select());
  $('#errretry').onclick = reload; $('#errhome').onclick = () => navigate(cur, HOME);
  $('#fq').addEventListener('input', () => { const q = $('#fq').value; if (q && cur.wv && cur.ready) cur.wv.findInPage(q); else $('#fcount').textContent = ''; });
  $('#fq').addEventListener('keydown', e => { if (e.key === 'Enter') findNext(!e.shiftKey); if (e.key === 'Escape') closeFind(); });
  $('#fnext').onclick = () => findNext(true); $('#fprev').onclick = () => findNext(false); $('#fclose').onclick = closeFind;

  let fs = false;
  const SHORTCUTS = {
    newtab: () => { newTab(); $('#url').focus(); },
    closetab: () => closeTab(cur.id),
    focusurl: () => { $('#url').focus(); $('#url').select(); },
    fav: addFav, reload, back, forward,
    home: () => navigate(cur, HOME),
    nexttab: () => switchTab(tabs[(tabs.indexOf(cur) + 1) % tabs.length].id),
    prevtab: () => switchTab(tabs[(tabs.indexOf(cur) - 1 + tabs.length) % tabs.length].id),
    zoomin: () => zoom(0.5), zoomout: () => zoom(-0.5), zoomreset: () => zoom(0),
    find: openFind,
    history: () => { panelMode = null; openPanel('hist'); },
    devtools: () => { if (cur.wv && cur.ready) cur.wv.openDevTools(); },
    fullscreen: () => { fs = !fs; window.eol.setFullscreen(fs); }
  };
  window.eol.onShortcut(name => { if (SHORTCUTS[name]) SHORTCUTS[name](); });
  window.eol.onNewTab(url => newTab(url));

  const tick = () => {
    const d = new Date();
    $('#clock').textContent = d.toLocaleTimeString('it-IT', {hour: '2-digit', minute: '2-digit'});
    const l = $('#lcd'); if (l) l.textContent = d.toLocaleTimeString('it-IT');
  };
  setInterval(tick, 1000);

  /* ---------- avvio ---------- */
  if (window.eol.platform === 'darwin') document.body.classList.add('mac');
  document.documentElement.dataset.theme = S.theme === 'eol' ? '' : S.theme;
  S.visits += 1; save();
  newTab(); drawHome(); tick();
  window.eol.ready();
})();
