"""扫描 Kotlin 源码中的硬编码中文字符串字面量（红线检查辅助脚本）。

用法: python scan_cjk.py [--detail]
仅统计代码中的字符串字面量，自动跳过整行注释与 KDoc。
"""
import io
import os
import re
import sys

STR = re.compile(r'"(?:[^"\\]|\\.)*"')
CJK = re.compile(r'[\u4e00-\u9fff]')
ROOT = 'app/src/main/java'


def scan():
    hits = {}
    for root, _dirs, files in os.walk(ROOT):
        for f in files:
            if not f.endswith('.kt'):
                continue
            p = os.path.join(root, f).replace('\\', '/')
            for i, line in enumerate(io.open(p, encoding='utf-8'), 1):
                s = line.strip()
                if s.startswith('//') or s.startswith('*') or s.startswith('/*'):
                    continue
                for m in STR.finditer(line):
                    if CJK.search(m.group(0)):
                        hits.setdefault(p, []).append((i, s[:160]))
                        break
    return hits


def main():
    hits = scan()
    detail = '--detail' in sys.argv
    total = 0
    for p in sorted(hits, key=lambda k: -len(hits[k])):
        print('%3d  %s' % (len(hits[p]), p))
        if detail:
            for i, s in hits[p]:
                print('       %d: %s' % (i, s))
        total += len(hits[p])
    print('TOTAL %d in %d files' % (total, len(hits)))


if __name__ == '__main__':
    main()
