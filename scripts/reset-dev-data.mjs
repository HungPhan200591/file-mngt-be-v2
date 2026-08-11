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
  const composeFile = normalizePath(path.resolve('infra', 'compose', 'compose.yaml'));
  const containers = containerIds.length === 0 ? [] : JSON.parse(docker(['inspect', ...containerIds]));
  const matchingContainers = containers.filter((container) => {
    const labels = container.Config.Labels ?? {};
    const configFiles = (labels['com.docker.compose.project.config_files'] ?? '')
      .split(',')
      .map(normalizePath);
    return labels['com.docker.compose.service'] === service && configFiles.includes(composeFile);
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

async function assertApplicationsStopped() {
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

  if (running.length > 0) {
    throw new Error(`Stop V2 application services before reset: ${running.join(', ')}.`);
  }
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
  log('Yêu cầu: dừng các service ứng dụng trước khi chạy.', COLORS.yellow);
  console.log('==========================================================\n');

  const postgres = requireContainer('postgres');
  const redis = requireContainer('redis');
  const kafka = requireContainer('kafka');
  await assertApplicationsStopped();

  log('[1/5] Truncate PostgreSQL business data (giữ Catalog master data)...', COLORS.yellow);
  runPsql(
    postgres,
    'scan_user',
    'scan_db',
    'TRUNCATE TABLE scan_review_issue, scan_review_proposal, scan_review_projection_task, scan_review_projection_root, scan_inventory_diff_stage, scan_inventory_stage, scan_outbox_event, scan_decision, scan_issue, scan_proposal, scan_run, scan_file_inventory RESTART IDENTITY CASCADE;',
  );
  runPsql(
    postgres,
    'catalog_user',
    'catalog_db',
    'TRUNCATE TABLE catalog_dead_letter_event, catalog_outbox_event, catalog_processed_event, media_asset, media_subject RESTART IDENTITY CASCADE;',
  );
  runPsql(
    postgres,
    'query_user',
    'query_db',
    'TRUNCATE TABLE query_search_outbox, query_processed_event, query_media_asset, query_media_subject RESTART IDENTITY CASCADE;',
  );
  verifyDatabaseEmpty(postgres, 'scan_user', 'scan_db', [
    'scan_issue',
    'scan_inventory_diff_stage',
    'scan_inventory_stage',
    'scan_outbox_event',
    'scan_decision',
    'scan_proposal',
    'scan_run',
    'scan_file_inventory',
    'scan_review_projection_root',
    'scan_review_projection_task',
    'scan_review_proposal',
    'scan_review_issue',
  ]);
  verifyDatabaseEmpty(postgres, 'catalog_user', 'catalog_db', [
    'catalog_dead_letter_event',
    'catalog_outbox_event',
    'catalog_processed_event',
    'media_asset',
    'media_subject',
  ]);
  verifyDatabaseEmpty(postgres, 'query_user', 'query_db', [
    'query_search_outbox',
    'query_processed_event',
    'query_media_asset',
    'query_media_subject',
  ]);
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

  console.log('\n==========================================================');
  log('RESET HOÀN TẤT: business data đã sạch, Catalog master data được giữ lại.', COLORS.green);
  console.log('==========================================================');
}

main().catch((error) => {
  console.error(`\nRESET FAILED. Một phần dữ liệu có thể đã được xóa; đọc lỗi trước khi chạy lại.\n${error.stack}`);
  process.exitCode = 1;
});
