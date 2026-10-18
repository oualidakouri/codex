#!/usr/bin/env python3
"""Pinned cloud toolchain, verified archives, no sudo or repository source rewrites."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tarfile
import tempfile
import urllib.parse
import urllib.request
import zipfile

BASE = Path('/workspace')
JDK = BASE / 'toolchains/jdk-21.0.8+9'
SDK = BASE / 'android-sdk'
TOOLS = SDK / 'cmdline-tools/19.0'

def download(url, path, algorithm, expected):
    urllib.request.urlretrieve(url, path)
    with open(path, 'rb') as stream:
        actual = hashlib.file_digest(stream, algorithm).hexdigest()
    if actual != expected:
        raise RuntimeError('Archive checksum mismatch; refusing installation')

def main():
    with tempfile.TemporaryDirectory(prefix='maison-bootstrap-') as tmp:
        tmp = Path(tmp)
        if not (JDK / 'bin/javac').exists():
            url = 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.8%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.8_9.tar.gz'
            expected = urllib.request.urlopen(url + '.sha256.txt', timeout=30).read().decode().split()[0]
            archive = tmp / 'jdk.tar.gz'
            download(url, archive, 'sha256', expected)
            JDK.parent.mkdir(parents=True, exist_ok=True)
            with tarfile.open(archive) as f:
                f.extractall(JDK.parent, filter='data')
        if not (TOOLS / 'bin/sdkmanager').exists():
            archive = tmp / 'android-tools.zip'
            # Official Google repository metadata for command-line tools 19.0.
            download('https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip', archive,
                     'sha1', '5fdcc763663eefb86a5b8879697aa6088b041e70')
            with zipfile.ZipFile(archive) as f:
                f.extractall(tmp / 'sdk')
            TOOLS.parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(tmp / 'sdk/cmdline-tools'), TOOLS)
            for f in (TOOLS / 'bin').iterdir():
                f.chmod(0o755)
    env = os.environ.copy()
    env['JAVA_HOME'] = str(JDK)
    env['ANDROID_USER_HOME'] = str(BASE / 'android-user')
    env['JAVA_TOOL_OPTIONS'] = '-Duser.home=/workspace/android-user'
    if Path('/etc/ssl/certs/java/cacerts').exists():
        env['JAVA_TOOL_OPTIONS'] += ' -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts'
    proxy = urllib.parse.urlsplit(env.get('HTTPS_PROXY', env.get('https_proxy', '')))
    args = [str(TOOLS / 'bin/sdkmanager'), '--sdk_root=' + str(SDK)]
    if proxy.hostname:
        args += ['--proxy=http', '--proxy_host=' + proxy.hostname, '--proxy_port=' + str(proxy.port or 80)]
    subprocess.run(args + ['--licenses'], input='y\n' * 100, text=True, env=env, check=True)
    subprocess.run(args + ['platform-tools', 'platforms;android-36', 'build-tools;36.0.0'], env=env, check=True)
    print('Pinned JDK 21 and Android SDK 36 installed. TLS and checksum verification enabled.')

if __name__ == '__main__':
    main()
