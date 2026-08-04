# -*- coding: utf-8 -*-
"""
Room 迁移 ↔ schema JSON 一致性静态校验（红线③「Room 显式 Migration」的配套自查工具）。

用法：python check_migrations.py   （在仓库根目录执行，仅用标准库）
退出码：发现不一致时为 1，可挂到本地提交前自查。

背景
----
Room 在升级完成后会把 **实际数据库结构** 与 **实体导出的 schema JSON** 逐列比对，
任何列名 / 类型 / 默认值对不上都会抛
`IllegalStateException: Migration didn't properly handle: <table>(...)` 直接崩溃。

这类缺陷有个恶劣特性：**只有从旧版本升级上来的用户会崩，全新安装完全正常**，
本地开发（每次都是新装）与单元测试都发现不了，只能靠仪器化 MigrationTest 或本脚本拦截。

历史事故（本脚本因此而生）
--------------------------
`MIGRATION_3_4` 写成 `ADD COLUMN geo_lat / geo_lng / geo_radius`（snake_case），
而实体 `TaskEntity` 的字段是 `geoLat / geoLng / geoRadius`，导出的 5.json~10.json 也是 camelCase。
=> 任何 DB v3 的老用户升级后必崩；`MIGRATION_4_5` 的 `repeatIntervalHours` 还漏了 `DEFAULT 0`。

校验内容
--------
逐个解析 `AppDatabase.kt` 中的 `MIGRATION_a_b` 代码块，抽取其中的 `ALTER TABLE ... ADD COLUMN`，
与目标版本 `b.json` 比对三项：列名存在性、类型亲和性、默认值。
早期未导出 schema 的版本，退化到最近的更高版本 JSON 校验（中间版本不会再动这些列）。
"""
import json
import os
import re
import sys

SRC = 'app/src/main/java/com/tickclear/app/data/local/AppDatabase.kt'
SCH = 'app/schemas/com.tickclear.app.data.local.AppDatabase'

AFFINITY = {'INTEGER': 'INTEGER', 'REAL': 'REAL', 'TEXT': 'TEXT', 'BLOB': 'BLOB'}


def migration_blocks(src):
    """切出每个 MIGRATION_a_b 的 migrate 方法体（按花括号配平）。"""
    blocks = {}
    pattern = r'MIGRATION_(\d+)_(\d+)\s*=\s*object\s*:\s*Migration\(\s*\d+\s*,\s*\d+\s*\)\s*\{'
    for m in re.finditer(pattern, src):
        a, b = int(m.group(1)), int(m.group(2))
        i = src.index('{', m.end() - 1)
        depth, j = 0, i
        while j < len(src):
            if src[j] == '{':
                depth += 1
            elif src[j] == '}':
                depth -= 1
                if depth == 0:
                    break
            j += 1
        blocks[(a, b)] = src[i:j]
    return blocks


def load_schema(version, notes):
    path = os.path.join(SCH, '%d.json' % version)
    if not os.path.exists(path):
        available = sorted(int(x[:-5]) for x in os.listdir(SCH) if x.endswith('.json'))
        higher = [v for v in available if v > version]
        if not higher:
            return None
        notes.append('v%d.json 缺失 → 退化用 %d.json 校验' % (version, higher[0]))
        path = os.path.join(SCH, '%d.json' % higher[0])
    with open(path, encoding='utf-8') as f:
        data = json.load(f)
    return {
        e['tableName']: {fld['columnName']: fld for fld in e['fields']}
        for e in data['database']['entities']
    }


def norm_default(value):
    return None if value is None else str(value).strip().strip("'")


def main():
    if not os.path.exists(SRC):
        print('✗ 找不到 %s，请在仓库根目录执行' % SRC)
        return 2

    with open(SRC, encoding='utf-8') as f:
        src = f.read()

    blocks = migration_blocks(src)
    if not blocks:
        print('✗ 未解析到任何 Migration，脚本正则可能已与源码结构脱节，请检查')
        return 2

    notes, problems, checked_columns = [], [], 0
    for (a, b), body in sorted(blocks.items()):
        target = load_schema(b, notes)
        if target is None:
            notes.append('v%d->v%d 无可用 schema JSON，跳过' % (a, b))
            continue
        stmt = r'ALTER\s+TABLE\s+`?(\w+)`?\s+ADD\s+COLUMN\s+`?(\w+)`?\s+(\w+)([^"]*)'
        for s in re.finditer(stmt, body, re.I):
            table, column, coltype, rest = s.group(1), s.group(2), s.group(3).upper(), s.group(4)
            checked_columns += 1
            if table not in target:
                problems.append('v%d->v%d: 表 %s 不在目标 schema 中' % (a, b, table))
                continue
            if column not in target[table]:
                problems.append(
                    'v%d->v%d: 迁移建了 %s.%s，但 schema 无此列 ⇒ 老用户升级必崩。'
                    ' schema 实际列名参考：%s'
                    % (a, b, table, column, sorted(target[table].keys()))
                )
                continue
            field = target[table][column]
            if AFFINITY.get(coltype, coltype) != field['affinity']:
                problems.append(
                    'v%d->v%d: %s.%s 类型不一致 迁移=%s schema=%s'
                    % (a, b, table, column, coltype, field['affinity'])
                )
            found = re.search(r"DEFAULT\s+('[^']*'|\S+)", rest, re.I)
            mig_default = found.group(1) if found else None
            if norm_default(mig_default) != norm_default(field.get('defaultValue')):
                problems.append(
                    'v%d->v%d: %s.%s 默认值不一致 迁移=%s schema=%s'
                    % (a, b, table, column, mig_default, field.get('defaultValue'))
                )

    for n in notes:
        print('  [note] %s' % n)
    print('=' * 64)
    if problems:
        print('✗ 发现 %d 处迁移与 schema 不一致：' % len(problems))
        for p in problems:
            print('   - %s' % p)
        return 1
    print('✓ %d 个迁移 / %d 条 ADD COLUMN 与 schema JSON 完全一致' % (len(blocks), checked_columns))
    return 0


if __name__ == '__main__':
    sys.exit(main())
