import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

TOML = "gradle/libs.versions.toml"
PLAN = "bump-plan.json"

DEPS = [
    {
        "name": "NewPipeExtractor",
        "key": "newpipe-extractor",
        "repo": "TeamNewPipe/NewPipeExtractor",
        "pom": "https://jitpack.io/com/github/teamnewpipe/NewPipeExtractor/{v}/NewPipeExtractor-{v}.pom",
    },
    {
        "name": "youtubedl-android",
        "key": "youtubedl-android",
        "repo": "yausername/youtubedl-android",
        "pom": "https://repo1.maven.org/maven2/io/github/junkfood02/youtubedl-android/library/{v}/library-{v}.pom",
    },
]


def http(url, headers=None, timeout=30):
    return urllib.request.urlopen(urllib.request.Request(url, headers=headers or {}), timeout=timeout)


def github_json(url):
    token = os.environ.get("GH_TOKEN") or os.environ.get("GITHUB_TOKEN")
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "gamut-bump"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    for attempt in range(4):
        try:
            with http(url, headers) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if 400 <= e.code < 500 and e.code != 429:
                raise
            if attempt == 3:
                raise
            print(f"retrying {url}: {e}")
            time.sleep(5 * (attempt + 1))
        except (urllib.error.URLError, TimeoutError) as e:
            if attempt == 3:
                raise
            print(f"retrying {url}: {e}")
            time.sleep(5 * (attempt + 1))


def latest_tag(repo):
    try:
        return github_json(f"https://api.github.com/repos/{repo}/releases/latest")["tag_name"]
    except urllib.error.HTTPError as e:
        if e.code != 404:
            raise
        print(f"{repo}: no GitHub release, falling back to tags")
    tags = [t["name"] for t in github_json(f"https://api.github.com/repos/{repo}/tags?per_page=100")]
    stable = [t for t in tags if re.fullmatch(r"v?\d+(\.\d+)*", t)]
    if not stable:
        raise RuntimeError(f"{repo}: no stable tags found")
    return max(stable, key=vtuple)


def current(key, text):
    m = re.search(rf'^{re.escape(key)}\s*=\s*"([^"]+)"', text, re.M)
    if not m:
        sys.exit(f"{key} not found in {TOML}")
    return m.group(1)


def vtuple(v):
    return tuple(int(x) for x in re.findall(r"\d+", v))


def match_v_style(tag, have):
    bare = tag.lstrip("v")
    return f"v{bare}" if have.startswith("v") else bare


def emit(name, value):
    path = os.environ.get("GITHUB_OUTPUT")
    if path:
        with open(path, "a") as f:
            f.write(f"{name}={value}\n")
    else:
        print(f"{name}={value}")


def plan():
    with open(TOML, newline="") as f:
        text = f.read()
    changes, failures = [], []
    for dep in DEPS:
        try:
            have = current(dep["key"], text)
            latest = match_v_style(latest_tag(dep["repo"]), have)
        except Exception as e:
            print(f"::warning::{dep['name']}: lookup failed: {e}")
            failures.append(dep["name"])
            continue
        newer = vtuple(latest) > vtuple(have)
        print(f"{dep['name']}: have {have}, latest {latest}{' -> bump' if newer else ''}")
        if newer:
            changes.append({**dep, "from": have, "to": latest})
    if failures and len(failures) == len(DEPS):
        sys.exit("all lookups failed")
    with open(PLAN, "w") as f:
        json.dump(changes, f, indent=2)
    emit("changed", "true" if changes else "false")
    emit("summary", ", ".join(f"{c['name']} {c['from']} -> {c['to']}" for c in changes))


def wait():
    with open(PLAN) as f:
        changes = json.load(f)
    for change in changes:
        url = change["pom"].format(v=change["to"])
        deadline = time.time() + 30 * 60
        while True:
            try:
                with http(url, {"User-Agent": "gamut-bump"}, timeout=180) as r:
                    if r.status == 200:
                        print(f"available: {url}")
                        break
            except urllib.error.HTTPError as e:
                print(f"{e.code}: {url}")
            except Exception as e:
                print(f"{e}: {url}")
            if time.time() > deadline:
                sys.exit(f"{url} did not become available within 30 minutes")
            time.sleep(30)


def apply():
    with open(TOML, newline="") as f:
        text = f.read()
    with open(PLAN) as f:
        changes = json.load(f)
    for change in changes:
        text, n = re.subn(
            rf'^({re.escape(change["key"])}\s*=\s*")[^"]+(")',
            rf'\g<1>{change["to"]}\g<2>',
            text,
            count=1,
            flags=re.M,
        )
        if n != 1:
            sys.exit(f"could not update {change['key']}")
    with open(TOML, "w", newline="") as f:
        f.write(text)


if __name__ == "__main__":
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    {"plan": plan, "wait": wait, "apply": apply}.get(command, lambda: sys.exit(__doc__))()
