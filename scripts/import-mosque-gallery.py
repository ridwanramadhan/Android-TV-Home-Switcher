#!/usr/bin/env python3
"""Rebuild the offline mosque gallery from reviewed Wikimedia Commons titles.

Uses only Python's standard library. Run from any directory with Python 3.10+.
All metadata and downloaded derivatives are checked before outputs are replaced.
"""
from __future__ import annotations

import html
from html.parser import HTMLParser
import json
from pathlib import Path
import re
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / "docs/mosque-gallery-sources.json"
DRAWABLES = ROOT / "launcher/app/src/widgets/res/drawable-nodpi"
JAVA = ROOT / "launcher/app/src/widgets/java/com/gathin/tvhome/MosquePhotos.java"
NOTICES = ROOT / "THIRD_PARTY_NOTICES.md"
USER_AGENT = "AquaTVHome/2.7 mosque-gallery-import"
ALLOWED_LICENSES = {
    "Public domain", "CC0 1.0", "CC BY 2.0", "CC BY 3.0", "CC BY 4.0",
    "CC BY-SA 2.0", "CC BY-SA 3.0", "CC BY-SA 4.0",
}
START = "<!-- mosque-gallery:start -->"
END = "<!-- mosque-gallery:end -->"


class PlainText(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.parts: list[str] = []

    def handle_data(self, data: str) -> None:
        self.parts.append(data)

    def handle_starttag(self, tag: str, attrs: list) -> None:
        if tag in {"br", "p", "div", "li"}:
            self.parts.append(" ")


def plain_text(value: str) -> str:
    parser = PlainText()
    parser.feed(value)
    return " ".join(html.unescape("".join(parser.parts)).split())


def request_bytes(url: str) -> bytes:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    # Commons can throttle maintenance runs; retry only transient network errors.
    for attempt in range(4):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.read()
        except urllib.error.HTTPError as error:
            if error.code not in {429, 502, 503, 504} or attempt == 3:
                raise
            time.sleep(max(5 * (attempt + 1), int(error.headers.get("Retry-After", "0"))))
    raise RuntimeError("unreachable")


def validate_records(records: list[dict]) -> None:
    if len(records) != 30:
        raise ValueError(f"expected 30 mosque photos, got {len(records)}")
    for record in records:
        for key in ("slug", "commons_title", "mosque", "location"):
            if not isinstance(record.get(key), str) or not record[key].strip():
                raise ValueError(f"mosque source is missing {key}")
        if not re.fullmatch(r"[a-z][a-z0-9_]*", record["slug"]):
            raise ValueError(f"invalid Android drawable slug: {record['slug']}")
        if not record["commons_title"].startswith("File:"):
            raise ValueError("Commons titles must begin with File:")
    if len({record["slug"] for record in records}) != 30:
        raise ValueError("mosque photo slugs must be unique")
    if len({record["commons_title"] for record in records}) != 30:
        raise ValueError("mosque photo titles must be unique")


def fetch_metadata(records: list[dict]) -> dict[str, dict]:
    params = urllib.parse.urlencode({
        "action": "query", "format": "json", "prop": "imageinfo",
        "iiprop": "url|extmetadata|size", "iiurlwidth": 1280,
        "titles": "|".join(record["commons_title"] for record in records),
    })
    result = json.loads(request_bytes("https://commons.wikimedia.org/w/api.php?" + params))
    if "error" in result:
        raise ValueError(f"Commons API error: {result['error']}")
    metadata = {}
    for page in result.get("query", {}).get("pages", {}).values():
        infos = page.get("imageinfo", [])
        if not infos:
            continue
        info = infos[0]
        extra = info.get("extmetadata", {})
        value = lambda name: plain_text(extra.get(name, {}).get("value", ""))
        metadata[page["title"]] = {
            "thumb_url": info.get("thumburl", ""),
            "creator": value("Artist"), "license": value("LicenseShortName"),
            "license_url": extra.get("LicenseUrl", {}).get("value", ""),
            "description_url": info.get("descriptionurl", ""),
            "attribution": value("Attribution"), "credit": value("Credit"),
            "width": info.get("thumbwidth", 0), "height": info.get("thumbheight", 0),
        }
    return metadata


def validate(records: list[dict], metadata: dict[str, dict]) -> None:
    validate_records(records)
    for record in records:
        title = record["commons_title"]
        if title not in metadata:
            raise ValueError(f"no Commons image metadata for {title}")
        info = metadata[title]
        for key in ("thumb_url", "creator", "license", "description_url"):
            if not info.get(key):
                raise ValueError(f"{title} is missing {key}")
        if info["license"] not in ALLOWED_LICENSES:
            raise ValueError(f"unsupported license: {info['license']}")
        for key in ("thumb_url", "description_url"):
            url = urllib.parse.urlparse(info[key])
            if url.scheme != "https" or not url.hostname or not url.hostname.endswith(".wikimedia.org"):
                raise ValueError(f"unexpected Commons {key}: {info[key]}")
        if info["width"] != 1280 or info["height"] <= 0 or info["height"] >= info["width"]:
            raise ValueError(f"{title} must have a landscape 1280-pixel derivative")
        if info["license"] != "Public domain" and not info["license_url"]:
            raise ValueError(f"{title} is missing license_url")


def download_jpeg(url: str, destination: Path) -> None:
    data = request_bytes(url)
    if not data.startswith(b"\xff\xd8") or not data.endswith(b"\xff\xd9"):
        raise ValueError(f"not a complete JPEG: {url}")
    temporary = destination.with_suffix(".tmp")
    temporary.write_bytes(data)
    temporary.replace(destination)


def java_string(value: str) -> str:
    return json.dumps(value, ensure_ascii=True)


def render_java(records: list[dict], metadata: dict[str, dict]) -> str:
    entries = []
    for record in records:
        info = metadata[record["commons_title"]]
        values = [record["mosque"], record["location"], info["creator"], info["license"], info["description_url"]]
        entries.append("                new MosquePhoto(R.drawable.mosque_" + record["slug"] + ",\n                        " + ", ".join(map(java_string, values)) + ")")
    return ("// Generated by scripts/import-mosque-gallery.py. Do not edit by hand.\n"
            "package com.gathin.tvhome;\n\nimport java.util.Arrays;\nimport java.util.Collections;\nimport java.util.List;\n\n"
            "final class MosquePhotos {\n    private MosquePhotos() {}\n\n"
            "    static List<MosquePhoto> all() {\n        return Collections.unmodifiableList(Arrays.asList(\n"
            + ",\n".join(entries) + "\n        ));\n    }\n}\n")


def markdown(value: str) -> str:
    return value.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]").replace("*", "\\*").replace("_", "\\_").replace("<", "&lt;").replace(">", "&gt;")


