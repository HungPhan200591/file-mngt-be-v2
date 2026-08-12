import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';

function getJava25Home() {
  if (process.env.JAVA_HOME && process.env.JAVA_HOME.includes('25')) {
    return process.env.JAVA_HOME;
  }
  const jdksDir = 'C:\\Users\\Admin\\.jdks';
  if (fs.existsSync(jdksDir)) {
    const entries = fs.readdirSync(jdksDir);
    const corretto25 = entries.find((e) => e.startsWith('corretto-25') && !e.startsWith('.'));
    if (corretto25) {
      return path.join(jdksDir, corretto25);
    }
  }
  return process.env.JAVA_HOME;
}

const javaHome = getJava25Home();
const env = { ...process.env };
if (javaHome) {
  env.JAVA_HOME = javaHome;
  env.PATH = path.join(javaHome, 'bin') + ';' + (process.env.PATH || '');
}

console.log(`\x1b[36mĐang biên dịch lại tất cả Backend JARs bằng JDK 25 (${javaHome})...\x1b[0m`);
try {
  execSync('mvnw.cmd clean package -DskipTests -Dspotless.check.skip=true', {
    stdio: 'inherit',
    env,
  });
  console.log(`\x1b[32m✔ Đã biên dịch xong tất cả Backend JARs!\x1b[0m\n`);
} catch (error) {
  console.error(`\x1b[31m✖ Lỗi biên dịch Maven JARs!\x1b[0m`);
  process.exit(1);
}
