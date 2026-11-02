#!/usr/bin/env python3
"""Compile production Java against SDK, then run tests with host-only fixtures.
Never invokes Gradle packaging, ADB, Android runtime, JNI, USB, or sockets.
Requires cached JUnit4/Hamcrest and a local Android SDK; no downloads.
"""
import argparse,json,os,pathlib,shutil,subprocess,sys,tempfile,traceback

def main():
 parser=argparse.ArgumentParser();parser.add_argument('--results',type=pathlib.Path);args=parser.parse_args()
 repo=pathlib.Path(__file__).resolve().parents[1];tool=repo/'tools/offline-permission-tests'
 results=args.results or pathlib.Path(tempfile.mkdtemp(prefix='rtl-permission-tests-'));results.mkdir(parents=True,exist_ok=True)
 user_home=pathlib.Path.home();flags=getattr(subprocess,'CREATE_NO_WINDOW',0)
 java_home=os.environ.get('JAVA_HOME');javac=pathlib.Path(java_home)/'bin/javac.exe' if java_home and os.name=='nt' else None
 if not javac or not javac.exists():
  found=shutil.which('javac');javac=pathlib.Path(found) if found else None
 if not javac or not javac.exists():
  candidates=sorted(pathlib.Path('C:/Program Files/Eclipse Adoptium').glob('*/bin/javac.exe'));javac=candidates[-1] if candidates else None
 if not javac or not javac.exists():raise RuntimeError('Local JDK required')
 java=javac.with_name('java.exe' if os.name=='nt' else 'java')
 sdk=pathlib.Path(os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT',str(user_home/'.android-sdk'))))
 candidates=sorted((sdk/'platforms').glob('*/android.jar'));preferred=sdk/'platforms/android-37.0/android.jar'
 android=preferred if preferred.exists() else (candidates[-1] if candidates else None)
 if android is None:raise RuntimeError('Local Android SDK platform required')
 cache=user_home/'.gradle/caches/modules-2/files-2.1'
 def jar(env,folder):
  override=os.environ.get(env)
  if override and pathlib.Path(override).is_file():return pathlib.Path(override)
  found=sorted((cache/folder).glob('*/*.jar'))
  if not found:raise RuntimeError('Cached dependency required: '+folder+' (or set '+env+')')
  return found[0]
 junit=jar('JUNIT_JAR','junit/junit/4.13.2');hamcrest=jar('HAMCREST_JAR','org.hamcrest/hamcrest-core/1.3')
 production=[
  'sdrdrivertools/src/main/java/com/sdrtouch/tools/AsyncFuture.java',
  'sdrdrivertools/src/main/java/com/sdrtouch/tools/CompletedFuture.java',
  'sdrdrivertools/src/main/java/com/sdrtouch/tools/UsbPermissionObtainer.java',
  'sdrdrivertools/src/main/java/com/sdrtouch/tools/Check.java',
  'sdrdrivertools/src/main/java/com/sdrtouch/tools/ArgumentParser.java',
  'sdrdrivertools/src/main/java/com/sdrtouch/core/SdrTcpArguments.java',
  'sdrdrivertools/src/main/java/com/sdrtouch/core/UsedByJni.java',
  'sdrdrivertools/src/main/java/com/sdrtouch/core/devices/SdrDevice.java',
  'rtlsdr/src/main/java/com/sdrtouch/rtlsdr/driver/RtlSdrDevice.java']
 sources=[repo/p for p in production];host=results/'host-classes';surface=results/'sdk-classes';host.mkdir(exist_ok=True);surface.mkdir(exist_ok=True)
 def command(name,cmd,timeout=60):
  p=subprocess.run([str(x) for x in cmd],cwd=repo,capture_output=True,text=True,timeout=timeout,creationflags=flags,shell=False)
  (results/(name+'.log')).write_text(p.stdout+p.stderr,encoding='utf8')
  if p.returncode:raise RuntimeError(name+' failed (see '+str(results/(name+'.log'))+')\n'+(p.stdout+p.stderr)[-10000:])
  return p.stdout
 support=[tool/'stubs/com/sdrtouch/tools/Log.java',tool/'stubs/com/sdrtouch/core/exceptions/SdrException.java']
 command('android-sdk-compile',[javac,'--release','8','-cp',android,'-d',surface,*sources,*support])
 tests=[repo/'sdrdrivertools/src/test/java/com/sdrtouch/tools/AsyncFutureTest.java',*sorted((tool/'src').rglob('*.java'))]
 command('host-compile',[javac,'--release','8','-cp',os.pathsep.join(map(str,[junit,hamcrest])),'-d',host,*sources,*sorted((tool/'stubs').rglob('*.java')),*tests])
 output=command('host-tests',[java,'-cp',os.pathsep.join(map(str,[host,junit,hamcrest])),'OfflinePermissionTestRunner'],timeout=30)
 result=json.loads(output.strip().splitlines()[-1]);result.update({'androidSdkCompile':'PASS','androidJar':str(android),'offlineOnly':True,'productionClassesUnderTest':production,'fixtureBoundary':'Fake Android APIs and fake NativeSession for execution; unchanged app Log/SdrException stand-ins for SDK surface compile; no full APK build'})
 (results/'RESULTS.json').write_text(json.dumps(result,indent=2),encoding='utf8');print(json.dumps({'tests':result['tests'],'failures':result['failures'],'androidSdkCompile':'PASS','realUSBOrJNIExecution':False,'results':str(results)}))
 return 0
if __name__=='__main__':
 try:sys.exit(main())
 except Exception:print(traceback.format_exc());sys.exit(1)
