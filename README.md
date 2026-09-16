# Student Expense Tracker

A native Android app and Django REST API for students to record personal income
and expenses, manage budgets and subscriptions, and split shared costs with
friends. The Android client is built with Kotlin and XML layouts; the API uses
Django REST Framework, MongoDB (via PyMongo), and JWTs.

> This repository deliberately contains only the Android app and backend.

## Features

- Account registration and JWT-based sign-in
- Personal expenses, income, budgets, and spending insights
- Subscription tracking
- Friend management and shared expense groups
- Split group bills, balance tracking, and settlement updates
- Persisted Android dark-mode preference

## Technology stack

| Area | Technologies |
| --- | --- |
| Android | Kotlin, XML layouts, Material 3, Gradle |
| Backend | Python, Django, Django REST Framework |
| Data | MongoDB Atlas or local MongoDB, PyMongo |
| Authentication | PyJWT, Django password hashing |
| Automation | GitHub Actions |

## Backend setup

Prerequisites: Python 3.12+ and access to a MongoDB instance.

```powershell
cd backend
python -m venv venv
.\venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r requirements.txt
Copy-Item .env.example .env
```

Edit `backend/.env` with your MongoDB connection details and long, randomly
generated `JWT_SECRET` and `DJANGO_SECRET_KEY` values. Then run:

```powershell
python manage.py check
python manage.py runserver 0.0.0.0:8000
```

The `0.0.0.0` binding makes the API reachable from an Android emulator or a
phone on the same local network. Do not use it as a production deployment
configuration.

## Android setup

Prerequisites: Android Studio with Android SDK 36 installed, and JDK 17.

Open the `android` folder in Android Studio, allow Gradle sync to complete, and
run the `app` configuration on an emulator or connected device. The application
ID is `com.example.studentexpensetrackerandroid`.

The API address is currently configured in
`android/app/src/main/java/com/example/studentexpensetrackerandroid/data/ApiClient.kt`.

### API URL by device

| Target | `BASE_URL` value |
| --- | --- |
| Android Emulator | `http://10.0.2.2:8000` |
| Physical Android phone | `http://<your-computer-LAN-IP>:8000` |
| Production | `https://<your-api-domain>` |

For a physical phone, both devices must be on the same network. Permit the
Python server through the computer firewall if the phone cannot connect. Use
HTTPS in production.

## Build and check commands

```powershell
# Backend verification
cd backend
python manage.py check

# Android debug APK
cd android
.\gradlew.bat assembleDebug
```

The debug APK is generated under `android/app/build/outputs/apk/debug/` and is
intentionally excluded from Git.

## API overview

All API endpoints are prefixed with `/api/`.

| Area | Representative endpoints |
| --- | --- |
| Authentication | `POST /api/auth/register/`, `POST /api/auth/login/`, `GET /api/auth/profile/` |
| Friends | `GET /api/auth/friends/list/`, `POST /api/auth/friends/add/` |
| Personal finances | `POST /api/expenses/personal/`, `POST /api/expenses/income/`, `GET /api/expenses/balance/` |
| Budget and insights | `GET, POST /api/expenses/budgets/`, `GET /api/expenses/insights/` |
| Groups | `GET /api/expenses/groups/`, `POST /api/expenses/groups/create/`, `POST /api/expenses/expenses/add-group/` |
| Settlements | `GET, POST /api/settlements/`, `PATCH /api/settlements/<id>/` |

Protected requests use `Authorization: Bearer <access_token>`.

## Security

- `backend/.env` is ignored and must never be committed. Use
  [`backend/.env.example`](backend/.env.example) as the template.
- Before making this repository public, rotate the MongoDB database password and
  JWT secret that were previously stored in the local `.env` file. Treat both as
  exposed even though they are not included in this repository.
- Never use the development HTTP URLs in production; configure HTTPS, restricted
  CORS origins, `ALLOWED_HOSTS`, and `DJANGO_DEBUG=False`.
- See [SECURITY.md](SECURITY.md) for vulnerability reporting and deployment
  guidance.

## Continuous integration

GitHub Actions runs `python manage.py check` for the backend and creates an
Android debug build for each pull request and push to `main`.
