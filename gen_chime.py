import math, struct, wave, os

SR = 44100
def tone(freq, dur, gain=0.4):
    n = int(SR * dur)
    out = []
    for i in range(n):
        t = i / SR
        env = min(1.0, t / 0.01) * min(1.0, (dur - t) / 0.02)
        out.append(int(32767 * gain * env * math.sin(2 * math.pi * freq * t)))
    return out

samples = tone(880, 0.22) + tone(1320, 0.26)
out_path = os.path.join(os.path.dirname(__file__), "app", "src", "main", "res", "raw", "notify_chime.wav")
os.makedirs(os.path.dirname(out_path), exist_ok=True)
with wave.open(out_path, "w") as w:
    w.setnchannels(1)
    w.setsampwidth(2)
    w.setframerate(SR)
    w.writeframes(b"".join(struct.pack("<h", s) for s in samples))
print("wrote", out_path, len(samples), "samples")
