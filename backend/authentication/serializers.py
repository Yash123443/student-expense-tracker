"""
Serializers for the authentication app.

These serializers validate the *shape* of incoming JSON payloads and
perform light database lookups (e.g. unique-email check). Password
hashing and JWT issuance live in the views.
"""

from rest_framework import serializers

from core.mongo import get_db


# --- Helpers --------------------------------------------------------------

def _users_collection():
    """Return the MongoDB `users` collection lazily.

    Defined as a function (rather than a module-level constant) so the
    connection is only opened when a serializer actually needs it — this
    keeps unit tests and `manage.py check` from hitting Mongo on import.
    """
    return get_db()["users"]


# --- Register -------------------------------------------------------------

class UserRegisterSerializer(serializers.Serializer):
    """Validates a new-user registration payload."""

    name = serializers.CharField(max_length=120, trim_whitespace=True)
    email = serializers.EmailField(max_length=254)
    password = serializers.CharField(min_length=8, max_length=128, write_only=True)
    monthly_budget = serializers.DecimalField(
        max_digits=12,
        decimal_places=2,
        min_value=0,
        required=False,
        default=0,
    )

    def validate_name(self, value: str) -> str:
        value = (value or "").strip()
        if not value:
            raise serializers.ValidationError("Name cannot be blank.")
        return value

    def validate_email(self, value: str) -> str:
        # Normalize to lowercase so lookups stay case-insensitive.
        value = (value or "").strip().lower()
        if not value:
            raise serializers.ValidationError("Email is required.")
        if _users_collection().find_one({"email": value}, projection={"_id": 1}):
            raise serializers.ValidationError("A user with this email already exists.")
        return value

    def validate_password(self, value: str) -> str:
        if not value or not value.strip():
            raise serializers.ValidationError("Password cannot be blank.")
        return value


# --- Login ----------------------------------------------------------------

class UserLoginSerializer(serializers.Serializer):
    """Validates a login payload. Credential verification happens in the view."""

    email = serializers.EmailField(max_length=254)
    password = serializers.CharField(max_length=128, write_only=True)

    def validate_email(self, value: str) -> str:
        value = (value or "").strip().lower()
        if not value:
            raise serializers.ValidationError("Email is required.")
        return value

    def validate_password(self, value: str) -> str:
        if not value:
            raise serializers.ValidationError("Password is required.")
        return value