/**
 * strings.xml 资源一致性扫描（红线②「中文全抽离」的配套自查工具）。
 *
 * 用法：node scan_strings.mjs   （在仓库根目录执行，零依赖，Node 18+）
 * 退出码：存在真实缺失时为 1，可直接挂到本地提交前自查。
 *
 * 检查项：
 *  1. MISSING —— 代码里 `R.string.xxx` 引用了但 strings.xml 未定义（编译期会失败，提前暴露）。
 *     · 自动跳过 `android.R.string.*` 等平台/三方资源；
 *     · 自动跳过运行期按前缀动态拼接的资源名（DYNAMIC_PREFIXES）。
 *  2. UNUSED —— strings.xml 定义了但全工程未引用（仅提示，可能是预留文案，不判失败）。
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

// 脚本位于 test/，相对路径需向上跳一级到仓库根
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const ROOT = path.resolve(__dirname, '..');

const RES = path.join(ROOT, 'app/src/main/res/values/strings.xml');
const SRC = path.join(ROOT, 'app/src/main/java');

/** 运行期按前缀 + 变量拼接资源名的场景，静态扫描无法解析具体 name，非缺失。 */
const DYNAMIC_PREFIXES = ['assistant_asr_', 'assistant_provider_'];

const xml = fs.readFileSync(RES, 'utf8');
const defined = new Set();
const reName = /<string\s+name="([^"]+)"/g;
let m;
while ((m = reName.exec(xml)) !== null) defined.add(m[1]);

function walk(dir, out) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (e.name.endsWith('.kt') || e.name.endsWith('.xml')) out.push(p);
  }
}
const files = [];
walk(SRC, files);
// also scan manifest & layouts for R.string usage
walk(path.join(ROOT, 'app/src/main/res'), files);

const used = new Map(); // name -> Set<file>
// 捕获限定前缀，用于剔除 android.R.string.* 这类不在本模块定义的资源
const reUse = /(?:([\w.]+)\.)?R\.string\.([A-Za-z0-9_]+)/g;
// XML（manifest / 布局 / 小组件 / 快捷方式）里的引用形态是 @string/name
const reXmlUse = /@string\/([A-Za-z0-9_]+)/g;
for (const f of files) {
  const t = fs.readFileSync(f, 'utf8');
  let u;
  while ((u = reUse.exec(t)) !== null) {
    const qualifier = u[1] || '';
    if (qualifier === 'android' || qualifier.startsWith('android.')) continue;
    const n = u[2];
    if (!used.has(n)) used.set(n, new Set());
    used.get(n).add(path.basename(f));
  }
  while ((u = reXmlUse.exec(t)) !== null) {
    const n = u[1];
    if (!used.has(n)) used.set(n, new Set());
    used.get(n).add(path.basename(f));
  }
}

let missing = 0;
let dynamic = 0;
for (const [n, set] of used) {
  if (defined.has(n)) continue;
  // DYNAMIC_PREFIXES 存的是「前缀」，必须用 startsWith 判定。
  // 原实现用 includes(n) 做数组精确匹配，等价于要求资源名恰好等于 'assistant_asr_'，
  // 该分支实际从未命中过——是一颗哑弹：只要出现一处运行期拼接的 R.string.assistant_asr_xxx
  // 未在 strings.xml 静态定义，就会被误报 MISSING 并让门禁 exit(1)。
  if (DYNAMIC_PREFIXES.some((p) => n.startsWith(p))) {
    dynamic++;
    continue;
  }
  missing++;
  console.log('MISSING:', n, '->', [...set].join(', '));
}

const unused = [...defined].filter((n) => !used.has(n));
for (const n of unused) console.log('UNUSED :', n);

console.log(
  `\nDefined: ${defined.size}  Used: ${used.size}  Missing: ${missing}` +
    `  Dynamic-skipped: ${dynamic}  Defined-but-unused: ${unused.length}`,
);
if (missing > 0) {
  console.error('\n✗ 存在未定义的字符串引用，请先补 strings.xml');
  process.exit(1);
}
console.log('✓ 字符串资源引用一致');
