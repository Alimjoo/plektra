#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = ["google-auth", "google-api-python-client"]
# ///
"""Inspect a Plektra Play channel and prepare an uncommitted test-release upload."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import stat
import sys
import zipfile

PROJECT = "pkb-rocks-plektra-play"
CHANNELS = {
    "nightly": "rocks.pkb.plektra.unsovereign.nightly",
    "stable": "rocks.pkb.plektra.unsovereign",
}
KEY_DIR = Path.home() / ".config/pkb-rocks/google-play"
STATE_DIR = Path.home() / ".local/state/pkb-rocks/google-play"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"


def client(key_file, channel):
    from google.oauth2 import service_account
    from googleapiclient.discovery import build

    if key_file.is_symlink() or not key_file.is_file():
        raise ValueError(f"Service-account key is missing or is a symlink: {key_file}")
    for path in (key_file.parent, key_file):
        info = path.stat()
        if info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) & 0o077:
            raise ValueError(f"Only the current user may access {path}")
    data = json.loads(key_file.read_text())
    account = f"plektra-{channel}-release@{PROJECT}.iam.gserviceaccount.com"
    if data.get("type") != "service_account" or data.get("project_id") != PROJECT or data.get("client_email") != account:
        raise ValueError(f"The key does not belong to the Plektra {channel} release service account")
    if data.get("token_uri") != "https://oauth2.googleapis.com/token":
        raise ValueError("Unexpected OAuth token endpoint")
    credentials = service_account.Credentials.from_service_account_info(data, scopes=[SCOPE])
    return build("androidpublisher", "v3", credentials=credentials, cache_discovery=False)


def upload_plan(bundle, name, notes_file, track, package):
    if bundle.suffix.lower() != ".aab" or not bundle.is_file():
        raise ValueError("Expected an existing .aab file")
    with zipfile.ZipFile(bundle) as archive:
        if not {"BundleConfig.pb", "base/manifest/AndroidManifest.xml"}.issubset(archive.namelist()):
            raise ValueError("The file is not an Android App Bundle")
    if not name.strip() or len(name) > 50:
        raise ValueError("Release name must contain 1–50 characters")
    notes = json.loads(notes_file.read_text())
    if not isinstance(notes, dict) or not notes:
        raise ValueError("Notes must be a JSON object mapping language codes to text")
    for language, text in notes.items():
        if not isinstance(language, str) or not language or not isinstance(text, str) or not text.strip() or len(text) > 500:
            raise ValueError("Each release note needs a language code and 1–500 characters")
    with bundle.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    return {"package": package, "track": track, "name": name, "bundle": str(bundle.resolve()),
            "sha256": digest, "releaseNotes": [{"language": k, "text": v} for k, v in notes.items()],
            "status": "draft", "committed": False}


def write_record(path, data, create=False):
    flags = os.O_WRONLY | os.O_CREAT | (os.O_EXCL if create else os.O_TRUNC)
    fd = os.open(path, flags, 0o600)
    with os.fdopen(fd, "w") as stream:
        json.dump(data, stream, ensure_ascii=False, indent=2)
        stream.write("\n")


def require_no_pending_edit(package):
    # Inserting a new edit with the same account invalidates its earlier edit.
    for path in (STATE_DIR / package).glob("edit-*.json"):
        record = json.loads(path.read_text())
        if record.get("phase") in ("created", "uploaded", "draft-prepared", "validated"):
            raise ValueError(f"An upload edit is pending: {path}. Inspect or save that edit before creating another.")


def show_status(service, package):
    require_no_pending_edit(package)
    edit = service.edits().insert(packageName=package, body={}).execute(num_retries=0)
    try:
        tracks = service.edits().tracks().list(packageName=package, editId=edit["id"]).execute(num_retries=0)
        bundles = service.edits().bundles().list(packageName=package, editId=edit["id"]).execute(num_retries=0)
        print(json.dumps({"package": package, "tracks": tracks.get("tracks", []),
                          "bundles": bundles.get("bundles", [])}, ensure_ascii=False, indent=2))
    finally:
        service.edits().delete(packageName=package, editId=edit["id"]).execute(num_retries=0)


def upload(service, plan):
    package = plan["package"]
    require_no_pending_edit(package)
    from googleapiclient.http import MediaFileUpload
    state_dir = STATE_DIR / package
    state_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
    if state_dir.is_symlink() or stat.S_IMODE(state_dir.stat().st_mode) & 0o077:
        raise ValueError(f"State directory must be private: {state_dir}")
    edit = service.edits().insert(packageName=package, body={}).execute(num_retries=0)
    record = state_dir / f"edit-{edit['id']}.json"
    result = {**plan, "edit": edit, "phase": "created"}
    write_record(record, result, create=True)
    print(f"Edit record: {record}", flush=True)
    current = service.edits().tracks().get(packageName=package, editId=edit["id"], track=plan["track"]).execute(num_retries=0)
    releases = current.get("releases", [])
    drafts = [release for release in releases if release.get("status") == "draft"]
    if drafts and (len(drafts) != 1 or drafts[0] != {"status": "draft"}):
        raise ValueError("The test track already contains a draft with content; review it before uploading another")
    with Path(plan["bundle"]).open("rb") as stream:
        if hashlib.file_digest(stream, "sha256").hexdigest() != plan["sha256"]:
            raise ValueError("The bundle changed after preparing the upload plan")
    request = service.edits().bundles().upload(packageName=package, editId=edit["id"],
        media_body=MediaFileUpload(plan["bundle"], mimetype="application/octet-stream", chunksize=8 * 1024 * 1024, resumable=True))
    response = None
    while response is None:
        progress, response = request.next_chunk(num_retries=0)
        if progress:
            print(f"Upload: {int(progress.progress() * 100)}%", flush=True)
    if response.get("sha256", "").lower() != plan["sha256"]:
        raise ValueError("Google's uploaded bundle digest differs from the local bundle")
    result.update(phase="uploaded", bundleResponse=response)
    write_record(record, result)
    release = {"name": plan["name"], "versionCodes": [str(response["versionCode"])],
               "status": "draft", "releaseNotes": plan["releaseNotes"]}
    service.edits().tracks().update(packageName=package, editId=edit["id"], track=plan["track"],
        body={"track": plan["track"], "releases": [*[r for r in releases if r.get("status") != "draft"], release]}).execute(num_retries=0)
    result.update(phase="draft-prepared", release=release)
    write_record(record, result)
    service.edits().validate(packageName=package, editId=edit["id"]).execute(num_retries=0)
    result["phase"] = "validated"
    write_record(record, result)
    print("Upload validated. The edit is NOT committed and is NOT published.")
    print(f"It expires at Unix time {edit.get('expiryTimeSeconds', 'unknown')}. Review {record} before a separate commit.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--channel", choices=tuple(CHANNELS), default="nightly")
    parser.add_argument("--credentials", type=Path)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("status", help="Read test tracks and bundles using a temporary, discarded edit")
    prepare = commands.add_parser("upload", help="Show an upload plan; add --execute to prepare an uncommitted test release")
    prepare.add_argument("bundle", type=Path)
    prepare.add_argument("--track", choices=("alpha", "internal"), default="alpha")
    prepare.add_argument("--name", required=True)
    prepare.add_argument("--notes", type=Path, required=True, help="JSON language-to-text mapping")
    prepare.add_argument("--execute", action="store_true")
    args = parser.parse_args()
    package = CHANNELS[args.channel]
    key_file = args.credentials or KEY_DIR / f"plektra-{args.channel}-service-account.json"
    if args.command == "status":
        show_status(client(key_file, args.channel), package)
    else:
        plan = upload_plan(args.bundle, args.name, args.notes, args.track, package)
        print(json.dumps(plan, ensure_ascii=False, indent=2), flush=True)
        if args.execute:
            upload(client(key_file, args.channel), plan)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"ERROR: {error}", file=sys.stderr)
        sys.exit(1)
