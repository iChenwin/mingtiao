#!/usr/bin/env python3
"""Check the built manifest, not just source declarations."""
from pathlib import Path
import xml.etree.ElementTree as ET
ns = '{http://schemas.android.com/apk/res/android}'
path = Path('app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml')
root = ET.parse(path).getroot()
permissions = [n.attrib.get(ns+'name') for n in root if n.tag.startswith('uses-permission')]
assert not permissions, f'Unexpected permissions: {permissions}'
app = root.find('application')
assert app.attrib[ns+'allowBackup'] == 'false'
assert app.attrib[ns+'dataExtractionRules'] == '@xml/data_extraction_rules'
services = app.findall('service')
assert len(services) == 1
assert services[0].attrib[ns+'permission'] == 'android.permission.BIND_ACCESSIBILITY_SERVICE'
assert services[0].attrib[ns+'exported'] == 'true'
print('PASS: merged manifest has no requested permissions; service is system-bound; backups disabled.')
