const { app, BrowserWindow, desktopCapturer, protocol, net, session, shell } = require('electron')
const path = require('node:path')
const fs = require('node:fs')
const { pathToFileURL } = require('node:url')

// The web app is served from its own origin, itilms://app, so its routes (/app/..., /login) work like on a website.
// The gateway must allow this origin (api-gateway.yml, allowedOriginPatterns).
const SCHEME = 'itilms'
const ORIGIN = `${SCHEME}://app`
const WEB_ROOT = path.join(__dirname, 'app')

protocol.registerSchemesAsPrivileged([
  { scheme: SCHEME, privileges: { standard: true, secure: true, supportFetchAPI: true, stream: true, corsEnabled: true } },
])

function serveApp() {
  protocol.handle(SCHEME, (request) => {
    const url = new URL(request.url)
    let file = path.normalize(path.join(WEB_ROOT, decodeURIComponent(url.pathname)))
    // Nothing outside the app folder, and any address that is not a file is a page of the single-page app.
    if (!file.startsWith(WEB_ROOT)) return new Response('Not found', { status: 404 })
    if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) file = path.join(WEB_ROOT, 'index.html')
    return net.fetch(pathToFileURL(file).toString())
  })
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 900,
    minHeight: 600,
    title: 'SutraVidya',
    autoHideMenuBar: true,
    backgroundColor: '#ffffff',
    icon: path.join(__dirname, 'build', 'icon.png'),
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, sandbox: true },
  })
  // The page sets its own title (the institute's name); the window keeps the product's name.
  win.on('page-title-updated', (event) => event.preventDefault())
  win.loadURL(`${ORIGIN}/`)

  // Links to other sites open in the browser, not inside the app.
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/.test(url)) void shell.openExternal(url)
    return { action: 'deny' }
  })
  win.webContents.on('will-navigate', (event, url) => {
    if (!url.startsWith(ORIGIN)) {
      event.preventDefault()
      if (/^https?:/.test(url)) void shell.openExternal(url)
    }
  })
}

app.whenReady().then(() => {
  serveApp()

  // Camera, microphone, fullscreen and screen sharing are what tests and live classes need - for this app only.
  const allowed = new Set(['media', 'fullscreen', 'display-capture', 'clipboard-sanitized-write', 'notifications'])
  session.defaultSession.setPermissionRequestHandler((contents, permission, callback) => {
    callback(contents.getURL().startsWith(ORIGIN) && allowed.has(permission))
  })
  session.defaultSession.setPermissionCheckHandler((contents, permission) => {
    return (contents?.getURL() ?? '').startsWith(ORIGIN) && allowed.has(permission)
  })
  // A trainer sharing a screen in a live class: the first screen is shared.
  session.defaultSession.setDisplayMediaRequestHandler((request, callback) => {
    desktopCapturer.getSources({ types: ['screen'] }).then((sources) => callback({ video: sources[0] }))
  })

  createWindow()
  app.on('activate', () => BrowserWindow.getAllWindows().length === 0 && createWindow())
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
