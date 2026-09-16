import os
from pathlib import Path
try:
    from dotenv import load_dotenv
    load_dotenv(Path(__file__).resolve().parent.parent / '.env')
except ImportError:
    pass

# Build paths inside the project like this: BASE_DIR / 'subdir'.
BASE_DIR = Path(__file__).resolve().parent.parent

# SECURITY WARNING: keep the secret key used in production secret!
SECRET_KEY = os.environ.get("DJANGO_SECRET_KEY", "django-insecure-development-only-change-me")

# SECURITY WARNING: don't run with debug turned on in production!
DEBUG = os.environ.get("DJANGO_DEBUG", "True").lower() in {"1", "true", "yes", "on"}

ALLOWED_HOSTS = ["*"]  # Allows mobile phones on local Wi-Fi to connect

# Application definition
INSTALLED_APPS = [
    "django.contrib.admin",
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    # Third Party Apps
    "rest_framework",
    "corsheaders",
    # Local Apps
    "authentication",
    "expenses",
]

MIDDLEWARE = [
    "corsheaders.middleware.CorsMiddleware",  # Placed right at the top for CORS handling
    "django.middleware.security.SecurityMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]

ROOT_URLCONF = "core.urls"

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.debug",
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
                "django.contrib.messages.context_processors.messages",
            ],
        },
    },
]

WSGI_APPLICATION = "core.wsgi.application"

# No default SQL Databases configured since we use MongoDB Atlas via PyMongo
DATABASES = {}

# Password validation
AUTH_PASSWORD_VALIDATORS = [
    {"NAME": "django.contrib.auth.password_validation.UserAttributeSimilarityValidator"},
    {"NAME": "django.contrib.auth.password_validation.MinimumLengthValidator"},
    {"NAME": "django.contrib.auth.password_validation.CommonPasswordValidator"},
    {"NAME": "django.contrib.auth.password_validation.NumericPasswordValidator"},
]

# Internationalization
LANGUAGE_CODE = "en-us"
TIME_ZONE = "UTC"
USE_I18N = True
USE_TZ = True

# Static files (CSS, JavaScript, Images)
STATIC_URL = "static/"
DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

# --- MANUAL PATCHES APPENDED BELOW ---

# CORS Configuration — allow local network requests from the Expo dev client
CORS_ALLOW_ALL_ORIGINS = True  
CORS_ALLOW_CREDENTIALS = True

# Django REST Framework Defaults
REST_FRAMEWORK = {
    "DEFAULT_RENDERER_CLASSES": (
        "rest_framework.renderers.JSONRenderer",
    ),
    "DEFAULT_PARSER_CLASSES": (
        "rest_framework.parsers.JSONParser",
    ),
    "DEFAULT_AUTHENTICATION_CLASSES": (),  # Custom JWT verification middleware to be connected later
    "DEFAULT_PERMISSION_CLASSES": (
        "rest_framework.permissions.AllowAny",
    ),
}

# JWT Security Setup
JWT_SECRET = os.environ.get("JWT_SECRET", SECRET_KEY)
JWT_ALGORITHM = os.environ.get("JWT_ALGORITHM", "HS256")
JWT_EXP_MINUTES = int(os.environ.get("JWT_EXP_MINUTES", "60"))
