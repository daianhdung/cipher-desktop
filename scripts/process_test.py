#!/usr/bin/env python3
"""One packaged Server and Alice/Bob/Charlie in three independent JVM processes."""
from pathlib import Path
import base64, os, socket, subprocess, tempfile, threading, time
from build import ROOT

def b64(text): return base64.b64encode(str(text).encode()).decode()
class Peer:
    def __init__(self, port, name, directory):
        cp=os.pathsep.join(map(str,[ROOT/'build/classes',ROOT/'build/test-v2',ROOT/'lib/*']))
        self.p=subprocess.Popen(['java','-Xmx96m','-cp',cp,'PeerProcess',str(port),name,str(directory)],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,bufsize=1)
        self.events=[]
        def read():
            for line in self.p.stdout:
                row=line.rstrip('\n').split('\t')
                if len(row)==6:
                    self.events.append(dict(type=row[0],id=row[1],sender=base64.b64decode(row[2]).decode(),to=base64.b64decode(row[3]).decode(),text=base64.b64decode(row[4]).decode(),extra=base64.b64decode(row[5]).decode()))
        threading.Thread(target=read,daemon=True).start()
        self.wait(lambda e:e['type']=='CONNECTED')
    def send(self, line): self.p.stdin.write(line+'\n');self.p.stdin.flush()
    def wait(self, predicate):
        until=time.monotonic()+15
        while time.monotonic()<until:
            for event in self.events:
                if predicate(event):return event
            if self.p.poll() is not None:raise AssertionError(self.p.stderr.read())
            time.sleep(.01)
        raise AssertionError(self.events)
    def close(self):
        if self.p.poll() is None:
            try:self.send('EXIT');self.p.wait(timeout=5)
            except Exception:self.p.kill();self.p.wait()

def run():
    peers=[]
    with tempfile.TemporaryDirectory(prefix='chat-processes-') as temp:
        directory=Path(temp)
        with socket.socket() as s:s.bind(('127.0.0.1',0));port=s.getsockname()[1]
        with (directory/'server.log').open('w') as log:
            server=subprocess.Popen(['java','-jar',str(ROOT/'dist/chat-server.jar'),'--host','127.0.0.1','--port',str(port),'--data',str(directory/'db')],stdout=log,stderr=log)
            try:
                for _ in range(150):
                    if 'RUNNING' in (directory/'server.log').read_text():break
                    if server.poll() is not None:raise AssertionError((directory/'server.log').read_text())
                    time.sleep(.02)
                for name in ['Alice','Bob','Charlie']:peers.append(Peer(port,name,directory/name))
                alice,bob,charlie=peers
                alice.wait(lambda e:e['type']=='USERS' and 'Charlie' in e['text'])
                alice.send('TEXT\tBob\t'+b64('private across JVMs'))
                bob.wait(lambda e:e['type']=='CHAT' and e['text']=='private across JVMs')
                bob.send('TEXT\tAlice\t'+b64('reply across JVMs'))
                alice.wait(lambda e:e['type']=='CHAT' and e['text']=='reply across JVMs')
                for sample in (ROOT/'data/mau').glob('*'):
                    alice.send('FILE\tBob\t'+b64(sample))
                    event=bob.wait(lambda e:e['type']=='FILE_RECEIVED' and e['text']==sample.name)
                    assert sample.read_bytes()==Path(event['extra']).read_bytes()
                assert not any(e['type'] in ['CHAT','FILE_RECEIVED'] for e in charlie.events)
                print('PASS packaged Server + 3 independent JVM Clients, private reply, image/audio/video hashes',flush=True)
            finally:
                for peer in peers:peer.close()
                server.terminate();server.wait(timeout=10)
if __name__=='__main__':run()
