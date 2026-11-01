#!/usr/bin/env python3
"""Isolated JavaFX build/package/run. Does not compile or start the Swing/TCP project."""
from pathlib import Path
import os, re, shutil, subprocess, sys, zipfile, platform, json
ROOT=Path(__file__).resolve().parents[1]
PROJECT=ROOT.parent

def env():
    result=subprocess.run(['java','-XshowSettings:properties','-version'],capture_output=True,text=True,check=True)
    match=re.search(r'java.home = (.+)',result.stderr)
    value=os.environ.copy()
    if match:value['JAVA_HOME']=match.group(1).strip()
    return value

def build():
    maven=shutil.which('mvn') or shutil.which('mvn.cmd')
    if not maven:raise RuntimeError('Maven 3.9+ is required to build. Packaged demo only needs Java 21.')
    subprocess.run([maven,'-q','package'],cwd=PROJECT,env=env(),check=True)
    bundle=ROOT/'target/demo';lib=bundle/'lib';lib.mkdir(parents=True,exist_ok=True)
    for old in lib.glob('*.jar'):old.unlink()
    modules=[]
    for jar in (PROJECT/'target/lib').glob('*.jar'):
        with zipfile.ZipFile(jar) as z:
            if 'module-info.class' in z.namelist():shutil.copy2(jar,lib/jar.name);modules.append(jar)
    if len(modules)!=4:raise RuntimeError('Expected only JavaFX base/graphics/controls/fxml, got '+str(modules))
    shutil.copy2(PROJECT/'target/cipher-desktop-1.0.0.jar',bundle/'cipher-demo.jar')
    for file in ['README.md','ARCHITECTURE.md','COMPONENTS.md','VERIFICATION.md']: 
        if (ROOT/file).exists():shutil.copy2(ROOT/file,bundle/file)
    (bundle/'run-demo.sh').write_text('#!/bin/sh\nset -eu\nAPP_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)\nexec java --module-path "$APP_DIR/lib" --add-modules javafx.controls,javafx.fxml -jar "$APP_DIR/cipher-demo.jar" "$@"\n')
    (bundle/'run-demo.sh').chmod(0o755)
    (bundle/'run-demo.cmd').write_text('@echo off\r\njava --module-path "%~dp0lib" --add-modules javafx.controls,javafx.fxml -jar "%~dp0cipher-demo.jar" %*\r\n')
    shutil.copytree(ROOT/'licenses',bundle/'licenses',dirs_exist_ok=True)
    if (ROOT/'docs').exists():shutil.copytree(ROOT/'docs',bundle/'docs',dirs_exist_ok=True)
    (bundle/'platform.json').write_text(json.dumps({'java':21,'javafx':'21.0.13','system':platform.system(),'architecture':platform.machine(),'mode':'mock-only'},indent=2))
    shutil.make_archive(str(ROOT/'target'/('cipher-demo-'+platform.system().lower()+'-'+platform.machine())), 'zip', root_dir=bundle)
    print('BUILD OK: '+str(bundle),flush=True)
    return bundle

def command(bundle,main=None,args=()):
    base=['java','--module-path',str(bundle/'lib'),'--add-modules','javafx.controls,javafx.fxml']
    if main:base+=['-cp',os.pathsep.join(map(str,[bundle/'cipher-demo.jar',PROJECT/'target/test-classes'])),main]
    else:base+=['-jar',str(bundle/'cipher-demo.jar')]
    return base+list(args)

if __name__=='__main__':
    mode=sys.argv[1] if len(sys.argv)>1 else 'run'
    if mode not in ['build','run','verify']:raise SystemExit('Usage: demo.py [build|run|verify] [--monitor]')
    bundle=build()
    if mode=='run':raise SystemExit(subprocess.call(command(bundle,args=sys.argv[2:])))
    if mode=='verify':raise SystemExit(subprocess.call(command(bundle,'studio.cipher.PreviewVerification',[str(ROOT/'target/screenshots')]),cwd=ROOT))
