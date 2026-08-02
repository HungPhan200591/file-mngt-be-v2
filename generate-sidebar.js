const fs = require('fs');
const path = require('path');

const ROOT_DIR = process.cwd();
const IGNORE_DIRS = new Set([
  'node_modules', 'target', '.git', '.docsify', '.mvn', '.idea', '.vscode', 'infra', 'coverage', 'test-output'
]);

function getTitleFromFile(filePath) {
  try {
    const content = fs.readFileSync(filePath, 'utf8');
    const firstHeader = content.split('\n').find(line => line.trim().startsWith('# '));
    if (firstHeader) {
      return firstHeader.trim().replace(/^#\s+/, '').replace(/[*_`]/g, '');
    }
  } catch (e) {}
  const basename = path.basename(filePath, '.md');
  if (basename.toLowerCase() === 'readme') {
    const parent = path.basename(path.dirname(filePath));
    return parent ? parent : basename;
  }
  return basename;
}

function scanDir(dirPath, relativeDir = '') {
  let entries;
  try {
    entries = fs.readdirSync(dirPath, { withFileTypes: true });
  } catch (e) {
    return [];
  }
  let items = [];

  for (const entry of entries) {
    if (entry.name.startsWith('.') && entry.name !== '.github') continue;
    if (IGNORE_DIRS.has(entry.name)) continue;

    const fullPath = path.join(dirPath, entry.name);
    const relPath = (relativeDir ? `${relativeDir}/${entry.name}` : entry.name).replace(/\\/g, '/');

    if (entry.isDirectory()) {
      const subItems = scanDir(fullPath, relPath);
      if (subItems.length > 0) {
        const title = entry.name.replace(/[-_]/g, ' ').toUpperCase();
        items.push({ type: 'dir', name: entry.name, title, path: relPath, children: subItems });
      }
    } else if (entry.isFile() && entry.name.endsWith('.md') && entry.name !== '_sidebar.md' && entry.name !== 'index.html') {
      const title = getTitleFromFile(fullPath);
      items.push({ type: 'file', name: entry.name, title, path: relPath });
    }
  }

  items.sort((a, b) => {
    if (a.name.toLowerCase() === 'readme.md') return -1;
    if (b.name.toLowerCase() === 'readme.md') return 1;
    if (a.type !== b.type) return a.type === 'dir' ? -1 : 1;
    return a.name.localeCompare(b.name);
  });

  return items;
}

function generateMarkdown(items, indentLevel = 0) {
  let lines = [];
  const indent = '  '.repeat(indentLevel);

  for (const item of items) {
    if (item.type === 'file') {
      if (indentLevel === 0 && item.name.toLowerCase() === 'readme.md') continue;
      lines.push(`${indent}* [${item.title}](${item.path})`);
    } else if (item.type === 'dir') {
      lines.push(`${indent}* **${item.title}**`);
      lines.push(generateMarkdown(item.children, indentLevel + 1));
    }
  }
  return lines.join('\n');
}

const structure = scanDir(ROOT_DIR);
const markdown = `* [🏠 Trang chủ](README.md)\n\n` + generateMarkdown(structure);

const outputPath = path.join(ROOT_DIR, '_sidebar.md');
fs.writeFileSync(outputPath, markdown);

console.log('Sidebar generated successfully at ' + outputPath);
