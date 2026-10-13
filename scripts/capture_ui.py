"""Capture real Compose screens on a fresh CI emulator, without accounts or secrets."""
from pathlib import Path
import re
import json
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
        nodes = [n for n in tree().iter('node') if (n.get('text', '').startswith(label) or n.get('content-desc') == label)
                 and len(re.findall(r'-?\d+', n.get('bounds', ''))) == 4
                 and int(re.findall(r'-?\d+', n.get('bounds'))[2]) > int(re.findall(r'-?\d+', n.get('bounds'))[0])
                 and int(re.findall(r'-?\d+', n.get('bounds'))[3]) > max(0, int(re.findall(r'-?\d+', n.get('bounds'))[1]))]
        if nodes:
            node = max(nodes, key=lambda n: int(re.findall(r'-?\d+', n.get('bounds'))[1]))
            left, top, right, bottom = map(int, re.findall(r'-?\d+', node.get('bounds')))
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

# Exercise the advanced features and an older task record on this disposable emulator.
def preferences():
    root = ET.fromstring(adb('shell', 'run-as', 'com.planly.app', 'cat', 'shared_prefs/planly.xml'))
    return {node.get('name'): (node.text if node.tag == 'string' else node.get('value')) for node in root}

def write_preferences(values, filename='planly.xml'):
    root = ET.Element('map')
    for name, value in values.items():
        if isinstance(value, bool):
            ET.SubElement(root, 'boolean', name=name, value=str(value).lower())
        elif name in ('tasks', 'sessions', 'theme', 'focusTask'):
            ET.SubElement(root, 'string', name=name).text = value
        else:
            ET.SubElement(root, 'long', name=name, value=str(value))
    fixture = OUTPUT / 'fixture.xml'
    fixture.write_bytes(ET.tostring(root, encoding='utf-8', xml_declaration=True))
    adb('push', str(fixture), '/data/local/tmp/planly-fixture.xml')
    adb('shell', 'run-as', 'com.planly.app', 'cp', '/data/local/tmp/planly-fixture.xml', 'shared_prefs/' + filename)
    fixture.unlink()

def launch():
    adb('shell', 'am', 'start', '-W', '-n', 'com.planly.app/.MainActivity')
    time.sleep(1)

def visible(label):
    return any(n.get('text', '').startswith(label) for n in tree().iter('node'))

now = int(time.time() * 1000)
legacy = {'id': 'legacy', 'title': 'Legacy task', 'project': 'Personal', 'dueAt': now - 3600000,
          'minutes': 15, 'priority': 'Normal', 'reminder': -1, 'done': False}
recurring = {'id': 'repeat', 'title': 'Daily review', 'project': 'Studies', 'dueAt': now + 3600000,
             'minutes': 25, 'priority': 'High', 'reminder': -1, 'repeat': 'Daily', 'done': False,
             'checklist': [{'id': 'step-1', 'title': 'Read chapter', 'done': False},
                           {'id': 'step-2', 'title': 'Summarize ideas', 'done': False}]}
adb('shell', 'am', 'force-stop', 'com.planly.app')
write_preferences({'theme': 'Light', 'tasks': json.dumps([legacy, recurring])})
launch()
tap('Tasks')
tap('Daily review')
tap('Read chapter')
records = json.loads(preferences()['tasks'])
assert next(t for t in records if t['id'] == 'repeat')['checklist'][0]['done'], 'Checklist toggle did not persist'
capture('09-checklist-light')
tap('Mark complete')
tap('Mark incomplete')
tap('Mark complete')
records = json.loads(preferences()['tasks'])
generated = [t for t in records if t.get('generatedBy') == 'repeat']
assert len(generated) == 1, 'Recurring completion duplicated its next occurrence'
assert not any(i['done'] for i in generated[0]['checklist']), 'Next checklist was not reset'
assert generated[0]['dueAt'] > now, 'Next occurrence is not in the future'
assert next(t for t in records if t['id'] == 'legacy')['repeat'] == 'Off', 'Legacy task did not migrate safely'
adb('shell', 'input', 'keyevent', '4')
tap('Tasks')
tap('Overdue')
assert visible('Legacy task') and not visible('Daily review'), 'Overdue filter is incorrect'
tap('Active')
tap('Project: All projects')
tap('Studies')
tap('Search tasks, notes or checklist')
adb('shell', 'input', 'text', 'chapter')
adb('shell', 'input', 'keyevent', '4')
time.sleep(1)
assert visible('Daily review') and not visible('Legacy task'), 'Checklist search and project filter did not combine'
capture('10-filtered-tasks-light')
tap('Today')
tap('Gentle focus')
tap('15 min')
tap('Start session')
tap('Pause session')
saved = preferences()
assert int(saved['focusDuration']) == 900 and int(saved['focusRemaining']) > 0 and int(saved['focusEnd']) == 0
# Resume a persisted timer whose deadline elapsed while the process was stopped.
adb('shell', 'am', 'force-stop', 'com.planly.app')
saved['focusEnd'] = str(int(time.time() * 1000) - 1000)
write_preferences({k: saved[k] for k in ('tasks', 'theme', 'focusTask', 'focusDuration', 'focusRemaining', 'focusEnd')})
launch()
tap('Gentle focus')
time.sleep(1)
saved = preferences()
history = json.loads(saved['sessions'])
assert len(history) == 1 and history[0]['minutes'] == 15 and int(saved['focusEnd']) == 0, 'Expired focus session was not recorded exactly once'
capture('11-focus-history-light')
tap('Today')
tap('Open menu')
tap('Progress')
capture('12-progress-light')
adb('shell', 'am', 'force-stop', 'com.planly.app')
launch()
tap('Gentle focus')
assert len(json.loads(preferences()['sessions'])) == 1, 'Focus history duplicated after restart'
assert len(json.loads(preferences()['tasks'])) == 3, 'Tasks did not persist across restart'
OUTPUT.joinpath('feature-checks.txt').write_text('PASS: legacy migration, checklist persistence, recurring duplicate protection, overdue filter, checklist search, project filter, focus presets, pause, expired timer recovery, focus history and restart persistence.\n')
OUTPUT.joinpath('startup-log.txt').write_bytes(adb('logcat', '-d', '-s', 'AndroidRuntime:E'))
print('Advanced Android feature checks passed.')

