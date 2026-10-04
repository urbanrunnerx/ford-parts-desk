import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import * as core from '../dist/core.js';
import * as jobs from '../dist/jobs.js';
import * as workspace from '../dist/workspace.js';

const read = name => fs.readFileSync(new URL('../dist/' + name, import.meta.url), 'utf8');
const html = read('index.html');
const css = read('style.css');
const app = read('app.js');
const catalog = JSON.parse(read('catalog.json'));

// Small DOM doubles exercise the shipped render/event code without a browser or
// third-party dependencies. Pixel layout remains an Android/browser QA concern.
async function mount({ saved = {}, data = catalog, failFetch = false, native = false, confirmRestore = true, rawStorage = {}, failMarker = false } = {}) {
  const elements = new Map();
  const handlers = new Map();
  let document;
  const registerIds = markup => {
    for (const match of markup.matchAll(/\bid="([^"]+)"/g)) {
      if (!elements.has('#' + match[1])) elements.set('#' + match[1], element(match[1]));
    }
  };
  function element(id = '') {
    const attributes = new Map();
    const classes = new Set();
    let markup = '';
    return {
      id, value: '', textContent: '', hidden: false, open: false, dataset: {},
      tagName: 'BUTTON',
      classList: {
        toggle(name, state) { if (state ?? !classes.has(name)) classes.add(name); else classes.delete(name); },
        contains: name => classes.has(name),
      },
      set innerHTML(value) { markup = value; registerIds(value); },
      get innerHTML() { return markup; },
      setAttribute: (name, value) => attributes.set(name, String(value)),
      getAttribute: name => attributes.get(name),
      hasAttribute: name => attributes.has(name),
      addEventListener(name, fn) { this['on' + name] = fn; },
      focus() { document.activeElement = this; },
      blur() { document.activeElement = { tagName: 'BODY' }; },
      showModal() { this.open = true; },
      close() { this.open = false; this.onclose?.(); },
      closest() { return this; },
    };
  }
  registerIds(html);
  elements.set('#empty h3', element());
  elements.set('#empty p', element());
  const views = [...html.matchAll(/data-view="([^"]+)"/g)].map(match => ({ ...element(), dataset: { view: match[1] } }));
  document = {
    documentElement: { dataset: {} }, activeElement: { tagName: 'BODY' },
    querySelector(selector) { return selector === 'dialog[open]' ? [...elements.values()].find(e => e.open) : elements.get(selector) || null; },
    querySelectorAll(selector) { return selector === '[data-view]' ? views : []; },
    addEventListener: (name, fn) => handlers.set(name, fn),
  };
  const node = id => elements.get('#' + id);
  node('query').tagName = 'INPUT';
  node('category').value = 'All systems';
  node('quality').value = 'all';
  node('sort').value = 'relevance';
  const storage = new Map([...Object.entries(saved).map(([k,v]) => ['partsdesk-v1-' + k, JSON.stringify(v)]), ...Object.entries(rawStorage)]);
  const localStorage = { get length() { return storage.size; }, key: index => [...storage.keys()][index] ?? null, getItem: key => storage.get(key) ?? null, setItem: (key,value) => { if (failMarker && key === 'partsdesk-v1-migration-state') throw Error('Fixture marker quota'); storage.set(key,value); }, removeItem: key => storage.delete(key) };
  const counter = { receiveEpc() {}, receiveVinResolution() {}, openJobs() {}, openJob() {}, openSnapOn() {}, openLookup() {}, addPart() {} };
  const timers = new Map(); let nextTimer = 0;
  const scope = {
    document, window: native ? { PartsNative: {} } : {}, localStorage,
    navigator: {}, location: { hostname: 'localhost', reload() {} },
    setTimeout: fn => { timers.set(++nextTimer, fn); return nextTimer; },
    clearTimeout: key => timers.delete(key),
    fetch: async () => { if (failFetch) throw Error('Offline fixture'); return { ok: true, json: async () => structuredClone(data) }; },
    createCounter: () => counter, openVinDecoder() {}, confirm: () => confirmRestore, ...core, ...jobs, ...workspace,
  };
  const AsyncFunction = Object.getPrototypeOf(async function() {}).constructor;
  await new AsyncFunction(...Object.keys(scope), app.replace(/^import .*;\n/gm, ''))(...Object.values(scope));
  function click(dataset = {}) { handlers.get('click')({ target: { ...element(), dataset } }); }
  function search(value) { node('query').value = value; node('searchForm').onsubmit({ preventDefault() {} }); }
  return { node, document, views, click, search, storage, handlers, timers };
}

