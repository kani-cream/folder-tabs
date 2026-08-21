#!/usr/bin/env python3
"""Annotation audit for IntelliJ Platform APIs (plan/grouped-editor-tabs-design.md 27.3).

Prints every member of the given classes that carries a stability annotation
(@Deprecated, @ApiStatus.Internal / Experimental / ScheduledForRemoval / Obsolete /
OverrideOnly / NonExtendable) so a PR can prove which members are safe to call.

Usage:
    tools/api_audit.py <ide-dir> <fqcn> [<fqcn> ...]

<ide-dir> is an extracted IntelliJ distribution, e.g. the one the Gradle plugin
downloaded under ~/.gradle/caches/<gradle>/transforms/*/transformed/idea-<version>-*/.
javap is taken from $JAVA_HOME/bin or the PATH.
"""
from __future__ import annotations

import glob
import os
import re
import shutil
import subprocess
import sys
import zipfile

ANNOTATION = re.compile(
    r"(org\.jetbrains\.annotations\.ApiStatus\$\w+|java\.lang\.Deprecated|kotlin\.Deprecated)"
)
MEMBER = re.compile(r"^  (\S.*[;{])\s*$")
CLASS_HEADER = re.compile(r"^(public|final|abstract|interface|class|enum) ")


def javap() -> str:
    home = os.environ.get("JAVA_HOME")
    if home and os.path.exists(os.path.join(home, "bin", "javap")):
        return os.path.join(home, "bin", "javap")
    found = shutil.which("javap")
    if not found:
        sys.exit("javap not found: set JAVA_HOME or add it to PATH")
    return found


def jars_containing(ide_dir: str, classes: list[str]) -> list[str]:
    wanted = {c.replace(".", "/") + ".class" for c in classes}
    hits: list[str] = []
    candidates = glob.glob(os.path.join(ide_dir, "lib", "*.jar")) + glob.glob(
        os.path.join(ide_dir, "lib", "modules", "*.jar")
    )
    for jar in candidates:
        try:
            names = set(zipfile.ZipFile(jar).namelist())
        except zipfile.BadZipFile:
            continue
        if wanted & names:
            hits.append(jar)
    return hits


def audit(classpath: str, cls: str) -> list[str]:
    out = subprocess.run(
        [javap(), "-v", "-p", "-cp", classpath, cls], capture_output=True, text=True
    ).stdout
    if not out.strip():
        return [f"  (class not found on classpath)"]
    member: str | None = None
    class_level: list[str] = []
    findings: list[tuple[str, str]] = []
    for line in out.splitlines():
        m = MEMBER.match(line)
        if m and not line.startswith("   "):
            member = m.group(1)
            continue
        if CLASS_HEADER.match(line):
            member = None
            continue
        a = ANNOTATION.search(line)
        if a and line.startswith("        ") and not line.strip().startswith("#"):
            name = a.group(1).split(".")[-1]
            if member is None:
                class_level.append(name)
            else:
                findings.append((member, name))
    lines: list[str] = []
    if class_level:
        lines.append("  CLASS-LEVEL: " + ", ".join(sorted(set(class_level))))
    for mem, ann in dict.fromkeys(findings):
        if mem.startswith("private "):
            continue
        lines.append(f"  [{ann}] {mem}")
    return lines or ["  (no stability annotations)"]


def main(argv: list[str]) -> int:
    if len(argv) < 3:
        print(__doc__)
        return 2
    ide_dir, classes = argv[1], argv[2:]
    jars = jars_containing(ide_dir, classes)
    if not jars:
        sys.exit(f"none of the classes were found under {ide_dir}/lib")
    classpath = os.pathsep.join(jars)
    for cls in classes:
        print(f"######## {cls}")
        print("\n".join(audit(classpath, cls)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
