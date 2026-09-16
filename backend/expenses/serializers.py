"""
Serializers for the expenses app.

GroupSerializer:        validates payload for creating a shared spending group.
GroupExpenseSerializer: validates payload for adding a bill that should be
                        split across a subset of a group's members.
"""

from rest_framework import serializers


class GroupSerializer(serializers.Serializer):
    """Validates a new-group payload: name + list of member user IDs."""

    name = serializers.CharField(max_length=120, trim_whitespace=True)
    members = serializers.ListField(
        child=serializers.CharField(max_length=64),
        min_length=1,
        max_length=50,
        allow_empty=False,
    )

    def validate_name(self, value: str) -> str:
        value = (value or "").strip()
        if not value:
            raise serializers.ValidationError("Group name cannot be blank.")
        return value

    def validate_members(self, value):
        cleaned = [m.strip() for m in value if m and m.strip()]
        if len(cleaned) < 1:
            raise serializers.ValidationError(
                "Select at least one other member for this group."
            )
        if len(set(cleaned)) != len(cleaned):
            raise serializers.ValidationError("Member list must not contain duplicates.")
        return cleaned


class GroupExpenseSerializer(serializers.Serializer):
    """Validates a group-bill payload to be split among participants."""

    group_id = serializers.CharField(max_length=64)
    amount = serializers.DecimalField(
        max_digits=12, decimal_places=2, min_value=0.01
    )
    category = serializers.CharField(max_length=64)
    description = serializers.CharField(
        max_length=255, allow_blank=True, required=False, default=""
    )
    paid_by = serializers.CharField(max_length=64)
    participants = serializers.ListField(
        child=serializers.CharField(max_length=64),
        min_length=1,
        max_length=50,
    )

    def validate_group_id(self, value: str) -> str:
        value = (value or "").strip()
        if not value:
            raise serializers.ValidationError("group_id is required.")
        return value

    def validate_paid_by(self, value: str) -> str:
        value = (value or "").strip()
        if not value:
            raise serializers.ValidationError("paid_by is required.")
        return value

    def validate_category(self, value: str) -> str:
        value = (value or "").strip()
        if not value:
            raise serializers.ValidationError("category is required.")
        return value

    def validate_participants(self, value):
        cleaned = [p.strip() for p in value if p and p.strip()]
        if not cleaned:
            raise serializers.ValidationError("At least one participant is required.")
        if len(set(cleaned)) != len(cleaned):
            raise serializers.ValidationError(
                "Participants list must not contain duplicates."
            )
        return cleaned

    def validate(self, attrs):
        # The payer is implicitly a participant: include them so they bear
        # their own share rather than getting fully reimbursed for it.
        if attrs["paid_by"] not in attrs["participants"]:
            attrs["participants"] = attrs["participants"] + [attrs["paid_by"]]
        return attrs
