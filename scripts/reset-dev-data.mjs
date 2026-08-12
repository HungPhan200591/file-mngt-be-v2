import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import { createConnection } from 'node:net';
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

function requireContainer(service) {
  const containerIds = docker(['ps', '-q'])
    .split(/\r?\n/)
    .map((value) => value.trim())
    .filter(Boolean);
  const containers = containerIds.length === 0 ? [] : JSON.parse(docker(['inspect', ...containerIds]));
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
    throw new Error(`Expected exactly one running V2 Compose service '${service}', found ${matchingContainers.length}.`);
  }
  return matchingContainers[0].Id;
}

function normalizePath(value) {
  return value.trim().toLowerCase().replaceAll('\\', '/');
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
    throw new Error(`No resettable tables found in ${database}.`);
  }
  runPsql(
    containerId,
    user,
    database,
    `TRUNCATE TABLE ${tables.map((table) => `"${table.replaceAll('"', '""')}"`).join(', ')} RESTART IDENTITY CASCADE;`,
  );
  return tables;
}

function verifyDatabaseEmpty(containerId, user, database, tables) {
  const remaining = tables
    .map((table) => [table, tableCount(containerId, user, database, table)])
    .filter(([, count]) => count !== 0);

  if (remaining.length > 0) {
    throw new Error(
      `Reset verification failed for ${database}: ${remaining
        .map(([table, count]) => `${table}=${count}`)
        .join(', ')}`,
    );
  }
}

function listKafkaItems(containerId, command) {
  const output = dockerExec(containerId, command);
  return output
    .split(/\r?\n/)
    .map((value) => value.trim())
    .filter(Boolean);
}

function sleep(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

function isPortOpen(port) {
  return new Promise((resolve) => {
    const socket = createConnection({ host: '127.0.0.1', port });
    socket.setTimeout(250);
    socket.once('connect', () => {
      socket.destroy();
      resolve(true);
    });
    socket.once('error', () => resolve(false));
    socket.once('timeout', () => {
      socket.destroy();
      resolve(false);
    });
  });
}

async function waitForTopicsDeletion(containerId) {
  for (let attempt = 1; attempt <= 20; attempt += 1) {
    const topics = listKafkaItems(containerId, [
      '/opt/kafka/bin/kafka-topics.sh',
      '--bootstrap-server',
      'localhost:9092',
      '--list',
    ]).filter((topic) => !topic.startsWith('__'));

    if (topics.length === 0) {
      return;
    }
    await sleep(500);
  }
  throw new Error('Kafka application topics were not deleted within 10 seconds. Stop all local services and retry.');
}

async function clearKafka(containerId) {
  const groups = listKafkaItems(containerId, [
    '/opt/kafka/bin/kafka-consumer-groups.sh',
    '--bootstrap-server',
    'localhost:9092',
    '--list',
  ]);
  for (const group of groups) {
    log(` -> Deleting Kafka consumer group: ${group}`, COLORS.gray);
    dockerExec(containerId, [
      '/opt/kafka/bin/kafka-consumer-groups.sh',
      '--bootstrap-server',
      'localhost:9092',
      '--delete',
      '--group',
      group,
    ]);
  }

  const topics = listKafkaItems(containerId, [
    '/opt/kafka/bin/kafka-topics.sh',
    '--bootstrap-server',
    'localhost:9092',
    '--list',
  ]).filter((topic) => !topic.startsWith('__'));
  for (const topic of topics) {
    log(` -> Deleting Kafka topic: ${topic}`, COLORS.gray);
    dockerExec(containerId, [
      '/opt/kafka/bin/kafka-topics.sh',
      '--bootstrap-server',
      'localhost:9092',
      '--delete',
      '--topic',
      topic,
    ]);
  }
  await waitForTopicsDeletion(containerId);
}

async function clearElasticsearch() {
  const listResponse = await fetch('http://localhost:18113/_cat/indices?format=json&h=index');
  if (!listResponse.ok) {
    throw new Error(`Elasticsearch index listing failed: HTTP ${listResponse.status} ${await listResponse.text()}`);
  }
  const indexes = (await listResponse.json())
    .map(({ index }) => index)
    .filter((index) => index.startsWith('media-subject-v1-'));

  for (const index of indexes) {
    log(` -> Deleting Elasticsearch index: ${index}`, COLORS.gray);
    const deleteResponse = await fetch(`http://localhost:18113/${encodeURIComponent(index)}`, { method: 'DELETE' });
    if (!deleteResponse.ok) {
      throw new Error(`Elasticsearch reset failed for ${index}: HTTP ${deleteResponse.status} ${await deleteResponse.text()}`);
    }
  }
}

function clearLogsDirectory(directory) {
  if (!fs.existsSync(directory)) {
    return;
  }
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      clearLogsDirectory(fullPath);
    } else if (entry.isFile() && entry.name.endsWith('.log')) {
      fs.truncateSync(fullPath, 0);
    }
  }
}

