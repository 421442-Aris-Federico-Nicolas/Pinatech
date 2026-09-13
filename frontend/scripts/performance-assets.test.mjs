import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';

const text = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8');

test('creates production hero preloads only for the Vercel home route', async () => {
  const angular = JSON.parse(await text('angular.json'));
  const genericIndex = await text('src/index.html');
  const vercelIndex = await text('src/index.vercel.html');
  const vercelBuild = angular.projects.frontend.architect.build.configurations.vercel;

  assert.deepEqual(vercelBuild.index, { input: 'src/index.vercel.html', output: 'index.html' });
  assert.doesNotMatch(genericIndex, /api\.pinatech\.com\.ar/);
  assert.match(vercelIndex, /rel="preconnect" href="https:\/\/api\.pinatech\.com\.ar"/);
  assert.doesNotMatch(vercelIndex, /<link rel="preload"/);

  const script = vercelIndex.match(/<script>([\s\S]*?)<\/script>/)?.[1];
  assert.ok(script);
  const execute = (pathname) => {
    const appended = [];
    const document = {
      createElement: (tagName) => ({ tagName }),
      head: { append: (element) => appended.push(element) },
    };
    vm.runInNewContext(script, { document, window: { location: { pathname } } });
    return appended.map(({ rel, as, type, fetchPriority, media, href }) => (
      { rel, as, type, fetchPriority, media, href }
    ));
  };

  assert.deepEqual(execute('/catalog'), []);
  assert.deepEqual(execute('/'), [
    {
      rel: 'preload', as: 'image', type: 'image/webp', fetchPriority: 'high',
      media: '(max-width: 620px)',
      href: 'https://api.pinatech.com.ar/api/home/hero/current/MOBILE/720.webp',
    },
    {
      rel: 'preload', as: 'image', type: 'image/webp', fetchPriority: 'high',
      media: '(min-width: 621px)',
      href: 'https://api.pinatech.com.ar/api/home/hero/current/DESKTOP/1920.webp',
    },
  ]);
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