test('HTML preserves all native/workspace controls and accessible input contracts', () => {
  const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map(m => m[1]);
  assert.equal(new Set(ids).size, ids.length, 'IDs must remain unique');
  for (const id of ['query','searchForm','clear','category','quality','sort','results','detail','info','detailBody','infoBody','vehicleBar','snaponButton','mobileSnapon','jobsButton','mobileJobs','tools','mobileTools','vinDecoder','coverage','coverageInline','gridView','listView','more','reset','theme','toast']) assert.ok(ids.includes(id), id);
  assert.match(html, /for="query"/);
  assert.match(html, /id="query"[^>]*enterkeyhint="search"/);
  assert.match(html, /id="query"[^>]*aria-describedby="searchHint"/);
  assert.match(html, /class="skip" href="#query"/);
  assert.match(html, /<dialog id="detail" aria-label=/);
  assert.match(html, /<dialog id="info" aria-label=/);
});

test('catalog counts and both dates come from the loaded snapshot', async () => {
  const future = { ...catalog, updatedAt: '2030-01-02' };
  const { node } = await mount({ data: future });
  assert.equal(node('baseCount').textContent, core.createIndex(future.parts).byBase.size.toLocaleString());
  assert.match(node('footerCount').textContent, /January 2, 2030/);
  assert.match(node('catalogSnapshot').textContent, /Jan 2, 2030/);
  assert.equal(node('catalogSnapshot').getAttribute('datetime'), '2030-01-02');
  assert.doesNotMatch(html + app, /SEP 2026|Updated September 17, 2026/);
});

test('common search, clear, saved filter, detail reopen, and list toggles still work', async () => {
  const ui = await mount();
  ui.search('purge valve');
  assert.match(ui.node('results').innerHTML, /EVAP purge valve/);
  assert.match(ui.node('results').innerHTML, /Copy base 9C915/);
  assert.match(ui.node('results').innerHTML, /Copy base 9D289/);
  assert.match(ui.node('results').innerHTML, /data-system="fuel"/);
  const id = /data-detail="([^"]+)"/.exec(ui.node('results').innerHTML)[1];
  ui.click({ save: id });
  assert.equal(ui.node('savedCount').textContent, 1);
  ui.click({ view: 'saved' });
  assert.ok(ui.views.filter(v => v.dataset.view === 'saved').every(v => v.getAttribute('aria-pressed') === 'true'));
  ui.click({ detail: id });
  assert.equal(ui.node('detail').open, true);
  assert.match(ui.node('detailBody').innerHTML, /data-lookup=/);
  ui.node('detail').close();
  ui.click({ detail: id });
  assert.equal(ui.node('detail').open, true);
  ui.node('detail').close();
  ui.node('listView').onclick();
  assert.equal(ui.node('results').classList.contains('list'), true);
  assert.equal(ui.node('listView').getAttribute('aria-pressed'), 'true');
  ui.node('gridView').onclick();
  assert.equal(ui.node('results').classList.contains('list'), false);
  ui.node('clear').onclick();
  assert.equal(ui.node('query').value, '');
  assert.equal(ui.document.activeElement, ui.node('query'));
});

test('theme labels track current state; keyboard shortcut respects active controls', async () => {
  const ui = await mount({ saved: { theme: 'dark' } });
  assert.equal(ui.document.documentElement.dataset.theme, 'dark');
  assert.equal(ui.node('theme').getAttribute('aria-label'), 'Switch to light theme');
  ui.node('theme').onclick();
  assert.equal(ui.document.documentElement.dataset.theme, 'light');
  assert.equal(ui.node('theme').getAttribute('aria-label'), 'Switch to dark theme');
  assert.equal(ui.storage.get('partsdesk-v1-theme'), '"light"');
  let prevented = false;
  ui.handlers.get('keydown')({ key: '/', preventDefault() { prevented = true; } });
  assert.equal(prevented, true);
  assert.equal(ui.document.activeElement, ui.node('query'));
  prevented = false;
  ui.handlers.get('keydown')({ key: '/', preventDefault() { prevented = true; } });
  assert.equal(prevented, false);
});

