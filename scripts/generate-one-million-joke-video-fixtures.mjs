import fs from 'node:fs';
import path from 'node:path';

const TARGET_DIR = 'D:/Study/Project/file_mngt_fixtures/one_million_joke_video';
const TOTAL_FILES = 1_000_000;
const SUB_DIRS_COUNT = 1_000;
const FILES_PER_DIR = TOTAL_FILES / SUB_DIRS_COUNT;

console.log(`====================================================`);
console.log(`🚀 BẮT ĐẦU TẠO 1 TRIỆU FILE FIXTURES (NODE.JS FAST IO)`);
console.log(`📍 Thư mục đích: ${TARGET_DIR}`);
console.log(`📁 1,000 thư mục con song song | Tổng: 1,000,000 files`);
console.log(`====================================================`);

const startTime = Date.now();

if (!fs.existsSync(TARGET_DIR)) {
  fs.mkdirSync(TARGET_DIR, { recursive: true });
}

let dirsCompleted = 0;

async function createSubDirFiles(dirIndex) {
  const dirName = `sub_${String(dirIndex).padStart(4, '0')}`;
  const dirPath = path.join(TARGET_DIR, dirName);
  if (!fs.existsSync(dirPath)) {
    fs.mkdirSync(dirPath, { recursive: true });
  }

  const startFileId = (dirIndex - 1) * FILES_PER_DIR;
  for (let f = 1; f <= FILES_PER_DIR; f++) {
    const fileId = startFileId + f;
    const fileIdStr = String(fileId).padStart(7, '0');
    const fileName = `Joke_AT_${fileIdStr} [JOKE-${fileIdStr}].mp4`;
    const filePath = path.join(dirPath, fileName);

    // openSync flag 'w' & closeSync nhanh hơn writeFileSync
    const fd = fs.openSync(filePath, 'w');
    fs.closeSync(fd);
  }

  dirsCompleted++;
  if (dirsCompleted % 100 === 0 || dirsCompleted === SUB_DIRS_COUNT) {
    const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
    console.log(`... Tiến độ: ${(dirsCompleted * FILES_PER_DIR).toLocaleString()} / ${TOTAL_FILES.toLocaleString()} files (${dirsCompleted}/${SUB_DIRS_COUNT} folders) [${elapsed}s]`);
  }
}

// Chạy song song 1,000 subdirectories
const tasks = [];
for (let d = 1; d <= SUB_DIRS_COUNT; d++) {
  tasks.push(createSubDirFiles(d));
}

await Promise.all(tasks);

const totalTime = ((Date.now() - startTime) / 1000).toFixed(2);
console.log(`====================================================`);
console.log(`⚡ HOÀN TẤT TẠO 1 TRIỆU FILE SIÊU TỐC TRONG ${totalTime}s!`);
console.log(`====================================================`);
