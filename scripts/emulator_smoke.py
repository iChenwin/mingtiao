#!/usr/bin/env python3
"""Run only on a disposable emulator. Never changes a physical phone."""
import argparse, json, subprocess, time, xml.sax.saxutils as xml
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('--adb', required=True);p.add_argument('--serial', required=True)
a=p.parse_args()
assert a.serial.startswith('emulator-'), 'Only disposable emulator serials are supported'
def adb(*args, stdin=None):
    return subprocess.run([a.adb,'-s',a.serial,*args],input=stdin,text=True,check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE).stdout
for apk in ['app/build/outputs/apk/debug/app-debug.apk','fixture/build/outputs/apk/debug/fixture-debug.apk']:
    adb('install','-r',apk)
adb('shell','am','start','-n','org.mingtiao.app/.MainActivity')
time.sleep(1)
# Configure only our debug APK in the disposable test emulator.
def configure(running, preview, allow, rules=None):
    adb('shell','am','force-stop','org.mingtiao.app')
    doc='<map><boolean name="consent" value="true"/><boolean name="running" value="%s"/><boolean name="preview" value="%s"/><set name="apps">%s</set><string name="rules">%s</string></map>' % (str(running).lower(),str(preview).lower(),'<string>org.mingtiao.fixture</string>' if allow else '',xml.escape(json.dumps({'version':1,'rules':rules or []},ensure_ascii=False)))
    adb('shell','run-as','org.mingtiao.app','mkdir','-p','shared_prefs')
    adb('shell','run-as org.mingtiao.app sh -c "cat > shared_prefs/local.xml"',stdin=doc)
    adb('shell','settings','put','secure','enabled_accessibility_services','org.mingtiao.app/.SkipService')
    adb('shell','settings','put','secure','accessibility_enabled','1')
    adb('shell','am','start','-n','org.mingtiao.app/.MainActivity')
    time.sleep(1)
def scenario(name, expected, label='Skip ad'):
    adb('shell','am','force-stop','org.mingtiao.fixture')
    adb('shell','input','keyevent','3')
    adb('shell','am','start','-n','org.mingtiao.fixture/.FixtureActivity','--es','label',label)
    time.sleep(1.6)
    adb('shell','uiautomator','dump','/sdcard/mingtiao-test.xml')
    view=adb('shell','cat','/sdcard/mingtiao-test.xml')
    actual='SKIPPED' in view
    assert actual==expected, f'{name}: expected clicked={expected}, got {actual}'
    print('PASS:',name,flush=True)
configure(False,False,True);scenario('paused service never clicks',False)
configure(True,False,False);scenario('unapproved package never clicks',False)
configure(True,True,True);scenario('preview never clicks',False)
configure(True,False,True);scenario('approved package skips ad',True)
scenario('non-ad label is not clicked',False,'跳过付款验证')
configure(True,False,True,[{'name':'fixture popup','package':'org.mingtiao.fixture','trigger':'text:这是广告','target':'text:关闭广告','action':'click','popup':True}])
scenario('explicit popup rule clicks only matching target',True,'关闭广告')
adb('shell','am','start','-n','org.mingtiao.app/.MainActivity')
print('All 6 Android emulator smoke scenarios passed. Not a substitute for OriginOS testing.')
