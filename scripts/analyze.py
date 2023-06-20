import argparse
import bisect
import json
import io
import os
import requests
import statistics
import utils
import zipfile


_WEBSERVICE_HOST = "localhost:5000"
_ANDROID_LOGS_URL = f"http://{_WEBSERVICE_HOST}/energy/android_logs.zip"

# Uncomment to enable authentication
_ANDROID_LOGS_USER = None
_ANDROID_LOGS_PASSWORD = None
# _ANDROID_LOGS_USER = "user"
# _ANDROID_LOGS_PASSWORD = "uyaeNiyaighiu4HaingohShee2ie9ahs"  # intentionally hard-coded

_SYNC_BLOCKS_EXPECTED = 1  # one at the start
_SYNC_EDGES_PER_BLOCK = 8  # both 8 raising and 8 falling edges
_SYNC_TOTAL_EDGES = _SYNC_BLOCKS_EXPECTED * _SYNC_EDGES_PER_BLOCK


def download_android_logs(output_dir=utils.DIR_MEASUREMENTS):
    print(f"[ ] Downloading from {_ANDROID_LOGS_URL}")
    if _ANDROID_LOGS_USER and _ANDROID_LOGS_PASSWORD:
        r = requests.get(
            _ANDROID_LOGS_URL,
            auth=(_ANDROID_LOGS_USER, _ANDROID_LOGS_PASSWORD),
            stream=True
        )
    else:
        r = requests.get(
            _ANDROID_LOGS_URL,
            stream=True
        )
    r.raise_for_status()

    z = zipfile.ZipFile(io.BytesIO(r.content))
    new_files = 0
    for zipped_file in z.infolist():
        output_path = os.path.join(output_dir, zipped_file.filename)
        if os.path.exists(output_path):
            continue

        z.extract(zipped_file.filename, output_dir)
        new_files += 1

    print(f"[+] Downloaded {len(z.infolist())} files and saved {new_files} new files.")


def find_sync_edges(entries):
    assert entries[0].input_pin == 0
    assert entries[-1].input_pin == 0

    raising_edges, falling_edges = [], []

    prev = entries[0].input_pin
    for entry in entries[1:]:
        curr = entry.input_pin
        if curr == prev:
            continue
        elif curr == 1:
            raising_edges.append(entry.time)
        elif curr == 0:
            falling_edges.append(entry.time)
        prev = curr

    return raising_edges, falling_edges


def get_android_to_raw_clock_conversion(edges_raising, android_entries):
    """
    Returns a function that converts time from ANDROID to RAW. It takes into account clock OFFSET only.
    """
    raising_edges = edges_raising[:_SYNC_EDGES_PER_BLOCK]
    mean_raising_edges_time = statistics.mean(raising_edges)
    print(f"[ ] {mean_raising_edges_time=:.2f}s")

    sync_operations = [x for x in android_entries if x.operation == "sync"]

    android_sections =[x.ts for x in sync_operations[:_SYNC_EDGES_PER_BLOCK]]
    android_sections_start_time = statistics.mean(android_sections)
    print(f"[ ] {android_sections_start_time=:.2f}s")

    deltas = [android-raw for raw, android in zip(raising_edges, android_sections)]
    delta_offset = statistics.mean(deltas)
    delta_std = statistics.stdev(deltas)

    print(f"[+] Android clock is offset by {delta_offset:.4f}s (stdev={delta_std:.4f}).")
    return lambda android_time: android_time - delta_offset


def get_interval(raw_entries, raw_times, time_start_incl, time_end_excl):
    start_idx = bisect.bisect_left(raw_times, time_start_incl)
    end_idx = bisect.bisect_left(raw_times, time_end_excl)
    return raw_entries[start_idx:end_idx]


