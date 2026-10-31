// k6: students signing in and opening the first screens, ramping up.
//   k6 run deploy/loadtest/login.js                      (each student has their own address, like students at home)
//   k6 run -e ONE_ADDRESS=1 deploy/loadtest/login.js     (all behind one college address: hits the 5/s sign-in limit)
//   k6 run -e VUS=100 deploy/loadtest/login.js
import http from 'k6/http';
import { check, sleep } from 'k6';

const data = JSON.parse(open('./loadtest-data.json'));
const VUS = Number(__ENV.VUS || 50);

export const options = {
  stages: [
    { duration: '30s', target: VUS },
    { duration: '1m', target: VUS },
    { duration: '10s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.02'],
    'http_req_duration{name:login}': ['p(95)<2000'],
  },
};

export default function () {
  const i = (__VU - 1) % data.emails.length;
  const headers = { 'Content-Type': 'application/json', 'X-Forwarded-For': __ENV.ONE_ADDRESS ? '203.0.113.7' : `198.51.100.${(i % 250) + 1}` };

  const login = http.post(`${data.gateway}/api/auth/login`, JSON.stringify({ identifier: data.emails[i], password: data.password }),
    { headers, tags: { name: 'login' } });
  const ok = check(login, { 'login 200': (r) => r.status === 200 });
  if (!ok) { sleep(2); return; }

  const auth = { headers: { ...headers, Authorization: `Bearer ${login.json('accessToken')}` } };
  for (const [name, path] of [['me', '/api/auth/me'], ['branding', '/api/public/branding'], ['quizzes', '/api/quizzes/available']]) {
    const r = http.get(`${data.gateway}${path}`, { ...auth, tags: { name } });
    check(r, { [`${name} ok`]: (x) => x.status === 200 });
  }
  sleep(5 + Math.random() * 5);
}
