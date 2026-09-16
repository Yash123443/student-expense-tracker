## Manual additions to `core/settings.py`

The auto-generated `core/settings.py` was left untouched. To complete the
baseline configuration, add the following changes by hand.

### 1. INSTALLED_APPS — add the three new entries

```python
INSTALLED_APPS = [
    # ... existing Django defaults ...
    "rest_framework",
    "corsheaders",
    "authentication",
    "expenses",
]
```

### 2. MIDDLEWARE — insert CorsMiddleware near the top

```python
MIDDLEWARE = [
    "corsheaders.middleware.CorsMiddleware",
    "django.middleware.security.SecurityMiddleware",
    # ... rest unchanged ...
]
```

### 3. CORS configuration (append to end of file)

```python
# CORS — allow local network requests from the Expo dev client.
CORS_ALLOW_ALL_ORIGINS = True          # dev only; tighten for production
CORS_ALLOW_CREDENTIALS = True
```

### 4. Django REST Framework defaults (append)

```python
REST_FRAMEWORK = {
    "DEFAULT_RENDERER_CLASSES": (
        "rest_framework.renderers.JSONRenderer",
    ),
    "DEFAULT_PARSER_CLASSES": (
        "rest_framework.parsers.JSONParser",
    ),
    "DEFAULT_AUTHENTICATION_CLASSES": (),  # plug in custom JWT auth later
    "DEFAULT_PERMISSION_CLASSES": (
        "rest_framework.permissions.AllowAny",
    ),
}
```

### 5. JWT settings (append)

```python
import os

JWT_SECRET = os.environ.get("JWT_SECRET", SECRET_KEY)
JWT_ALGORITHM = os.environ.get("JWT_ALGORITHM", "HS256")
JWT_EXP_MINUTES = int(os.environ.get("JWT_EXP_MINUTES", "60"))
```

### 6. MongoDB

No settings change required — connection details live in environment
variables and are read by `core/mongo.py`. Set `MONGO_URI` and
`MONGO_DB_NAME` in your `.env` (see `.env.example`).

### 7. Wire app URLs

In `core/urls.py`, replace the body with:

```python
from django.contrib import admin
from django.urls import include, path

urlpatterns = [
    path("admin/", admin.site.urls),
    path("api/auth/",     include("authentication.urls")),
    path("api/expenses/", include("expenses.urls")),
]
```
