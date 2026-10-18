#!/usr/bin/env python3
"""Build tiny, non-installed APK fixtures. Keys/manifests remain outside test assets."""
import os
import pathlib
import subprocess
import sys
import tempfile

sdk, jdk, signing_key, destination, current_version = sys.argv[1:]
output = pathlib.Path(destination)
output.mkdir(parents=True, exist_ok=True)
build_tools = pathlib.Path(sdk) / 'build-tools' / '36.0.0'
env = dict(os.environ, JAVA_HOME=jdk)
def run(args):
    subprocess.run([str(a) for a in args], check=True, env=env, capture_output=True)
with tempfile.TemporaryDirectory(prefix='maison-update-fixtures-') as directory:
    temp = pathlib.Path(directory)
    wrong_key = temp / 'unrelated.keystore'
    run([pathlib.Path(jdk) / 'bin/keytool', '-genkeypair', '-noprompt', '-keystore', wrong_key,
         '-storepass', 'android', '-alias', 'androiddebugkey', '-keypass', 'android', '-dname', 'CN=Unrelated fixture',
         '-keyalg', 'RSA', '-keysize', '2048', '-validity', '100'])
    fixtures = [('future', int(current_version) + 1, 'fr.maisonos.diagnostic', signing_key),
                ('wrong-signer', int(current_version) + 1, 'fr.maisonos.diagnostic', wrong_key),
                ('wrong-package', int(current_version) + 1, 'fr.maisonos.other', signing_key),
                ('same-version', int(current_version), 'fr.maisonos.diagnostic', signing_key)]
    for name, version, package, key in fixtures:
        manifest = temp / 'AndroidManifest.xml'
        manifest.write_text(f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{package}" android:versionCode="{version}" android:versionName="fixture"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="36"/><application android:hasCode="false" android:label="Update fixture"/></manifest>')
        unsigned = temp / 'unsigned.apk'
        run([build_tools / 'aapt2', 'link', '-o', unsigned, '--manifest', manifest, '-I', pathlib.Path(sdk) / 'platforms/android-36/android.jar'])
        run([build_tools / 'apksigner', 'sign', '--ks', key, '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', output / f'{name}.apk', unsigned])
    # V4 sidecars are not needed as APK assets.
    for sidecar in output.glob('*.idsig'):
        sidecar.unlink()
print('Created four signed update test fixtures; no private keys included in assets.')
