import fs from 'node:fs';
import path from 'node:path';

const TARGET_DIR = 'D:/Study/Project/file_mngt_fixtures/one_million_joke_video';
const TOTAL_FILES = 1_000_000;
const SUB_DIRS_COUNT = 1_000;
const FILES_PER_DIR = TOTAL_FILES / SUB_DIRS_COUNT;

console.log(`====================================================`);
console.log(`🚀 BẮT ĐẦU TẠO 1 TRIỆU FILE DUMMY JOKE VIDEO FIXTURES`);
console.log(`📍 Thư mục đích: ${TARGET_DIR}`);
console.log(`📁 Số thư mục con: ${SUB_DIRS_COUNT} (mỗi thư mục ${FILES_PER_DIR.toLocaleString()} files)`);
console.log(`====================================================`);

const startTime = Date.now();

if (!fs.existsSync(TARGET_DIR)) {
  fs.mkdirSync(TARGET_DIR, { recursive: true });
}

let totalCreated = 0;

for (let d = 1; d <= SUB_DIRS_COUNT; d++) {
  const dirName = `sub_${String(d).padStart(4, '0')}`;
  const dirPath = path.join(TARGET_DIR, dirName);
  if (!fs.existsSync(dirPath)) {
    fs.mkdirSync(dirPath, { recursive: true });
  }

  for (let f = 1; f <= FILES_PER_DIR; f++) {
    totalCreated++;
    const fileIdStr = String(totalCreated).padStart(7, '0');
    // Format tên file chuẩn JOKE profile: Joke_Video_Clip_0000001 [JOKE-0000001].mp4
    const fileName = `Joke_Video_Clip_${fileIdStr} [JOKE-${fileIdStr}].mp4`;
    const filePath = path.join(dirPath, fileName);

    fs.writeFileSync(filePath, '');
  }

  if (d % 100 === 0 || d === SUB_DIRS_COUNT) {
    const elapsed = ((Date.now() - startTime) / 1000).toFixed(1);
    console.log(`... Đã tạo ${totalCreated.toLocaleString()} / ${TOTAL_FILES.toLocaleString()} files (${d}/${SUB_DIRS_COUNT} folders) [${elapsed}s]`);
  }
}

const totalTime = ((Date.now() - startTime) / 1000).toFixed(2);
console.log(`====================================================`);
console.log(`✅ HOÀN TẤT TẠO 1 TRIỆU FILE JOKE VIDEO FIXTURES TRONG ${totalTime}s!`);
console.log(`====================================================`);