# Background delivery checks use granted permissions on this disposable emulator.
tap('Open menu')
tap('App Health')
before = json.loads(preferences()['tasks'])
tap('Fix issues')
assert json.loads(preferences()['tasks']) == before, 'Fix issues changed saved tasks'
capture('13-app-health-light')
adb('shell', 'pm', 'grant', 'com.planly.app', 'android.permission.POST_NOTIFICATIONS')
adb('shell', 'appops', 'set', 'com.planly.app', 'SCHEDULE_EXACT_ALARM', 'allow')
adb('shell', 'settings', 'put', 'system', 'screen_off_timeout', '600000')
adb('shell', 'input', 'keyevent', '224')
adb('shell', 'wm', 'dismiss-keyguard')
saved = preferences()
now = int(time.time() * 1000)
records = json.loads(saved['tasks'])
records.append({'id': 'alarm-test', 'title': 'Task reminder smoke test', 'project': 'Personal',
                'dueAt': now + 25000, 'minutes': 15, 'priority': 'Normal', 'reminder': 0, 'done': False})
adb('shell', 'am', 'force-stop', 'com.planly.app')
write_preferences({'theme': 'Light', 'tasks': json.dumps(records), 'sessions': saved['sessions'],
                   'reminders': True, 'screenBreaks': True, 'quiet': False})
write_preferences({'nextAt': now + 20000}, 'planly_breaks.xml')
launch()
adb('shell', 'input', 'keyevent', '3')
time.sleep(32)
notifications = adb('shell', 'dumpsys', 'notification', '--noredact').decode(errors='replace')
assert 'Task reminder smoke test' in notifications, 'Task notification did not arrive in the background'
assert 'Time for a screen break' in notifications, 'Hourly screen-break notification did not arrive'
records = json.loads(preferences()['tasks'])
assert next(t for t in records if t['id'] == 'alarm-test')['notified'] > 0, 'Task delivery was not recorded'
break_root = ET.fromstring(adb('shell', 'run-as', 'com.planly.app', 'cat', 'shared_prefs/planly_breaks.xml'))
next_break = int(break_root.find("long[@name='nextAt']").get('value'))
assert next_break > int(time.time() * 1000) + 3000000, 'Screen break did not advance to the next hour'
launch()
tap('Open menu')
tap('App Health')
tap('Fix issues')
assert len(json.loads(preferences()['tasks'])) == 4, 'Reminder repair deleted task data'
tap('Send test reminder')
assert 'PLANLY reminder test' in adb('shell', 'dumpsys', 'notification', '--noredact').decode(errors='replace')
capture('14-app-health-ready')
tap('Settings')
tap('Hourly screen breaks')
assert preferences()['screenBreaks'] == 'false', 'Hourly reminder switch did not disable'
break_root = ET.fromstring(adb('shell', 'run-as', 'com.planly.app', 'cat', 'shared_prefs/planly_breaks.xml'))
assert break_root.find("long[@name='nextAt']") is None, 'Disabling screen breaks left a next reminder'
tap('Hourly screen breaks')
assert preferences()['screenBreaks'] == 'true', 'Hourly reminder switch did not re-enable'
capture('15-hourly-reminder-settings')
OUTPUT.joinpath('feature-checks.txt').write_text(OUTPUT.joinpath('feature-checks.txt').read_text() +
    'PASS: App Health repair preserves tasks, task notification arrives while app is backgrounded, screen-break notification arrives, next hourly reminder advances, test notification sends, hourly switch disables/cancels and re-enables.\n')
OUTPUT.joinpath('startup-log.txt').write_bytes(adb('logcat', '-d', '-s', 'AndroidRuntime:E'))
print('App Health and background reminder checks passed.')
