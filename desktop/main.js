const { app, BrowserWindow, ipcMain, session, Menu, clipboard } = require('electron');
const path = require('path');

const PARTITION = 'persist:eol';
const ICON = path.join(__dirname, 'renderer', 'icon.png');
let win = null;
let rendererReady = false;
let pendingUrls = [];

// URL passati da riga di comando (es. quando è il browser predefinito)
const urlFromArgs = argv => argv.filter(a => /^https?:\/\//i.test(a));
pendingUrls = urlFromArgs(process.argv);

const send = (channel, ...args) => {
  if (win && !win.isDestroyed()) win.webContents.send(channel, ...args);
};
const openUrl = url => (rendererReady ? send('new-tab', url) : pendingUrls.push(url));

// Scorciatoie da tastiera, valide anche quando il focus è dentro la pagina web
function shortcutFor(i) {
  const k = (i.key || '').toLowerCase();
  const ctrl = i.control || i.meta;
  if (i.alt && !ctrl) {
    if (k === 'arrowleft') return 'back';
    if (k === 'arrowright') return 'forward';
    if (k === 'home') return 'home';
    return null;
  }
  if (k === 'f5') return 'reload';
  if (k === 'f11') return 'fullscreen';
  if (k === 'f12') return 'devtools';
  if (!ctrl) return null;
  if (k === 'tab') return i.shift ? 'prevtab' : 'nexttab';
  if (i.shift && k === 'i') return 'devtools';
  if (i.shift) return null;
  switch (k) {
    case 't': return 'newtab';
    case 'w': return 'closetab';
    case 'l': return 'focusurl';
    case 'd': return 'fav';
    case 'r': return 'reload';
    case 'f': return 'find';
    case 'h': return 'history';
    case '+': case '=': return 'zoomin';
    case '-': return 'zoomout';
    case '0': return 'zoomreset';
    default: return null;
  }
}
function hookShortcuts(contents) {
  contents.on('before-input-event', (event, input) => {
    if (input.type !== 'keyDown') return;
    const name = shortcutFor(input);
    if (name) { event.preventDefault(); send('shortcut', name); }
  });
}

function createWindow() {
  win = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 520,
    minHeight: 420,
    backgroundColor: '#0e1f2b',
    title: 'EOL Explorer',
    icon: ICON,
    titleBarStyle: 'hidden',
    titleBarOverlay: { color: '#0e1f2b', symbolColor: '#ffffff', height: 34 },
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      webviewTag: true,
      contextIsolation: true,
      nodeIntegration: false
    }
  });
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
  hookShortcuts(win.webContents);
  // L'interfaccia non deve mai navigare da sola né aprire finestre
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  win.webContents.on('will-navigate', e => e.preventDefault());
  win.on('closed', () => { win = null; rendererReady = false; });
}

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', (_e, argv) => {
    if (win) { if (win.isMinimized()) win.restore(); win.focus(); }
    urlFromArgs(argv).forEach(openUrl);
  });

  app.on('web-contents-created', (_e, contents) => {
    if (contents.getType() !== 'webview') return;
    hookShortcuts(contents);

    // Link in nuova scheda; le finestre "vere" (es. login) restano finestre
    contents.setWindowOpenHandler(({ url, disposition }) => {
      if (disposition === 'new-window') {
        return { action: 'allow', overrideBrowserWindowOptions: { autoHideMenuBar: true, icon: ICON } };
      }
      if (/^https?:/i.test(url)) send('new-tab', url);
      return { action: 'deny' };
    });

    // Menu contestuale (tasto destro)
    contents.on('context-menu', (_ev, p) => {
      const items = [];
      if (p.linkURL) {
        items.push(
          { label: 'Apri link in una nuova scheda', click: () => send('new-tab', p.linkURL) },
          { label: 'Copia indirizzo del link', click: () => clipboard.writeText(p.linkURL) },
          { type: 'separator' }
        );
      }
      if (p.mediaType === 'image' && p.srcURL) {
        items.push(
          { label: 'Apri immagine in una nuova scheda', click: () => send('new-tab', p.srcURL) },
          { label: 'Copia indirizzo immagine', click: () => clipboard.writeText(p.srcURL) },
          { type: 'separator' }
        );
      }
      if (p.isEditable) {
        items.push(
          { label: 'Taglia', click: () => contents.cut() },
          { label: 'Copia', click: () => contents.copy() },
          { label: 'Incolla', click: () => contents.paste() },
          { label: 'Seleziona tutto', click: () => contents.selectAll() },
          { type: 'separator' }
        );
      } else if (p.selectionText) {
        items.push({ label: 'Copia', click: () => contents.copy() }, { type: 'separator' });
      }
      items.push(
        { label: 'Indietro', enabled: contents.canGoBack(), click: () => contents.goBack() },
        { label: 'Avanti', enabled: contents.canGoForward(), click: () => contents.goForward() },
        { label: 'Aggiorna', click: () => contents.reload() },
        { type: 'separator' },
        { label: 'Ispeziona elemento', click: () => contents.inspectElement(p.x, p.y) }
      );
      Menu.buildFromTemplate(items).popup({ window: win });
    });
  });

  app.whenReady().then(() => {
    if (process.platform === 'darwin') {
      Menu.setApplicationMenu(Menu.buildFromTemplate([{ role: 'appMenu' }, { role: 'editMenu' }, { role: 'windowMenu' }]));
    } else {
      Menu.setApplicationMenu(null);
    }

    const ses = session.fromPartition(PARTITION);
    // Toglie "Electron" dallo user agent: molti siti lo bloccano
    ses.setUserAgent(ses.getUserAgent().replace(/\s?Electron\/\S+/i, '').replace(/\s?eol-explorer\/\S+/i, ''));
    const allowed = ['fullscreen', 'clipboard-sanitized-write', 'media', 'pointerLock', 'notifications'];
    ses.setPermissionRequestHandler((_wc, permission, cb) => cb(allowed.includes(permission)));

    ipcMain.handle('fullscreen', (_e, on) => { if (win) win.setFullScreen(on); });
    ipcMain.on('renderer-ready', () => {
      rendererReady = true;
      pendingUrls.forEach(u => send('new-tab', u));
      pendingUrls = [];
    });

    createWindow();
    app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
  });

  app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
}
