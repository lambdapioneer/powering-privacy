from collections import namedtuple
from datetime import datetime

import os

#
# Scenarios
#

Operation = namedtuple('Operation', ['name', 'op_name', 'params'])


def read_scenario(path):
    result = []
    with open(path, 'r') as f:
        for line in f.readlines():
            if line.startswith('#') or len(line.strip()) == 0:
                continue

            parts = line.strip().split(';')
            if len(parts) == 4:
                # legacy for previous format
                iterations, name, op_name, params = parts
            elif len(parts) == 5:
                iterations, pause, name, op_name, params = parts
            else:
                raise ValueError(f"cannot split line: {line}")

            params = {
                x[0].strip(): x[1].strip()
                for x in map(lambda kv: kv.split('='), params.split(','))
            } if len(params) > 0 else {}
            for _ in range(int(iterations)):
                result.append(Operation(name.strip(), op_name.strip(), params))

    return result


#
# Android Logs
#

AndroidEntry = namedtuple('AndroidEntry', ['scenario', 'operation', 'ts', 'te', 'debug'])


def read_android_log(path, scenario_name=None):
    result = []
    with open(path, 'r') as csv:
        csv.readline()  # skip header

        for line in csv.readlines():
            data = line.split(',')
            entry = AndroidEntry(
                scenario=data[0],
                operation=data[1],
                ts=float(data[2]),
                te=float(data[3]),
                debug=data[4].strip(),
            )
            result.append(entry)

            if not scenario_name:
                scenario_name = entry.scenario
            elif entry.scenario != scenario_name:
                raise ValueError(f"{entry.scenario=} != {scenario_name=}")

    return result


#
# Analysis and CSV
#


class Section():
    def __init__(self, entries, name=None, android_extras=""):
        self.entries = entries
        self.name = name if name else "unknown"
        self.android_extras = android_extras

    def duration(self):
        return self.end_time() - self.start_time()

    def start_time(self):
        return self.entries[0].time

    def end_time(self):
        return self.entries[-1].time

    def energy(self):
        return self.power_avg() * self.duration()

    def power_avg(self):
        return sum(map(lambda x: x.power, self.entries)) / len(self.entries)

    def is_empty(self):
        return len(self.entries) == 0

    def android_extras_for_csv(self):
        """Returns extras from the AndroidLog in as a CSV safe string"""
        return self.android_extras


Entry = namedtuple('Entry', ['time', 'power', 'input_pin'])


def write_entries_to_csv(path, entries):
    with open(path, 'w') as csv:
        csv.write('time_s,power_mw,input_pin\n')
        csv.writelines(map(lambda x: "%.6f,%d,%d\n" % x, entries))


def read_entries_from_csv(path):
    result = []
    with open(path, 'r') as csv:
        csv.readline()  # skip header

        for line in csv.readlines():
            data = line.split(',')
            result.append(Entry(
                time=float(data[0]),
                power=int(data[1]),
                input_pin=int(data[2])
            ))
    return result


#
# Filenames
#


DIR_SCENARIOS = "../scenarios"
DIR_MEASUREMENTS = "../measurements"


def get_basename_from_scenario_path(scenario_path):
    basename = os.path.basename(scenario_path)
    basename = basename.replace(".scenario", "")
    return basename


def get_datetime_stamp(now=None):
    if not now:
        now = datetime.now()
    return now.strftime("%Y%m%d_%H%M")


def get_raw_filename(basename, now=None):
    datetime_stamp = get_datetime_stamp(now)
    return os.path.join(DIR_MEASUREMENTS, f"{basename}_{datetime_stamp}.raw")


def get_csv_filename(basename, now=None):
    datetime_stamp = get_datetime_stamp(now)
    return os.path.join(DIR_MEASUREMENTS, f"{basename}_{datetime_stamp}_raw.csv")


def get_sections_filename(basename, now=None):
    datetime_stamp = get_datetime_stamp(now)
    return os.path.join(DIR_MEASUREMENTS, f"{basename}_{datetime_stamp}_sections.csv")


def get_most_recent_filename(folder, prefix, suffix):
    best_candidate = None
    files = sorted(os.listdir(folder))
    for file in files:
        if not file.startswith(prefix + "_20") or not file.endswith(suffix):
            continue
        best_candidate = os.path.join(folder, file)
    return best_candidate
