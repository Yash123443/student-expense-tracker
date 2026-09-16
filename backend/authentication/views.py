"""
Authentication views.

Endpoints
---------
POST /api/auth/register/   Create a new user, return the created user payload.
POST /api/auth/login/      Verify credentials, return a signed JWT access token.

Passwords are hashed with Django's PBKDF2 implementation
(`django.contrib.auth.hashers`), and tokens are signed with PyJWT using
settings configured in `core.settings`.
"""

from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone

# pyrefly: ignore [missing-import]
import jwt
from bson.objectid import ObjectId
from django.conf import settings
from django.contrib.auth.hashers import check_password, make_password
from pymongo.errors import ConnectionFailure, PyMongoError, ServerSelectionTimeoutError
from rest_framework import status
from rest_framework.permissions import AllowAny
from rest_framework.request import Request
from rest_framework.response import Response
from rest_framework.views import APIView

from core.mongo import get_db

from .serializers import UserLoginSerializer, UserRegisterSerializer


logger = logging.getLogger(__name__)


# --- Helpers --------------------------------------------------------------

def _users_collection():
    """Return the MongoDB `users` collection."""
    return get_db()["users"]


def _issue_access_token(user_id: str) -> tuple[str, datetime]:
    """Encode a JWT containing the user id and return (token, expiry)."""
    exp_minutes = int(getattr(settings, "JWT_EXP_MINUTES", 60))
    now = datetime.now(timezone.utc)
    expires_at = now + timedelta(minutes=exp_minutes)

    payload = {
        "sub": str(user_id),
        "iat": int(now.timestamp()),
        "exp": int(expires_at.timestamp()),
        "type": "access",
    }
    token = jwt.encode(
        payload,
        settings.JWT_SECRET,
        algorithm=getattr(settings, "JWT_ALGORITHM", "HS256"),
    )
    # PyJWT >=2 returns str; older versions returned bytes — normalize.
    if isinstance(token, bytes):
        token = token.decode("utf-8")
    return token, expires_at

def _get_user_id_from_request(request: Request) -> str | None:
    auth_header = request.headers.get("Authorization")
    if not auth_header or not auth_header.startswith("Bearer "):
        return None
    token = auth_header.split(" ")[1]
    try:
        payload = jwt.decode(
            token,
            settings.JWT_SECRET,
            algorithms=[getattr(settings, "JWT_ALGORITHM", "HS256")],
        )
        return payload.get("sub")
    except jwt.PyJWTError:
        return None


def _mongo_unavailable_response() -> Response:
    """Standardized 503 when the database is unreachable."""
    return Response(
        {"detail": "Database temporarily unavailable. Please try again."},
        status=status.HTTP_503_SERVICE_UNAVAILABLE,
    )


# --- Views ----------------------------------------------------------------

class RegisterView(APIView):
    """Create a new user account."""

    permission_classes = [AllowAny]
    authentication_classes: list = []

    def post(self, request: Request) -> Response:
        serializer = UserRegisterSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        data = serializer.validated_data
        now = datetime.now(timezone.utc)

        document = {
            "name": data["name"],
            "email": data["email"],
            "password": make_password(data["password"]),
            "monthly_budget": float(data.get("monthly_budget", 0) or 0),
            "created_at": now,
            "updated_at": now,
        }

        try:
            result = _users_collection().insert_one(document)
        except (ServerSelectionTimeoutError, ConnectionFailure):
            logger.exception("Mongo unavailable during user registration")
            return _mongo_unavailable_response()
        except PyMongoError:
            logger.exception("Mongo write failure during user registration")
            return Response(
                {"detail": "Could not create account. Please try again."},
                status=status.HTTP_500_INTERNAL_SERVER_ERROR,
            )

        return Response(
            {
                "id": str(result.inserted_id),
                "name": document["name"],
                "email": document["email"],
                "monthly_budget": document["monthly_budget"],
            },
            status=status.HTTP_201_CREATED,
        )


class LoginView(APIView):
    """Verify credentials and return a JWT access token."""

    permission_classes = [AllowAny]
    authentication_classes: list = []

    # A single generic message for any credential mismatch — prevents
    # account-enumeration via differing error text.
    INVALID_CREDENTIALS = {"detail": "Invalid email or password."}

    def post(self, request: Request) -> Response:
        serializer = UserLoginSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        email = serializer.validated_data["email"]
        password = serializer.validated_data["password"]

        try:
            user = _users_collection().find_one({"email": email})
        except (ServerSelectionTimeoutError, ConnectionFailure):
            logger.exception("Mongo unavailable during login")
            return _mongo_unavailable_response()
        except PyMongoError:
            logger.exception("Mongo read failure during login")
            return Response(
                {"detail": "Login failed. Please try again."},
                status=status.HTTP_500_INTERNAL_SERVER_ERROR,
            )

        if not user or not check_password(password, user.get("password", "")):
            return Response(self.INVALID_CREDENTIALS, status=status.HTTP_401_UNAUTHORIZED)

        token, expires_at = _issue_access_token(user["_id"])

        return Response(
            {
                "access_token": token,
                "token_type": "Bearer",
                "expires_at": expires_at.isoformat(),
                "user": {
                    "id": str(user["_id"]),
                    "name": user.get("name", ""),
                    "email": user["email"],
                    "monthly_budget": user.get("monthly_budget", 0),
                },
            },
            status=status.HTTP_200_OK,
        )

