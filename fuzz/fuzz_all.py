import glob
import os.path
import subprocess
import sys
import time

def main():
    processes = {}
    for target_dir in glob.glob('fuzz/*/'):
      process = subprocess.Popen(
          "python {0} {1} --exact-artifact-path={2}".format(
              os.path.join(target_dir, 'fuzz.py'),
              os.path.join(target_dir, 'seeds'),
              os.path.join(target_dir, 'crash')),
          shell=True)
      processes[target_dir] = process

    try:
        while True:
            for target_dir, process in processes.items():
                process.poll()

                if process.returncode is not None:
                    raise Exception("Fuzzer {} crashed".format(target_dir))
            time.sleep(1)
    finally:
        print('Killing remaining fuzzers...')
        for process in processes.values():
            process.kill()
        print('Remaining fuzzers killed. Waiting for them to end...')
        for target_dir, process in processes.items():
            print('Waiting for fuzzer', target_dir, '...')
            process.wait()


if __name__ == '__main__':
    main()
