const { app, BrowserWindow, desktopCapturer, ipcMain, protocol, net, session, shell } = require('electron')
const path = require('node:path')
const os = require('node:os')
const { execFile } = require('node:child_process')
const fs = require('node:fs')
const { pathToFileURL } = require('node:url')

// The web app is bundled in the app and served to the window under the web site's own address (build-web.mjs writes it
// to app-config.json). Cloudflare Turnstile, the human check on the login page, only runs on the host names it was
// registered for, and a custom scheme is none of them. Only requests to that one host are answered from the bundled
// files; everything else goes to the network as usual. Without a configured address the app falls back to itilms://app
// (then the gateway must allow that origin and the human check cannot run).
const SCHEME = 'itilms'
const WEB_ROOT = path.join(__dirname, 'app')

function readConfig() {
  try {
    return JSON.parse(fs.readFileSync(path.join(__dirname, 'app-config.json'), 'utf8'))
  } catch {
    return {}
  }
}
const CONFIG = readConfig()
const WEB = CONFIG.webUrl ? new URL(CONFIG.webUrl) : null
const API = CONFIG.apiUrl ? new URL(CONFIG.apiUrl) : null
const ORIGIN = WEB ? WEB.origin : `${SCHEME}://app`

protocol.registerSchemesAsPrivileged([
  { scheme: SCHEME, privileges: { standard: true, secure: true, supportFetchAPI: true, stream: true, corsEnabled: true } },
])

function bundledFile(request) {
  const url = new URL(request.url)
  let file = path.normalize(path.join(WEB_ROOT, decodeURIComponent(url.pathname)))
  // Nothing outside the app folder, and any address that is not a file is a page of the single-page app.
  if (!file.startsWith(WEB_ROOT)) return new Response('Not found', { status: 404 })
  if (!fs.existsSync(file) || fs.statSync(file).isDirectory()) file = path.join(WEB_ROOT, 'index.html')
  return net.fetch(pathToFileURL(file).toString())
}

function serveApp() {
  if (WEB) {
    protocol.handle('https', (request) =>
      new URL(request.url).host === WEB.host ? bundledFile(request) : net.fetch(request, { bypassCustomProtocolHandlers: true }),
    )
  } else {
    protocol.handle(SCHEME, bundledFile)
  }
}

// Remote-control programs a secure test warns about. The page asks (preload.js) and gets back the names found. This is a
// match on the running programs' names, so it catches the common ones and not a renamed copy or one hidden on purpose:
// it is one layer next to the camera and window checks, not a guarantee.
const REMOTE_TOOLS = [
  'anydesk', 'teamviewer', 'rustdesk', 'getscreen', 'ultraviewer', 'ammyy', 'rutserv', 'rfusclient', 'logmein',
  'remoting_host', 'winvnc', 'tvnserver', 'vncserver', 'splashtop', 'supremo', 'dwagsvc', 'screenconnect', 'msra',
]

function runningProgramNames() {
  return new Promise((resolve) => {
    const [command, args] =
      process.platform === 'win32' ? ['tasklist', ['/fo', 'csv', '/nh']] : ['ps', ['-A', '-o', 'comm=']]
    execFile(command, args, { windowsHide: true, timeout: 8000, maxBuffer: 8 * 1024 * 1024 }, (error, stdout) => {
      if (error) {
        resolve([])
        return
      }
      // tasklist CSV lines start with "Image Name", ps lines are the program's path.
      resolve(
        stdout
          .split(/\r?\n/)
          .map((line) => (process.platform === 'win32' ? (line.match(/^"([^"]+)"/) ?? [])[1] : line.split('/').pop()))
          .filter(Boolean)
          .map((name) => name.toLowerCase()),
      )
    })
  })
}

// Only the app's own page may ask.
ipcMain.handle('remote-tools', async (event) => {
  if (!event.senderFrame || !event.senderFrame.url.startsWith(ORIGIN)) return []
  const found = new Set()
  for (const name of await runningProgramNames()) {
    const tool = REMOTE_TOOLS.find((t) => name.includes(t))
    if (tool) found.add(tool)
  }
  return [...found]
})

// One copy at a time. Two copies share the same cache folder, fight over it and one of them shows a blank window.
const gotLock = app.requestSingleInstanceLock()
if (!gotLock) app.quit()
app.on('second-instance', () => {
  const [win] = BrowserWindow.getAllWindows()
  if (win) {
    if (win.isMinimized()) win.restore()
    win.focus()
  }
})

// What the page and the window report goes to a small log file next to the app's data, so a blank window can be
// explained afterwards: %APPDATA%\sutravidya-desktop\app.log
function log(line) {
  try {
    fs.appendFileSync(path.join(app.getPath('userData'), 'app.log'), `${new Date().toISOString()} ${line}${os.EOL}`)
  } catch {
    // Logging must never stop the app.
  }
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
  log(`start ${app.getVersion()} electron ${process.versions.electron} ${os.release()}`)
  win.webContents.on('console-message', (_event, level, message, line, source) => {
    if (level >= 2) log(`console[${level}] ${message} (${source}:${line})`)
  })
  win.webContents.on('did-finish-load', () => log(`loaded ${win.webContents.getURL()}`))
  win.webContents.on('render-process-gone', (_event, details) => log(`render-process-gone ${details.reason} ${details.exitCode}`))
  win.webContents.on('did-fail-load', (_event, code, description, url) => log(`did-fail-load ${code} ${description} ${url}`))
  app.on('child-process-gone', (_event, details) => log(`child-process-gone ${details.type} ${details.reason}`))
  // Ctrl+Shift+I opens the developer tools, to look at the page when something is wrong.
  win.webContents.on('before-input-event', (event, input) => {
    if (input.type === 'keyDown' && ((input.control && input.shift && input.key.toLowerCase() === 'i') || input.key === 'F12')) {
      win.webContents.toggleDevTools()
      event.preventDefault()
    }
  })
  // A page that crashed or failed to load comes back by itself instead of staying blank.
  win.webContents.on('render-process-gone', () => setTimeout(() => win.reload(), 500))
  win.webContents.on('did-fail-load', (_event, _code, _description, _url, isMainFrame) => {
    if (isMainFrame) setTimeout(() => win.loadURL(`${ORIGIN}/`), 1500)
  })

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
  if (!gotLock) return
  serveApp()

  // Cloudflare's human check does not pass inside an embedded browser, so the server lets this app skip it by a key
  // shared with it (CAPTCHA_DESKTOP_KEY on the server, ITILMS_DESKTOP_KEY when this app is built). Sent only to the API.
  if (API && CONFIG.desktopKey) {
    session.defaultSession.webRequest.onBeforeSendHeaders({ urls: [`${API.origin}/*`] }, (details, callback) => {
      callback({ requestHeaders: { ...details.requestHeaders, 'X-Desktop-Key': CONFIG.desktopKey } })
    })
  }

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
