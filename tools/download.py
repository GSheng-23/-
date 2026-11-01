import argparse
import os
import shutil
import ssl
import sys
import time
import urllib.request


def human(n):
    for unit in ("B", "KB", "MB", "GB"):
        if n < 1024 or unit == "GB":
            return f"{n:.1f}{unit}" if unit != "B" else f"{n}B"
        n /= 1024


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", required=True)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    out = os.path.abspath(args.out)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    part = out + ".part"

    existing = os.path.getsize(part) if os.path.exists(part) else 0
    headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}
    if existing:
        headers["Range"] = f"bytes={existing}-"

    ctx = ssl.create_default_context()
    req = urllib.request.Request(args.url, headers=headers)
    with urllib.request.urlopen(req, timeout=60, context=ctx) as r:
        status = r.status
        if existing and status != 206:
            print(f"server ignored resume (HTTP {status}); restarting", flush=True)
            existing = 0
            mode = "wb"
        else:
            mode = "ab" if existing else "wb"

        length = r.headers.get("Content-Length")
        total = int(length) + existing if length is not None else None
        print(f"HTTP {status} {args.url}", flush=True)
        print(f"writing {out} start={human(existing)} total={human(total) if total else 'unknown'}", flush=True)

        start = time.time()
        last = start
        done = existing
        with open(part, mode) as f:
            while True:
                chunk = r.read(1024 * 1024)
                if not chunk:
                    break
                f.write(chunk)
                done += len(chunk)
                now = time.time()
                if now - last >= 2:
                    speed = (done - existing) / max(now - start, 0.001)
                    if total:
                        pct = done * 100.0 / total
                        print(f"{pct:5.1f}%  {human(done)} / {human(total)}  {human(speed)}/s", flush=True)
                    else:
                        print(f"{human(done)}  {human(speed)}/s", flush=True)
                    last = now

    actual = os.path.getsize(part)
    if total is not None and actual != total:
        print(f"ERROR size mismatch: got {actual}, expected {total}", flush=True)
        sys.exit(2)
    if os.path.exists(out):
        os.remove(out)
    os.replace(part, out)
    print(f"DONE {out} {human(actual)}", flush=True)


if __name__ == "__main__":
    main()
