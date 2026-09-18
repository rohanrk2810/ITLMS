// A full run through the running system, through the gateway only.
//
//   docker compose -f deploy/docker/docker-compose.yml up -d     (all healthy)
//   node deploy/scripts/smoke-test.mjs
//
// It exercises one complete slice of IT-ILMS: sign in, publish a course, admit
// a trainer and students, open an online batch, schedule a live class, join it,
// replay what LiveKit reports about the room, and confirm the attendance
// register that comes out the other side. It then carries the same students on
// through a quiz and an assignment (assessment), fees and payments (finance), a
// certificate, files, a job application (placement) and the reporting views. Along the way it checks the security
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
const MAILPIT = 'http://localhost:8025';
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
  const lesson = await must('POST', `/api/modules/${mod.id}/lessons`, admin, { title: 'JVM basics', type: 'VIDEO', contentUrl: 'https://example.com/jvm', durationMinutes: 30, mandatory: true });
  await must('POST', `/api/courses/${courseId}/publish`, admin);
  check('Course created with module and lesson, then published', !!courseId, `courseId=${courseId}`);

  // ------------------------------------------------------------------ people (admission -> identity over Feign)
  const person = (first, n) => ({ firstName: first, lastName: `Test${run}`, email: `${first.toLowerCase()}${run}@test.local`, phone: `9${run}${n}00` });
  const trainer = await must('POST', '/api/trainers', admin, { ...person('Trainer', 1), specialization: 'Java' });
  const s1 = await must('POST', '/api/students', admin, person('Asha', 2));
  const s2 = await must('POST', '/api/students', admin, person('Ravi', 3));
  const s3 = await must('POST', '/api/students', admin, person('Outsider', 4));
  check('Trainer and students created (identity accounts made through Feign)', !!(trainer.userId && s1.userId && s2.userId && s3.userId));

  // New accounts get a generated temporary password, emailed by notification-service
  // (Mailpit catches it). Read it from there, the way the person would.
  const tempPassword = email => until(`temporary-password email for ${email}`, async () => {
    const found = await (await fetch(`${MAILPIT}/api/v1/search?query=${encodeURIComponent('to:' + email)}`)).json();
    for (const m of found.messages ?? []) {
      const full = await (await fetch(`${MAILPIT}/api/v1/message/${m.ID}`)).json();
      const hit = /Temporary password:\s*(\S+)/.exec(full.Text ?? '');
      if (hit) return hit[1];
    }
    return null;
  }, 90000);
  const passwords = await Promise.all([trainer, s1, s2, s3].map(p => tempPassword(p.email)));
  check('Temporary passwords delivered by email (read from Mailpit)', passwords.every(Boolean));

  // Profile ids reach identity-service by Kafka (ProfileLinkedEvent); tokens carry them.
  await until('profile ids to be linked in identity-service', async () =>
    psql(`select count(*) from users where id in (${trainer.userId},${s1.userId},${s2.userId},${s3.userId}) and profile_id is not null`) === '4', 60000);
  check('Profile ids linked back to identity-service over Kafka', true);

  const [tTok, s1Tok, s2Tok, s3Tok] = await Promise.all([trainer, s1, s2, s3].map((p, i) => login(p.email, passwords[i])));

  // ------------------------------------------------------------------ batch, enrolment, online session
  const now = new Date();
  let start = new Date(now.getTime() - 5 * 60000);
  let end = new Date(start.getTime() + 60 * 60000);
  let sp = istParts(start), ep = istParts(end);
  if (sp.date !== ep.date) {
    // A session cannot cross midnight, so late in the IST evening slide the whole
    // hour back to finish at 23:59 (it is still running now, so still joinable).
    end = new Date(now.getTime() + (23 * 60 + 59 - (Number(istParts(now).time.slice(0, 2)) * 60 + Number(istParts(now).time.slice(3)))) * 60000);
    start = new Date(end.getTime() - 60 * 60000);
    sp = istParts(start); ep = istParts(end);
    if (sp.date !== ep.date) throw new Error('Cannot fit a one-hour session before midnight IST; run the test earlier in the day');
  }

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

  // =================================================================== the rest of the services
  // Each section below runs on its own: a failure inside one is reported and the
  // next section still runs, so a single broken service does not hide the others.
  const section = async (name, fn) => {
    try { await fn(); } catch (e) { check(`${name}: unexpected error`, false, e.message); }
  };
  const iso = ms => new Date(Date.now() + ms).toISOString();
  const bytes = async (method, path, token, body) => {
    const res = await fetch(GW + path, { method, headers: token ? { Authorization: `Bearer ${token}` } : {}, body });
    return { status: res.status, buf: Buffer.from(await res.arrayBuffer()), type: res.headers.get('content-type') ?? '' };
  };
  // fetch sets the multipart boundary itself, so the JSON header api() adds must not be sent.
  const uploadRaw = async (token, category, name, type, content) => {
    const form = new FormData();
    form.append('file', new Blob([content], { type }), name);
    const res = await fetch(`${GW}/api/files?category=${category}`, { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: form });
    const text = await res.text();
    let json = null; try { json = JSON.parse(text); } catch { /* not json */ }
    return { status: res.status, json, text };
  };

  // Two more staff accounts, created the way an administrator would (temporary password by email).
  const financeUser = await must('POST', '/api/users', admin, { firstName: 'Fiona', lastName: `Fin${run}`, email: `fin${run}@test.local`, role: 'FINANCE' });
  const placementUser = await must('POST', '/api/users', admin, { firstName: 'Pia', lastName: `Plc${run}`, email: `plc${run}@test.local`, role: 'PLACEMENT' });
  const [fTok, pTok] = await Promise.all([financeUser, placementUser].map(async u => login(u.email, await tempPassword(u.email))));
  check('Finance and placement staff accounts created and able to sign in', !!(fTok && pTok));

  // ------------------------------------------------------------------ assessment-service
  let quizId;
  await section('Quiz', async () => {
    const quiz = await must('POST', '/api/quizzes', tTok, {
      courseId, batchId: batch.id, title: `Java basics quiz ${run}`, durationMinutes: 20, passPercentage: 50,
      attemptsAllowed: 2, showResultImmediately: true, mandatory: true,
    });
    quizId = quiz.id;
    for (const q of ['What runs Java bytecode?', 'Which keyword declares a constant?']) {
      await must('POST', `/api/quizzes/${quizId}/questions`, tTok, {
        questionText: q, type: 'SINGLE_CHOICE', marks: 5,
        options: [{ optionText: 'Right answer', correct: true }, { optionText: 'Wrong one', correct: false }, { optionText: 'Wrong two', correct: false }],
      });
    }
    await must('POST', `/api/quizzes/${quizId}/publish`, tTok);
    const key = await must('GET', `/api/quizzes/${quizId}`, tTok);
    check('Trainer built and published a quiz with an answer key', key.status === 'PUBLISHED' && key.questions?.length === 2 && key.totalMarks === 10, `status=${key.status} questions=${key.questions?.length} marks=${key.totalMarks}`);

    let r = await api('GET', '/api/quizzes/available', s1Tok);
    check('Enrolled student sees the quiz as available', Array.isArray(r.json) && r.json.some(x => (x.id ?? x.quizId) === quizId), `status=${r.status}`);

    const paper = await must('POST', `/api/quizzes/${quizId}/attempts`, s1Tok);
    check('Attempt paper never carries the answer key', !JSON.stringify(paper).includes('"correct"') && paper.questions.length === 2, `questions=${paper.questions?.length}`);

    r = await api('POST', `/api/quizzes/${quizId}/attempts`, s3Tok);
    check('Student outside the batch cannot start the quiz', r.status >= 400 && r.status < 500, `got ${r.status}`);
    r = await api('GET', `/api/quiz-attempts/${paper.attemptId}`, s2Tok);
    check('Another student cannot open my attempt', r.status === 403 || r.status === 404, `got ${r.status}`);

    const answers = key.questions.map(q => ({ questionId: q.id, selectedOptionIds: q.options.filter(o => o.correct).map(o => o.id) }));
    const saved = await must('PUT', `/api/quiz-attempts/${paper.attemptId}/answers`, s1Tok, { answers: answers.slice(0, 1) });
    check('Partial answers autosaved', saved.answeredCount === 1, `answered=${saved.answeredCount}/${saved.questionCount}`);
    const result = await must('POST', `/api/quiz-attempts/${paper.attemptId}/submit`, s1Tok, { answers });
    check('Submitted attempt is marked and passed (10/10)', result.score === 10 && result.percentage === 100 && result.passed === true, `score=${result.score} passed=${result.passed}`);
    r = await api('POST', `/api/quiz-attempts/${paper.attemptId}/submit`, s1Tok, { answers });
    check('Re-submitting a finished attempt is a harmless repeat (same result, no re-marking)', r.status === 200 && r.json?.score === 10 && r.json?.attemptId === paper.attemptId, `got ${r.status} score=${r.json?.score}`);
    const all = await must('GET', `/api/quizzes/${quizId}/results`, tTok);
    check('Trainer sees the results sheet', Array.isArray(all) && all.length === 1, `rows=${all?.length}`);
    r = await api('GET', `/api/quizzes/${quizId}/results`, s1Tok);
    check('A student cannot read the results sheet', r.status === 403, `got ${r.status}`);
  });

  await section('Assignment', async () => {
    let a = await must('POST', '/api/assignments', tTok, {
      batchId: batch.id, courseId, title: `Build a REST API ${run}`, instructions: 'Spring Boot CRUD',
      dueAt: iso(7 * 86400000), maxMarks: 100, allowLate: false, mandatory: true,
    });
    if (a.status === 'DRAFT') a = await must('POST', `/api/assignments/${a.id}/publish`, tTok);
    check('Trainer created and published an assignment', a.status === 'PUBLISHED', `status=${a.status}`);
    let r = await api('GET', '/api/assignments/mine', s1Tok);
    check('Enrolled student sees it under "mine"', Array.isArray(r.json) && r.json.some(x => x.id === a.id), `status=${r.status}`);
    r = await api('POST', `/api/assignments/${a.id}/submissions`, s3Tok, { textAnswer: 'not mine' });
    check('Student outside the batch cannot submit', r.status >= 400 && r.status < 500, `got ${r.status}`);
    const sub = await must('POST', `/api/assignments/${a.id}/submissions`, s1Tok, { textAnswer: 'My API is at github.com/asha/api' });
    check('Student submitted the assignment', sub.status === 'SUBMITTED' || !!sub.id, `status=${sub.status}`);
    r = await api('GET', `/api/submissions/${sub.id}`, s2Tok);
    check('Another student cannot read my submission', r.status === 403 || r.status === 404, `got ${r.status}`);
    r = await api('PUT', `/api/submissions/${sub.id}/evaluate`, s1Tok, { marks: 90 });
    check('A student cannot evaluate a submission', r.status === 403, `got ${r.status}`);
    r = await api('PUT', `/api/submissions/${sub.id}/evaluate`, tTok, { marks: 150, feedback: 'too high' });
    check('Marks above the maximum are rejected', r.status >= 400 && r.status < 500, `got ${r.status}`);
    const ev = await must('PUT', `/api/submissions/${sub.id}/evaluate`, tTok, { marks: 85, feedback: 'Well structured' });
    check('Trainer evaluated the submission', ev.marks === 85 && ev.status === 'EVALUATED', `marks=${ev.marks} status=${ev.status}`);
    const mine = await must('GET', '/api/assignments/mine', s1Tok);
    check('Student sees the marks and feedback', mine.find(x => x.id === a.id)?.mySubmission?.feedback === 'Well structured');
  });

  // ------------------------------------------------------------------ finance-service
  let planId, planNet;
  await section('Finance', async () => {
    let plans = await must('GET', `/api/fees/students/${s1.id}`, admin);
    let plan = plans.find(p => p.courseId === courseId);
    if (!plan) {
      plan = await must('POST', '/api/fee-plans', admin, {
        studentId: s1.id, courseId, batchId: batch.id, totalFee: 45000, discount: 5000, installmentCount: 3, firstDueDate: iso(30 * 86400000).slice(0, 10),
      });
    }
    planId = plan.id; planNet = plan.netFee;
    check('Fee plan exists for the student with instalments', plan.installments?.length >= 1 && plan.netFee > 0, `net=${plan.netFee} instalments=${plan.installments?.length}`);

    let r = await api('GET', `/api/fees/students/${s1.id}`, s2Tok);
    check('A student cannot read another student\'s fees', r.status === 403, `got ${r.status}`);
    r = await api('POST', '/api/payments', tTok, { feePlanId: planId, amount: 1000, method: 'CASH' });
    check('A trainer cannot record a payment', r.status === 403, `got ${r.status}`);
    r = await api('POST', '/api/payments', s1Tok, { feePlanId: planId, amount: 1000, method: 'CASH' });
    check('A student cannot record their own payment', r.status === 403, `got ${r.status}`);

    const pay = await must('POST', '/api/payments', fTok, { feePlanId: planId, amount: 1000, method: 'UPI', referenceNo: `UPI${run}` });
    check('Finance desk recorded a payment and got a receipt number', !!pay.receiptNo, `receipt=${pay.receiptNo} status=${pay.status}`);
    r = await api('POST', '/api/payments', fTok, { feePlanId: planId, amount: planNet + 1, method: 'CASH' });
    check('Paying more than is outstanding is rejected', r.status >= 400 && r.status < 500, `got ${r.status}`);
    const receipt = await must('GET', `/api/payments/${pay.id}/receipt`, s1Tok);
    check('Student can read their own receipt', receipt.receiptNo === pay.receiptNo && receipt.amount === 1000, `amount=${receipt.amount}`);
    r = await api('GET', `/api/payments/${pay.id}/receipt`, s2Tok);
    check('Another student cannot read that receipt', r.status === 403 || r.status === 404, `got ${r.status}`);

    r = await api('POST', `/api/payments/${pay.id}/reverse`, fTok, { reason: '' });
    check('Reversing without a reason is rejected', r.status >= 400 && r.status < 500, `got ${r.status}`);
    await must('POST', `/api/payments/${pay.id}/reverse`, fTok, { reason: 'Recorded against the wrong student' });
    let mine = (await must('GET', '/api/fees/me', s1Tok)).find(p => p.id === planId);
    check('Reversal restores the outstanding balance', mine.paid === 0 && mine.outstanding === planNet, `paid=${mine.paid} outstanding=${mine.outstanding}`);

    const dash = await must('GET', '/api/fees/dashboard', fTok);
    check('Finance dashboard and overdue list are available to the desk', dash.activePlans >= 1 && (await api('GET', '/api/fees/overdue', fTok)).status === 200, `activePlans=${dash.activePlans}`);
    r = await api('GET', '/api/fees/dashboard', s1Tok);
    check('A student cannot open the finance dashboard', r.status === 403, `got ${r.status}`);

    // Settle in full so the certificate check below has nothing owed.
    await must('POST', '/api/payments', fTok, { feePlanId: planId, amount: planNet, method: 'BANK_TRANSFER', referenceNo: `NEFT${run}` });
    mine = (await must('GET', '/api/fees/me', s1Tok)).find(p => p.id === planId);
    check('Paid in full: plan settled with nothing outstanding', mine.outstanding === 0 && mine.status !== 'ACTIVE', `outstanding=${mine.outstanding} status=${mine.status}`);
  });

  // ------------------------------------------------------------------ certificate-service
  await section('Certificate', async () => {
    let e2 = await must('GET', `/api/certificates/eligibility?studentId=${s2.id}&courseId=${courseId}`, s2Tok);
    check('Student who did nothing is not eligible, with outstanding work listed', e2.eligible === false && e2.outstandingWork?.length > 0, `outstanding=${e2.outstandingWork?.length}`);
    check('Every eligibility criterion was answered by its owning service (none UNAVAILABLE)',
      e2.criteria.every(c => c.outcome !== 'UNAVAILABLE'), e2.criteria.map(c => `${c.key}:${c.outcome}`).join(' '));
    let r = await api('POST', '/api/certificates/claim', s2Tok, { courseId });
    check('Claiming without meeting the criteria is refused', r.status >= 400 && r.status < 500, `got ${r.status}`);

    // Make student 1 complete: the lesson, the quiz, the assignment, attendance and fees are done above.
    await must('POST', `/api/progress/lessons/${lesson.id}`, s1Tok, { watchedSeconds: 1800, completed: true });
    let e1 = await must('GET', `/api/certificates/eligibility?studentId=${s1.id}&courseId=${courseId}`, s1Tok);
    check('Student who finished everything is eligible', e1.eligible === true,
      e1.criteria.map(c => `${c.key}:${c.outcome}`).join(' ') + ` | left=${(e1.outstandingWork ?? []).join('; ')}`);
    const cert = await must('POST', '/api/certificates/claim', s1Tok, { courseId });
    check('Eligible student claimed a certificate', cert.status === 'ISSUED' && !!cert.certificateNo, `no=${cert.certificateNo}`);
    r = await api('POST', '/api/certificates/claim', s1Tok, { courseId });
    check('A second claim does not issue a duplicate', r.status === 409 || (r.status < 300 && r.json?.certificateNo === cert.certificateNo), `got ${r.status}`);

    const verifyUrl = `/api/certificates/verify/${cert.certificateNo}`;
    r = await api('GET', verifyUrl, null);
    check('Verification without the code is refused', r.status === 422, `got ${r.status}`);
    r = await api('GET', `${verifyUrl}?code=WRONG-CODE`, null);
    check('Verification with a wrong code is refused', r.status >= 400 && r.status < 500, `got ${r.status}`);
    const pub = await api('GET', `${verifyUrl}?code=${encodeURIComponent(cert.verificationCode)}`, null);
    check('Certificate verifies publicly, without signing in, given its code', pub.status === 200, `got ${pub.status} ${pub.text.slice(0, 120)}`);
    const pdf = await bytes('GET', `/api/certificates/${cert.id}/pdf`, s1Tok);
    check('Certificate PDF downloads', pdf.status === 200 && pdf.buf.subarray(0, 4).toString() === '%PDF', `status=${pdf.status} type=${pdf.type} bytes=${pdf.buf.length}`);
    r = await api('GET', `/api/certificates/${cert.id}`, s2Tok);
    check('Another student cannot open my certificate', r.status === 403 || r.status === 404, `got ${r.status}`);
    r = await api('POST', `/api/certificates/${cert.id}/revoke`, s1Tok, { reason: 'x' });
    check('A student cannot revoke', r.status === 403, `got ${r.status}`);
    await must('POST', `/api/certificates/${cert.id}/revoke`, admin, { reason: 'Smoke test revocation' });
    const after = await api('GET', `${verifyUrl}?code=${encodeURIComponent(cert.verificationCode)}`, null);
    check('Revoked certificate no longer verifies as valid', after.status !== 200 || /REVOKED|revoked/.test(after.text), `got ${after.status} ${after.text.slice(0, 120)}`);
  });

  // ------------------------------------------------------------------ file-service
  let resumeFileId;
  await section('Files', async () => {
    const pdfBytes = Buffer.from('%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF\n');
    const doc = await uploadRaw(s1Tok, 'DOCUMENT', 'id-proof.pdf', 'application/pdf', pdfBytes);
    check('Student uploaded a document', doc.status === 201 || doc.status === 200, `got ${doc.status} ${doc.text.slice(0, 160)}`);
    const id = doc.json.id;
    let r = await uploadRaw(s1Tok, 'DOCUMENT', 'run.exe', 'application/x-msdownload', Buffer.from('MZ'));
    check('A disallowed file type is rejected', r.status === 415 || r.status === 400 || r.status === 422, `got ${r.status}`);
    r = await uploadRaw(s1Tok, 'AVATAR', 'huge.png', 'image/png', Buffer.alloc(2 * 1024 * 1024 + 1024));
    check('A file over the category size ceiling is rejected', r.status === 413 || r.status === 400 || r.status === 422, `got ${r.status}`);
    r = await uploadRaw(s1Tok, 'CERTIFICATE', 'fake.pdf', 'application/pdf', pdfBytes);
    check('A student cannot upload into a system-only category', r.status === 403 || r.status === 400, `got ${r.status}`);

    const mine = await must('GET', '/api/files/mine', s1Tok);
    check('"My files" lists the upload', mine.content?.some(f => f.id === id), `total=${mine.totalElements}`);
    const dl = await bytes('GET', `/api/files/${id}/download`, s1Tok);
    check('Owner downloads the exact bytes back', dl.status === 200 && dl.buf.equals(pdfBytes), `status=${dl.status} bytes=${dl.buf.length}`);
    r = await api('GET', `/api/files/${id}`, s2Tok);
    check('Another student cannot read my document', r.status === 403 || r.status === 404, `got ${r.status}`);
    r = await bytes('GET', `/api/files/${id}/download`, s2Tok);
    check('Another student cannot download my document', r.status === 403 || r.status === 404, `got ${r.status}`);
    r = await bytes('GET', `/api/files/${id}/download`, admin);
    check('Staff can download it', r.status === 200, `got ${r.status}`);
    r = await api('GET', '/api/files', s1Tok);
    check('The all-files directory is staff-only', r.status === 403, `got ${r.status}`);
    r = await api('DELETE', `/api/files/${id}`, s2Tok);
    check('Another student cannot delete my file', r.status === 403 || r.status === 404, `got ${r.status}`);
    r = await api('DELETE', `/api/files/${id}`, s1Tok);
    check('The uploader can remove their own file', r.status === 204 || r.status === 200, `got ${r.status}`);
    r = await api('GET', `/api/files/${id}`, admin);
    check('A deleted file is gone', r.status === 404, `got ${r.status}`);
    r = await api('GET', '/api/files/mine', null);
    check('File endpoints need a token', r.status === 401, `got ${r.status}`);

    const resume = await uploadRaw(s1Tok, 'RESUME', 'asha-resume.pdf', 'application/pdf', pdfBytes);
    check('Student uploaded a resume', resume.status === 201 || resume.status === 200, `got ${resume.status} ${resume.text.slice(0, 120)}`);
    resumeFileId = resume.json?.id;
  });

  // ------------------------------------------------------------------ placement-service
  await section('Placement', async () => {
    let r = await api('POST', '/api/companies', tTok, { name: 'Nope Ltd' });
    check('A trainer cannot add a company', r.status === 403, `got ${r.status}`);
    const company = await must('POST', '/api/companies', pTok, { name: `Acme Software ${run}`, industry: 'IT', location: 'Pune', contactEmail: `hr${run}@acme.test` });
    let job = await must('POST', '/api/jobs', pTok, {
      companyId: company.id, title: `Junior Java Developer ${run}`, jobType: 'FULL_TIME', openings: 2, location: 'Pune',
      eligibleCourseIds: [courseId], requireCertificate: false, applicationDeadline: iso(14 * 86400000).slice(0, 10),
    });
    check('Placement officer created a company and a draft job', job.status === 'DRAFT', `status=${job.status}`);
    r = await api('GET', '/api/jobs', s1Tok);
    check('A draft job is not on the student job board', !(r.json?.content ?? []).some(j => j.id === job.id), `status=${r.status}`);
    job = await must('POST', `/api/jobs/${job.id}/publish`, pTok);
    check('Job published', job.status === 'OPEN', `status=${job.status}`);
    r = await api('GET', '/api/jobs', s1Tok);
    const onBoard = (r.json?.content ?? []).find(j => j.id === job.id);
    check('Published job is on the board, marked eligible for the enrolled student', !!onBoard && onBoard.eligible === true, JSON.stringify(onBoard?.ineligibleReasons));

    r = await api('POST', `/api/jobs/${job.id}/apply`, s3Tok, { coverNote: 'Please' });
    check('A student from another course cannot apply', r.status >= 400 && r.status < 500, `got ${r.status}`);
    const app = await must('POST', `/api/jobs/${job.id}/apply`, s1Tok, { resumeRef: String(resumeFileId), coverNote: 'Keen to join' });
    check('Eligible student applied with their resume', app.stage === 'APPLIED', `stage=${app.stage}`);
    r = await api('POST', `/api/jobs/${job.id}/apply`, s1Tok, { coverNote: 'again' });
    check('Applying twice is refused', r.status === 409 || r.status === 422, `got ${r.status}`);

    r = await api('GET', `/api/jobs/${job.id}/applications`, s1Tok);
    check('A student cannot see a job\'s applicant list', r.status === 403, `got ${r.status}`);
    const apps = await must('GET', `/api/jobs/${job.id}/applications`, pTok);
    check('Placement officer sees the applicant', apps.some(a => a.id === app.id));
    const resume = await bytes('GET', `/api/files/${resumeFileId}/download`, pTok);
    check('Placement officer can open the applicant\'s resume', resume.status === 200, `got ${resume.status}`);
    r = await bytes('GET', `/api/files/${resumeFileId}/download`, s2Tok);
    check('Another student cannot open that resume', r.status === 403 || r.status === 404, `got ${r.status}`);
    r = await bytes('GET', `/api/files/${resumeFileId}/download`, tTok);
    check('A trainer cannot open that resume', r.status === 403 || r.status === 404, `got ${r.status}`);

    r = await api('PUT', `/api/applications/${app.id}/stage`, s1Tok, { stage: 'SELECTED' });
    check('A student cannot move their own application forward', r.status === 403, `got ${r.status}`);
    await must('PUT', `/api/applications/${app.id}/stage`, pTok, { stage: 'SHORTLISTED', note: 'Good profile' });
    await must('PUT', `/api/applications/${app.id}/stage`, pTok, { stage: 'INTERVIEW', roundNo: 1, nextInterviewAt: iso(3 * 86400000) });
    const sel = await must('PUT', `/api/applications/${app.id}/stage`, pTok, { stage: 'SELECTED', offerDetails: '4.2 LPA' });
    check('Application moved through shortlist, interview and selection', sel.stage === 'SELECTED', `stage=${sel.stage}`);
    r = await api('PUT', `/api/applications/${app.id}/stage`, pTok, { stage: 'APPLIED' });
    check('A decided application cannot be moved back', r.status >= 400 && r.status < 500, `got ${r.status}`);
    const hist = await must('GET', `/api/applications/${app.id}/history`, s1Tok);
    check('Student can read the stage history of their own application', hist.length >= 4, `entries=${hist.length}`);
    const mine = await must('GET', '/api/applications/me', s1Tok);
    check('Student sees the selection in "my applications"', mine.find(a => a.id === app.id)?.stage === 'SELECTED');
    const dash = await must('GET', '/api/placements/dashboard', pTok);
    check('Placement dashboard counts the selection', dash.selected >= 1 && dash.totalApplications >= 1, `selected=${dash.selected}`);
    r = await api('GET', '/api/placements/dashboard', s1Tok);
    check('A student cannot open the placement dashboard', r.status === 403, `got ${r.status}`);
  });

  // ------------------------------------------------------------------ reporting-service
  await section('Reporting', async () => {
    let r = await api('GET', '/api/dashboard/summary', s1Tok);
    check('A student cannot open the institute dashboard', r.status === 403, `got ${r.status}`);
    const summary = await until('the dashboard to reflect this run (Kafka events)', async () => {
      const x = await api('GET', '/api/dashboard/summary', admin);
      return x.status === 200 && JSON.stringify(x.json).match(/[1-9]/) ? x.json : null;
    }, 60000);
    check('Institute dashboard has figures built from events', !!summary, JSON.stringify(summary).slice(0, 200));

    const logs = await until('audit records to arrive from the other services', async () => {
      const x = await api('GET', '/api/audit-logs', admin);
      return x.status === 200 && x.json?.content?.length > 0 ? x.json : null;
    }, 60000);
    check('Audit log holds records from the running services', logs.totalElements > 0, `total=${logs.totalElements}`);
    r = await api('GET', '/api/audit-logs', tTok);
    check('A trainer cannot read the audit log', r.status === 403, `got ${r.status}`);
    r = await api('GET', '/api/audit-logs', null);
    check('Audit log needs a token', r.status === 401, `got ${r.status}`);

    const csv = await bytes('GET', '/api/reports/audit-logs/export?format=CSV', admin);
    check('Audit log exports as CSV', csv.status === 200 && csv.buf.length > 20 && /csv|text/.test(csv.type), `status=${csv.status} type=${csv.type} bytes=${csv.buf.length}`);
    const xlsx = await bytes('GET', '/api/reports/audit-logs/export?format=XLSX', admin);
    check('Audit log exports as Excel', xlsx.status === 200 && xlsx.buf.subarray(0, 2).toString() === 'PK', `status=${xlsx.status} bytes=${xlsx.buf.length}`);
    const pdf = await bytes('GET', '/api/reports/audit-logs/export?format=PDF', admin);
    check('Audit log exports as PDF', pdf.status === 200 && pdf.buf.subarray(0, 4).toString() === '%PDF', `status=${pdf.status} bytes=${pdf.buf.length}`);
    r = await bytes('GET', '/api/reports/audit-logs/export?format=CSV', s1Tok);
    check('A student cannot export the audit log', r.status === 403, `got ${r.status}`);
  });
} catch (e) {
  check('Unexpected error', false, e.message);
}

console.log(`\n${passed} passed, ${failed} failed`);
process.exit(failed ? 1 : 0);
