import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


class ToolboxInstallTest(unittest.TestCase):
    def test_installed_catalog_returns_structured_session_via_existing_core(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            server = root / 'server'
            server.mkdir()
            (server / 'catalog.py').write_text('''TASKS = {}
def param(kind, label, default=None, **kwargs): return dict(type=kind, label=label, default=default, **kwargs)
def task(id, label, description, **kwargs): TASKS[id] = dict(id=id, label=label, description=description, **kwargs)
''')
            (server / 'core.py').write_text('''from catalog import TASKS
def builtin(name, values):
    if name == 'system': return {'existing': True}
    raise ValueError(name)
''')
            command = [sys.executable, 'tools/install_devloop_toolbox.py', '--server', str(server), '--no-service']
            subprocess.run(command, check=True, capture_output=True, text=True)
            # Reinstallation must not wrap core twice or replace another task.
            subprocess.run(command, check=True, capture_output=True, text=True)
            code = "import core,json; print(json.dumps([core.TASKS['aergis.devloop'],core.builtin('aergisdevloop',{'view':'latest'}),core.builtin('system',{})]))"
            result = subprocess.run([sys.executable, '-c', code], cwd=server, check=True, capture_output=True, text=True)
            task, data, old = json.loads(result.stdout)
            self.assertEqual('aergisdevloop', task['builtin'])
            self.assertFalse(task['changes'])
            self.assertIn('summary', data)
            self.assertEqual({'existing': True}, old)
            self.assertTrue(list((server / 'devloop-backups').glob('*.json')))


if __name__ == '__main__':
    unittest.main()