class RefreshView(APIView):
    permission_classes = [AllowAny]
    def post(self, request: Request) -> Response:
        return Response({"detail": "Not implemented yet"}, status=status.HTTP_501_NOT_IMPLEMENTED)

class MeView(APIView):
    permission_classes = [AllowAny]
    def get(self, request: Request) -> Response:
        user_id = _get_user_id_from_request(request)
        if not user_id:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)
        user = _users_collection().find_one({"_id": ObjectId(user_id)})
        if not user:
            return Response({"detail": "User not found"}, status=status.HTTP_404_NOT_FOUND)
        return Response({
            "id": str(user["_id"]),
            "name": user.get("name", ""),
            "email": user["email"],
            "upi_id": user.get("upi_id", ""),
        })

class ProfileView(APIView):
    permission_classes = [AllowAny]

    def get(self, request: Request) -> Response:
        user_id = _get_user_id_from_request(request)
        if not user_id:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)
        
        user = _users_collection().find_one({"_id": ObjectId(user_id)})
        if not user:
            return Response({"detail": "User not found"}, status=status.HTTP_404_NOT_FOUND)
        
        return Response({
            "id": str(user["_id"]),
            "name": user.get("name", ""),
            "email": user["email"],
            "upi_id": user.get("upi_id", ""),
        })

    def patch(self, request: Request) -> Response:
        user_id = _get_user_id_from_request(request)
        if not user_id:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)
        
        upi_id = request.data.get("upi_id")
        if upi_id is not None:
            _users_collection().update_one(
                {"_id": ObjectId(user_id)},
                {"$set": {"upi_id": upi_id, "updated_at": datetime.now(timezone.utc)}}
            )
        
        user = _users_collection().find_one({"_id": ObjectId(user_id)})
        return Response({
            "id": str(user["_id"]),
            "name": user.get("name", ""),
            "email": user["email"],
            "upi_id": user.get("upi_id", ""),
        })

class UserSearchView(APIView):
    permission_classes = [AllowAny]

    def get(self, request: Request) -> Response:
        user_id = _get_user_id_from_request(request)
        if not user_id:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)
            
        email_query = request.query_params.get("email", "").strip()
        if not email_query:
            return Response({"detail": "Query parameter 'email' is required"}, status=status.HTTP_400_BAD_REQUEST)
            
        # Case insensitive regex match for email, returning only name, email, and id
        # Limit to 5 results to prevent scraping
        users = _users_collection().find(
            {"email": {"$regex": f"^{email_query}", "$options": "i"}, "_id": {"$ne": ObjectId(user_id)}},
            {"_id": 1, "name": 1, "email": 1}
        ).limit(5)
        
        results = []
        for u in users:
            results.append({
                "id": str(u["_id"]),
                "name": u.get("name", ""),
                "email": u.get("email", ""),
            })
            
        return Response(results, status=status.HTTP_200_OK)

class FriendAddView(APIView):
    permission_classes = [AllowAny]

    def post(self, request: Request) -> Response:
        user_id = _get_user_id_from_request(request)
        if not user_id:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)
            
        friend_id = request.data.get("friend_id")
        if not friend_id:
            return Response({"detail": "friend_id is required"}, status=status.HTTP_400_BAD_REQUEST)
            
        if friend_id == user_id:
            return Response({"detail": "Cannot add yourself"}, status=status.HTTP_400_BAD_REQUEST)
            
        friend = _users_collection().find_one({"_id": ObjectId(friend_id)})
        if not friend:
            return Response({"detail": "User not found"}, status=status.HTTP_404_NOT_FOUND)
            
        _users_collection().update_one(
            {"_id": ObjectId(user_id)},
            {"$addToSet": {"friends": friend_id}}
        )
        
        return Response({"detail": "Friend added successfully"}, status=status.HTTP_200_OK)

class FriendListView(APIView):
    permission_classes = [AllowAny]

    def get(self, request: Request) -> Response:
        user_id = _get_user_id_from_request(request)
        if not user_id:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)
            
        user = _users_collection().find_one({"_id": ObjectId(user_id)})
        if not user:
            return Response({"detail": "User not found"}, status=status.HTTP_404_NOT_FOUND)
            
        friends_ids = user.get("friends", [])
        if not friends_ids:
            return Response([], status=status.HTTP_200_OK)
            
        friends_object_ids = [ObjectId(fid) for fid in friends_ids if ObjectId.is_valid(fid)]
        
        friends = _users_collection().find(
            {"_id": {"$in": friends_object_ids}},
            {"_id": 1, "name": 1, "email": 1}
        )
        
        results = []
        for f in friends:
            results.append({
                "id": str(f["_id"]),
                "name": f.get("name", ""),
                "email": f.get("email", "")
            })
            
        return Response(results, status=status.HTTP_200_OK)