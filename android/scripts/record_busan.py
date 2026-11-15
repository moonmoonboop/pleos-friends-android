#!/usr/bin/env python3
"""Build, install, and record the five-scene Busan journey on a parked Pleos emulator.

No SMS is sent. Scenario data lives in ../scenarios/busan-roadtrip.json.
Only the demo APK exposes the fixture, the DEMO viewer strip (예빈/도현/서윤), and simulated arrival events.
"""
from __future__ import annotations
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import threading

ANDROID = Path(__file__).resolve().parents[1]
PACKAGE = "com.pleosfriends.demo"
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"

def run(args, **kwargs):
    return subprocess.run([str(a) for a in args], check=True, **kwargs)

class Recorder:
    """Rotate before Android's 180-second limit, then join without re-encoding."""
    def __init__(self, base, output):
        self.base=base;self.output=output;self.parts=[];self.event=threading.Event();self.process=None;self.pid=None
        existing=subprocess.run(base+["shell","pidof","screenrecord"],capture_output=True,text=True).stdout.strip()
        if existing: raise SystemExit("Another Android screen recording is running. Stop it before starting this recording.")
        self._start_segment()
        self.thread=threading.Thread(target=self._rotate,daemon=True);self.thread.start()
    def _start_segment(self):
        remote=f"/sdcard/Download/pleos-busan-demo-{len(self.parts)+1:03d}.mp4"
        subprocess.run(self.base+["shell","rm","-f",remote],check=True)
        self.process=subprocess.Popen(self.base+["shell","screenrecord","--bit-rate","6000000","--size","1920x1080","--time-limit","150",remote],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        time.sleep(.5)
        pids=subprocess.run(self.base+["shell","pidof","screenrecord"],capture_output=True,text=True).stdout.strip().split()
        self.pid=next((p for p in pids if p.isdigit()),None)
        if not self.pid: raise RuntimeError("Android screenrecord did not start.")
        self.parts.append(remote)
    def _stop_segment(self):
        if self.pid: subprocess.run(self.base+["shell","kill","-2",self.pid],capture_output=True)
        if self.process:
            try: self.process.wait(timeout=10)
            except subprocess.TimeoutExpired: self.process.terminate()
        self.pid=None
    def _rotate(self):
        while not self.event.wait(135):
            self._stop_segment()
            if not self.event.is_set(): self._start_segment()
    def finish(self):
        self.event.set();self.thread.join(timeout=15);self._stop_segment()
        local=[]
        for i,remote in enumerate(self.parts):
            part=self.output/f"part-{i+1:03d}.mp4";run(self.base+["pull",remote,part]);local.append(part)
        final=self.output/"PleosFriends-Busan-Demo.mp4"
        if len(local)==1: shutil.copy2(local[0],final)
        elif sys.platform=="darwin" and shutil.which("swift"):
            run(["swift",ANDROID/"scripts/join_recordings.swift",final,*local])
        elif shutil.which("ffmpeg"):
            listing=self.output/"video-parts.txt"
            listing.write_text("".join("file '"+str(p).replace("'","'\\''")+"'\n" for p in local))
            run(["ffmpeg","-y","-f","concat","-safe","0","-i",listing,"-c","copy",final])
        else: print("Install ffmpeg to join the saved part-*.mp4 files. All segments have been kept.")

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", help="adb serial when more than one device is connected")
    parser.add_argument("--avd", help="Start this AVD if no device is connected")
    parser.add_argument("--skip-build", action="store_true")
    parser.add_argument("--test-class", default="com.pleosfriends.BusanStoryTest#busanFiveSceneRecording", help="Optional alternate native test case or Class#method")
    parser.add_argument("--test-only", action="store_true", help="Run quickly without recording or scene holds")
    parser.add_argument("--output", type=Path, default=ANDROID.parent / "recordings")
    args = parser.parse_args()
    output = args.output.resolve(); output.mkdir(parents=True, exist_ok=True)
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or Path.home() / "Library/Android/sdk")
    if not sdk.exists(): sdk = Path.home() / "Android/Sdk"
    adb_path = sdk / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")
    adb = str(adb_path) if adb_path.exists() else shutil.which("adb")
    if not adb: raise SystemExit("Install Android SDK Platform Tools or set ANDROID_HOME.")
    env = os.environ.copy()
    if not args.skip_build:
        candidates = [Path(env["JAVA_HOME"])] if env.get("JAVA_HOME") else []
        candidates += list((Path.home() / "Library/Java/JavaVirtualMachines").glob("*/Contents/Home"))
        candidates += list(Path("/Library/Java/JavaVirtualMachines").glob("*/Contents/Home"))
        for home in candidates:
            java = home / "bin/java"
            if not java.exists(): continue
            version = subprocess.run([str(java),"-version"], capture_output=True,text=True)
            match = re.search(r'version "(\d+)',version.stderr)
            if match and 17 <= int(match[1]) <= 23:
                env["JAVA_HOME"] = str(home); break
        run(([str(ANDROID / "gradlew.bat")] if os.name == "nt" else ["bash",str(ANDROID / "gradlew")])+[":app:assembleDemoDebug",":app:assembleDemoDebugAndroidTest",":app:assembleConnectDebug","--console=plain"],cwd=ANDROID,env=env)
    devices = subprocess.check_output([adb,"devices"],text=True)
    online = [line.split()[0] for line in devices.splitlines()[1:] if line.endswith("\tdevice")]
    if not online:
        avd = args.avd or "PleosFriends_S17"
        emulator = sdk / "emulator" / ("emulator.exe" if os.name == "nt" else "emulator")
        available = subprocess.check_output([str(emulator),"-list-avds"],text=True).splitlines()
        if avd not in available: raise SystemExit("Start a Pleos Connect 2.1.0 emulator, or pass --avd NAME. Available: " + ", ".join(available))
        with (output / "emulator.log").open("w") as log:
            subprocess.Popen([str(emulator),"-avd",avd,"-no-snapshot","-no-boot-anim"],stdout=log,stderr=log,start_new_session=True)
        run([adb,"wait-for-device"])
        deadline=time.monotonic()+150
        while time.monotonic()<deadline:
            if subprocess.check_output([adb,"shell","getprop","sys.boot_completed"],text=True).strip()=="1": break
            time.sleep(1)
        else: raise SystemExit("Emulator did not finish booting. Check emulator.log.")
        online=[subprocess.check_output([adb,"get-serialno"],text=True).strip()]
    serial=args.serial or (online[0] if len(online)==1 else None)
    if not serial: raise SystemExit("Multiple devices are connected; choose --serial SERIAL.")
    base=[adb,"-s",serial]
    user=subprocess.check_output(base+["shell","am","get-current-user"],text=True).strip()
    restrictions=subprocess.check_output(base+["shell","dumpsys","-t","5","car_service","--services","CarUxRestrictionsManagerService"],text=True)
    if "TIMEOUT" in restrictions or "No such service" in restrictions or not restrictions.strip(): raise SystemExit("Pleos vehicle service is unavailable. Cold Boot the emulator and retry.")
    if "DO: false" not in restrictions: raise SystemExit("Park the Pleos vehicle/emulator before recording. The script does not override AAOS driving restrictions.")
    run(base+["install","--user",user,"-r",ANDROID/"app/build/outputs/apk/demo/debug/app-demo-debug.apk"])
    run(base+["install","--user",user,"-r",ANDROID/"app/build/outputs/apk/androidTest/demo/debug/app-demo-debug-androidTest.apk"])
    run(base+["shell","am","force-stop","--user",user,PACKAGE])
    # Export only captures made by this run, never leftovers from another test.
    previous=subprocess.run(base+["shell","run-as",PACKAGE,"--user",user,"ls","files/screenshots"],capture_output=True,text=True)
    for name in previous.stdout.splitlines():
        if re.fullmatch(r"[a-zA-Z0-9_-]+\.png",name):
            run(base+["shell","run-as",PACKAGE,"--user",user,"rm","files/screenshots/"+name])
    recorder=None
    if not args.test_only:
        recorder=Recorder(base,output)
    try:
        result=subprocess.run(base+["shell","am","instrument","--user",user,"-w","-r","-e","class",args.test_class,"-e","record",str(not args.test_only).lower(),RUNNER],capture_output=True,text=True,timeout=600)
        log=result.stdout+result.stderr
        (output/"busan-test.log").write_text(log)
        print(log)
    finally:
        if recorder:
            recorder.finish()
    # These are private to the installed debug app; no broad storage permission is required.
    listing=subprocess.run(base+["shell","run-as",PACKAGE,"--user",user,"ls","files/screenshots"],capture_output=True,text=True)
    for name in listing.stdout.splitlines():
        if re.fullmatch(r"[a-zA-Z0-9_-]+\.png",name):
            with (output/name).open("wb") as file:
                subprocess.run(base+["exec-out","run-as",PACKAGE,"--user",user,"cat","files/screenshots/"+name],stdout=file,check=True)
    if result.returncode or "FAILURES!!!" in log or "INSTRUMENTATION_FAILED" in log or not re.search(r"OK \(\d+ tests?\)", log):
        raise SystemExit("The scenario did not pass. See " + str(output/"busan-test.log"))
    print("Passed. Recording and screenshots: " + str(output))
    print("Android screenrecord creates a silent MP4. Demo voice audio is included in the app for live playback.")

if __name__ == "__main__":
    main()
