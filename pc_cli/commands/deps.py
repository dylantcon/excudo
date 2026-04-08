"""
Dependency resolver for Excudo.

Downloads JARs from Maven Central based on dependencies.json manifest.
Idempotent: skips JARs that already exist with correct SHA256.
"""

import hashlib
import io
import json
import sys
import tempfile
import urllib.request
import urllib.error
import zipfile
from pathlib import Path


class DependencyResolver:

    def __init__(self, project_root: Path):
        self.project_root = project_root
        self.manifest_path = project_root / "dependencies.json"
        self.manifest = self._load_manifest()
        self.lib_dir = project_root / self.manifest.get("lib_dir", "lib")
        self.repo_url = self.manifest.get("repository", "https://repo1.maven.org/maven2")

    def _load_manifest(self) -> dict:
        if not self.manifest_path.exists():
            print(f"ERROR: {self.manifest_path} not found")
            sys.exit(1)
        with open(self.manifest_path, "r") as f:
            return json.load(f)

    def _maven_url(self, dep: dict) -> str:
        group_path = dep["group"].replace(".", "/")
        artifact = dep["artifact"]
        version = dep["version"]
        classifier = dep.get("classifier")
        if classifier:
            filename = f"{artifact}-{version}-{classifier}.jar"
        else:
            filename = f"{artifact}-{version}.jar"
        return f"{self.repo_url}/{group_path}/{artifact}/{version}/{filename}"

    def _download_url(self, dep: dict) -> str:
        if "url" in dep:
            return dep["url"]
        return self._maven_url(dep)

    def _sha256(self, path: Path) -> str:
        h = hashlib.sha256()
        with open(path, "rb") as f:
            for chunk in iter(lambda: f.read(8192), b""):
                h.update(chunk)
        return h.hexdigest()

    def _check_existing(self, dep: dict) -> str:
        """Check if JAR exists with correct hash. Returns status string."""
        jar_path = self.lib_dir / dep["file"]
        if not jar_path.exists():
            return "missing"
        actual = self._sha256(jar_path)
        if actual == dep["sha256"]:
            return "ok"
        return "mismatch"

    def _download(self, url: str, dest: Path, zip_entry: str = None) -> bool:
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Excudo-DepResolver/1.0"})
            with urllib.request.urlopen(req, timeout=120) as resp:
                data = resp.read()

            dest.parent.mkdir(parents=True, exist_ok=True)

            if zip_entry:
                with zipfile.ZipFile(io.BytesIO(data)) as zf:
                    with zf.open(zip_entry) as entry, open(dest, "wb") as f:
                        f.write(entry.read())
            else:
                with open(dest, "wb") as f:
                    f.write(data)
            return True
        except urllib.error.HTTPError as e:
            print(f"  HTTP {e.code}: {url}")
            return False
        except KeyError:
            print(f"  Entry '{zip_entry}' not found in archive")
            return False
        except Exception as e:
            print(f"  Download failed: {e}")
            return False

    def _all_entries(self) -> list:
        """Merge dependencies and fonts into a single list."""
        return self.manifest.get("dependencies", []) + self.manifest.get("fonts", [])

    def resolve(self, verbose: bool = False, dry_run: bool = False, force: bool = False) -> bool:
        deps = self._all_entries()
        if not deps:
            print("No dependencies defined in manifest.")
            return True

        self.lib_dir.mkdir(parents=True, exist_ok=True)

        ok_count = 0
        fetch_count = 0
        fail_count = 0
        mismatch_count = 0

        for dep in deps:
            jar_path = self.lib_dir / dep["file"]
            status = "missing" if force else self._check_existing(dep)

            if status == "ok":
                ok_count += 1
                if verbose:
                    print(f"  OK  {dep['file']}")
                continue

            if status == "mismatch":
                mismatch_count += 1
                print(f"  MISMATCH  {dep['file']} (SHA256 differs, re-downloading)")

            url = self._download_url(dep)

            if dry_run:
                print(f"  FETCH  {dep['file']}  <-  {url}")
                fetch_count += 1
                continue

            print(f"  Fetching {dep['file']}...", end="", flush=True)
            if not self._download(url, jar_path, dep.get("zip_entry")):
                fail_count += 1
                print("")
                continue

            actual = self._sha256(jar_path)
            if actual != dep["sha256"]:
                print(f" SHA256 MISMATCH")
                print(f"    Expected: {dep['sha256']}")
                print(f"    Got:      {actual}")
                jar_path.unlink()
                fail_count += 1
                continue

            print(f" done")
            fetch_count += 1

        # Summary
        total = len(deps)
        print(f"\nDependencies: {total} total, {ok_count} up-to-date, {fetch_count} fetched, {fail_count} failed")
        if mismatch_count > 0:
            print(f"  ({mismatch_count} had SHA256 mismatches and were re-downloaded)")

        return fail_count == 0

    def verify(self) -> bool:
        """Verify all entries exist with correct hashes, without downloading."""
        deps = self._all_entries()
        all_ok = True

        for dep in deps:
            status = self._check_existing(dep)
            if status == "ok":
                print(f"  OK       {dep['file']}")
            elif status == "missing":
                print(f"  MISSING  {dep['file']}")
                all_ok = False
            else:
                print(f"  CORRUPT  {dep['file']} (SHA256 mismatch)")
                all_ok = False

        if all_ok:
            print(f"\nAll {len(deps)} dependencies verified.")
        else:
            print(f"\nVerification failed. Run 'python3 pc.py deps' to fix.")
        return all_ok

    def list_deps(self) -> None:
        """Print a table of all dependencies."""
        deps = self._all_entries()
        max_file = max(len(d["file"]) for d in deps)

        def _coord(d):
            if "group" in d:
                c = f"{d['group']}:{d['artifact']}:{d['version']}"
                if d.get('classifier'):
                    c += f":{d['classifier']}"
                return c
            return d.get("url", "(direct)")

        max_coord = max(len(_coord(d)) for d in deps)

        print(f"{'File':<{max_file}}  {'Source':<{max_coord}}  Status")
        print(f"{'-' * max_file}  {'-' * max_coord}  ------")

        for dep in deps:
            status = self._check_existing(dep)
            tag = {"ok": "OK", "missing": "MISSING", "mismatch": "CORRUPT"}[status]
            print(f"{dep['file']:<{max_file}}  {_coord(dep):<{max_coord}}  {tag}")


def handle_deps_command(args, env) -> int:
    resolver = DependencyResolver(env.project_root)

    if getattr(args, "deps_verify", False):
        return 0 if resolver.verify() else 1
    elif getattr(args, "deps_list", False):
        resolver.list_deps()
        return 0
    else:
        verbose = getattr(args, "verbose", False)
        dry_run = getattr(args, "deps_dry_run", False)
        force = getattr(args, "deps_force", False)
        success = resolver.resolve(verbose=verbose, dry_run=dry_run, force=force)
        return 0 if success else 1
