const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('eol', {
  platform: process.platform,
  onNewTab: cb => ipcRenderer.on('new-tab', (_e, url) => cb(url)),
  onShortcut: cb => ipcRenderer.on('shortcut', (_e, name) => cb(name)),
  setFullscreen: on => ipcRenderer.invoke('fullscreen', !!on),
  ready: () => ipcRenderer.send('renderer-ready')
});
