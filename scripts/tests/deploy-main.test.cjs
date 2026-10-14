'use strict';

// Offline integration harness: every Docker/network command is replaced by a
// private executable. Run with: node scripts/tests/deploy-main.test.cjs
const fs = require('node:fs');
const os = require('node:os');
const { spawnSync } = require('node:child_process');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');

const script = `${__dirname}/../deploy-main.sh`;
// Ubuntu uses modern Bash. Set DEPLOY_TEST_BASH to an installed modern Bash
// on macOS; Apple's Bash 3.2 does not trigger errexit/ERR for bare [[ ... ]]
// failures, so the health/login regression cases correctly expose that mismatch.
const bash = process.env.DEPLOY_TEST_BASH || '/bin/bash';
const sha = 'a'.repeat(40);
const oldImage = 'sha256:offline-old-image';
const newImage = 'sha256:offline-new-image';
const hash = data => crypto.createHash('sha256').update(data).digest('hex');

// The helper deliberately rejects unrecognized calls instead of falling through
// to real commands. It also supplies the small Python JSON operations, making
// this suite independent of Python availability on the developer's machine.
const helper = String.raw`
'use strict';
const fs = require('node:fs');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const [command, ...args] = process.argv.slice(2);
const root = process.env.OFFLINE_ROOT;
process.on('uncaughtException', error => {
  fs.appendFileSync(root + '/mock-errors', error.stack + '\n');
  console.error(error.stack);
  process.exit(99);
});
const base = root + '/base';
const scenario = process.env.OFFLINE_SCENARIO;
const oldImage = 'sha256:offline-old-image';
const newImage = 'sha256:offline-new-image';
fs.appendFileSync(root + '/calls.jsonl', JSON.stringify({command, args}) + '\n');
const fail = code => process.exit(code);
const current = () => fs.readFileSync(root + '/image', 'utf8');
const writeImage = id => fs.writeFileSync(root + '/image', id);
function unknown() { throw Error('Unexpected mock invocation: ' + command + ' ' + JSON.stringify(args)); }
if (command === 'docker') {
  if (args[0] === 'inspect') {
    assert.equal(args.length, 3);
    assert.equal(args[2], 'meeteam-test');
    const format = args[1];
    if (format.includes('config_files')) console.log(base + '/compose-test.yml,' + base + '/previous.yml');
    else if (format.includes('compose.project')) console.log('offline-project');
    else if (format.includes('compose.service')) console.log('meeteam-test');
    else if (format === '--format={{.Image}}') console.log(current());
    else unknown();
  } else if (args[0] === 'tag') {
    assert.equal(args[1], oldImage);
    assert.match(args[2], /^meeteam-server:rollback-/);
  } else if (args[0] === 'build') {
    assert.equal(args.at(-1), root + '/payload');
    if (scenario === 'build-fail') fail(31);
    fs.writeFileSync(root + '/built', 'yes');
  } else if (args[0] === 'image' && args[1] === 'inspect') {
    assert.equal(fs.readFileSync(root + '/built', 'utf8'), 'yes');
    assert.match(args[3], /^meeteam-server:main-/);
    console.log(newImage);
  } else if (args[0] === 'exec') {
    assert.equal(args[1], 'meeteam-test-mysql');
    assert.equal(args[2], 'sh');
    assert.equal(args[3], '-c');
    assert.match(args[4], /mysqldump.*--single-transaction.*--no-tablespaces/);
    if (scenario === 'backup-fail') fail(32);
    if (scenario !== 'backup-empty') console.log('-- fake private database dump');
  } else if (args[0] === 'compose') {
    assert.deepEqual(args.slice(1, 3), ['-p', 'offline-project']);
    const files = [];
    let i = 3;
    while (args[i] === '-f') { files.push(args[i + 1]); i += 2; }
    files.forEach(file => assert.ok(fs.existsSync(file)));
    const override = fs.readFileSync(files.at(-1), 'utf8');
    if (args[i] === 'config') {
      assert.deepEqual(args.slice(i), ['config', '--quiet']);
      assert.match(override, /SPRING_JPA_HIBERNATE_DDL_AUTO: validate/);
      assert.match(override, /SPRING_SQL_INIT_MODE: never/);
      assert.match(override, /SPRING_FLYWAY_ENABLED: "false"/);
      assert.ok(override.includes(base + '/.env.test-account'));
    } else if (args[i] === 'up') {
      assert.deepEqual(args.slice(i), ['up', '-d', '--no-deps', '--pull', 'never', 'meeteam-test']);
      const rollback = override.includes('meeteam-server:rollback-');
      assert.deepEqual(files.slice(0, -1), rollback
        ? [base + '/compose-test.yml', base + '/previous.yml']
        : [base + '/compose-test.yml']);
      assert.ok(rollback || override.includes('meeteam-server:main-'));
      if (!rollback) assert.ok(fs.existsSync(root + '/built'));
      writeImage(rollback ? oldImage : newImage);
    } else unknown();
  } else unknown();
} else if (command === 'curl') {
  const url = args.at(-1);
  if (url === 'http://127.0.0.1:8080/actuator/health') {
    console.log(JSON.stringify({status: scenario === 'health-fail' && current() === newImage ? 'DOWN' : 'UP'}));
  } else if (url === 'https://meeteam.fignal.me/api/v1/auth/login/sejong') {
    assert.equal(current(), newImage);
    const requestFile = args[args.indexOf('--data-binary') + 1];
    assert.ok(requestFile.startsWith('@'));
    assert.deepEqual(JSON.parse(fs.readFileSync(requestFile.slice(1), 'utf8')),
      {studentId: '99990001', password: 'offline-fake-password'});
    fs.writeFileSync(args[args.indexOf('-c') + 1], 'offline-fake-cookie');
    fs.writeFileSync(args[args.indexOf('-o') + 1], '{}');
    process.stdout.write(scenario === 'login-fail' ? '401' : '200');
  } else if (url === 'https://meeteam.fignal.me/api/v1/members/me') {
    assert.equal(fs.readFileSync(args[args.indexOf('-b') + 1], 'utf8'), 'offline-fake-cookie');
    fs.writeFileSync(args[args.indexOf('-o') + 1], JSON.stringify({result: {
      email: scenario === 'profile-fail' ? 'wrong@example.invalid' : 'meeteam-test@example.invalid'
    }}));
    process.stdout.write('200');
  } else unknown();
} else if (command === 'python3') {
  if (args[0] === '-c') {
    assert.ok(args[1].includes('get("status")=="UP"'));
    fail(JSON.parse(fs.readFileSync(args[2], 'utf8')).status === 'UP' ? 0 : 1);
  } else if (args[0] === '-') {
    const source = fs.readFileSync(0, 'utf8');
    if (args.length === 3 && source.includes("'studentId': '99990001'")) {
      const line = fs.readFileSync(args[1], 'utf8').split('\n').find(s => s.startsWith('TEST_ACCOUNT_PASSWORD='));
      assert.ok(line);
      fs.writeFileSync(args[2], JSON.stringify({studentId: '99990001', password: line.slice(line.indexOf('=') + 1)}));
    } else if (args.length === 2 && source.includes("profile.get('email')")) {
      fail(JSON.parse(fs.readFileSync(args[1], 'utf8')).result?.email === 'meeteam-test@example.invalid' ? 0 : 1);
    } else unknown();
  } else unknown();
} else if (command === 'sha256sum') {
  assert.deepEqual(args, ['-c', 'SHA256SUMS']);
  const entries = fs.readFileSync('SHA256SUMS', 'utf8').trim().split('\n');
  for (const entry of entries) {
    const match = /^([a-f0-9]{64})  (.+)$/.exec(entry);
    assert.ok(match);
    const actual = crypto.createHash('sha256').update(fs.readFileSync(match[2])).digest('hex');
    if (actual !== match[1]) { console.error(match[2] + ': FAILED'); fail(1); }
  }
} else if (command === 'flock') {
  assert.deepEqual(args, ['-n', '9']);
} else if (command === 'sleep') {
  assert.deepEqual(args, ['0']);
} else unknown();
`;

