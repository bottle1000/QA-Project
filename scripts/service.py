#!/usr/bin/env python3
import os, pathlib, signal, socket, subprocess, sys, time, urllib.request
ROOT=pathlib.Path(__file__).resolve().parents[1]
os.chdir(ROOT)
RUN=ROOT/'.run'; RUN.mkdir(exist_ok=True)
PID=RUN/'server.pid'
JAR=ROOT/'target/qa-practice-0.1.0.jar'

def alive():
 if not PID.exists(): return False
 try:
  pid=int(PID.read_text())
  cmd=subprocess.check_output(['ps','-p',str(pid),'-o','command='],text=True).strip()
  return str(JAR) in cmd
 except (ValueError,subprocess.CalledProcessError): return False

def port_busy():
 with socket.socket() as s: return s.connect_ex(('127.0.0.1',8087))==0

def start():
 if alive(): print('이미 실행 중: http://127.0.0.1:8087');return
 if port_busy(): sys.exit('8087 포트가 사용 중입니다. 기존 프로그램을 확인하세요.')
 subprocess.run([str(ROOT/'mvnw'),'-q','-DskipTests','package'],check=True)
 java=subprocess.check_output(['/usr/libexec/java_home','-v','21'],text=True).strip()+'/bin/java'
 (ROOT/'data').mkdir(exist_ok=True)
 with (RUN/'server.log').open('w') as log:
  process=subprocess.Popen([java,'-Duser.timezone=Asia/Seoul','-jar',str(JAR)],cwd=ROOT,stdin=subprocess.DEVNULL,stdout=log,stderr=log,start_new_session=True)
 PID.write_text(str(process.pid))
 for _ in range(60):
  if process.poll() is not None: sys.exit('실행 실패: .run/server.log를 확인하세요.')
  try:
   with urllib.request.urlopen('http://127.0.0.1:8087/posts',timeout=1) as response:
    if response.status==200: print('실행 완료: http://127.0.0.1:8087 · 빌드 0.1.0');return
  except Exception: time.sleep(1)
 process.terminate();sys.exit('시작 시간 초과: .run/server.log를 확인하세요.')

def stop():
 if not alive(): print('실행 중인 프로젝트 서버가 없습니다.');PID.unlink(missing_ok=True);return
 pid=int(PID.read_text());os.kill(pid,signal.SIGTERM)
 for _ in range(30):
  if not alive(): PID.unlink(missing_ok=True);print('서버 종료 완료');return
  time.sleep(1)
 sys.exit('종료 대기 시간이 초과되었습니다. 초기화를 진행하지 않았습니다.')

def reset():
 if alive() or port_busy(): sys.exit('초기화 전 ./stop.sh 로 서버를 종료하세요.')
 data=ROOT/'data'
 if data.is_symlink(): sys.exit('data 폴더가 심볼릭 링크이므로 초기화를 중단합니다.')
 for name in ('qa.mv.db','qa.trace.db','qa.lock.db','qa.temp.db'):
  f=data/name
  if f.is_symlink(): sys.exit('DB 경로가 심볼릭 링크이므로 초기화를 중단합니다.')
 for name in ('qa.mv.db','qa.trace.db','qa.lock.db','qa.temp.db'): (data/name).unlink(missing_ok=True)
 print('이 프로젝트의 테스트 데이터 초기화 완료. ./start.sh 실행 시 기본 데이터를 생성합니다.')

if __name__=='__main__':
 action=sys.argv[1] if len(sys.argv)>1 else ''
 if action not in ('start','stop','reset','status'): sys.exit('start | stop | reset | status')
 lock=RUN/'operation.lock'
 try: lock.mkdir()
 except FileExistsError: sys.exit('다른 관리 작업이 실행 중입니다. 완료 후 다시 시도하세요.')
 try:
  if action=='status': print('실행 중: http://127.0.0.1:8087' if alive() else '종료 상태')
  else: {'start':start,'stop':stop,'reset':reset}[action]()
 finally: lock.rmdir()
