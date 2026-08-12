import { execSync } from 'child_process';

const targetService = process.argv[2];

if (!targetService) {
  console.log('\x1b[31mVui lòng chỉ định tên service cần restart. Ví dụ: npm run docker:restart scan-service\x1b[0m');
  process.exit(1);
}

const appsServices = ['catalog-service', 'scan-service', 'query-service', 'media-worker', 'gateway-service', 'web-fe-v2'];

let composeFile = 'infra/compose/compose.yaml';
if (appsServices.includes(targetService)) {
  composeFile = 'infra/compose/compose.apps.yaml';
}

try {
  console.log(`\x1b[36mĐang restart container '${targetService}'...\x1b[0m`);
  execSync(`docker compose --env-file .env -f ${composeFile} restart ${targetService}`, {
    stdio: 'inherit',
  });
  console.log(`\x1b[32m✔ Đã restart ${targetService} thành công!\x1b[0m\n`);
} catch (error) {
  console.error(`\x1b[31m✖ Lỗi khi restart container ${targetService}\x1b[0m`);
  process.exit(1);
}
