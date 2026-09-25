// MediaPipe's face detector needs its WebAssembly files served from our own origin (a test taken behind a
// school firewall cannot reach a CDN, and the page must not depend on one). They ship inside the npm package,
// so they are copied into public/ before every build and dev start rather than committed: about 33 MB.
import { cpSync, existsSync, mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const from = join(root, 'node_modules', '@mediapipe', 'tasks-vision', 'wasm')
const to = join(root, 'public', 'mediapipe', 'wasm')

if (!existsSync(from)) {
  console.error(`copy-mediapipe: ${from} not found. Run npm install first.`)
  process.exit(1)
}
mkdirSync(to, { recursive: true })
cpSync(from, to, { recursive: true })
console.log(`copy-mediapipe: copied the face-detector runtime to ${to}`)
