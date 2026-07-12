import { readdir, rename } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { join, parse } from 'node:path';

const outputDir = new URL('../dist/build/mp-weixin/', import.meta.url);

async function renameStyles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const directoryPath = fileURLToPath(directory);

  await Promise.all(entries.map(async (entry) => {
    const source = join(directoryPath, entry.name);

    if (entry.isDirectory()) {
      await renameStyles(new URL(`${entry.name}/`, directory));
      return;
    }

    if (entry.isFile() && entry.name.endsWith('.css')) {
      const { name } = parse(entry.name);
      await rename(source, join(directoryPath, `${name}.wxss`));
    }
  }));
}

await renameStyles(outputDir);
console.log('微信小程序样式文件已转换为 .wxss');
