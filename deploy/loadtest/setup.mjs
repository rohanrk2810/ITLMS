// Prepares a LOCAL stack for the k6 load tests: one course, one trainer, N students enrolled in a batch, and a
// published quiz. Writes loadtest-data.json (student emails, quiz id, answer key) for the k6 scripts.
//
//   node deploy/loadtest/setup.mjs 100        (N students, default 100)
//
// Needs the stack up (gateway on :8080) and CAPTCHA_ENABLED=false for that run. It writes real rows, so never point it
// at a stack with real data: it refuses any address other than localhost.
import { readFileSync, writeFileSync } from 'node:fs';
import { execSync } from 'node:child_process';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const DOCKER_DIR = join(here, '..', 'docker');
const GW = process.env.GATEWAY ?? 'http://localhost:8080';
if (!/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?$/.test(GW)) throw new Error(`Refusing to load-test ${GW}: local stacks only`);

const N = Number(process.argv[2] ?? 100);
const PASSWORD = 'LoadTest#123';
// bcrypt strength 12, the same cost as production, of PASSWORD.
const HASH = process.env.PASSWORD_HASH ?? readFileSync(join(here, 'password.hash'), 'utf8').trim();

const env = Object.fromEntries(readFileSync(join(DOCKER_DIR, '.env'), 'utf8')
  .split(/\r?\n/).filter((l) => l && !l.startsWith('#') && l.includes('='))
  .map((l) => [l.slice(0, l.indexOf('=')), l.slice(l.indexOf('=') + 1)]));

async function api(method, path, token, body) {
  const res = await fetch(GW + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* not json */ }
  if (res.status >= 300) throw new Error(`${method} ${path} -> ${res.status}: ${text.slice(0, 300)}`);
  return json;
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const psql = (db, sql) => execSync(`docker compose exec -T postgres psql -U itilms -d ${db} -tA`,
  { cwd: DOCKER_DIR, input: sql, encoding: 'utf8' }).trim();

const run = String(Date.now()).slice(-6);
const admin = (await api('POST', '/api/auth/login', null, { identifier: env.BOOTSTRAP_ADMIN_EMAIL, password: env.BOOTSTRAP_ADMIN_PASSWORD })).accessToken;

const course = await api('POST', '/api/courses', admin, {
  title: `Load test ${run}`, code: `LT-${run}`, summary: 'Load test', description: 'Load test', durationHours: 10, level: 'BEGINNER', fee: 1000,
});
const courseId = course.id ?? course.course?.id;
const mod = await api('POST', `/api/courses/${courseId}/modules`, admin, { title: 'M1' });
await api('POST', `/api/modules/${mod.id}/lessons`, admin, { title: 'L1', type: 'VIDEO', contentUrl: 'https://example.com/v', durationMinutes: 5, mandatory: true });
await api('POST', `/api/courses/${courseId}/publish`, admin);

const person = (first, n) => ({ firstName: first, lastName: `L${run}`, email: `${first.toLowerCase()}${n}.${run}@load.local`, phone: `9${run}${String(n).padStart(3, '0')}`.slice(0, 10) });
const trainer = await api('POST', '/api/trainers', admin, { ...person('Trainer', 0), specialization: 'Java' });

const students = [];
for (let i = 1; i <= N; i += 10) {
  const part = await Promise.all(Array.from({ length: Math.min(10, N - i + 1) }, (_, k) => api('POST', '/api/students', admin, person('Stu', i + k))));
  students.push(...part);
  process.stdout.write(`\rstudents ${students.length}/${N}`);
}
console.log();

// Profile ids reach identity over Kafka; wait for them, then give every account the one known password.
for (let t = 0; t < 60; t++) {
  const linked = psql('itilms_identity', `select count(*) from users where email like '%.${run}@load.local' and profile_id is not null;`);
  if (Number(linked) >= N + 1) break;
  await sleep(2000);
}
psql('itilms_identity', `update users set password_hash='${HASH}', must_change_password=false where email like '%.${run}@load.local';`);

const now = new Date();
const ist = (d) => new Date(d.getTime() + 5.5 * 3600e3).toISOString();
const sp = { date: ist(now).slice(0, 10), time: '00:00', dow: ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'][new Date(ist(now)).getUTCDay()] };
const batch = await api('POST', '/api/batches', admin, {
  name: `Load ${run}`, courseId, trainerId: trainer.id, startDate: sp.date, startTime: '06:00', endTime: '20:00',
  classDays: [sp.dow], mode: 'ONLINE', capacity: Math.max(30, N + 10),
});
for (let i = 0; i < students.length; i += 50) {
  await api('POST', `/api/batches/${batch.id}/students`, admin, { studentIds: students.slice(i, i + 50).map((s) => s.id) });
}

const trainerTok = (await api('POST', '/api/auth/login', null, { identifier: trainer.email, password: PASSWORD })).accessToken;
const quiz = await api('POST', '/api/quizzes', trainerTok, {
  courseId, batchId: batch.id, title: `Load quiz ${run}`, durationMinutes: 60, passPercentage: 50,
  attemptsAllowed: 2, showResultImmediately: true, mandatory: true,
});
for (let q = 1; q <= 20; q++) {
  await api('POST', `/api/quizzes/${quiz.id}/questions`, trainerTok, {
    questionText: `Question ${q}?`, type: 'SINGLE_CHOICE', marks: 1,
    options: [{ optionText: 'Right', correct: true }, { optionText: 'Wrong 1', correct: false }, { optionText: 'Wrong 2', correct: false }],
  });
}
await api('POST', `/api/quizzes/${quiz.id}/publish`, trainerTok);

writeFileSync(join(here, 'loadtest-data.json'), JSON.stringify({
  gateway: GW, password: PASSWORD, quizId: quiz.id, emails: students.map((s) => s.email),
  trainer: trainer.email, courseId, batchId: batch.id,
}, null, 2));
console.log(`Ready: ${students.length} students, quiz ${quiz.id}. Data in deploy/loadtest/loadtest-data.json`);
