import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const text = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8');

test('uses production hero preloads only in the Vercel index', async () => {
  const angular = JSON.parse(await text('angular.json'));
  const genericIndex = await text('src/index.html');
  const vercelIndex = await text('src/index.vercel.html');
  const vercelBuild = angular.projects.frontend.architect.build.configurations.vercel;

  assert.deepEqual(vercelBuild.index, { input: 'src/index.vercel.html', output: 'index.html' });
  assert.doesNotMatch(genericIndex, /api\.pinatech\.com\.ar/);
  assert.match(vercelIndex, /rel="preconnect" href="https:\/\/api\.pinatech\.com\.ar"/);
  assert.match(vercelIndex, /current\/MOBILE\/720\.webp/);
  assert.match(vercelIndex, /current\/DESKTOP\/1920\.webp/);
});

test('publishes crawler files with an explicit Vercel cache policy', async () => {
  const robots = await text('public/robots.txt');
  const sitemap = await text('public/sitemap.xml');
  const vercel = JSON.parse(await text('vercel.json'));
  const crawlerHeaders = vercel.headers.find((rule) => rule.source === '/(robots.txt|sitemap.xml)');

  assert.match(robots, /^User-agent: \*/);
  assert.match(robots, /Sitemap: https:\/\/pinatech\.com\.ar\/sitemap\.xml/);
  assert.match(sitemap, /^<\?xml version="1\.0" encoding="UTF-8"\?>/);
  assert.match(sitemap, /<loc>https:\/\/pinatech\.com\.ar\/catalog<\/loc>/);
  assert.equal(crawlerHeaders?.headers[0]?.value, 'public, max-age=3600');
});
