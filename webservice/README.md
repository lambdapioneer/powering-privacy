# Webservice

The webservice can be run locally or remotely and provides a REST API to sync scenarios to the Android device and to sync the results back.


> **Warning**
> Note that this is a very simple implementation and does not provide any security or authentication.
Hence, if the service is run from a publicly accessible server, it should be protected by a reverse-proxy with authentication or similar.
See the sample .htpasswd file for the sample user/password configuration (user:uyaeNiyaighiu4HaingohShee2ie9ahs).


## Setup and build

Ensure that a version of Python 3 is installed.

First setup the virtual environment and install all required dependencies:

```
$ python3 -m venv venv
(env)$ source venv/bin/activate
(env)$ pip install -r requirements.txt
```


## Run

Start the webservice with the virtual environment activated as follows:

```
(env)$ FLASK_APP=app.py flask run
```

You can verify that the service is running correctly by opening http://127.0.0.1:5000/energy/android_logs.zip which should download an empty .zip archive.

Update the scripts and the Android apps with the correct IP address of the webservice and authentication details (if applicable).


## Docker

Alternatively, you can build and run the project using Docker:

```
$ docker build --network=host -t webservice .
$ docker run -it --rm --volume=storage:/usr/src/webservice/measurements --publish=5000:5000 webservice
```
