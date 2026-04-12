import requests
try:
    r = requests.get("https://ipwho.is/")
    print(r.json())
except Exception as e:
    print(e)
