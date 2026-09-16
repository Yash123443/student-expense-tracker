"""
MongoDB connection handler using PyMongo.

Exposes a process-wide pooled `MongoClient` and a `get_db()` helper that
returns the application database. Connection details are read from
environment variables so each environment can point at its own cluster.

Environment variables
---------------------
MONGO_URI       Full Mongo connection string. Default: mongodb://localhost:27017
MONGO_DB_NAME   Database name.                  Default: student_expense_tracker

Usage
-----
    from core.mongo import get_db
    db = get_db()
    db.users.find_one({"email": "a@b.com"})
"""

import os
from functools import lru_cache

from pymongo import MongoClient
from pymongo.database import Database
from pymongo.errors import ConnectionFailure, ServerSelectionTimeoutError


# --- Configuration ---------------------------------------------------------

# `or` (rather than the default arg to .get) collapses both "unset" and
# "set-to-empty-string" cases to the same fallback, preventing a silent
# crash on `MongoClient("")`.
MONGO_URI = os.environ.get("MONGO_URI") or "mongodb://localhost:27017"
MONGO_DB_NAME = os.environ.get("MONGO_DB_NAME") or "student_expense_tracker"

# Reasonable defaults for a mobile-app backend: fail fast on bad URIs,
# but keep idle sockets open long enough to amortize TLS handshakes.
_SERVER_SELECTION_TIMEOUT_MS = 5_000
_CONNECT_TIMEOUT_MS = 5_000
_SOCKET_TIMEOUT_MS = 10_000
_MAX_POOL_SIZE = 50


# --- Connection pool ------------------------------------------------------

@lru_cache(maxsize=1)
def get_client() -> MongoClient:
    """
    Return a process-wide singleton MongoClient.

    `MongoClient` is itself a connection pool — creating one per process
    is the recommended pattern. `lru_cache` makes this lazy and thread-safe.
    """
    client = MongoClient(
        MONGO_URI,
        serverSelectionTimeoutMS=_SERVER_SELECTION_TIMEOUT_MS,
        connectTimeoutMS=_CONNECT_TIMEOUT_MS,
        socketTimeoutMS=_SOCKET_TIMEOUT_MS,
        maxPoolSize=_MAX_POOL_SIZE,
        retryWrites=True,
    )
    host_display = MONGO_URI.split("@")[-1].split("/")[0] if "@" in MONGO_URI else "localhost"
    print(f"🚀 [MongoDB] Successfully initialized connection to Cluster!")
    print(f"🌍 [MongoDB] Host: {host_display}")
    print(f"📂 [MongoDB] Database: {MONGO_DB_NAME}")
    return client


def get_db() -> Database:
    """
    Return the default application database handle.

    Raises
    ------
    pymongo.errors.ServerSelectionTimeoutError
        If the cluster is unreachable within the configured timeout.
    pymongo.errors.ConnectionFailure
        For other connectivity problems (auth, TLS, etc.).
    """
    client = get_client()
    return client[MONGO_DB_NAME]


def ping() -> bool:
    """
    Lightweight health-check used by views / management commands.

    Returns True on success, False on any connectivity failure.
    """
    try:
        get_client().admin.command("ping")
        return True
    except (ConnectionFailure, ServerSelectionTimeoutError):
        return False