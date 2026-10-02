import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { resolve, join } from 'node:path';
import { randomUUID } from 'node:crypto';

const root = fileURLToPath(new URL('../../', import.meta.url));
const jar = join(root, 'backend/target/plan-platform-api-0.1.0.jar');
if (!existsSync(jar)) throw new Error('Build the isolated acceptance API first: mvn -f ../backend/pom.xml package');
const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
// Command-line properties override inherited production .env / datasource settings.
const args = ['-Duser.timezone=Asia/Shanghai', '-jar', jar,
  '--server.address=127.0.0.1', '--server.port=43131', '--spring.profiles.active=acceptance',
  `--spring.datasource.url=jdbc:h2:mem:acceptance_${randomUUID().replaceAll('-', '')};MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1`,
  '--spring.datasource.driver-class-name=org.h2.Driver', '--spring.datasource.username=sa', '--spring.datasource.password=',
  '--app.security.registration-enabled=true', '--app.security.email-verification-enabled=false',
  '--app.security.refresh-cookie-secure=false', '--app.security.refresh-cookie-name=plan_refresh',
  '--app.security.refresh-cookie-same-site=Strict', '--app.security.max-sessions=20',
  '--app.security.password-reset-enabled=false', '--app.security.bootstrap-admin-email=',
  '--app.jwt.secret=acceptance-only-not-production-at-least-32-bytes', '--app.jwt.issuer=plan-platform-acceptance',
  '--app.cors-allowed-origins=http://127.0.0.1:43130', '--spring.h2.console.enabled=false'];
const child = spawn(java, args, { cwd: resolve(root, 'backend'), stdio: 'inherit', windowsHide: true });
child.on('error', error => { console.error(`Cannot start isolated Java API: ${error.message}`); process.exit(1); });
child.on('exit', code => process.exit(code ?? 1));
for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => { child.kill(signal); });
