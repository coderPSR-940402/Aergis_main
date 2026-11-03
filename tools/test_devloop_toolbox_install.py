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
            self.assertIn('offset', task['params'])
            self.assertIn('session', task['params'])
            self.assertIn('snapshot', task['params'])
            self.assertIn('summary', data)
            self.assertEqual({'existing': True}, old)
            self.assertTrue(list((server / 'devloop-backups').glob('*.json')))
            # The real Toolbox normalizer returns integer parameters as strings and rejects empty text.
            result = subprocess.run([sys.executable, '-c', "import core,json; print(json.dumps(core.builtin('aergisdevloop',{'view':'replay','offset':'0','session':'latest','snapshot':'first'})))"],
                                    cwd=server, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual([], json.loads(result.stdout)['records'])



class ToolboxDiscoveryTest(unittest.TestCase):
    def test_detects_existing_active_runtime_instead_of_requiring_a_manual_server_path(self):
        import os
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            server = root / 'versions/0.3.1/server'
            server.mkdir(parents=True)
            (root / 'active.json').write_text('{"active":"0.3.1"}')
            (server / 'catalog.py').write_text('''TASKS = {}
def param(kind, label, default=None, **kwargs): return dict(type=kind, label=label, default=default, **kwargs)
def task(id, label, description, **kwargs): TASKS[id] = dict(id=id, label=label, description=description, **kwargs)
''')
            (server / 'core.py').write_text('def builtin(name, values): return {"old": True}\n')
            result = subprocess.run([sys.executable, 'tools/install_devloop_toolbox.py', '--no-service'],
                                    env={**os.environ, 'ATB_INSTALL_ROOT': str(root)}, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual('installed', json.loads(result.stdout)['state'])

if __name__ == '__main__':
    unittest.main()
