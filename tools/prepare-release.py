#!/usr/bin/env python3
"""Prepare a signed, immutable APK filename and the public in-app update manifest."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--sdk', default=os.environ.get('ANDROID_SDK_ROOT', '/workspace/android-sdk'))
parser.add_argument('--notes', default=None)
args = parser.parse_args()
metadata_file = root / 'app/build/outputs/apk/debug/output-metadata.json'
metadata = json.loads(metadata_file.read_text())
assert metadata['applicationId'] == 'fr.maisonos.diagnostic'
element = metadata['elements'][0]
version, code = element['versionName'], element['versionCode']
assert re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+', version)
apk = metadata_file.parent / element['outputFile']
verification = subprocess.run([str(Path(args.sdk) / 'build-tools/36.0.0/apksigner'), 'verify', '--verbose', '--print-certs', str(apk)], check=True, capture_output=True, text=True).stdout
# Public certificate fingerprint of the diagnostic/V1 installation; never sign an update with a replacement key.
expected = '19ba351806c0a807a2820aa362e45baded20f4a74e83454cd811ba291c351799'
assert f'Signer #1 certificate SHA-256 digest: {expected}' in verification, 'Signing key differs from the installed APK'
sha = hashlib.sha256(apk.read_bytes()).hexdigest()
destination = root / f'downloads/maison-os-{version}.apk'
tracked = subprocess.run(['git', 'ls-files', '--error-unmatch', str(destination.relative_to(root))], cwd=root, capture_output=True).returncode == 0
if tracked and destination.exists():
    assert hashlib.sha256(destination.read_bytes()).hexdigest() == sha, 'Published versions are immutable. Increase versionCode/versionName.'
else:
    shutil.copyfile(apk, destination)
notes = (root / (args.notes or f'docs/releases/{version}.md')).read_text().strip()
assert len(notes) <= 4000
manifest = dict(versionCode=code, versionName=version, packageName=metadata['applicationId'],
                apkUrl=f'https://raw.githubusercontent.com/oualidakouri/codex/main/downloads/maison-os-{version}.apk',
                size=destination.stat().st_size, sha256=sha, minSdk=metadata.get('minSdkVersionForDexing', 26), notes=notes)
(root / 'downloads/update.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n')
report = root / f'downloads/verification-{version}'
report.mkdir(exist_ok=True)
(report / 'signature.txt').write_text(verification)
(report / 'checksums.sha256').write_text(f'{sha}  downloads/{destination.name}\n')
print(f'Prepared {destination.relative_to(root)} ({destination.stat().st_size} bytes), version code {code}.')
print('Updated downloads/update.json. Review and publish these files together.')
