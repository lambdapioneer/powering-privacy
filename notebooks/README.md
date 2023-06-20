# Notebooks

The Python notebooks allow generating the plots and tables used in the paper.


## Setup and build

Ensure that a version of Python 3 is installed.

First setup the virtual environment and install all required dependencies:

```
$ python3 -m venv venv
$ source venv/bin/activate
(env)$ pip install -r requirements.txt
```


## Run

You can start the Jupyter notebook server with the virtual environment activated as follows:

```
$ source venv/bin/activate
(env)$ jupyter notebook
```


## Docker

Alternatively, you can build and run the notebooks using Docker:

```
$ docker build -t notebooks .
$ docker run -it --rm -p 8888:8888 -v $(pwd)/../measurements:/measurements notebooks
```
