// Lists and installs language packages in the optional Piston container (docs/08-code-execution.md).
//
// Piston ships with no languages; each one is a package fetched from GitHub into the piston-packages
// volume, one time. Run this INSIDE the container by piping it into the node that is already there:
//
//   docker compose --profile piston exec -T piston node - list  < deploy/scripts/piston-packages.js
//   docker compose --profile piston exec -T piston node - install java@15.0.2 python@3.12.0 \
//       gcc@10.2.0 mono@6.12.0 sqlite3@3.36.0                   < deploy/scripts/piston-packages.js
//
// (Plain CommonJS on purpose: the container's Node is 15.)
const http = require('http');

function call(method, path, body, timeoutMs) {
  return new Promise((resolve, reject) => {
    const data = body ? JSON.stringify(body) : null;
    const req = http.request(
      {
        host: '127.0.0.1',
        port: 2000,
        path,
        method,
        headers: data ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) } : {},
      },
      (res) => {
        let text = '';
        res.on('data', (chunk) => (text += chunk)).on('end', () => {
          try {
            resolve({ status: res.statusCode, body: JSON.parse(text) });
          } catch (e) {
            resolve({ status: res.statusCode, body: text });
          }
        });
      },
    );
    req.setTimeout(timeoutMs, () => req.destroy(new Error('timed out after ' + timeoutMs / 1000 + 's')));
    req.on('error', reject);
    if (data) req.write(data);
    req.end();
  });
}

async function main() {
  const [mode, ...args] = process.argv.slice(2);

  if (mode === 'list') {
    const r = await call('GET', '/api/v2/packages', null, 90000);
    if (!Array.isArray(r.body)) {
      console.log('Could not read the package list (HTTP ' + r.status + '): ' + JSON.stringify(r.body).slice(0, 300));
      process.exitCode = 1;
      return;
    }
    const wanted = args.length ? args : ['java', 'python', 'gcc', 'mono', 'sqlite3'];
    for (const language of wanted) {
      const versions = r.body
        .filter((p) => p.language === language)
        .map((p) => p.language_version + (p.installed ? ' (installed)' : ''));
      console.log(language + ': ' + (versions.join(', ') || 'no such package'));
    }
    return;
  }

  if (mode === 'install' && args.length) {
    // One at a time: each is a large download plus an unpack, and they compete for the same disk and RAM.
    for (const spec of args) {
      const [language, version] = spec.split('@');
      const started = Date.now();
      try {
        const r = await call('POST', '/api/v2/packages', { language, version }, 900000);
        const seconds = ((Date.now() - started) / 1000).toFixed(0);
        console.log(r.status === 200 ? 'installed ' + spec + ' (' + seconds + 's)' : 'FAILED ' + spec + ': HTTP ' + r.status + ' ' + JSON.stringify(r.body).slice(0, 200));
        if (r.status !== 200) process.exitCode = 1;
      } catch (e) {
        console.log('FAILED ' + spec + ': ' + e.message);
        process.exitCode = 1;
      }
    }
    return;
  }

  console.log('usage: node - list [language...]  |  node - install language@version ...');
  process.exitCode = 2;
}

main();
