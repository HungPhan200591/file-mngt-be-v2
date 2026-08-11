import { execFileSync } from 'node:child_process';
import path from 'node:path';

const COLORS = {
  cyan: '\x1b[36m',
  gray: '\x1b[90m',
  green: '\x1b[32m',
  red: '\x1b[31m',
  yellow: '\x1b[33m',
};

function log(message, color = COLORS.cyan) {
  console.log(`${color}${message}\x1b[0m`);
}

function run(command, args) {
  try {
    return execFileSync(command, args, { encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] }).trim();
  } catch (error) {
    const detail = [error.stdout, error.stderr, error.message]
      .filter((value) => value && value.trim())
      .join('\n')
      .trim();
    throw new Error(`Command failed: ${command} ${args.join(' ')}\n${detail}`, { cause: error });
  }
}

function docker(args) {
  return run('docker', args);
}

function dockerExec(containerId, args) {
  return docker(['exec', '-i', containerId, ...args]);
}

function normalizePath(value) {
  return value.trim().toLowerCase().replaceAll('\\', '/');
}

function requireContainer(service) {
  const containerIds = docker(['ps', '-q'])
    .split(/\r?\n/)
    .map((value) => value.trim())
    .filter(Boolean);
  if (containerIds.length === 0) {
    throw new Error('Không tìm thấy container Docker nào đang chạy. Vui lòng bật PostgreSQL container bằng: npm run docker:up');
  }
  const containers = JSON.parse(docker(['inspect', ...containerIds]));
  const matchingContainers = containers.filter((container) => {
    const labels = container.Config.Labels ?? {};
    const configFiles = (labels['com.docker.compose.project.config_files'] ?? '')
      .split(',')
      .map(normalizePath);
    return (
      labels['com.docker.compose.service'] === service &&
      configFiles.some((file) => file.endsWith('infra/compose/compose.yaml'))
    );
  });

  if (matchingContainers.length !== 1) {
    throw new Error(`Kỳ vọng đúng 1 container V2 Compose service '${service}', tìm thấy ${matchingContainers.length}.`);
  }
  return matchingContainers[0].Id;
}

function runPsql(containerId, user, database, sql) {
  return dockerExec(containerId, [
    'psql',
    '-v',
    'ON_ERROR_STOP=1',
    '-U',
    user,
    '-d',
    database,
    '-c',
    sql,
  ]);
}

function tableCount(containerId, user, database, table) {
  const output = dockerExec(containerId, [
    'psql',
    '-At',
    '-v',
    'ON_ERROR_STOP=1',
    '-U',
    user,
    '-d',
    database,
    '-c',
    `SELECT count(*) FROM ${table};`,
  ]);
  return Number.parseInt(output, 10);
}

function listTables(containerId, user, database, predicate) {
  const output = dockerExec(containerId, [
    'psql',
    '-At',
    '-v',
    'ON_ERROR_STOP=1',
    '-U',
    user,
    '-d',
    database,
    '-c',
    `SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND (${predicate}) ORDER BY table_name;`,
  ]);
  return output.split(/\r?\n/).map((value) => value.trim()).filter(Boolean);
}

function truncateTables(containerId, user, database, predicate) {
  const tables = listTables(containerId, user, database, predicate);
  if (tables.length === 0) {
    throw new Error(`Không tìm thấy bảng phù hợp để reset trong ${database}.`);
  }
  runPsql(
    containerId,
    user,
    database,
    `TRUNCATE TABLE ${tables.map((table) => `"${table.replaceAll('"', '""')}"`).join(', ')} RESTART IDENTITY CASCADE;`,
  );
  return tables;
}

function verifyTablesEmpty(containerId, user, database, tables) {
  const remaining = tables
    .map((table) => [table, tableCount(containerId, user, database, table)])
    .filter(([, count]) => count !== 0);

  if (remaining.length > 0) {
    throw new Error(
      `Xác minh reset thất bại cho ${database}: ${remaining
        .map(([table, count]) => `${table}=${count}`)
        .join(', ')}`,
    );
  }
}

async function main() {
  console.log('==========================================================');
  log('RESET DỮ LIỆU SCAN SERVICE (SC-01 / BT-01)', COLORS.cyan);
  console.log('==========================================================\n');

  const postgres = requireContainer('postgres');

  log('[1/2] Truncate dữ liệu nghiệp vụ, staging và projection của Scan Service trong scan_db...', COLORS.yellow);
  const scanTables = truncateTables(postgres, 'scan_user', 'scan_db', "table_name LIKE 'scan_%'");

  log('[2/2] Xác minh các bảng đã trống...', COLORS.yellow);
  verifyTablesEmpty(postgres, 'scan_user', 'scan_db', scanTables);

  log('\n ✓ Reset thành công dữ liệu nghiệp vụ và staging của Scan Service!', COLORS.green);
  console.log('==========================================================');
}

main().catch((error) => {
  console.error(`\nRESET FAILED. ${error.message}`);
  process.exitCode = 1;
});
