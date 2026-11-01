#!/usr/bin/env python3
"""Java 21 build with pinned, SHA-256 verified JDBC runtime dependencies."""
from pathlib import Path
import hashlib, shutil, subprocess, urllib.request
ROOT = Path(__file__).resolve().parents[1]
DEPS = {
    'sqlite-jdbc-3.46.1.0.jar': ('org/xerial/sqlite-jdbc/3.46.1.0/', '6dc7464e3803648d3ff18a7359bab6adf079fcd8495b18991f6f5edcb8ac6e3b'),
    'slf4j-api-2.0.13.jar': ('org/slf4j/slf4j-api/2.0.13/', 'e7c2a48e8515ba1f49fa637d57b4e2f590b3f5bd97407ac699c3aa5efb1204a9'),
    'slf4j-nop-2.0.13.jar': ('org/slf4j/slf4j-nop/2.0.13/', '8962a107b4a8bdf80b6c17e470cd3614ad3329643833ff5cf0c60c7dce9deaac'),
}
def build():
    lib=ROOT/'lib'; lib.mkdir(exist_ok=True)
    for name,(url,digest) in DEPS.items():
        path=lib/name
        if not path.exists():
            part=path.with_suffix('.download')
            with urllib.request.urlopen('https://repo.maven.apache.org/maven2/'+url+name, timeout=60) as response:
                part.write_bytes(response.read())
            if hashlib.sha256(part.read_bytes()).hexdigest()!=digest:
                part.unlink(); raise RuntimeError('Dependency checksum mismatch: '+name)
            part.replace(path)
        if hashlib.sha256(path.read_bytes()).hexdigest()!=digest: raise RuntimeError('Dependency checksum mismatch: '+name)
    classes=ROOT/'build/classes'; classes.mkdir(parents=True,exist_ok=True)
    subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',str(classes),*map(str,(ROOT/'src').rglob('*.java'))],check=True)
    dist=ROOT/'dist'; (dist/'lib').mkdir(parents=True,exist_ok=True)
    for name in DEPS: shutil.copy2(lib/name,dist/'lib'/name)
    shutil.copy2(lib/'THIRD_PARTY.md',dist/'THIRD_PARTY.md')
    shutil.copytree(lib/'licenses',dist/'licenses',dirs_exist_ok=True)
    for name,main in [('chat-server','chat.server.ServerMain'),('chat-client','client.ClientMain')]:
        manifest=ROOT/'build'/f'{name}.mf'
        manifest.write_text('Manifest-Version: 1.0\nMain-Class: '+main+'\nClass-Path: '+' '.join('lib/'+n for n in DEPS)+'\n\n')
        subprocess.run(['jar','--create','--file',str(dist/f'{name}.jar'),'--manifest',str(manifest),'-C',str(classes),'.'],check=True)
    shutil.copy2(dist/'chat-client.jar',dist/'DoAn_ChatAES.jar')
    print('BUILD OK: dist/chat-server.jar, dist/chat-client.jar (keep dist/lib beside jars)',flush=True)
if __name__=='__main__': build()
