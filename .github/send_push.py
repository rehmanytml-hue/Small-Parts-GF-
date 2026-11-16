"""Sends the instant "new version" push to every phone subscribed to the updates topic."""
import json, os, sys
import requests
from google.oauth2 import service_account
from google.auth.transport.requests import Request

version = sys.argv[1]
info = json.loads(os.environ['FCM_SERVICE_ACCOUNT'])
creds = service_account.Credentials.from_service_account_info(info, scopes=['https://www.googleapis.com/auth/firebase.messaging'])
creds.refresh(Request())
body = {'message': {
    'topic': 'updates',
    'notification': {'title': 'Hourly Recorder update ready', 'body': 'Version %s is available. Tap to open the app and update.' % version},
    'data': {'version': version},
    'android': {'priority': 'HIGH', 'notification': {'channel_id': 'updates'}},
}}
r = requests.post('https://fcm.googleapis.com/v1/projects/%s/messages:send' % info['project_id'],
                  headers={'Authorization': 'Bearer ' + creds.token}, json=body, timeout=30)
print(r.status_code, r.text)
r.raise_for_status()
