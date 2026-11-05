'use strict';
// Offline only. All Docker, HTTP, lock, EC2-probe and wait calls are mocked,
// rejecting unknown invocations. No host Docker daemon or credentials are used.
// Optional ADMIN_SETUP_TEST_BASH selects a modern Bash; Bash 3.2 is also tested.
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const assert = require('node:assert/strict');
const { spawnSync } = require('node:child_process');
const script = path.resolve(__dirname, '../setup-admin-account.sh');
const bash = process.env.ADMIN_SETUP_TEST_BASH || '/bin/bash';
const syntax = spawnSync(bash, ['-n',script], {encoding:'utf8'});
assert.equal(syntax.status,0,syntax.stderr);
const python = spawnSync('/bin/sh', ['-c', 'command -v python3'], {encoding:'utf8'}).stdout.trim();
assert.ok(python, 'python3 required for actual private-file and JSON validation');
const image = 'sha256:' + 'a'.repeat(64);
// Synthetic non-credential test data, never an actual account password/hash.
const hash = '$2b$12$' + 'A'.repeat(53);
const envData = `ADMIN_ACCOUNT_ENABLED=true\nADMIN_ACCOUNT_USERNAME=99990002\nADMIN_ACCOUNT_PASSWORD_HASH='${hash}'\n`;
const helper = String.raw`
'use strict';
const fs = require('node:fs');
const assert = require('node:assert/strict');
const [cmd, ...a] = process.argv.slice(2);
const root = process.env.OFFLINE_ROOT, scenario = process.env.OFFLINE_SCENARIO;
const base = root + '/base', image = 'sha256:' + 'a'.repeat(64), hash = '$2b$12$' + 'A'.repeat(53);
const exists = file => fs.existsSync(root + '/' + file);
const state = file => fs.writeFileSync(root + '/' + file, 'yes');
fs.appendFileSync(root + '/calls', JSON.stringify({cmd, args:a}) + '\n');
process.on('uncaughtException', e => { fs.appendFileSync(root + '/errors', e.stack + '\n'); console.error(e.stack); process.exit(99); });
function unknown() { throw Error('Unrecognized offline invocation: ' + cmd + JSON.stringify(a)); }
if (cmd === 'cat') {
  assert.deepEqual(a, ['/sys/devices/virtual/dmi/id/sys_vendor']);
  console.log(scenario === 'not-ec2' ? 'Other' : 'Amazon EC2');
} else if (cmd === 'flock') {
  assert.deepEqual(a, ['-n','9']);
  if (scenario === 'lock-fail') process.exit(1);
} else if (cmd === 'sleep') { assert.deepEqual(a,['3']); }
else if (cmd === 'date') unknown();
else if (cmd === 'id') {
  assert.equal(a.length,1); assert.ok(a[0] === '-u' || a[0] === '-g');
  console.log(scenario === 'root-user' && a[0] === '-u' ? '0' : a[0] === '-u' ? process.getuid() : process.getgid());
}
else if (cmd === 'curl') {
  assert.deepEqual(a, ['-fsS','--connect-timeout','2','--max-time','5','http://127.0.0.1:8080/actuator/health']);
  console.log(JSON.stringify({status: scenario === 'health-fail' && exists('activated') && !exists('rollback') ? 'DOWN' : 'UP'}));
} else if (cmd === 'docker') {
  if (a[0] === 'inspect') {
    assert.equal(a.length, 3); assert.equal(a[2], 'meeteam-test');
    const f = a[1];
    if (f === '--format={{.Name}}') console.log(scenario === 'wrong-target' ? '/other' : '/meeteam-test');
    else if (f === '--format={{.Image}}') console.log(image);
    else if (f.includes('config_files')) console.log(scenario === 'unsafe-config' ? '/outside/compose.yml' : base + '/compose-test.yml,' + base + '/previous.yml');
    else if (f.includes('working_dir')) console.log(base);
    else if (f.includes('compose.project')) console.log('offline-project');
    else if (f.includes('compose.service')) console.log('meeteam-test');
    else if (f.includes('range .Config.Env')) {
      assert.equal(f, '--format={{range .Config.Env}}{{if ge (len .) 22}}{{if eq (slice . 0 22) "ADMIN_ACCOUNT_ENABLED="}}{{if ne . "ADMIN_ACCOUNT_ENABLED=false"}}enabled{{end}}{{end}}{{end}}{{end}}');
      if (scenario === 'enabled' || (exists('activated') && !exists('rollback') && scenario !== 'env-shadow')) process.stdout.write('enabled');
    } else unknown();
  } else if (a[0] === 'run') {
    const mount = a[a.indexOf('--mount') + 1];
    const m = /^type=bind,src=(.+),dst=\/setup$/.exec(mount); assert.ok(m);
    const scratch = m[1]; assert.ok(scratch.startsWith(base + '/.deploy/admin-account.'));
    assert.equal(fs.statSync(scratch).mode & 0o777, 0o700);
    assert.deepEqual(a, ['run','--rm','-it','--network','none','--read-only','--cap-drop','ALL','--security-opt','no-new-privileges','--memory','256m','--pids-limit','128','--tmpfs','/tmp:rw,noexec,nosuid,size=16m','--user',process.getuid()+':'+process.getgid(),'--mount',mount,'--entrypoint','java',image,'-Xms16m','-Xmx96m','-XX:MaxMetaspaceSize=96m','-XX:+UseSerialGC','-Dloader.main=com.wardk.meeteam_backend.global.auth.service.AdminPasswordSetupTool','-cp','/app.jar','org.springframework.boot.loader.launch.PropertiesLauncher','/setup/admin.hash']);
    assert.ok(!fs.existsSync(scratch + '/admin.hash'));
    if (scenario === 'tool-fail') process.exit(3);
    const value = scenario === 'bad-hash' ? 'not-a-hash\n' : scenario === 'wrong-cost' ? hash.replace('$12$', '$10$')+'\n' : scenario === 'extra-output' ? hash+'\nextra\n' : hash+'\n';
    fs.writeFileSync(scratch + '/admin.hash',value,{mode: scenario === 'wrong-mode' ? 0o644 : 0o600});
    if(scenario === 'wrong-mode') fs.chmodSync(scratch + '/admin.hash',0o644);
    state('generated');
  } else if (a[0] === 'exec') {
    const query = a[1] === '-i';
    assert.deepEqual(a.slice(0,query?5:4), query?['exec','-i','meeteam-test-mysql','sh','-c']:['exec','meeteam-test-mysql','sh','-c']);
    assert.equal(a.length,query?6:5);
    const command = a.at(-1);
    if (query) {
      assert.equal(command,'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --skip-column-names "$MYSQL_DATABASE"');
      const sql = fs.readFileSync(0,'utf8');
      assert.ok(sql.startsWith('SELECT COUNT(*) FROM') || sql.startsWith('SELECT COUNT(*), COALESCE(SUM('));
      assert.ok(sql.includes("email='meeteam-admin@example.invalid'") && sql.includes("student_id='99990002'"));
      assert.ok(!/\b(?:DELETE|UPDATE|INSERT|ALTER)\b/i.test(sql));
      const collision = ['collision','resume','resume-mismatch'].includes(scenario) || exists('activated');
      if (sql.startsWith('SELECT COUNT(*) FROM')) console.log(collision ? '1' : '0');
      else {
        assert.ok(sql.includes("BINARY password=BINARY '"+hash+"'"));
        assert.ok(sql.includes("provider='local-admin' AND role='ADMIN' AND deleted_at IS NULL AND admin_withdrawn=0 AND suspended=0"));
        console.log(collision ? '1\t'+(scenario==='resume-mismatch' || (scenario==='postcheck-fail' && exists('activated')) ? '0':'1') : '0\t0');
      }
    } else {
      assert.equal(command,'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --no-tablespaces "$MYSQL_DATABASE"');
      if (scenario === 'backup-fail') process.exit(2);
      console.log('-- synthetic private backup'); state('backup');
    }
  } else if (a[0] === 'compose') {
    assert.deepEqual(a.slice(1,3),['-p','offline-project']);
    let i=3; const files=[];
    while(a[i]==='-f') { files.push(a[i+1]); i+=2; }
    assert.deepEqual(files.slice(0,2),[base+'/compose-test.yml',base+'/previous.yml']);
    assert.equal(files.length,3);
    const rollback = files[2].endsWith('/prior-image.yml');
    const content = fs.readFileSync(files[2],'utf8');
    assert.ok(content.includes('image: '+image));
    if (rollback) assert.ok(!content.includes('env_file'));
    else assert.ok(content.includes('env_file:\n      - '+base+'/.env.admin-account'));
    if (a[i]==='config') {
      assert.ok(!rollback); assert.deepEqual(a.slice(i),['config','--quiet']);
      assert.equal(fs.readFileSync(base+'/.env.admin-account','utf8'), 'ADMIN_ACCOUNT_ENABLED=true\nADMIN_ACCOUNT_USERNAME=99990002\nADMIN_ACCOUNT_PASSWORD_HASH=\''+hash+'\'\n');
      if (scenario === 'config-fail') process.exit(2);
    } else if (a[i]==='up') {
      assert.deepEqual(a.slice(i),['up','-d','--no-deps','--pull','never','meeteam-test']);
      if(rollback) { assert.ok(!fs.existsSync(base+'/.env.admin-account')); state('rollback'); }
      else { assert.ok(exists('backup')); state('activated'); if(scenario==='up-fail')process.exit(2); }
    } else unknown();
  } else unknown();
} else unknown();
`;
const quote = value => "'"+value.replace(/'/g,"'\\''")+"'";
let count=0;
function run(name, options={}) {
  const root=fs.mkdtempSync(path.join(os.tmpdir(),'meeteam-admin-offline-'));
  try {
    const base=root+'/base';
    fs.mkdirSync(root+'/bin'); fs.mkdirSync(base);
    const originals={
      '.env':'MYSQL_ROOT_PASSWORD=offline-db-secret\nADMIN_ACCOUNT_ENABLED=false\n',
      '.env.test-account':'TEST_ACCOUNT_ENABLED=true\nTEST_ACCOUNT_PASSWORD=offline-user-secret\n',
      'compose-test.yml':'services:\n  meeteam-test:\n    image: preserve-original\n',
      'previous.yml':'services:\n  meeteam-test:\n    environment:\n      OFFLINE: true\n'
    };
    for(const [file,data] of Object.entries(originals))fs.writeFileSync(base+'/'+file,data,{mode:0o600});
    fs.writeFileSync(root+'/mock.cjs',helper);
    fs.writeFileSync(root+'/calls','');
    for(const cmd of ['docker','curl','flock','sleep','cat','date','id']) {
      fs.writeFileSync(root+'/bin/'+cmd,'#!/bin/sh\nexec '+quote(process.execPath)+' '+quote(root+'/mock.cjs')+' '+quote(cmd)+' "$@"\n',{mode:0o700});
    }
    fs.writeFileSync(root+'/bin/python3','#!/bin/sh\nexec '+quote(python)+' "$@"\n',{mode:0o700});
    const pending=base+'/.env.admin-account.pending', active=base+'/.env.admin-account';
    if(options.pending)fs.writeFileSync(pending,options.pending===true?envData:options.pending,{mode:0o600});
    if(name==='active')fs.writeFileSync(active,envData,{mode:0o600});
    if(name==='pending-symlink')fs.symlinkSync(base+'/.env.test-account',pending);
    const result=spawnSync(bash,[script,...(options.resume?['--resume']:[])],{
      env:{OPENSSL_CONF:'/dev/null',PATH:root+'/bin:/usr/bin:/bin',HOME:root,TMPDIR:root,LC_ALL:'C',OFFLINE_ROOT:root,OFFLINE_SCENARIO:name,...(name==='sudo-user'?{SUDO_USER:'offline-deploy-owner'}:{}),MEETEAM_DEPLOY_DIR:base},encoding:'utf8',timeout:120000
    });
    assert.ok(!result.error,name+': '+result.error);
    if(fs.existsSync(root+'/errors'))assert.fail(name+': '+fs.readFileSync(root+'/errors','utf8'));
    if(options.success) assert.equal(result.status,0,name+': '+result.stdout+result.stderr); else assert.ok(result.status !== 0 && result.status !== null,name+': '+result.stdout+result.stderr);
    const output=result.stdout+result.stderr;
    if(!options.success) assert.ok(!output.includes('activated and healthy'),name+': false success');
    const calls=fs.readFileSync(root+'/calls','utf8').trim().split('\n').filter(Boolean).map(JSON.parse);
    assert.ok(!output.includes(hash) && !output.includes('offline-db-secret') && !output.includes('offline-user-secret'),name+': secret output');
    assert.ok(!JSON.stringify(calls).includes(hash),name+': hash on argv');
    assert.ok(!JSON.stringify(calls).includes('offline-db-secret') && !JSON.stringify(calls).includes('offline-user-secret'));
    for(const [file,data] of Object.entries(originals))assert.equal(fs.readFileSync(base+'/'+file,'utf8'),data,name+': source changed');
    if(options.success){
      assert.equal(fs.readFileSync(active,'utf8'),envData); assert.equal(fs.statSync(active).mode&0o777,0o600); assert.ok(!fs.existsSync(pending));
    } else if(name!=='active')assert.ok(!fs.existsSync(active),name+': active file left behind');
    if(options.retained)assert.equal(fs.readFileSync(pending,'utf8'),envData,name+': pending must retain identical hash');
    if(options.rollback)assert.ok(fs.existsSync(root+'/rollback'),name+': rollback missing');
    if(['root-user','sudo-user'].includes(name))assert.ok(!fs.existsSync(base+'/.deploy')&&!fs.existsSync(base+'/backups'),name+': guard created recovery files');
    if(options.noRun)assert.ok(!calls.some(c=>c.cmd==='docker'&&c.args[0]==='run'),name+': unexpected password prompt');
    if(options.noUp)assert.ok(!calls.some(c=>c.cmd==='docker'&&c.args[0]==='compose'&&c.args.includes('up')),name+': unexpected mutation');
    if(options.resume)assert.ok(!fs.existsSync(root+'/generated'),name+': resume generated new hash');
    if(fs.existsSync(base+'/backups'))for(const f of fs.readdirSync(base+'/backups'))assert.equal(fs.statSync(base+'/backups/'+f).mode&0o777,0o600);
    if(fs.existsSync(base+'/.deploy'))for(const f of fs.readdirSync(base+'/.deploy').filter(f=>f.startsWith('admin-account.')))assert.ok(!fs.existsSync(base+'/.deploy/'+f+'/setup'),name+': scratch not cleaned');
    console.log('PASS '+name); count++;
  } finally { fs.rmSync(root,{recursive:true,force:true}); }
}
run('success',{success:true});
for(const name of ['not-ec2','root-user','sudo-user','lock-fail','wrong-target','unsafe-config','enabled','collision','pending-symlink'])run(name,{noRun:true,noUp:true});
run('active',{noRun:true,noUp:true});
run('pending',{pending:true,noRun:true,noUp:true});
for(const name of ['bad-hash','wrong-cost','extra-output','wrong-mode','tool-fail'])run(name,{noUp:true});
for(const name of ['config-fail','backup-fail'])run(name,{retained:true,noUp:true});
for(const name of ['up-fail','health-fail','env-shadow','postcheck-fail'])run(name,{retained:true,rollback:true});
run('resume',{pending:true,resume:true,success:true,noRun:true});
run('resume-empty',{pending:true,resume:true,success:true,noRun:true});
run('resume-mismatch',{pending:true,resume:true,retained:true,noRun:true,noUp:true});
run('resume-malformed',{pending:'invalid\n',resume:true,noRun:true,noUp:true});
console.log(count+' offline admin setup cases passed');
