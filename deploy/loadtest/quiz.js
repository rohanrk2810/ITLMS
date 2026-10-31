// k6: a whole class starting the same quiz together, answering and submitting. The hardest moment of an exam.
//   k6 run -e VUS=100 deploy/loadtest/quiz.js
// Students sign in during setup(), then all begin at once, as they do when a test opens.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

// How many answers of each kind (name + status code) came back, to tell a limit (429) from a failure (5xx).
const statuses = new Counter('statuses');
const seen = (name, r) => {
  statuses.add(1, { name, status: String(r.status) });
  if (r.status >= 300 && __ITER === 0) console.log(`${name} ${r.status} ${String(r.body).slice(0, 160)}`);
};

const data = JSON.parse(open('./loadtest-data.json'));
const VUS = Number(__ENV.VUS || 50);

export const options = {
  setupTimeout: '10m',
  scenarios: {
    exam: { executor: 'per-vu-iterations', vus: VUS, iterations: 1, maxDuration: '10m' },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    'http_req_duration{name:start}': ['p(95)<3000'],
    'http_req_duration{name:save}': ['p(95)<1500'],
    'http_req_duration{name:submit}': ['p(95)<3000'],
  },
};

const jsonHeaders = (token, i) => ({
  'Content-Type': 'application/json', Authorization: `Bearer ${token}`, 'X-Forwarded-For': `198.51.100.${(i % 250) + 1}`,
});

// Signing in 100 students at once would test the sign-in limit, not the quiz, so it is done up front, paced.
export function setup() {
  const tokens = [];
  for (let i = 0; i < VUS; i++) {
    const r = http.post(`${data.gateway}/api/auth/login`, JSON.stringify({ identifier: data.emails[i % data.emails.length], password: data.password }),
      { headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': `198.51.100.${(i % 250) + 1}` } });
    tokens.push(r.status === 200 ? r.json('accessToken') : null);
    sleep(0.15);
  }
  return { tokens };
}

export default function (setupData) {
  const i = __VU - 1;
  const token = setupData.tokens[i];
  if (!token) return;
  const h = jsonHeaders(token, i);

  const start = http.post(`${data.gateway}/api/quizzes/${data.quizId}/attempts`, null, { headers: h, tags: { name: 'start' } });
  seen('start', start);
  if (!check(start, { 'attempt started': (r) => r.status === 200 || r.status === 201 })) return;
  const paper = start.json();

  // The key is not in the paper, so pick the first option: marks do not matter here, only the traffic does.
  const answers = paper.questions.map((q) => ({ questionId: q.id, selectedOptionIds: [q.options[0].id] }));
  for (let n = 0; n < answers.length; n++) {
    const r = http.put(`${data.gateway}/api/quiz-attempts/${paper.attemptId}/answers`, JSON.stringify({ answers: answers.slice(0, n + 1) }),
      { headers: h, tags: { name: 'save' } });
    seen('save', r);
    check(r, { 'answer saved': (x) => x.status === 200 });
    sleep(2 + Math.random() * 3);
  }
  const done = http.post(`${data.gateway}/api/quiz-attempts/${paper.attemptId}/submit`, JSON.stringify({ answers }), { headers: h, tags: { name: 'submit' } });
  seen('submit', done);
  check(done, { 'submitted': (r) => r.status === 200 });
}