if __name__ == "__main__":
    parser = argparse.ArgumentParser("analyse.py")
    parser.add_argument("scenario")
    parser.add_argument("--raw-file", help="If not given, the most recent one is chosen")
    parser.add_argument("--android-log", help="If not given, the most recent one is chosen")
    parser.add_argument("--no-download", action="store_const", const=True, default=False)
    args = parser.parse_args()

    scenario_file = args.scenario
    raw_file = args.raw_file
    android_log_file = args.android_log

    # Retrieve any files from server
    if not args.no_download:
        download_android_logs()

    # Get files ready (and optionally search if none provided)
    print(f"[ ] Scenario file: {scenario_file}")
    basename = utils.get_basename_from_scenario_path(scenario_file)

    if not raw_file:
        raw_file = utils.get_most_recent_filename(utils.DIR_MEASUREMENTS, basename, "raw.csv")
    print(f"[ ] Raw file:      {raw_file}")

    if not raw_file:
        raise RuntimeError("Raw file not found")

    if not android_log_file:
        android_log_file = utils.get_most_recent_filename(
            utils.DIR_MEASUREMENTS, basename, "android.csv")
    print(f"[ ] Android file:  {android_log_file}")

    if not android_log_file:
        raise RuntimeError("Android file not found")

    sections_file = raw_file.replace("_raw.csv", "_sections.csv")
    print(f"[ ] Output file:   {sections_file}")

    info_file = raw_file.replace("_raw.csv", "_info.json")
    print(f"[ ] Info file:     {info_file}")

    print("[+] All files available")

    # Parse input files
    scenario_operations = utils.read_scenario(scenario_file)
    raw_entries = utils.read_entries_from_csv(raw_file)
    android_entries = utils.read_android_log(android_log_file)

    if len(scenario_operations) == 0:
        raise RuntimeError("Scenario file empty")
    if len(raw_entries) == 0:
        raise RuntimeError("Raw file empty")
    if len(android_entries) == 0:
        raise RuntimeError("Android file empty")
    if len(scenario_operations) + _SYNC_TOTAL_EDGES != len(android_entries):
        raise RuntimeError(f"Scenario file operation count {len(scenario_operations)}" +
                           f" must {_SYNC_TOTAL_EDGES} fewer than Android log entries {len(android_entries)}")

    print("[+] All input files parsed and verified basic consistency")

    # Find edges and use them for clock-synchronisation
    edges_raising, edges_falling = find_sync_edges(raw_entries)
    print(f"[ ] Found {len(edges_raising)} raising edges and {len(edges_falling)} falling edges")

    if len(edges_raising) != _SYNC_TOTAL_EDGES or len(edges_falling) != _SYNC_TOTAL_EDGES:
        print(" *** WARNING ***")
        print("[!] Number of edges do not match expected pattern")
        print(f"[!] Got {len(edges_raising)} raising and { len(edges_falling)}, but expected {_SYNC_TOTAL_EDGES}")
        print(" *** WARNING ***")

    clock_conversion_android_to_raw = get_android_to_raw_clock_conversion(
        edges_raising,
        android_entries
    )
    
    # Save metadata of this run
    with open(info_file, "w") as f:
        info = {
            "clock_offset": clock_conversion_android_to_raw(0),
        }
        json.dump(info, f)
    

    # Extract sections based on Android logs and converted to raw times
    sections = []
    raw_times = [x.time for x in raw_entries]
    for entry in android_entries:
        rs = clock_conversion_android_to_raw(entry.ts)
        re = clock_conversion_android_to_raw(entry.te)

        data = get_interval(raw_entries, raw_times, rs, re)
        section = utils.Section(data, entry.operation, android_extras=entry.debug)
        sections.append(section)

    print(f"[+] Extracted {len(sections)} sections.")

    # CSV output
    with open(sections_file, 'w') as f:
        f.write("start,end,name,power,energy,debug\n")
        for it in sections:
            if it.is_empty():
                f.write(
                    "%.4f,%.4f,%s,%.4f,%.4f,%s\n" % (
                        0.0, 0.0, it.name, 0.0, 0.0,""
                    ))
            else:
                f.write(
                    "%.4f,%.4f,%s,%.4f,%.4f,%s\n" % (
                        it.start_time(), it.end_time(), it.name, it.power_avg(), it.energy(), it.android_extras_for_csv()
                    ))
    print("[+] Saved CSV file. All happy :)")
