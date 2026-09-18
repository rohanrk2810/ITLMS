// A full run through the running system, through the gateway only.
//
//   docker compose -f deploy/docker/docker-compose.yml up -d     (all healthy)
//   node deploy/scripts/smoke-test.mjs
//
// It exercises one complete slice of IT-ILMS: sign in, publish a course, admit
// a trainer and students, open an online batch, schedule a live class, join it,
// replay what LiveKit reports about the room, and confirm the attendance
// register that comes out the other side. Along the way it checks the security
// boundaries that matter - no token, another student's data, an unsigned webhook.
//
// It writes real data into the running databases, so point it at a development
// stack, never at an institute's own.
// Usage: node smoke-test.mjs <path-to-deploy/docker>
import { readFileSync } from 'node:fs';
import { execSync } from 'node:child_process';
import { join } from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const DOCKER_DIR = process.argv[2] ??
  join(fileURLToPath(new URL('.', import.meta.url)), '..', 'docker');
const GW = 'http://localhost:8080';
const LIVEKIT = 'http://localhost:7880';
const env = Object.fromEntries(readFileSync(`${DOCKER_DIR}/.env`, 'utf8')
  .split(/\r?\n/).filter(l => l && !l.startsWith('#') && l.includes('='))
  .map(l => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));

let passed = 0, failed = 0;
function check(name, ok, detail = '') {
  ok ? passed++ : failed++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -- ' + detail : ''}`);
}
const sleep = ms => new Promise(r => setTimeout(r, ms));

async function api(method, path, token, body, headers = {}) {
  const res = await fetch(GW + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
    body: body === undefined ? undefined : (typeof body === 'string' ? body : JSON.stringify(body)),
  });
  const text = await res.text();
  let json = null; try { json = text ? JSON.parse(text) : null; } catch { /* not json */ }
  return { status: res.status, json, text };
}
async function must(method, path, token, body, expect = [200, 201]) {
  const r = await api(method, path, token, body);
  if (!expect.includes(r.status)) {
    throw new Error(`${method} ${path} -> ${r.status}: ${r.text.slice(0, 400)}`);
  }
  return r.json;
}
async function until(what, fn, timeoutMs = 60000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const v = await fn().catch(() => null);
    if (v) return v;
    await sleep(1500);
  }
  throw new Error(`Timed out waiting for ${what}`);
}
const login = async (identifier, password) =>
  (await must('POST', '/api/auth/login', null, { identifier, password })).accessToken;

function psql(sql) {
  return execSync(`docker compose exec -T postgres psql -U itilms -d itilms_identity -tAc "${sql}"`,
    { cwd: DOCKER_DIR, encoding: 'utf8' }).trim();
}

// ---- LiveKit signing (what the media server does for webhooks and its admin API) ----
const b64url = b => Buffer.from(b).toString('base64').replace(/=+$/, '').replace(/\+/g, '-').replace(/\//g, '_');
function signJwt(claims) {
  const header = b64url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = b64url(JSON.stringify(claims));
  const sig = b64url(crypto.createHmac('sha256', env.LIVEKIT_API_SECRET).update(`${header}.${payload}`).digest());
  return `${header}.${payload}.${sig}`;
}
async function sendWebhook(event) {
  const body = JSON.stringify(event);
  const now = Math.floor(Date.now() / 1000);
  const token = signJwt({
    iss: env.LIVEKIT_API_KEY, nbf: now - 10, exp: now + 600,
    sha256: crypto.createHash('sha256').update(body).digest('base64'),
  });
  return api('POST', '/api/liveclass/webhook', null, body, { Authorization: token, 'Content-Type': 'application/webhook+json' });
}
async function liveKitRooms() {
  const now = Math.floor(Date.now() / 1000);
  const token = signJwt({ iss: env.LIVEKIT_API_KEY, nbf: now - 10, exp: now + 600, video: { roomList: true } });
  const res = await fetch(`${LIVEKIT}/twirp/livekit.RoomService/ListRooms`, {
    method: 'POST', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }, body: '{}',
  });
  return (await res.json()).rooms || [];
}

// ---- Time helpers in the institute timezone ----
function istParts(date) {
  const f = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23', weekday: 'short' });
  const p = Object.fromEntries(f.formatToParts(date).map(x => [x.type, x.value]));
  return { date: `${p.year}-${p.month}-${p.day}`, time: `${p.hour}:${p.minute}`, dow: p.weekday.toUpperCase().slice(0, 3) };
}

const run = Date.now().toString().slice(-6);

