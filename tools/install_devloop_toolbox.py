#!/usr/bin/env python3
"""Add the read-only DevLoop collector to an existing Toolbox; preserve its relay and pairing."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shlex
import re
import shutil
import subprocess
import sys
import tempfile
import time

MARKER = '# Aergis DevLoop integration v1'
CATALOG = '''
# Aergis DevLoop integration v1
task('aergis.devloop', 'Aergis test session',
     'Show recorded successes, failures, session state, and replay evidence in plain language.',
     builtin='aergisdevloop', changes=False,
     params={'view': param('choice', 'Evidence to show', 'latest',
             choices=['latest', 'session', 'event', 'manifest', 'history', 'status', 'replay'])})
'''
CORE = '''
# Aergis DevLoop integration v1
_devloop_original_builtin = builtin
def builtin(name, values):
    if name == 'aergisdevloop':
        from aergis_live_collector import Collector
        return Collector(Path.home() / 'agent-gates/aergis-live').view(values.get('view', 'latest'))
    return _devloop_original_builtin(name, values)
'''


def atomic(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(dir=path.parent, prefix='.devloop-')
    try:
        with os.fdopen(fd, 'w') as stream:
            stream.write(content)
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def install(server, service=True):
    server = Path(server).resolve(strict=True)
    files = {name: (server / name).read_text() for name in ('catalog.py', 'core.py')}
    if 'def task(' not in files['catalog.py'] or 'def builtin(' not in files['core.py']:
        raise ValueError('This is not a supported Toolbox server. No files were changed.')
    patch = dict(files)
    if MARKER not in patch['catalog.py']:
        patch['catalog.py'] += CATALOG
    if MARKER not in patch['core.py']:
        patch['core.py'] += '\nfrom pathlib import Path\n' + CORE
    for name, content in patch.items():
        compile(content, name, 'exec')
    collector = Path(__file__).with_name('aergis_live_collector.py').read_text()
    compile(collector, 'aergis_live_collector.py', 'exec')
    backup_id = hashlib.sha256(json.dumps(files, sort_keys=True).encode()).hexdigest()[:16]
    backup = server / 'devloop-backups' / (backup_id + '.json')
    if not backup.exists():
        atomic(backup, json.dumps({'schema': 1, 'created_at': int(time.time()), 'files': files}))
    atomic(server / 'aergis_live_collector.py', collector)
    for name, content in patch.items():
        atomic(server / name, content)
    result = dict(state='installed', task='aergis.devloop', backup=str(backup),
                  relay='Existing Android Power Toolbox connection', service='not_requested')
    if service:
        prefix = Path(os.environ.get('PREFIX', '/data/data/com.termux/files/usr'))
        run = prefix / 'var/service/aergis-devloop/run'
        atomic(run, '#!' + str(prefix / 'bin/sh') + '\nexec 2>&1\nexec ' + shlex.quote(sys.executable) + ' ' +
               shlex.quote(str(server / 'aergis_live_collector.py')) + ' watch\n')
        run.chmod(0o700)
        boot = Path.home() / '.termux/boot/aergis-devloop'
        atomic(boot, '#!' + str(prefix / 'bin/sh') + '\nsv up ' + shlex.quote(str(run.parent)) + '\n')
        boot.chmod(0o700)
        if shutil.which('sv'):
            status = subprocess.run(['sv', 'up', str(run.parent)], capture_output=True, text=True, timeout=10)
            result['service'] = 'started' if status.returncode == 0 else 'installed_needs_supervisor'
            reloaded = subprocess.run(['sv', 'restart', str(prefix / 'var/service/atb-agent'),
                                      str(prefix / 'var/service/atb-dashboard')], capture_output=True, text=True, timeout=20)
            result['runtime_reload'] = 'reloaded' if reloaded.returncode == 0 else 'needs_restart'
        else:
            result['service'] = 'installed_needs_supervisor'
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--server', type=Path)
    parser.add_argument('--no-service', action='store_true')
    args = parser.parse_args()
    server = args.server
    if server is None:
        root = Path(os.environ.get('ATB_INSTALL_ROOT', str(Path.home() / '.local/share/android-power-toolbox')))
        active = json.loads((root / 'active.json').read_text())['active']
        if not isinstance(active, str) or not re.fullmatch(r'\d+\.\d+\.\d+', active):
            parser.error('Existing Toolbox has no valid active runtime; pass --server explicitly')
        server = root / 'versions' / active / 'server'
    print(json.dumps(install(server, not args.no_service), indent=2))


if __name__ == '__main__':
    main()
