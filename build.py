#!/usr/bin/env python3
"""Build offline using an existing Minecraft 1.21.1 Fabric Modrinth App profile."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile


def run(command):
    result = subprocess.run([str(value) for value in command])
    if result.returncode:
        raise SystemExit(f"{Path(command[0]).name} failed with exit code {result.returncode}")


def library_path(meta, library):
    artifact = library.get("downloads", {}).get("artifact", {})
    if artifact.get("path"):
        return meta / "libraries" / artifact["path"]
    parts = library["name"].split(":")
    group, name, release = parts[:3]
    classifier = "-" + parts[3] if len(parts) > 3 else ""
    return (meta / "libraries" / group.replace(".", "/") / name / release
            / f"{name}-{release}{classifier}.jar")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--profile", type=Path, required=True,
                        help="Existing Minecraft 1.21.1 Fabric Modrinth App profile")
    parser.add_argument("--meta", type=Path,
                        help="Modrinth App meta folder; defaults to the sibling of profiles")
    parser.add_argument("--java-bin", type=Path,
                        help="JDK bin folder if javac is not available on PATH")
    parser.add_argument("--runtime-java", type=Path,
                        help="Optional Java executable for regression tests")
    parser.add_argument("--test", action="store_true", help="Run regression scenarios")
    args = parser.parse_args()
    base = Path(__file__).resolve().parent
    profile = args.profile.expanduser().resolve()
    meta = args.meta.expanduser().resolve() if args.meta else profile.parent.parent / "meta"
    candidates = list((profile / ".fabric/remappedJars").glob(
        "minecraft-1.21.1-*/client-intermediary.jar"))
    if not candidates:
        raise SystemExit("Minecraft 1.21.1 Fabric cache not found. Launch the profile once first.")
    minecraft = max(candidates, key=lambda item: item.stat().st_mtime)
    loader_version = minecraft.parent.name.removeprefix("minecraft-1.21.1-")
    version_file = meta / "versions" / f"1.21.1-{loader_version}" / f"1.21.1-{loader_version}.json"
    if not version_file.is_file():
        raise SystemExit(f"Modrinth version metadata not found: {version_file}. Check --meta.")
    version = json.loads(version_file.read_text())
    javac = args.java_bin / "javac" if args.java_bin else shutil.which("javac")
    if not javac or not Path(javac).is_file():
        raise SystemExit("A JDK 21+ is required. Install one or pass --java-bin /path/to/jdk/bin.")
    java = args.runtime_java or (args.java_bin / "java" if args.java_bin else shutil.which("java"))
    if args.test and (not java or not Path(java).is_file()):
        raise SystemExit("Java 21+ is required to run tests. Check --runtime-java or --java-bin.")

    paths = [minecraft]
    paths.extend(path for path in (library_path(meta, library) for library in version["libraries"])
                 if path.is_file())
    paths.extend(path for path in sorted((profile / "mods").glob("*.jar"))
                 if not path.name.startswith("tf-bossbar-packet-fix-"))
    paths.extend(sorted((profile / ".fabric/processedMods").glob("*.jar")))
    classpath = os.pathsep.join(map(str, paths))
    classes = base / "build/classes"
    classes.mkdir(parents=True, exist_ok=True)
    sources = sorted((base / "src").rglob("*.java"))
    run([javac, "--release", "21", "-proc:none", "-classpath", classpath,
         "-d", classes, *sources])
    output = base / "build/tf-bossbar-packet-fix-1.0.0+mc1.21.1.jar"
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as jar:
        jar.write(base / "fabric.mod.json", "fabric.mod.json")
        for item in sorted(classes.rglob("*.class")):
            jar.write(item, item.relative_to(classes).as_posix())
    print("Built", output, flush=True)

    if args.test:
        test_classes = base / "build/test-classes"
        test_classes.mkdir(parents=True, exist_ok=True)
        tests = sorted((base / "test").rglob("*.java"))
        run([javac, "--release", "21", "-proc:none", "-classpath",
             str(classes) + os.pathsep + classpath, "-d", test_classes, *tests])
        run([java, "-classpath", str(test_classes) + os.pathsep + str(classes)
             + os.pathsep + classpath, "local.twilightfix.PacketOrderingTest"])


if __name__ == "__main__":
    main()
