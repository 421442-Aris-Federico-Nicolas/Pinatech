import assert from 'node:assert/strict';
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';
import {
  resolveDeploymentVersion,
  writeDeploymentVersion,
} from './write-deployment-version.mjs';

test('uses Vercel SHA before GitHub SHA and a generated fallback', () => {
  assert.equal(resolveDeploymentVersion({
    VERCEL_GIT_COMMIT_SHA: ' vercel-sha ',
    GITHUB_SHA: 'github-sha',
  }), 'vercel-sha');
  assert.equal(resolveDeploymentVersion({ GITHUB_SHA: ' github-sha ' }), 'github-sha');
  assert.equal(resolveDeploymentVersion({}, () => 'random-build-id'), 'random-build-id');
});

test('writes the deployment marker as JSON', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'pinatech-version-'));
  const outputPath = join(directory, 'browser', 'version.json');
  const indexPath = join(directory, 'browser', 'index.html');

  try {
    await mkdir(join(directory, 'browser'), { recursive: true });
    await writeFile(indexPath, '<html><head><title>Pinatech</title></head><body></body></html>', 'utf8');
    const version = await writeDeploymentVersion(
      outputPath,
      { GITHUB_SHA: 'build-sha' },
      () => 'unused-random-id',
      indexPath,
    );

    assert.equal(version, 'build-sha');
    assert.deepEqual(JSON.parse(await readFile(outputPath, 'utf8')), { version: 'build-sha' });
    assert.match(await readFile(indexPath, 'utf8'), /<meta name="pinatech-deployment-version" content="build-sha">/u);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
