"""Capture real Compose screens on a fresh CI emulator, without accounts or secrets."""
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

OUTPUT = Path('ui-preview')
OUTPUT.mkdir(exist_ok=True)

def adb(*args):
    return subprocess.check_output(['adb', *args], timeout=30)

def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/planly-ui.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/planly-ui.xml'))

def tap(label):
    for attempt in range(4):
        nodes = [n for n in tree().iter('node') if n.get('text') == label or n.get('content-desc') == label]
        if nodes:
            node = max(nodes, key=lambda n: int(re.findall(r'\d+', n.get('bounds'))[1]))
            left, top, right, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
            adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))
            time.sleep(1)
            return
        adb('shell', 'input', 'swipe', '500', '1700', '500', '750', '350')
        time.sleep(.5)
    raise RuntimeError(f'Cannot find visible control: {label}')

def capture(name):
    if not adb('shell', 'pidof', 'com.planly.app').strip():
        raise RuntimeError('PLANLY stopped unexpectedly')
    OUTPUT.joinpath(name + '.png').write_bytes(adb('exec-out', 'screencap', '-p'))
    OUTPUT.joinpath(name + '.xml').write_bytes(ET.tostring(tree(), encoding='utf-8'))

adb('install', '-r', 'preview-apk/app-debug.apk')
adb('shell', 'am', 'start', '-W', '-n', 'com.planly.app/.MainActivity')
time.sleep(2)
capture('01-today-light')
tap('Tasks')
capture('02-tasks-light')
tap('Calendar')
capture('03-calendar-light')
tap('Today')
tap('Gentle focus')
capture('04-focus-light')
tap('Today')
tap('AI planner')
capture('05-ai-light')
tap('Settings')
capture('06-settings-light')
tap('Dark')
tap('Today')
capture('07-today-dark')
tap('Tasks')
capture('08-tasks-dark')
OUTPUT.joinpath('startup-log.txt').write_bytes(adb('logcat', '-d', '-s', 'AndroidRuntime:E'))
print('Captured eight real Android screens in light and dark modes.')