try {
  // ------------------------------------------------------------------ security edges
  const admin = await login(env.BOOTSTRAP_ADMIN_EMAIL, env.BOOTSTRAP_ADMIN_PASSWORD);
  check('Bootstrap admin can sign in through the gateway', !!admin);

  let r = await api('GET', '/api/courses', null);
  check('Protected endpoint without a token is refused', r.status === 401, `got ${r.status}`);

  r = await api('GET', '/api/batches/internal/1/enrolled/1', admin);
  check('Internal endpoint is not reachable through the gateway', r.status === 404, `got ${r.status}`);

  r = await api('POST', '/api/auth/register', null, { firstName: 'Self', lastName: 'Signup', email: `self${run}@test.local`, phone: `98${run}11`, password: 'Passw0rdX' });
  check('Self-registration is closed by default', r.status === 403, `got ${r.status}`);

  r = await api('POST', '/api/liveclass/webhook', null, '{"event":"room_started"}');
  check('Unsigned LiveKit webhook is rejected', r.status === 401, `got ${r.status}`);

  r = await sendWebhook({ event: 'room_started', id: `EV_forged_${run}`, room: { name: 'nope' } });
  check('Correctly signed webhook for an unknown room is accepted and ignored', r.status === 200, `got ${r.status}`);

  // ------------------------------------------------------------------ catalog
  const course = await must('POST', '/api/courses', admin, {
    title: `Java Full Stack ${run}`, code: `JFS-${run}`, summary: 'Spring Boot and React from zero',
    description: 'A complete full stack programme.', durationHours: 120, level: 'BEGINNER', fee: 45000,
  });
  const courseId = course.id ?? course.course?.id;
  const mod = await must('POST', `/api/courses/${courseId}/modules`, admin, { title: 'Core Java' });
  await must('POST', `/api/modules/${mod.id}/lessons`, admin, { title: 'JVM basics', type: 'VIDEO', contentUrl: 'https://example.com/jvm', durationMinutes: 30, mandatory: true });
  await must('POST', `/api/courses/${courseId}/publish`, admin);
  check('Course created with module and lesson, then published', !!courseId, `courseId=${courseId}`);

  // ------------------------------------------------------------------ people (admission -> identity over Feign)
  const person = (first, n) => ({ firstName: first, lastName: `Test${run}`, email: `${first.toLowerCase()}${run}@test.local`, phone: `9${run}${n}00` });
  const trainer = await must('POST', '/api/trainers', admin, { ...person('Trainer', 1), specialization: 'Java' });
  const s1 = await must('POST', '/api/students', admin, person('Asha', 2));
  const s2 = await must('POST', '/api/students', admin, person('Ravi', 3));
  const s3 = await must('POST', '/api/students', admin, person('Outsider', 4));
  check('Trainer and students created (identity accounts made through Feign)', !!(trainer.userId && s1.userId && s2.userId && s3.userId));

  // New accounts get a generated temporary password that reaches the person
  // through notification-service, which is not built yet. Until it is, the only
  // way for this test to sign in as them is to set a password it knows, so it
  // copies the administrator password hash onto the new accounts. Replace this
  // with reading the message out of Mailpit once notifications are delivered.
  const hash = psql(`select password_hash from users where email='${env.BOOTSTRAP_ADMIN_EMAIL}'`);
  psql(`update users set password_hash='${hash}' where id in (${trainer.userId},${s1.userId},${s2.userId},${s3.userId})`);

  // Profile ids reach identity-service by Kafka (ProfileLinkedEvent); tokens carry them.
  await until('profile ids to be linked in identity-service', async () =>
    psql(`select count(*) from users where id in (${trainer.userId},${s1.userId},${s2.userId},${s3.userId}) and profile_id is not null`) === '4', 60000);
  check('Profile ids linked back to identity-service over Kafka', true);

  const pw = env.BOOTSTRAP_ADMIN_PASSWORD;
  const [tTok, s1Tok, s2Tok, s3Tok] = await Promise.all([trainer, s1, s2, s3].map(p => login(p.email, pw)));

  // ------------------------------------------------------------------ batch, enrolment, online session
  const now = new Date();
  const start = new Date(now.getTime() - 5 * 60000);
  const end = new Date(start.getTime() + 60 * 60000);
  const sp = istParts(start), ep = istParts(end);
  if (sp.date !== ep.date) throw new Error('Session would cross midnight in IST; run the test earlier in the day');

  const batch = await must('POST', '/api/batches', admin, {
    name: `JFS Evening ${run}`, courseId, trainerId: trainer.id, startDate: sp.date,
    startTime: sp.time, endTime: ep.time, classDays: [sp.dow], mode: 'ONLINE', capacity: 30,
  });
  const enrol = await must('POST', `/api/batches/${batch.id}/students`, admin, { studentIds: [s1.id, s2.id] });
  check('Online batch created and two students enrolled', !!batch.id, JSON.stringify(enrol).slice(0, 120));

  const session = await must('POST', '/api/sessions', tTok, {
    batchId: batch.id, sessionDate: sp.date, startTime: sp.time, endTime: ep.time, topic: 'Smoke-test live class', mode: 'ONLINE',
  });
  check('Trainer scheduled an online session', session.live === true, `sessionId=${session.id} ${sp.time}-${ep.time} IST`);

  const live = await until('liveclass-service to provision the room from the Kafka event', async () => {
    const x = await api('GET', `/api/liveclass/class-sessions/${session.id}`, admin);
    return x.status === 200 ? x.json : null;
  });
  check('Live room record provisioned from SessionScheduledEvent (Kafka)', live.roomName === `itilms-session-${session.id}`, `status=${live.status} joinable=${live.joinable}`);

  // ------------------------------------------------------------------ joining
  const sJoin = await must('POST', `/api/liveclass/class-sessions/${session.id}/join`, s1Tok);
  check('Enrolled student receives a join token', !!sJoin.token && sJoin.role === 'STUDENT' && sJoin.roomAdmin === false,
    `identity=${sJoin.identity} canPublish=${sJoin.canPublish} roomAdmin=${sJoin.roomAdmin}`);

  const tJoin = await must('POST', `/api/liveclass/class-sessions/${session.id}/join`, tTok);
  check('Trainer receives a room-admin token', tJoin.role === 'TRAINER' && tJoin.roomAdmin === true);

  r = await api('POST', `/api/liveclass/class-sessions/${session.id}/join`, s3Tok);
  check('Student not in the batch is refused entry', r.status === 403, `got ${r.status}`);

  const rooms = await liveKitRooms();
  check('Room actually exists on the LiveKit server', rooms.some(x => x.name === sJoin.roomName), `rooms=${rooms.map(x => x.name).join(',')}`);

  // ------------------------------------------------------------------ IDOR check
  r = await api('GET', `/api/progress/students/${s1.id}/courses/${courseId}`, s3Tok);
  check('A student cannot read another student\'s progress', r.status === 403, `got ${r.status}`);

  // ------------------------------------------------------------------ attendance from (signed) LiveKit webhooks
  const t0 = Math.floor(new Date(live.scheduledStartAt).getTime() / 1000);
  const room = { name: live.roomName, sid: 'RM_smoke' };
  const hook = (event, identity, minute) => sendWebhook({
    event, id: `EV_${run}_${event}_${identity || 'room'}_${minute}`, createdAt: String(t0 + minute * 60),
    room, ...(identity ? { participant: { identity, sid: `PA_${identity}` } } : {}),
  });
  const statuses = [];
  statuses.push((await hook('participant_joined', tJoin.identity, 0)).status);
  statuses.push((await hook('participant_joined', sJoin.identity, 2)).status);
  statuses.push((await hook('participant_left', sJoin.identity, 20)).status);   // wifi drop
  statuses.push((await hook('participant_joined', sJoin.identity, 22)).status);
  statuses.push((await hook('participant_left', sJoin.identity, 46)).status);   // 18 + 24 = 42 min
  // A redelivery of the same notice must not add time.
  statuses.push((await hook('participant_left', sJoin.identity, 46)).status);
  statuses.push((await hook('participant_left', tJoin.identity, 58)).status);
  check('Signed participant webhooks accepted', statuses.every(s => s === 200), statuses.join(','));

  r = await hook('room_finished', null, 10);
  // A mid-class closure must NOT settle the class (the defect fixed earlier).
  const afterEarlyClose = await api('GET', `/api/liveclass/sessions/${live.id}`, admin);
  check('Room closing mid-class does not settle attendance', afterEarlyClose.json?.attendanceComputed === false, `status=${afterEarlyClose.json?.status}`);

  r = await hook('room_finished', null, 59);
  const settled = await until('attendance to be settled', async () => {
    const x = await api('GET', `/api/liveclass/sessions/${live.id}`, admin);
    return x.json?.attendanceComputed ? x.json : null;
  }, 30000);
  const asha = settled.participants.find(p => p.userId === s1.userId);
  check('Room closing near the end settles the class', true, `status=${settled.status}`);
  check('Student room time summed across reconnect (42 of 58 min = 72%)',
    asha?.attendedSeconds === 42 * 60 && asha?.attendancePercent === 72 && asha?.computedStatus === 'PRESENT',
    `seconds=${asha?.attendedSeconds} percent=${asha?.attendancePercent} status=${asha?.computedStatus}`);

  // batch-service writes the register from the Kafka event
  const register = await until('batch-service to write the register', async () => {
    const x = await api('GET', `/api/sessions/${session.id}/attendance`, tTok);
    return x.status === 200 && x.json?.length >= 2 ? x.json : null;
  }, 60000);
  const rowFor = id => register.find(a => a.studentId === id);
  check('Register: attending student PRESENT, from the live class',
    rowFor(s1.id)?.status === 'PRESENT' && rowFor(s1.id)?.source === 'LIVE_CLASS', JSON.stringify(rowFor(s1.id)));
  check('Register: enrolled student who never joined is ABSENT', rowFor(s2.id)?.status === 'ABSENT', JSON.stringify(rowFor(s2.id)));

  r = await api('GET', `/api/students/me/attendance/summary?batchId=${batch.id}`, s1Tok);
  check('Student sees their own attendance summary', r.status === 200, r.text.slice(0, 160));

  r = await api('POST', `/api/liveclass/class-sessions/${session.id}/join`, s1Tok);
  check('Joining a settled class is refused', r.status === 422, `got ${r.status}`);
} catch (e) {
  check('Unexpected error', false, e.message);
}

console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
