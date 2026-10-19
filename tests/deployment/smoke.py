#!/usr/bin/env python3
"""Deployment acceptance against a dedicated instance. Creates one temporary user/note.
Usage: python3 tests/deployment/smoke.py create|verify|cleanup [base_url]
State lives in ignored target/docker-smoke.json. Uses only Python's standard library.
"""
import http.cookiejar
import json
import os
from pathlib import Path
import re
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request

phase = sys.argv[1]
base = (sys.argv[2] if len(sys.argv) > 2 else "http://localhost").rstrip("/")
fixture = Path("target/docker-smoke.json")
cookies = http.cookiejar.CookieJar()
client = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPCookieProcessor(cookies))

def request(path, method="GET", data=None, headers=None):
    req = urllib.request.Request(base + path, data=data, headers=headers or {}, method=method)
    try:
        response = client.open(req, timeout=30)
    except urllib.error.HTTPError as exc:
        response = exc
    body = response.read()
    return response.status, response.geturl(), body, response.headers

def page(path):
    status, url, body, headers = request(path)
    assert status == 200, (path, status, body[:200])
    return body.decode()

def csrf(html):
    return re.search(r'<meta name="csrf-token" content="([^"]+)"', html).group(1)

def form(path, fields):
    return request(path, "POST", urllib.parse.urlencode(fields).encode(), {"Content-Type": "application/x-www-form-urlencoded"})

def login(state):
    token = csrf(page('/login'))
    status, url, body, headers = form('/login', dict(username=state['username'], password=state['password'], _csrf=token))
    assert status == 200 and urllib.parse.urlparse(url).path == '/', (status, url)
    assert all(not c.secure for c in cookies), 'HTTP session unexpectedly requires HTTPS'
    return csrf(page('/notes/new'))

if phase == 'create':
    assert not fixture.exists(), 'Existing fixture: verify/cleanup it before creating another'
    state = dict(username='deploy_' + secrets.token_hex(4), password=secrets.token_hex(12)+'a9')
    health = json.loads(request('/actuator/health')[2])
    assert health['status'] == 'UP' and 'components' not in health
    html = page('/')
    css = re.search(r'href="([^"]+\.css)"', html).group(1)
    assert request(css)[0] == 200
    token = csrf(page('/register'))
    status, url, _, _ = form('/register', dict(username=state['username'], password=state['password'], confirm=state['password'], _csrf=token))
    assert status == 200 and 'registered' in url, (status, url)
    token = login(state)
    headers = {'X-CSRF-TOKEN': token}
    image = Path('src/main/resources/static/images/photo-1441974231531-c6227db76b6e.jpg').read_bytes()
    boundary = 'rednote' + secrets.token_hex(12)
    data = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="deployment.jpg"\r\nContent-Type: image/jpeg\r\n\r\n'.encode() + image + f'\r\n--{boundary}--\r\n'.encode())
    status, _, body, _ = request('/actions/files', 'POST', data, {**headers, 'Content-Type': 'multipart/form-data; boundary='+boundary})
    assert status == 200, (status, body[:200])
    asset = json.loads(body)
    state['asset'] = asset['url']
    state['title'] = '部署验收 ' + secrets.token_hex(4)
    status, url, body, _ = form('/notes', dict(title=state['title'], body='Docker Compose persistence acceptance', images=asset['id'], _csrf=token))
    assert status == 200 and re.fullmatch(r'/notes/\d+', urllib.parse.urlparse(url).path), (status, url)
    state['note'] = urllib.parse.urlparse(url).path
    note_id = state['note'].split('/')[-1]
    status, _, _, _ = request('/actions/notes/'+note_id+'/comments', 'POST', b'{"text":"deployment verification"}', {'Content-Type':'application/json'})
    assert status == 403, ('missing CSRF should fail', status)
    status, _, _, _ = request('/actions/notes/'+note_id+'/comments', 'POST', b'{"text":"deployment verification"}', {**headers,'Content-Type':'application/json'})
    assert status == 200, ('comment', status)
    status, _, _, _ = request('/actions/notes/'+note_id+'/like', 'PUT', b'{"liked":true}', {**headers,'Content-Type':'application/json'})
    assert status == 200, ('like', status)
    state['image_sha256'] = __import__('hashlib').sha256(image).hexdigest()
    fixture.parent.mkdir(exist_ok=True)
    fd = os.open(fixture, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as out:
        json.dump(state, out)
    print('PASS health, static CSS, registration, HTTP login, CSRF, upload, publish, comment, like')
else:
    state = json.loads(fixture.read_text())
    token = login(state)
    if phase == 'verify':
        assert state['title'] in page(state['note'])
        status, _, image, headers = request(state['asset'])
        assert status == 200 and __import__('hashlib').sha256(image).hexdigest() == state['image_sha256']
        assert headers.get('Cache-Control') == 'no-store'
        print('PASS persisted user, note, image bytes and access policy')
    elif phase == 'cleanup':
        status, _, _, _ = request('/actions/notes/'+state['note'].split('/')[-1], 'DELETE', headers={'X-CSRF-TOKEN':token})
        assert status == 200, status
        fixture.unlink()
        print('PASS test note removed; random acceptance user retained')
    else:
        raise SystemExit('Unknown phase')