function runCase(name, setup, expectation) {
  const root = fs.mkdtempSync(`${os.tmpdir()}/meeteam-deploy-offline-`);
  try {
    for (const dir of ['bin', 'base', 'payload']) fs.mkdirSync(`${root}/${dir}`);
    const base = `${root}/base`;
    const payload = `${root}/payload`;
    const privateFiles = {
      '.env': 'MYSQL_ROOT_PASSWORD=offline-fake-db-password\nMYSQL_DATABASE=offline_fake_db\n',
      '.env.test-account': 'TEST_ACCOUNT_ENABLED=true\nTEST_ACCOUNT_PASSWORD=offline-fake-password\n',
      'compose-test.yml': 'services:\n  meeteam-test:\n    image: offline-old\n',
      'previous.yml': 'services:\n  meeteam-test:\n    environment:\n      OFFLINE: "true"\n'
    };
    for (const [file, content] of Object.entries(privateFiles)) fs.writeFileSync(`${base}/${file}`, content);
    const artifacts = {
      REVISION: `${sha}\n`, Dockerfile: 'FROM scratch\n',
      'custom.java.security': 'networkaddress.cache.ttl=60\n', 'app.jar': 'offline-fake-jar\n'
    };
    for (const [file, content] of Object.entries(artifacts)) fs.writeFileSync(`${payload}/${file}`, content);
    fs.writeFileSync(`${payload}/SHA256SUMS`, Object.entries(artifacts).map(([file, content]) => `${hash(content)}  ${file}\n`).join(''));
    fs.writeFileSync(`${root}/image`, oldImage);
    fs.writeFileSync(`${root}/calls.jsonl`, '');
    fs.writeFileSync(`${root}/mock.cjs`, helper);
    // Absolute Node path and tightly controlled PATH ensure mocks cannot reach
    // a host Docker, curl, Python, flock, sleep, or sha256sum by accident.
    const quote = value => "'" + value.replace(/'/g, "'\\''") + "'";
    for (const command of ['docker', 'curl', 'python3', 'flock', 'sleep', 'sha256sum']) {
      fs.writeFileSync(`${root}/bin/${command}`, `#!/bin/bash\nexec ${quote(process.execPath)} ${quote(root + '/mock.cjs')} ${quote(command)} "$@"\n`, {mode: 0o700});
    }
    if (setup) setup({root, base, payload});
    const result = spawnSync(bash, [script, payload, sha, 'offline-run'], {
      env: {
        PATH: `${root}/bin:/usr/bin:/bin`, HOME: root, TMPDIR: root,
        LC_ALL: 'C', OPENSSL_CONF: '/dev/null', OFFLINE_ROOT: root, OFFLINE_SCENARIO: name,
        MEETEAM_DEPLOY_DIR: base, MEETEAM_HEALTH_RETRIES: '1', MEETEAM_HEALTH_INTERVAL: '0'
      }, encoding: 'utf8', timeout: 30000, maxBuffer: 1024 * 1024
    });
    assert.ifError(result.error);
    assert.equal(result.signal, null, result.stderr);
    assert.ok(!fs.existsSync(`${root}/mock-errors`),
      fs.existsSync(`${root}/mock-errors`) ? fs.readFileSync(`${root}/mock-errors`, 'utf8') : '');
    assert.ok(!result.stderr.includes('Unexpected mock invocation'), result.stderr);
    assert.ok(!result.stderr.includes('AssertionError'), result.stderr);
    const calls = fs.readFileSync(`${root}/calls.jsonl`, 'utf8').trim().split('\n').filter(Boolean).map(JSON.parse);
    const ups = calls.filter(c => c.command === 'docker' && c.args[0] === 'compose' && c.args.includes('up'));
    const revisionFile = `${base}/.deploy/current-revision`;
    if (expectation === 'success') {
      assert.equal(result.status, 0, result.stdout + result.stderr);
      assert.equal(fs.readFileSync(`${root}/image`, 'utf8'), newImage);
      assert.equal(fs.readFileSync(revisionFile, 'utf8'), `${sha}\n`);
      assert.equal(ups.length, 1);
      assert.match(result.stdout, /DEPLOY SUCCESS/);
      assert.match(result.stdout, /Existing test login and authenticated profile verified/);
      const backups = fs.readdirSync(`${base}/backups`);
      assert.equal(backups.length, 1);
      assert.ok(fs.statSync(`${base}/backups/${backups[0]}`).size > 0);
    } else {
      assert.notEqual(result.status, 0, result.stdout + result.stderr);
      assert.equal(fs.readFileSync(`${root}/image`, 'utf8'), oldImage);
      assert.ok(!fs.existsSync(revisionFile));
      assert.equal(ups.length, expectation === 'rollback' ? 2 : 0);
      if (expectation === 'rollback') assert.match(result.stdout, /Previous app restored and healthy/);
      if (expectation === 'untouched') {
        assert.match(result.stdout, /Existing app was left running/);
        assert.ok(calls.some(c => c.command === 'docker' && c.args[0] === 'build'));
        const dumps = calls.filter(c => c.command === 'docker' && c.args[0] === 'exec');
        assert.equal(dumps.length, name === 'build-fail' ? 0 : 1);
        if (name === 'build-fail') assert.equal(result.status, 31);
        if (name === 'backup-fail') assert.equal(result.status, 32);
      }
      if (expectation === 'early') assert.equal(calls.filter(c => c.command === 'docker' || c.command === 'curl').length, 0);
    }
    // Every scenario leaves original server/private configuration byte-for-byte
    // unchanged (except the deliberate pre-run removal in missing-env cases).
    for (const [file, content] of Object.entries(privateFiles)) {
      if (fs.existsSync(`${base}/${file}`)) assert.equal(fs.readFileSync(`${base}/${file}`, 'utf8'), content);
    }
    const indexOf = predicate => calls.findIndex(predicate);
    if (ups.length) {
      const backupIndex = indexOf(c => c.command === 'docker' && c.args[0] === 'exec');
      const upIndex = indexOf(c => c.command === 'docker' && c.args[0] === 'compose' && c.args.includes('up'));
      assert.ok(backupIndex >= 0 && backupIndex < upIndex, 'backup must precede app replacement');
    }
    console.log(`ok - ${name}`);
  } finally {
    fs.rmSync(root, {recursive: true, force: true});
  }
}

const cases = [
  ['success', null, 'success'],
  ['build-fail', null, 'untouched'],
  ['backup-fail', null, 'untouched'],
  ['backup-empty', null, 'untouched'],
  ['health-fail', null, 'rollback'],
  ['login-fail', null, 'rollback'],
  ['profile-fail', null, 'rollback'],
  ['missing-private-env', ({base}) => fs.unlinkSync(`${base}/.env`), 'early'],
  ['missing-test-account-env', ({base}) => fs.unlinkSync(`${base}/.env.test-account`), 'early'],
  ['checksum-mismatch', ({payload}) => fs.appendFileSync(`${payload}/app.jar`, 'corrupted'), 'early'],
  ['missing-checksum-manifest', ({payload}) => fs.unlinkSync(`${payload}/SHA256SUMS`), 'early']
];
const version = spawnSync(bash, ['--version'], {encoding: 'utf8'});
assert.ifError(version.error);
assert.equal(version.status, 0, version.stderr);
console.log(`Shell: ${version.stdout.split('\n')[0]}`);
let failures = 0;
for (const [name, setup, expectation] of cases) {
  try { runCase(name, setup, expectation); }
  catch (error) { failures++; console.error(`not ok - ${name}\n${error.stack}`); }
}
console.log(`${cases.length - failures}/${cases.length} offline deployment regression tests passed`);
process.exitCode = failures ? 1 : 0;
