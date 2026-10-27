// Builds the web app with the API address baked in, and copies it into desktop/app for packaging.
//   ITILMS_API_URL=https://api.yourcollege.in npm run build:web
// With nothing set it points at the local stack (http://localhost:8080), which is fine for trying the app.
import { cpSync, existsSync, rmSync } from 'node:fs'
import { execSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const frontend = path.resolve(here, '../../frontend')
const target = path.resolve(here, '../app')
const api = process.env.ITILMS_API_URL || 'http://localhost:8080'

console.log(`Building the web app against ${api}`)
execSync('npm run build', { cwd: frontend, stdio: 'inherit', env: { ...process.env, VITE_API_BASE_URL: api } })
if (existsSync(target)) rmSync(target, { recursive: true })
cpSync(path.join(frontend, 'dist'), target, { recursive: true })
console.log(`Copied to ${target}`)
