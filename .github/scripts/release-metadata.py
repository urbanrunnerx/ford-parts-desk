#!/usr/bin/env python3
"""Fail closed on mismatched, debug, reused, or downgrade release inputs."""
import json
from pathlib import Path
import re
import sys


def version(tag):
    match = re.fullmatch(r"v([1-9][0-9]*)\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)", tag)
    if not match:
        raise ValueError("Use a stable vMAJOR.MINOR.PATCH tag; minor/patch must be 0..99.")
    parts = tuple(map(int, match.groups()))
    code = parts[0] * 10000 + parts[1] * 100 + parts[2]
    if code > 2100000000:
        raise ValueError("versionCode exceeds Android's limit.")
    return tag[1:], code, parts


def source(tag, releases):
    name, code, parts = version(tag)
    if code <= 30000:
        raise ValueError("v3.0.0 already shipped; use a versionCode greater than 30000.")
    for release in releases:
        if release["tag_name"] == tag:
            raise ValueError("A release/draft already exists for this tag; never overwrite it.")
        if not release["draft"] and not release["prerelease"]:
            # Unknown version schemes require an explicit policy review, not a silent skip.
            if version(release["tag_name"])[2] >= parts:
                raise ValueError("The release version must exceed every published stable release.")
    gradle = Path("android/app/build.gradle").read_text()
    names = re.findall(r"^\s*versionName\s+['\"]([^'\"]+)['\"]\s*$", gradle, re.M)
    codes = re.findall(r"^\s*versionCode\s+(\d+)\s*$", gradle, re.M)
    if names != [name] or codes != [str(code)]:
        raise ValueError("Tag must match Android versionName and MAJOR*10000+MINOR*100+PATCH versionCode.")
    if json.loads(Path("package.json").read_text())["version"] != name:
        raise ValueError("package.json version must match the tag.")


def apk(tag, badging):
    name, code, _ = version(tag)
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging, re.M)
    if not package or package.groups() != ("dev.urbanrunnerx.partsdesk", str(code), name):
        raise ValueError("APK application ID/version does not match the intended release.")
    if re.search(r"^application-debuggable(?:\s|$)", badging, re.M):
        raise ValueError("Debuggable APKs cannot be released.")
    if not re.search(r"^sdkVersion:'26'$", badging, re.M):
        raise ValueError("Unexpected minimum SDK; review release compatibility before changing it.")


if __name__ == "__main__":
    try:
        mode, tag, input_file = sys.argv[1:]
        data = Path(input_file).read_text()
        if mode == "source":
            source(tag, [item for page in json.loads(data) for item in page])
        elif mode == "apk":
            apk(tag, data)
        else:
            raise ValueError("Unknown validation mode.")
    except (ValueError, KeyError, OSError) as error:
        sys.exit(f"Release validation failed: {error}")