test('no-result, unavailable catalog and native offline states remain explicit', async () => {
  const ui = await mount();
  ui.search('glitter banana satellite');
  assert.equal(ui.node('empty').hidden, false);
  assert.equal(ui.node('more').hidden, true);
  const offline = await mount({ failFetch: true });
  assert.match(offline.node('resultSubtitle').textContent, /could not load/);
  assert.ok(offline.node('retry').onclick);
  const native = await mount({ native: true });
  assert.equal(native.node('offlineLabel').textContent, 'Offline ready');
  assert.equal(native.node('metricOffline').textContent, 'Stored on this device');
});

test('responsive controls, safe areas, reduced motion, and offline assets are retained', () => {
  for (const selector of ['.base-chip', '.save', '.layout-switch button', '.search-submit', '.filters select']) {
    const block = css.slice(css.indexOf(selector + ' {')).split('}')[0];
    assert.match(block, /min-height: (44|48)px/, selector);
  }
  assert.match(css, /\.search-panel \{ position: sticky;/);
  assert.match(css, /@media \(max-width: 560px\)/);
  assert.match(css, /@media \(max-height: 520px\)/);
  assert.match(css, /env\(safe-area-inset-bottom\)/);
  assert.match(css, /prefers-reduced-motion: reduce/);
  assert.match(css, /\[data-theme=dark\]/);
  assert.doesNotMatch(css, /url\(['"]?https?:/i);
  for (const match of html.matchAll(/<(?:script|link|img)\b[^>]*(?:src|href)="([^"]+)"/g)) assert.doesNotMatch(match[1], /^https?:/, 'UI assets must remain local');
  assert.doesNotMatch(read('assets/fonts.css'), /https?:/);
});

function contrast(a, b) {
  const lum = hex => hex.match(/[\da-f]{2}/gi).map(v => parseInt(v,16)/255).map(v => v <= .04045 ? v/12.92 : ((v+.055)/1.055)**2.4).reduce((sum,v,i) => sum + v*[.2126,.7152,.0722][i],0);
  const values = [lum(a),lum(b)].sort((a,b) => b-a);
  return (values[0]+.05)/(values[1]+.05);
}

test('core reading text and category labels meet WCAG AA normal-text contrast', () => {
  for (const [foreground,background] of [
    ['192e44','ffffff'],['5d6d7c','ffffff'],['5d6d7c','f4f3ef'],['1b5799','ffffff'],
    ['eef3f7','1b2b3b'],['afbfcd','1b2b3b'],['96c5ff','1b2b3b'],
    ['9b5124','ffffff'],['297263','ffffff'],['ab4345','ffffff'],['24718c','ffffff'],['7b6119','ffffff'],['6c598d','ffffff'],
  ]) assert.ok(contrast(foreground,background) >= 4.5, `${foreground} on ${background}`);
});


test('workspace restore updates visible theme and saved state; cancellation leaves them intact', async () => {
  const job = jobs.freshJob();
  const part = catalog.parts[0];
  const backup = {
    schema: 2, favorites: [part.id], recent: ['purge valve'], notes: { [part.id]: 'Fixture note' },
    imports: [], worksheet: job, jobs: { activeId: job.id, items: [job] }, vinCache: [], theme: 'dark', layout: true,
  };
  const change = () => ({ target: { value: 'fixture.json', files: [{ size: 1000, text: async () => JSON.stringify(backup) }] } });
  const restored = await mount();
  restored.node('tools').onclick();
  await restored.node('restoreFile').onchange(change());
  assert.match(restored.node('restoreStatus').textContent, /Workspace restored/);
  assert.equal(restored.document.documentElement.dataset.theme, 'dark');
  assert.equal(restored.node('theme').getAttribute('aria-label'), 'Switch to light theme');
  assert.equal(restored.node('savedCount').textContent, 1);
  assert.equal(restored.node('listView').getAttribute('aria-pressed'), 'true');
  const canceled = await mount({ confirmRestore: false });
  canceled.node('tools').onclick();
  await canceled.node('restoreFile').onchange(change());
  assert.equal(canceled.document.documentElement.dataset.theme, 'light');
  assert.equal(canceled.node('savedCount').textContent, 0);
  assert.equal(canceled.node('listView').getAttribute('aria-pressed'), 'false');
});

test('neutral identity, offline cache scope and visible dialog dismissal are consistent', () => {
  const manifest = JSON.parse(read('manifest.webmanifest'));
  assert.equal(manifest.name, 'Ford Parts Desk');
  assert.match(html, /<title>Ford Parts Desk<\/title>/);
  assert.match(html, /src="assets\/wordmark\.svg" alt="Ford Parts Desk"/);
  assert.match(read('install.html'), /Download a signed Android release/);
  assert.match(read('install.html'), /https:\/\/github\.com\/urbanrunnerx\/ford-parts-desk\/releases/);
  assert.match(read('install.html'), /Unsigned and debug workflow artifacts are for testing/);
  assert.doesNotMatch(read('install.html'), /releases\/latest\/download/);
  assert.match(read('sw.js'), /caches\.open\(CACHE\)\.then\(cache=>cache\.match\(event\.request\)\)/);
  assert.match(read('sw.js'), /assets\/wordmark\.svg/);
  assert.match(css, /\.close \{ position: sticky; top: 0;/);
  const closeRule = css.slice(css.indexOf('.close {')).split('}')[0];
  assert.match(closeRule, /display: flex;/, 'sticky close needs block-level flex for auto left margin');
  assert.match(closeRule, /width: 44px; height: 44px;/);
  assert.match(closeRule, /margin: -22px -22px -22px auto;/);
  assert.match(app, /const prefix='partsdesk-v1-'/);
  assert.match(app, /migrateLegacyWorkspace\(localStorage,prefix\)/);
});

test('startup cannot seed a destination when the recovery marker cannot be saved', async () => {
  const job = jobs.freshJob();
  const rawStorage = {
    'legacy-parts-v1-jobs': JSON.stringify({ activeId: job.id, items: [job] }),
    'legacy-parts-v1-worksheet': JSON.stringify(job),
    'legacy-parts-v1-favorites': JSON.stringify([catalog.parts[0].id]),
  };
  const ui = await mount({ rawStorage, failMarker: true });
  assert.equal(ui.node('workspaceNotice').hidden, false);
  assert.deepEqual(Object.fromEntries(ui.storage), rawStorage);
  ui.node('theme').onclick();
  assert.deepEqual(Object.fromEntries(ui.storage), rawStorage);
  const next = await mount({ rawStorage: Object.fromEntries(ui.storage) });
  assert.equal(next.node('savedCount').textContent, 1);
  assert.equal(next.storage.has('partsdesk-v1-migration-state'), false);
});

test('blocked recovery notice survives startup defaults and reload until a manual restore succeeds', async () => {
  const job = jobs.freshJob();
  const rawStorage = {
    'legacy-parts-v1-jobs': JSON.stringify({ activeId: job.id, items: [job] }),
    'another-parts-v1-jobs': JSON.stringify({ activeId: job.id, items: [job] }),
  };
  const first = await mount({ rawStorage });
  assert.equal(first.node('workspaceNotice').hidden, false);
  const second = await mount({ rawStorage: Object.fromEntries(first.storage) });
  assert.equal(second.node('workspaceNotice').hidden, false);
  assert.match(second.node('workspaceNotice').textContent, /More than one/);
  second.node('tools').onclick();
  const backup = { schema: 2, favorites: [], recent: [], notes: {}, imports: [], worksheet: job, jobs: { activeId: job.id, items: [job] }, vinCache: [], theme: 'dark', layout: false };
  await second.node('restoreFile').onchange({ target: { value: 'backup.json', files: [{ size: 1000, text: async () => JSON.stringify(backup) }] } });
  assert.match(second.node('restoreStatus').textContent, /Workspace restored/);
  assert.equal(second.node('workspaceNotice').hidden, true);
  assert.equal(second.storage.has('partsdesk-v1-migration-state'), false);
});
