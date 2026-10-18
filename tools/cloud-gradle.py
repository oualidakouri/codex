#!/usr/bin/env python3
"""Run the normal Gradle wrapper with the cloud's SDK, JDK, proxy and CA trust."""
import os
from pathlib import Path
import shlex
import subprocess
import sys
import urllib.parse

root = Path(__file__).resolve().parents[1]
env = os.environ.copy()
env.setdefault('JAVA_HOME', '/workspace/toolchains/jdk-21.0.8+9')
env.setdefault('ANDROID_HOME', '/workspace/android-sdk')
env.setdefault('ANDROID_USER_HOME', '/workspace/android-user')
env.setdefault('GRADLE_USER_HOME', '/workspace/gradle-cache')
options = ['-Duser.home=/workspace/android-user']
if Path('/etc/ssl/certs/java/cacerts').exists():
    options += ['-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts']
proxy = urllib.parse.urlsplit(env.get('HTTPS_PROXY', env.get('https_proxy', '')))
if proxy.hostname:
    for scheme in ('http', 'https'):
        options += [f'-D{scheme}.proxyHost={proxy.hostname}', f'-D{scheme}.proxyPort={proxy.port or 80}']
    options += ['-Dhttp.nonProxyHosts=localhost|127.*']
# No proxy credentials or environment values are printed or saved.
env['JAVA_OPTS'] = env.get('JAVA_OPTS', '') + ' ' + shlex.join(options)
raise SystemExit(subprocess.call([str(root / 'gradlew'), *sys.argv[1:]], cwd=root, env=env))
