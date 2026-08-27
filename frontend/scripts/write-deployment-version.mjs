import { randomUUID } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const defaultOutputPath = resolve(
  dirname(fileURLToPath(import.meta.url)),
  '../dist/frontend/browser/version.json',
);

export function resolveDeploymentVersion(environment = process.env, createRandomId = randomUUID) {
  return environment.VERCEL_GIT_COMMIT_SHA?.trim()
    || environment.GITHUB_SHA?.trim()
    || createRandomId();
}

export async function writeDeploymentVersion(
  outputPath = defaultOutputPath,
  environment = process.env,
  createRandomId = randomUUID,
  indexPath = join(dirname(outputPath), 'index.html'),
) {
  const version = resolveDeploymentVersion(environment, createRandomId);
  await mkdir(dirname(outputPath), { recursive: true });
  await writeFile(outputPath, `${JSON.stringify({ version }, null, 2)}\n`, 'utf8');
  const index = await readFile(indexPath, 'utf8');
  const withoutPreviousMarker = index.replace(/\s*<meta name="pinatech-deployment-version" content="[^"]*">/u, '');
  if (!withoutPreviousMarker.includes('</head>')) throw new Error(`Could not inject deployment version into ${indexPath}`);
  const escapedVersion = version.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
  await writeFile(indexPath, withoutPreviousMarker.replace(
    '</head>',
    `  <meta name="pinatech-deployment-version" content="${escapedVersion}">\n</head>`,
  ), 'utf8');
  return version;
}

const invokedPath = process.argv[1] ? pathToFileURL(resolve(process.argv[1])).href : '';
if (import.meta.url === invokedPath) {
  await writeDeploymentVersion();
}
