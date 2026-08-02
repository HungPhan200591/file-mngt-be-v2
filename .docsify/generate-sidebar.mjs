import { promises as fs } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const sidebarPath = path.join(repositoryRoot, '_sidebar.md');
const excludedSegments = new Set(['.git', '.idea', 'target', 'node_modules', '_site']);
const rootOrder = new Map([
  ['manual', '10-manual'], ['docs', '20-docs'], ['apps', '30-apps'],
  ['platform', '40-platform'], ['infra', '50-infra'], ['tests', '60-tests'],
  ['gemini', '70-gemini'], ['.agents', '80-agents'], ['.docsify', '90-docsify']
]);

async function collectMarkdown(directory) {
  const entries = await fs.readdir(directory, { withFileTypes: true });
  const files = [];
  for (const entry of entries) {
    if (excludedSegments.has(entry.name)) continue;
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) files.push(...await collectMarkdown(fullPath));
    if (entry.isFile() && entry.name.endsWith('.md')) files.push(fullPath);
  }
  return files;
}

async function titleFor(fullPath, relativePath) {
  const content = await fs.readFile(fullPath, 'utf8');
  const heading = content.split(/\r?\n/, 40).find((line) => /^#\s+/.test(line));
  return heading ? heading.replace(/^#\s+/, '').trim() : path.basename(relativePath, '.md');
}

function folderSortName(name) {
  return rootOrder.get(name) ?? `50-${name.toLowerCase()}`;
}

function addFile(root, file) {
  let current = root;
  const segments = file.relativePath.split('/');
  for (const segment of segments.slice(0, -1)) {
    if (!current.folders.has(segment)) {
      current.folders.set(segment, { name: segment, sortName: folderSortName(segment), folders: new Map(), files: [] });
    }
    current = current.folders.get(segment);
  }
  current.files.push(file);
}

function renderTree(node, prefix = '') {
  const lines = [];
  for (const file of [...node.files].sort((a, b) => a.sortName.localeCompare(b.sortName))) {
    lines.push(`${prefix}* [${file.title}](/${file.relativePath})`);
  }
  for (const folder of [...node.folders.values()].sort((a, b) => a.sortName.localeCompare(b.sortName))) {
    lines.push(`${prefix}* **${folder.name.toLowerCase()}**`);
    lines.push(...renderTree(folder, `${prefix}  `));
  }
  return lines;
}

const files = (await Promise.all([
  collectMarkdown(path.join(repositoryRoot, 'manual')),
  collectMarkdown(path.join(repositoryRoot, 'docs'))
])).flat();
const root = { folders: new Map(), files: [] };
for (const fullPath of files.sort()) {
  const relativePath = path.relative(repositoryRoot, fullPath).split(path.sep).join('/');
  addFile(root, {
    relativePath,
    title: await titleFor(fullPath, relativePath),
    sortName: relativePath === 'AGENTS.md' ? '000-agents' : path.basename(relativePath).toLowerCase()
  });
}

const sidebar = [
  '* [Home](/)', '', ...renderTree(root), '', '---',
  '_Generated from repository Markdown. Local output and dependencies are excluded._', ''
].join('\n');

await fs.writeFile(sidebarPath, sidebar, 'utf8');
console.log(`Generated ${path.relative(repositoryRoot, sidebarPath)} from ${files.length} Markdown files.`);
