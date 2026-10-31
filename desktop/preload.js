// The page is the same web app that runs in a browser, plus one thing only the desktop app can do: say which
// remote-control programs (AnyDesk, TeamViewer, ...) are running on this computer, so a secure test can warn about them.
// A browser has no way to look, so there the page finds no `sutraDesktop` and simply does not check.
const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('sutraDesktop', {
  /** Resolves to the names of remote-control programs found running; an empty list when none. */
  remoteTools: () => ipcRenderer.invoke('remote-tools'),
})
