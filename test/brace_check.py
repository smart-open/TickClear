import sys

files = [
 "../app/src/main/java/com/tickclear/app/ui/assistant/AssistantViewModel.kt",
 "../app/src/main/java/com/tickclear/app/domain/assistant/WebSocketXiaozhiTransport.kt",
 "../app/src/main/java/com/tickclear/app/domain/assistant/XiaozhiConnectionTester.kt",
 "../app/src/main/java/com/tickclear/app/domain/assistant/LocalSpeechRecognizer.kt",
 "../app/src/main/java/com/tickclear/app/ui/settings/SettingsViewModel.kt",
]

def check(path):
    try:
        src = open(path, encoding="utf-8").read()
    except Exception as e:
        return "  [ERR] cannot read: " + str(e)
    i, n = 0, len(src)
    counts = {'(':0,')':0,'{':0,'}':0,'[':0,']':0}
    pairs = {')':'(',']':'[','}':'{'}
    stack = []
    line = 1
    in_line = False
    in_block = False
    in_str = False
    in_str2 = False
    while i < n:
        c = src[i]
        if c == '\n':
            line += 1
            in_line = False
            i += 1
            continue
        if in_block:
            if src[i:i+2] == "*/":
                in_block = False
                i += 2
                continue
            i += 1
            continue
        if in_line:
            i += 1
            continue
        if in_str:
            if c == '\\':
                i += 2
                continue
            if c == '"':
                in_str = False
            i += 1
            continue
        if in_str2:
            if c == '\\':
                i += 2
                continue
            if c == "'":
                in_str2 = False
            i += 1
            continue
        if c == '/' and i+1 < n and src[i+1] == '/':
            in_line = True
            i += 2
            continue
        if c == '/' and i+1 < n and src[i+1] == '*':
            in_block = True
            i += 2
            continue
        if c == '"':
            in_str = True
            i += 1
            continue
        if c == "'":
            in_str2 = True
            i += 1
            continue
        if c in counts:
            counts[c] += 1
            if c in ('(','{','['):
                stack.append((c, line))
            else:
                op = pairs[c]
                if stack and stack[-1][0] == op:
                    stack.pop()
                else:
                    return "  [FAIL] 第%d行 多余/不匹配的 '%s'" % (line, c)
        i += 1
    if stack:
        return "  [FAIL] 未闭合: " + ", ".join("'%s'@L%d" % (p[0], p[1]) for p in stack[-5:])
    if counts['('] != counts[')'] or counts['{'] != counts['}'] or counts['['] != counts[']']:
        return "  [FAIL] 计数不等 {}=(%d,%d) ()=(%d,%d) []=(%d,%d)" % (
            counts['{'], counts['}'], counts['('], counts[')'], counts['['], counts[']'])
    return "  [OK] ()=%d {}=(%d,%d) []=(%d,%d)" % (
        counts['('], counts['{'], counts['}'], counts['['], counts[']'])

for f in files:
    print(f)
    print(check(f))
