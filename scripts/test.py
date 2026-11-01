#!/usr/bin/env python3
from pathlib import Path
import os, socket, subprocess, tempfile, time, hashlib
from build import ROOT, build

def run():
    build()
    tests=ROOT/'build/test-v2'; tests.mkdir(parents=True,exist_ok=True)
    cp=os.pathsep.join(map(str,[ROOT/'build/classes',tests,ROOT/'lib/*']))
    subprocess.run(['javac','--release','21','-encoding','UTF-8','-cp',cp,'-d',str(tests),*map(str,(ROOT/'test').glob('*.java'))],check=True)
    subprocess.run(['java','-cp',cp,'common.CommonSelfTest'],check=True,timeout=30)
    subprocess.run(['java','-Xmx128m','-cp',cp,'ChatIntegration'],check=True,timeout=120)
    subprocess.run(['java','-Xmx128m','-cp',cp,'ChatFaults'],check=True,timeout=60)
    subprocess.run(['python3',str(ROOT/'scripts/process_test.py')],check=True,timeout=90)
    with tempfile.TemporaryDirectory(prefix='aes-legacy-test-') as tmp:
        with socket.socket() as probe: probe.bind(('127.0.0.1',0)); port=probe.getsockname()[1]
        with (Path(tmp)/'server.log').open('w') as log:
            server=subprocess.Popen(['java','-cp',cp,'server.MockServer',str(port)],cwd=tmp,stdout=log,stderr=log)
            try:
                for _ in range(100):
                    with socket.socket() as s:
                        if s.connect_ex(('127.0.0.1',port))==0: break
                    if server.poll() is not None: raise RuntimeError('MockServer exited')
                    time.sleep(.05)
                samples=list((ROOT/'data/mau').glob('*'))
                subprocess.run(['java',f'-Daes.test.port={port}','-cp',cp,'E2E',*map(str,samples)],cwd=tmp,check=True,timeout=30)
                for f in samples:
                    received=list((Path(tmp)/'server_files').glob('*_'+f.name))
                    assert len(received)==1 and hashlib.sha256(f.read_bytes()).digest()==hashlib.sha256(received[0].read_bytes()).digest()
                subprocess.run(['java',f'-Daes.test.port={port}','-cp',cp,'E2E2',str(server.pid)],cwd=tmp,check=True,timeout=15)
            finally:
                if server.poll() is None: server.terminate()
                server.wait(timeout=5)
    print('ALL AUTOMATED NETWORK TESTS OK')
if __name__=='__main__': run()
