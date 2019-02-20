import os
from flask import Flask, request
from werkzeug import secure_filename


app = Flask(__name__)


@app.route('/')
def hello_world():
    return 'Hello, World!'


@app.route('/user/<user>')
def hello_user(user):
    return 'Hello %s' % (user)


@app.route('/about')
def about():
    return "About"


@app.route('/pusher', methods=['GET', 'POST'])
def UploadVideo():
    if request.method == 'POST':
        file = request.files['video']
        print("file", file)
        file.save(secure_filename(file.filename))
        file = request.form['chambers']
        print("chambers", file)
        return "http://google.com"
    else:
        return "NOT POST FILE"

if __name__ == '__main__':
    app.run(host='0.0.0.0')

