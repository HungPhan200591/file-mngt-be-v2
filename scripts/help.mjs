const categoryArg = process.argv[2]?.toLowerCase();

const categories = {
  docker: {
    title: 'DOCKER INFRASTRUCTURE COMMANDS',
    commands: [
      { cmd: 'npm run docker:rerun', desc: 'Recreate & khởi động lại Core infra (Postgres, Redis, Kafka, Nginx)' },
      { cmd: 'npm run docker:up', desc: 'Khởi động Core infra ở background' },
      { cmd: 'npm run docker:down', desc: 'Hạ toàn bộ container Core infra' },
      { cmd: 'npm run docker:restart', desc: 'Restart các container Core infra' },
      { cmd: 'npm run docker:rerun:all', desc: 'Recreate & khởi động lại TOÀN BỘ infra (Core + Search + Observability)' },
      { cmd: 'npm run docker:up:all', desc: 'Khởi động TOÀN BỘ infra (Core + Search + Observability)' },
      { cmd: 'npm run docker:down:all', desc: 'Hạ TOÀN BỘ container infra' },
      { cmd: 'npm run docker:ps', desc: 'Xem danh sách & trạng thái các container đang chạy' },
      { cmd: 'npm run docker:logs', desc: 'Theo dõi log realtime (tail -f) của container' },
    ]
  },
  test: {
    title: 'UNIT & INTEGRATION TESTING COMMANDS (MAVEN / JAVA 25)',
    commands: [
      { cmd: 'npm run test:all', desc: 'Chạy toàn bộ unit test của tất cả microservices' },
      { cmd: 'npm run test:catalog', desc: 'Chạy test cho Catalog Service' },
      { cmd: 'npm run test:scan', desc: 'Chạy test cho Scan Service' },
      { cmd: 'npm run test:scan:it', desc: 'Chạy Integration Test cho Scan Service' },
      { cmd: 'npm run test:query', desc: 'Chạy test cho Query Service' },
      { cmd: 'npm run test:gateway', desc: 'Chạy test cho Gateway Service' },
      { cmd: 'npm run test:media', desc: 'Chạy test cho Media Worker Service' },
    ]
  },
  e2e: {
    title: 'E2E HTTP TESTING COMMANDS',
    commands: [
      { cmd: 'npm run e2e:init', desc: 'Khởi tạo config và cài đặt dependency cho E2E harness' },
      { cmd: 'npm run e2e:all', desc: 'Chạy tất cả E2E test suites' },
      { cmd: 'npm run e2e:gateway', desc: 'Chạy E2E test suite cho Gateway' },
      { cmd: 'npm run e2e:catalog', desc: 'Chạy E2E test suite cho Catalog Service' },
      { cmd: 'npm run e2e:scan', desc: 'Chạy E2E test suite cho Scan Service' },
      { cmd: 'npm run e2e:query:cache', desc: 'Chạy E2E test suite cho Query Service cache' },
      { cmd: 'npm run e2e:media', desc: 'Chạy E2E test suite cho Media Worker Service' },
      { cmd: 'npm run e2e:observability', desc: 'Chạy E2E test suite kiểm tra Observability' },
    ]
  },
  data: {
    title: 'DATA MANAGEMENT COMMANDS',
    commands: [
      { cmd: 'npm run reset:data', desc: 'Reset lại dữ liệu dev/local database' },
      { cmd: 'npm run scan-sc01:reset-data', desc: 'Truncate toàn bộ dữ liệu nghiệp vụ, staging và projection trong scan_db cho SC-01' },
    ]
  },
  fixture: {
    title: 'FIXTURE & BENCHMARK TOOLS (SC-01 / JAVA 25)',
    commands: [
      { cmd: 'npm run fixture:sc01:gen', desc: 'Sinh 1 triệu file rỗng fixture cho SC-01 bằng Java 25' },
      { cmd: 'npm run fixture:sc01:clean', desc: 'Dọn dẹp / xóa sạch 1 triệu file rỗng fixture của SC-01' },
      { cmd: 'npm run fixture:sc01:benchmark-read', desc: 'Đo riêng filesystem metadata read của SC-01' },
      { cmd: 'npm run fixture:sc01:benchmark-copy', desc: 'Đo walkFileTree + một streaming COPY vào TEMP TABLE rồi rollback' },
    ]
  }
};

function printHelp(filterCategory) {
  console.log('\n=================== BACKEND V2 NPM COMMANDS ===================\n');

  let found = false;
  for (const [key, category] of Object.entries(categories)) {
    if (filterCategory && filterCategory !== key) {
      continue;
    }
    found = true;
    console.log(`\x1b[36m[ ${category.title} ]\x1b[0m`);
    category.commands.forEach(item => {
      const paddedCmd = item.cmd.padEnd(30, ' ');
      console.log(`  \x1b[32m${paddedCmd}\x1b[0m : ${item.desc}`);
    });
    console.log('');
  }

  if (!found) {
    console.log(`\x1b[31mKhông tìm thấy nhóm lệnh '${filterCategory}'.\x1b[0m Các nhóm khả dụng: ${Object.keys(categories).join(', ')}\n`);
  } else {
    console.log('---------------------------------------------------------------');
    console.log('Mẹo: Dùng \x1b[33mnpm run help:<nhóm>\x1b[0m để lọc (VD: npm run help:docker, npm run help:test, npm run help:e2e)\n');
  }
}

printHelp(categoryArg);
