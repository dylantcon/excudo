"""
Dependency resolver for Excudo.

Downloads JARs from Maven Central based on dependencies.json manifest.
Idempotent: skips JARs that already exist with correct SHA256.
"""

import hashlib
import io
import json
import platform
import shutil
import sys
import tempfile
import urllib.request
import urllib.error
import zipfile
from pathlib import Path


def _detect_platform() -> tuple:
    """Return (os, arch) normalized to manifest values: linux/macos/windows + x64/aarch64."""
    system = platform.system().lower()
    if system == "darwin":
        os_name = "macos"
    elif system.startswith("linux"):
        os_name = "linux"
    elif system == "windows":
        os_name = "windows"
    else:
        os_name = system

    machine = platform.machine().lower()
    if machine in ("x86_64", "amd64"):
        arch = "x64"
    elif machine in ("aarch64", "arm64"):
        arch = "aarch64"
    else:
        arch = machine

    return os_name, arch


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

    def _select_javafx_platform(self) -> dict:
        """Pick the JavaFX archive matching the current OS+arch, or None if unsupported."""
        jfx = self.manifest.get("javafx")
        if not jfx:
            return None
        os_name, arch = _detect_platform()
        for entry in jfx.get("platforms", []):
            if entry.get("os") == os_name and entry.get("arch") == arch:
                return entry
        return None

    def _javafx_target_dir(self) -> Path:
        jfx = self.manifest.get("javafx", {})
        return self.lib_dir / jfx.get("target_dir", "javafx-sdk-21")

    def _javafx_sentinel_path(self) -> Path:
        return self._javafx_target_dir() / ".excudo-source-sha256"

    def _check_javafx(self, plat: dict) -> str:
        """Return ok / missing / mismatch for the installed JavaFX SDK."""
        target = self._javafx_target_dir()
        sentinel = self._javafx_sentinel_path()
        if not target.is_dir() or not (target / "lib").is_dir():
            return "missing"
        if not sentinel.exists():
            return "mismatch"
        try:
            installed_hash = sentinel.read_text().strip()
        except OSError:
            return "mismatch"
        if installed_hash != plat["sha256"]:
            return "mismatch"
        return "ok"

    def _install_javafx(self, plat: dict, verbose: bool, dry_run: bool, force: bool) -> tuple:
        """
        Download and extract the JavaFX SDK matching the host platform.
        Returns (status, ok_delta, fetch_delta, fail_delta) where status is the action taken.
        """
        jfx = self.manifest["javafx"]
        target = self._javafx_target_dir()
        sentinel = self._javafx_sentinel_path()
        archive_root = jfx["archive_root"]
        url = plat["url"]

        status = "missing" if force else self._check_javafx(plat)

        if status == "ok":
            if verbose:
                print(f"  OK  javafx-sdk ({_detect_platform()[0]}-{_detect_platform()[1]})")
            return "ok", 1, 0, 0

        if status == "mismatch":
            print(f"  MISMATCH  javafx-sdk (sentinel differs, reinstalling)")

        if dry_run:
            print(f"  FETCH  javafx-sdk  <-  {url}")
            return "fetch", 0, 1, 0

        print(f"  Fetching javafx-sdk ({_detect_platform()[0]}-{_detect_platform()[1]})...", end="", flush=True)

        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Excudo-DepResolver/1.0"})
            with urllib.request.urlopen(req, timeout=300) as resp:
                data = resp.read()
        except Exception as e:
            print(f" download failed: {e}")
            return "fail", 0, 0, 1

        actual = hashlib.sha256(data).hexdigest()
        if actual != plat["sha256"]:
            print(f" SHA256 MISMATCH")
            print(f"    Expected: {plat['sha256']}")
            print(f"    Got:      {actual}")
            return "fail", 0, 0, 1

        # Replace any existing partial install
        if target.exists():
            shutil.rmtree(target)
        self.lib_dir.mkdir(parents=True, exist_ok=True)

        try:
            with zipfile.ZipFile(io.BytesIO(data)) as zf:
                # Extract only members under archive_root/, stripping the prefix
                prefix = archive_root.rstrip("/") + "/"
                for member in zf.infolist():
                    if not member.filename.startswith(prefix):
                        continue
                    rel = member.filename[len(prefix):]
                    if not rel:
                        continue
                    dest = target / rel
                    if member.is_dir():
                        dest.mkdir(parents=True, exist_ok=True)
                        continue
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    with zf.open(member) as src, open(dest, "wb") as out:
                        shutil.copyfileobj(src, out)
        except Exception as e:
            print(f" extract failed: {e}")
            if target.exists():
                shutil.rmtree(target)
            return "fail", 0, 0, 1

        if not (target / "lib").is_dir():
            print(f" archive missing 'lib/' subdirectory after extract")
            return "fail", 0, 0, 1

        sentinel.write_text(plat["sha256"])
        print(f" done")
        return "fetch", 0, 1, 0

    def resolve(self, verbose: bool = False, dry_run: bool = False, force: bool = False) -> bool:
        deps = self._all_entries()
        if not deps and not self.manifest.get("javafx"):
            print("No dependencies defined in manifest.")
            return True

        self.lib_dir.mkdir(parents=True, exist_ok=True)

        ok_count = 0
        fetch_count = 0
        fail_count = 0
        mismatch_count = 0

        # JavaFX SDK (platform-specific archive)
        if self.manifest.get("javafx"):
            plat = self._select_javafx_platform()
            if plat is None:
                os_name, arch = _detect_platform()
                print(f"  WARN  no JavaFX SDK entry for {os_name}-{arch}; skipping")
            else:
                _, ok_d, fetch_d, fail_d = self._install_javafx(plat, verbose, dry_run, force)
                ok_count += ok_d
                fetch_count += fetch_d
                fail_count += fail_d

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

        # Summary (+1 for the JavaFX SDK if defined and supported)
        total = len(deps) + (1 if self.manifest.get("javafx") and self._select_javafx_platform() else 0)
        print(f"\nDependencies: {total} total, {ok_count} up-to-date, {fetch_count} fetched, {fail_count} failed")
        if mismatch_count > 0:
            print(f"  ({mismatch_count} had SHA256 mismatches and were re-downloaded)")

        return fail_count == 0

    def verify(self) -> bool:
        """Verify all entries exist with correct hashes, without downloading."""
        deps = self._all_entries()
        all_ok = True
        total = len(deps)

        if self.manifest.get("javafx"):
            plat = self._select_javafx_platform()
            if plat is None:
                os_name, arch = _detect_platform()
                print(f"  SKIP     javafx-sdk (no entry for {os_name}-{arch})")
            else:
                total += 1
                status = self._check_javafx(plat)
                if status == "ok":
                    print(f"  OK       javafx-sdk")
                elif status == "missing":
                    print(f"  MISSING  javafx-sdk")
                    all_ok = False
                else:
                    print(f"  CORRUPT  javafx-sdk (sentinel mismatch)")
                    all_ok = False

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
            print(f"\nAll {total} dependencies verified.")
        else:
            print(f"\nVerification failed. Run 'python3 pc.py deps' to fix.")
        return all_ok

    def list_deps(self) -> None:
        """Print a table of all dependencies."""
        deps = self._all_entries()

        def _coord(d):
            if "group" in d:
                c = f"{d['group']}:{d['artifact']}:{d['version']}"
                if d.get('classifier'):
                    c += f":{d['classifier']}"
                return c
            return d.get("url", "(direct)")

        # JavaFX rendered as a synthetic row
        jfx_row = None
        if self.manifest.get("javafx"):
            plat = self._select_javafx_platform()
            jfx = self.manifest["javafx"]
            jfx_file = f"{jfx['target_dir']}/  ({_detect_platform()[0]}-{_detect_platform()[1]})"
            if plat is None:
                jfx_row = (jfx_file, "(no platform match)", "SKIP")
            else:
                status = self._check_javafx(plat)
                tag = {"ok": "OK", "missing": "MISSING", "mismatch": "CORRUPT"}[status]
                jfx_row = (jfx_file, plat["url"], tag)

        all_files = [d["file"] for d in deps]
        all_coords = [_coord(d) for d in deps]
        if jfx_row:
            all_files.append(jfx_row[0])
            all_coords.append(jfx_row[1])

        max_file = max(len(f) for f in all_files)
        max_coord = max(len(c) for c in all_coords)

        print(f"{'File':<{max_file}}  {'Source':<{max_coord}}  Status")
        print(f"{'-' * max_file}  {'-' * max_coord}  ------")

        if jfx_row:
            print(f"{jfx_row[0]:<{max_file}}  {jfx_row[1]:<{max_coord}}  {jfx_row[2]}")

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
