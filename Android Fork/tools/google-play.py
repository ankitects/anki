#!/usr/bin/python
#
# Copyright 2014 Google Inc. All Rights Reserved.
# Copyright 2015 Tim Rae
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Lists all the apks for a given app."""

import argparse
import os
from os.path import expanduser

from apiclient.discovery import build
from apiclient.http import MediaFileUpload
import httplib2
from oauth2client import client


SERVICE_ACCOUNT_EMAIL = (
    '294046724212-r3bef6kl46pb9gk0h1pl5rcjmpfrdpjl@developer.gserviceaccount.com')
PACKAGE = 'com.ichi2.anki'
IMAGES_DIR = './docs/marketing/screenshots/'
LISTINGS_DIR = './docs/marketing/localized_description/'
LANGS = ['uk', 'pt-PT', 'zh-CN', 'th', 'sl', 'ar', 'de-DE', 'ru-RU', 'hu-HU', 'zh-TW', 'fi-FI', 'el-GR', 'ja-JP', 'pt-BR', 'nl-NL', 'no-NO', 'es-ES', 'it-IT', 'id', 'pl-PL', 'cs-CZ', 'ca', 'sr', 'fr-FR', 'ro', 'en-US', 'ko-KR', 'bg', 'tr-TR', 'fa', 'sv-SE']
IMAGE_TYPES = {'phone':'phoneScreenshots', 'sevenInch':'sevenInchScreenshots', 'tenInch':'tenInchScreenshots'}


# Declare command-line flags.
argparser = argparse.ArgumentParser(add_help=False)
argparser.add_argument('task', help='The task to execute: uploadImages, listApks')

def uploadImages(service, edit_id):
    # Check the screenshots dir exists
    if not os.path.exists(IMAGES_DIR):
        raise Exception("The directory %s does not exist" % IMAGES_DIR)
    # Read the language code folders
    subdirs = os.listdir(IMAGES_DIR)
    langs = []
    for l in subdirs:
        if l in LANGS: langs.append(l)
    if len(langs) == 0: raise Excetion ("The directory %s is empty" % IMAGES_DIR)
    print("The following languages were found:\n")
    print(langs)
    # Confirm that the user wants to go ahead
    response = raw_input("This will erase all the images on Google Play that have a language and image type subfolder in %s, and replace them with the images in those subfolders. Type YES if you are sure you want to proceed\n" % IMAGES_DIR)
    if not response == 'YES':
        raise Exception('uploadImages was cancelled')
    # Loop through each language and image type, erase any existing images and upload
    for l in langs:
        for k in IMAGE_TYPES:
            t = IMAGE_TYPES[k]
            subdir = os.path.join(IMAGES_DIR, l, k)
            if os.path.exists(subdir):
                print('Erasing images for language: %s , image type: %s'%(l, t))
                service.edits().images().deleteall(packageName=PACKAGE, 
                    editId=edit_id, language=l, imageType=t).execute()
                files = os.listdir(subdir)
                files.sort()
                cntr = 0
                for f in files:
                    fn = os.path.join(subdir, f)
                    media = MediaFileUpload(fn, mimetype='image/png')
                    result = service.edits().images().upload(packageName=PACKAGE, 
                        editId=edit_id, language=l, imageType=t, media_body=media).execute()
                    if result["image"]["url"]: cntr += 1
                print('Uploaded %d images for language: %s , image type: %s'%(cntr, l, t))
    # Comitting changes
    commit_request = service.edits().commit(
        editId=edit_id, packageName=PACKAGE).execute()
    print 'Edit "%s" has been committed' % (commit_request['id'])

            
def listImages(service, edit_id):
    images_result = service.edits().images().list(
        editId=edit_id, packageName=PACKAGE, language='en-us',
        imageType='phoneScreenshots').execute()
            
    for image in images_result['images']:
      print 'url: %s, sha1: %s, id %s' % (
image['url'], image['sha1'], image['id'])

           
def listApks(service, edit_id):
    apks_result = service.edits().apks().list(
        editId=edit_id, packageName=PACKAGE).execute()

    for apk in apks_result['apks']:
      print 'versionCode: %s, binary.sha1: %s' % (
          apk['versionCode'], apk['binary']['sha1'])
          

def main():
    # Load the key in PKCS 12 format that you downloaded from the Google APIs
    # Console when you created your Service account.
    key_path = os.path.join(expanduser("~"), "src", "583631bdd16d.p12")
    f = file(key_path, 'rb')
    key = f.read()
    f.close()

    # Create an httplib2.Http object to handle our HTTP requests and authorize it
    # with the Credentials. Note that the first parameter, service_account_name,
    # is the Email address created for the Service account. It must be the email
    # address associated with the key that was created.
    credentials = client.SignedJwtAssertionCredentials(
      SERVICE_ACCOUNT_EMAIL,
      key,
      scope='https://www.googleapis.com/auth/androidpublisher')
    http = httplib2.Http()
    http = credentials.authorize(http)
    service = build('androidpublisher', 'v2', http=http)
    edit_request = service.edits().insert(body={}, packageName=PACKAGE)
    result = edit_request.execute()
    edit_id = result['id']

    # Process flags and read their values.
    flags = argparser.parse_args()

    task = flags.task

    try:
        if task == 'uploadImages':
            # Upload screenshots
            uploadImages(service, edit_id)
        elif task == 'listImages':
            # List all the images and their URLs. 
            # This task can be used as a non-destructive test to check the API is working
            listImages(service, edit_id)
        elif task == 'listApks':
            # List all the APKs and their hashes. 
            # This task can be used as a non-destructive test to check the API is working
            listApks(service, edit_id)
        else:
            raise ValueError('Unrecognized task name')
    except client.AccessTokenRefreshError:
        print ('The credentials have been revoked or expired, please re-run the '
           'application to re-authorize')


if __name__ == '__main__':
  main()
