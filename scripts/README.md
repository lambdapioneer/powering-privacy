# Scripts

This folder contains for automating parts of the set-up as well as one for uploading scenarios and one for the analysis of the micro studies.


## Setup and build

Ensure that a version of Python 3 is installed.

First setup the virtual environment and install all required dependencies:

```
$ python3 -m venv venv
$ source venv/bin/activate
(env)$ pip install -r requirements.txt
```


## Run: import sample traces

You can import the sample traces into the `measurements` folder as follows.
This step is optional, but you might want to do it if you do not have access to the hardware kit.

```
$ ./import-sample-traces.sh
```


## Run: upload scenarios

For running micro studies, the scenarios need to be uploaded to the [web service](../webservice/README.md).
This can be done as follows:

```
$ ./upload-scenarios.sh
```

Change the variables at the top of the script to match your setup.


## Run: analysis scenario

After running a micro studies and uploading the logs from Android, the files need to be downloaded, compared to the local recordings, and broken into the individual sections for the operations.
This is done using the `analyze.py` script.
It takes the path to the `.scenario` file as its mandatory argument and derives all other paths from that.

```
$ source venv/bin/activate
(env)$ python3 analyze.py ../scenarios/myexperiment.scenario
```

When just using the sample traces, the program can be tested without the webservice like so:

```
$ python3 analyze.py ../scenarios/crypto_asymmetric.scenario --no-download
[ ] Scenario file: ../scenarios/crypto_asymmetric.scenario
[ ] Raw file:      ../measurements/crypto_asymmetric_20220323_1659_raw.csv
[ ] Android file:  ../measurements/crypto_asymmetric_20220323_1659_android.csv
[ ] Output file:   ../measurements/crypto_asymmetric_20220323_1659_sections.csv
[ ] Info file:     ../measurements/crypto_asymmetric_20220323_1659_info.json
[+] All files available
[+] All input files parsed and verified basic consistency
[ ] Found 8 raising edges and 8 falling edges
[ ] mean_raising_edges_time=11.35s
[ ] android_sections_start_time=7.63s
[+] Android clock is offset by -3.7261s (stdev=0.0002).
[+] Extracted 268 sections.
[+] Saved CSV file. All happy :)
```
