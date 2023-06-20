# RSOXY

This is a fast Rust-based logger for the custom serial port protocol.
It reads from the connected Arduino and appends the data into the given file in a CSV format.
The data can be later read for analysis and during the recording one can `tail -f` that file or follow its updating content using the `livelogger`.


## Setup and build

Install Rust using [rustup](https://rustup.rs/).

For building the `serialport` dependency, the system must have the `libudev` installed.
On Ubuntu, you can do this as follows:

```
$ sudo apt install libudev-dev
```

Then build the project using Cargo:

```
$ cargo build --release
```


## Run

Once built, the binary can be run as follows:

```
$ ./target/release/rsoxy <FILE> [PORT]
```

where `<FILE>` is the path to the file to which the data will be written to and `[PORT]` is the optional path to the serial port device.

The (stderr) output should start with debug information similar to the following:

```
>> # Compiled on 'Apr 13 2021' at '16:13:53'
>> # mps:2897 (limit:2500)
>> # input pin: 14
>> start
```


## Docker

Alternatively, you can build and run the project using Docker. However, this tends to be a bit more quirky than building and running it directly on the host machine.

The following commands build the Docker image and run the container with the serial port device mounted and the measurements volume mounted as well.

```
$ docker build -t rsoxy .
$ docker run -it --rm --volume=measurements:/usr/src/rsoxy/measurements --device=/dev/ttyACM0 rsoxy measurements/myfile.csv
```

Your data will be stored in the `measurements` volume which you can access under `/var/lib/docker/volumes/measurements/_data` on Linux.
