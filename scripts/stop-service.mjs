import { execSync } from 'node:child_process';

const targetService = process.argv[2];

if (!targetService) {
  console.log('\x1b[33mCú pháp: node scripts/stop-service.mjs <service-name>\x1b[0m');
  console.log('Ví dụ: node scripts/stop-service.mjs scan-service');
  console.log('Dịch vụ khả dụng: catalog-service, scan-service, query-service, media-worker, gateway-service, web-fe-v2\n');
  process.exit(1);
}

try {
  console.log(`\x1b[36mĐang dừng container '${targetService}' để giải phóng port cho Local IDE Debugging...\x1b[0m`);
  execSync(`docker compose -f infra/compose/compose.apps.yaml stop ${targetService}`, {
    stdio: 'inherit',
  });
  console.log(`\x1b[32m✔ Đã dừng ${targetService}. Bây giờ bạn có thể khởi chạy ${targetService} trong IDE để debug local!\x1b[0m\n`);
} catch (err) {
  console.error(`\x1b[31m Lỗi khi dừng service ${targetService}:\x1b[0m`, err.message);
  process.exit(1);
}
