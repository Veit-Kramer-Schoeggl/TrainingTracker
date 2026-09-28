// Runs the prototype's own JavaScript (with a stubbed DOM and a fixed "today") on a synthetic
// dataset and prints what it renders — the expected values for PrototypeParityTest.
// Regenerate after a new design version of the prototype:
//   node tools/prototype-golden.js ~/Downloads/klimmzug-tracker-9.html > app/src/test/resources/prototype-golden.json
const fs = require('fs');
const html = fs.readFileSync(process.argv[2], 'utf8');
const script = html.split('<script>')[1].split('</script>')[0];

const TODAY = '2026-09-28';
const RealDate = Date;
class FakeDate extends RealDate {
  constructor(...a) { if (a.length === 0) super(TODAY + 'T10:00:00'); else super(...a); }
  static now() { return new RealDate(TODAY + 'T10:00:00').getTime(); }
}
globalThis.Date = FakeDate;

// ── deterministic synthetic data ──
let seed = 42;
const rnd = () => (seed = (seed * 1103515245 + 12345) % 2147483648) / 2147483648;
const pad = n => String(n).padStart(2, '0');
const days = {};
const notes = ['Leicht', 'Mit Gewicht 5kg', 'Müde', 'Neuer Rekord!', 'Breiter Griff'];
let d = new RealDate(2025, 11, 20); // 2025-12-20: data across the year boundary (ISO week 1/2026)
const end = new RealDate(2026, 8, 28);
while (d <= end) {
  const key = d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
  const inLongPause = key >= '2026-03-02' && key <= '2026-03-15';
  if (!inLongPause && rnd() < 0.55) {
    const n = 1 + Math.floor(rnd() * 5);
    const sets = [];
    for (let i = 0; i < n; i++) {
      const reps = 2 + Math.floor(rnd() * 14);
      const note = rnd() < 0.12 ? notes[Math.floor(rnd() * notes.length)] : '';
      sets.push({ reps, note, ts: key + 'T' + pad(8 + i) + ':' + pad(Math.floor(rnd() * 60)) + ':00' });
    }
    days[key] = { sets };
  }
  d.setDate(d.getDate() + 1);
}
// A 9-day streak ending today, and ties for the best day
for (let i = 0; i < 9; i++) {
  const x = new RealDate(2026, 8, 20 + i);
  const key = x.getFullYear() + '-' + pad(x.getMonth() + 1) + '-' + pad(x.getDate());
  if (!days[key]) days[key] = { sets: [{ reps: 6, note: '', ts: key + 'T18:00:00' }] };
}
days['2026-01-10'] = { sets: [{ reps: 30, note: '', ts: '2026-01-10T09:00:00' }, { reps: 30, note: 'Bestwert', ts: '2026-01-10T10:00:00' }] };
days['2026-05-05'] = { sets: [{ reps: 60, note: '', ts: '2026-05-05T09:00:00' }] };

// ── stubbed browser ──
const elements = {};
const el = id => elements[id] ??= {
  id, textContent: '', innerHTML: '', value: '', disabled: false, dataset: {}, style: {}, offsetWidth: 400,
  classList: { toggle() {}, add() {}, remove() {}, contains() { return false; } },
  addEventListener() {}, querySelector() { return null; },
  getContext() { return new Proxy({}, { get: () => () => {} }); },
};
const store = { 'pullup-days-v4': JSON.stringify(days) };
globalThis.document = { getElementById: el, querySelectorAll: () => [], documentElement: { dataset: {} } };
globalThis.window = { addEventListener() {}, scrollTo() {}, location: { href: '' }, devicePixelRatio: 1 };
globalThis.localStorage = { getItem: k => store[k] ?? null, setItem: (k, v) => { store[k] = String(v); } };
globalThis.setTimeout = () => 0;
URL.createObjectURL = () => '';

(0, eval)(script);
const text = id => el(id).textContent;
const stats = {};
for (const id of ['stat-total', 'stat-max', 'stat-week-avg', 'stat-avg', 'stat-count', 'stat-best-week',
  'stat-best-week-sub', 'stat-streak', 'stat-streak-sub', 'stat-streak-reps', 'stat-inactive', 'stat-avg10',
  'stat-avg4w', 'stat-avg-set']) stats[id] = String(text(id));

const all = (re, s) => [...s.matchAll(re)].map(m => m.slice(1));
const months = all(/month-name">([^<]*)<[\s\S]*?width:(\d+)%[\s\S]*?month-val">(\d+)</g, el('month-list').innerHTML);
const top3 = all(/top3-reps">(\d+)<[\s\S]*?top3-date">([^<]*)</g, el('top3-list').innerHTML);
const dayItems = el('days-list').innerHTML.split('<div class="day-item').slice(1).map(chunk => ({
  isNew: /^[^"]*is-new/.test(chunk),
  isToday: /^[^"]*is-today/.test(chunk),
  isMax: /day-reps is-max/.test(chunk),
  total: Number(/day-reps[^"]*">(\d+)</.exec(chunk)[1]),
  label: /day-date">([^<]*)/.exec(chunk)[1],
  sets: /day-sets">([^<]*)</.exec(chunk)[1],
  notes: (/day-note">([^<]*)</.exec(chunk) || [null, ''])[1],
}));
(0, eval)("switchTab('weeks')");
const weeks = all(/week-label"(?: style="[^"]*")?>([^<]*)<[\s\S]*?week-total"(?: style="[^"]*")?>(\d+)</g, el('week-view').innerHTML);

const sets = [];
for (const key of Object.keys(days).sort()) for (const s of days[key].sets) sets.push([key, s.reps, s.note, s.ts]);
console.log(JSON.stringify({ today: TODAY, sets, expected: { stats, months, top3, days: dayItems, weeks } }, null, 1));