async function main() {
  console.log('==========================================================');
  log('BẮT ĐẦU RESET DỮ LIỆU LOCAL BACKEND V2', COLORS.cyan);
  console.log('==========================================================\n');

  const postgres = requireContainer('postgres');
  const redis = requireContainer('redis');
  const kafka = requireContainer('kafka');

  const services = [
    ['gateway-service', 18100],
    ['catalog-service', 18101],
    ['scan-service', 18102],
    ['query-service', 18103],
    ['media-worker', 18104],
  ];
  const running = (await Promise.all(
    services.map(async ([service, port]) => ((await isPortOpen(port)) ? `${service}:${port}` : null)),
  )).filter(Boolean);

  let wasAppsRunningInDocker = false;
  if (running.length > 0) {
    log(`Phát hiện ứng dụng đang chạy (${running.join(', ')}). Tự động dừng để reset...`, COLORS.yellow);
    try {
      execFileSync('docker', ['compose', '--env-file', '.env', '-f', 'infra/compose/compose.apps.yaml', 'stop'], { stdio: 'inherit' });
      wasAppsRunningInDocker = true;
      await sleep(1000);
    } catch (e) {
      log('Lưu ý: Tiếp tục reset dữ liệu...', COLORS.gray);
    }
  }

  log('[1/5] Truncate PostgreSQL business data (giữ Catalog master data)...', COLORS.yellow);
  const scanTables = truncateTables(postgres, 'scan_user', 'scan_db', "table_name LIKE 'scan_%'");
  const catalogTables = truncateTables(
    postgres,
    'catalog_user',
    'catalog_db',
    "table_name NOT IN ('flyway_schema_history', 'studio', 'studio_code', 'tag', 'actress', 'master_data_registry', 'master_data_import')",
  );
  const queryTables = truncateTables(postgres, 'query_user', 'query_db', "table_name LIKE 'query_%'");
  verifyDatabaseEmpty(postgres, 'scan_user', 'scan_db', scanTables);
  verifyDatabaseEmpty(postgres, 'catalog_user', 'catalog_db', catalogTables);
  verifyDatabaseEmpty(postgres, 'query_user', 'query_db', queryTables);
  log(' ✓ PostgreSQL business data đã được xóa và xác minh.', COLORS.green);

  log('\n[2/5] Reset Kafka application topics và consumer groups...', COLORS.yellow);
  await clearKafka(kafka);
  log(' ✓ Kafka application state đã được xóa.', COLORS.green);

  log('\n[3/5] Flush Redis cache...', COLORS.yellow);
  dockerExec(redis, ['redis-cli', 'FLUSHALL']);
  log(' ✓ Redis cache đã được xóa.', COLORS.green);

  log('\n[4/5] Xóa Elasticsearch media-subject indexes...', COLORS.yellow);
  await clearElasticsearch();
  log(' ✓ Elasticsearch media-subject indexes đã được xóa.', COLORS.green);

  log('\n[5/5] Xóa local log files...', COLORS.yellow);
  clearLogsDirectory('logs');
  clearLogsDirectory('apps');
  log(' ✓ Local log files đã được xóa.', COLORS.green);

  if (wasAppsRunningInDocker) {
    log('\n[Khởi chạy lại Ứng dụng]', COLORS.cyan);
    try {
      execFileSync('docker', ['compose', '--env-file', '.env', '-f', 'infra/compose/compose.apps.yaml', 'start'], { stdio: 'inherit' });
      log(' ✓ Đã tự động khởi chạy lại tất cả ứng dụng trong Docker!', COLORS.green);
    } catch (e) {
      log('Gợi ý: Bật lại ứng dụng bằng lệnh `npm run docker:apps:up`.', COLORS.yellow);
    }
  }

  console.log('\n==========================================================');
  log('RESET HOÀN TẤT: business data đã sạch, Catalog master data được giữ lại.', COLORS.green);
  console.log('==========================================================');
}

main().catch((error) => {
  console.error(`\nRESET FAILED. Một phần dữ liệu có thể đã được xóa; đọc lỗi trước khi chạy lại.\n${error.stack}`);
  process.exitCode = 1;
});