def render_notices(records: list[dict], metadata: dict[str, dict], current: str) -> str:
    lines = [START, "", "## Indonesian mosque photograph gallery", "",
             "These photographs are third-party works under their individual licenses below; they are excluded from the project's MIT grant. Wikimedia Commons generated the bundled 1280-pixel JPEG derivatives. The app may center-crop them for display. No other image edits were made. Follow the linked licenses for reuse, attribution, and share-alike requirements.", ""]
    for record in records:
        info = metadata[record["commons_title"]]
        license_label = markdown(info["license"])
        license_text = f"[{license_label}]({info['license_url']})" if info["license_url"] else license_label
        source_url = info["description_url"]
        if "(" in source_url or ")" in source_url:
            source_url = "<" + source_url + ">"
        line = f"- **{markdown(record['mosque'])}**, {markdown(record['location'])} — {markdown(info['creator'])}. {license_text}. [Wikimedia Commons file page]({source_url})."
        if info["attribution"] and info["attribution"] != info["creator"]:
            line += " Attribution: " + markdown(info["attribution"]) + "."
        if info["credit"] and info["credit"].lower() not in {"own work", "karya sendiri", "self-photographed", "self-made"}:
            line += " Source credit: " + markdown(info["credit"]) + "."
        lines.append(line)
    lines += ["", END]
    block = "\n".join(lines)
    if current.count(START) != current.count(END) or current.count(START) > 1:
        raise ValueError("invalid mosque attribution block markers")
    if START in current:
        start, end = current.index(START), current.index(END)
        if end < start:
            raise ValueError("reversed mosque attribution block markers")
        return current[:start] + block + current[end + len(END):]
    return current.rstrip() + "\n\n" + block + "\n"


def main() -> None:
    records = json.loads(SOURCES.read_text(encoding="utf-8"))
    validate_records(records)
    metadata = fetch_metadata(records)
    validate(records, metadata)
    java = render_java(records, metadata)
    notices = render_notices(records, metadata, NOTICES.read_text(encoding="utf-8"))
    # Stage the entire download set first, so a failed download keeps current assets intact.
    with tempfile.TemporaryDirectory(prefix="mosque-gallery-") as staging:
        staged = Path(staging)
        for record in records:
            name = "mosque_" + record["slug"] + ".jpg"
            download_jpeg(metadata[record["commons_title"]]["thumb_url"], staged / name)
            print(f"Downloaded {name}", flush=True)
        total = sum(path.stat().st_size for path in staged.glob("*.jpg"))
        if total > 10 * 1024 * 1024:
            raise ValueError(f"gallery exceeds 10 MiB: {total} bytes")
        DRAWABLES.mkdir(parents=True, exist_ok=True)
        expected = {path.name for path in staged.glob("*.jpg")}
        unexpected = {path.name for path in DRAWABLES.glob("mosque_*.jpg")} - expected
        if unexpected:
            raise ValueError(f"remove stale mosque drawables explicitly: {sorted(unexpected)}")
        for path in sorted(staged.glob("*.jpg")):
            destination = DRAWABLES / path.name
            temporary = destination.with_suffix(".tmp")
            temporary.write_bytes(path.read_bytes())
            temporary.replace(destination)
        JAVA.write_text(java, encoding="utf-8")
        NOTICES.write_text(notices, encoding="utf-8")
    print(f"Imported {len(records)} photos; {total} bytes ({total / 1024 / 1024:.2f} MiB).")


if __name__ == "__main__":
    main()
