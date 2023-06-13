from flask import Flask, request
from flask.helpers import send_file
from werkzeug.utils import secure_filename

import io
import os
import pathlib
import zipfile

STORAGE_DIR = 'storage'
SCENARIOS_FILE = os.path.join(STORAGE_DIR, 'scenarios.zip')
ANDROID_LOGS_DIR = os.path.join(STORAGE_DIR, 'android_logs')
os.makedirs(STORAGE_DIR, exist_ok=True)
os.makedirs(ANDROID_LOGS_DIR, exist_ok=True)

app = Flask(__name__)


@app.route('/energy/scenarios.zip', methods=['GET'])
def get_scenarios():
    return send_file(SCENARIOS_FILE)


@app.route('/energy/scenarios', methods=['POST'])
def post_scenarios():
    f = request.files['file']
    f.save(SCENARIOS_FILE)
    return "success\n"


@app.route('/energy/android_logs.zip', methods=['GET'])
def get_android_logs():
    data = io.BytesIO()
    with zipfile.ZipFile(data, mode='w') as z:
        path = pathlib.Path(ANDROID_LOGS_DIR)
        for filename in path.iterdir():
            z.write(filename, arcname=filename.name)

    data.seek(0)
    return send_file(data, mimetype='application/zip')


@app.route('/energy/android_log/<name>', methods=['POST'])
def post_android_log(name):
    f = request.files['file']
    f.save(os.path.join(ANDROID_LOGS_DIR, secure_filename(name)))
    return "success\n"


@app.route('/energy/download', methods=['GET'])
def get_download():
    size = request.args.get("size")
    data = os.urandom(int(size))
    return send_file(
        io.BytesIO(data),
        as_attachment=True,
        attachment_filename='download_%s' % size
    )
