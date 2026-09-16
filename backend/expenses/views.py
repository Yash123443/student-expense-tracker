"""
Expense and group-splitting views.

Endpoints
---------
POST /api/expenses/groups/create/       Create a new shared group.
POST /api/expenses/expenses/add-group/  Add a bill and split it across participants.

Balance ledger
--------------
Each group document carries a `balances` field shaped as:

    {debtor_id: {creditor_id: amount_owed_in_dollars}}

Only non-negative amounts are stored, and after every new expense we
*net* opposing edges between any pair of users — we never store
"A owes B 5 AND B owes A 3"; that collapses to "A owes B 2".
"""

from __future__ import annotations

import logging
from datetime import datetime, timezone
from decimal import ROUND_HALF_UP, Decimal

from bson import ObjectId
from bson.errors import InvalidId
from pymongo.errors import ConnectionFailure, PyMongoError, ServerSelectionTimeoutError
from rest_framework import status
from rest_framework.permissions import AllowAny
from rest_framework.request import Request
from rest_framework.response import Response
from rest_framework.views import APIView

from core.mongo import get_db
from authentication.views import _get_user_id_from_request
from .serializers import GroupExpenseSerializer, GroupSerializer


logger = logging.getLogger(__name__)

CENT = Decimal("0.01")


# --- Mongo helpers --------------------------------------------------------

def _groups_collection():
    return get_db()["groups"]


def _expenses_collection():
    return get_db()["expenses"]

def _settlements_collection():
    return get_db()["settlements"]

def _subscriptions_collection():
    return get_db()["subscriptions"]

def _budgets_collection():
    return get_db()["budgets"]

def _incomes_collection():
    return get_db()["incomes"]


def _to_object_id(value: str):
    """Convert a string ID to ObjectId, returning None on failure."""
    try:
        return ObjectId(value)
    except (InvalidId, TypeError):
        return None


def _mongo_unavailable_response() -> Response:
    return Response(
        {"detail": "Database temporarily unavailable. Please try again."},
        status=status.HTTP_503_SERVICE_UNAVAILABLE,
    )


# --- Splitting math -------------------------------------------------------

def _empty_balance_matrix(member_ids: list[str]) -> dict:
    """
    Build a fresh nested ledger where every member starts at 0 net debt
    with every other member:

        {A: {B: 0.0, C: 0.0}, B: {A: 0.0, C: 0.0}, ...}
    """
    return {
        m: {other: 0.0 for other in member_ids if other != m}
        for m in member_ids
    }


def _split_amount(total: Decimal, n: int) -> list[Decimal]:
    """
    Split `total` into `n` shares rounded to the nearest cent.

    Any leftover pennies after rounding are distributed one-by-one to the
    leading participants so the shares always sum back to `total` exactly.
    """
    base = (total / Decimal(n)).quantize(CENT, rounding=ROUND_HALF_UP)
    shares = [base] * n

    drift = total - sum(shares)              # e.g. Decimal("0.01") or Decimal("-0.02")
    cents = int((drift / CENT).to_integral_value(rounding=ROUND_HALF_UP))
    step = CENT if cents > 0 else -CENT
    for i in range(abs(cents)):
        shares[i % n] += step

    return shares


def _apply_split_to_balances(
    balances: dict, payer: str, participant_shares: dict
) -> dict:
    """
    Mutate `balances` in place, applying one expense's worth of debt and
    netting opposing edges between every (debtor, payer) pair.

    Returns `balances` for convenience.
    """
    for participant, share in participant_shares.items():
        if participant == payer or share <= 0:
            continue

        # Defensive: ensure rows exist (e.g. if balances was sparse).
        balances.setdefault(participant, {}).setdefault(payer, 0.0)
        balances.setdefault(payer, {}).setdefault(participant, 0.0)

        # Record what the participant now owes the payer.
        balances[participant][payer] = round(
            balances[participant][payer] + float(share), 2
        )

        # Net any opposing debt so we never carry both directions.
        owed_back = balances[payer][participant]
        owed_now = balances[participant][payer]
        if owed_back > 0 and owed_now > 0:
            cancel = min(owed_back, owed_now)
            balances[payer][participant] = round(owed_back - cancel, 2)
            balances[participant][payer] = round(owed_now - cancel, 2)

    return balances


# --- Views ----------------------------------------------------------------

class CreateGroupView(APIView):
    """Create a new shared spending group and initialize its balance ledger."""

    permission_classes = [AllowAny]
    def post(self, request: Request) -> Response:
        user_id = _get_user_id_from_request(request)
        if not user_id:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)

        serializer = GroupSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        data = serializer.validated_data
        now = datetime.now(timezone.utc)
        
        # The creator must always be a member. Other members must already be
        # friends of the creator; this prevents strangers adding users to
        # arbitrary groups.
        raw_members = data.get("members", [])
        incoming_member_ids = [str(m).strip() for m in raw_members if m and str(m).strip()]
        if user_id not in incoming_member_ids:
            incoming_member_ids.append(user_id)

        creator_oid = _to_object_id(user_id)
        if creator_oid is None:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)

        valid_member_ids = []
        users_col = get_db()["users"]
        creator = users_col.find_one({"_id": creator_oid}, {"friends": 1})
        if not creator:
            return Response({"detail": "User not found"}, status=status.HTTP_401_UNAUTHORIZED)
        friend_ids = set(creator.get("friends", []))
        
        for member_id in incoming_member_ids:
            try:
                # Cast string to BSON ObjectId
                bson_id = ObjectId(member_id)
                # Query the users collection with the casted ObjectId
                normalized_id = str(bson_id)
                user_exists = users_col.find_one({"_id": bson_id}, {"_id": 1})
                if user_exists and (normalized_id == user_id or normalized_id in friend_ids):
                    # Store it in the format your groups collection expects (string)
                    valid_member_ids.append(str(bson_id))
            except InvalidId:
                return Response({"detail": "Invalid member ID."}, status=status.HTTP_400_BAD_REQUEST)

        if len(valid_member_ids) != len(incoming_member_ids):
            return Response(
                {"detail": "Members must be existing friends of the creator."},
                status=status.HTTP_400_BAD_REQUEST,
            )

        document = {
            "name": data["name"],
            "members": valid_member_ids,
            "balances": _empty_balance_matrix(valid_member_ids),
            "created_at": now,
            "updated_at": now,
        }

        try:
            result = _groups_collection().insert_one(document)
        except (ServerSelectionTimeoutError, ConnectionFailure):
            logger.exception("Mongo unavailable creating group")
            return _mongo_unavailable_response()
        except PyMongoError:
            logger.exception("Mongo write failure creating group")
            return Response(
                {"detail": "Could not create group. Please try again."},
                status=status.HTTP_500_INTERNAL_SERVER_ERROR,
            )

        return Response(
            {
                "id": str(result.inserted_id),
                "name": document["name"],
                "members": document["members"],
                "balances": document["balances"],
            },
            status=status.HTTP_201_CREATED,
        )


class AddGroupExpenseView(APIView):
    """Record a group bill and update the group's running balance ledger."""

    permission_classes = [AllowAny]
    def post(self, request: Request) -> Response:
        user_id = _get_user_id_from_request(request)
        if not user_id:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)

        serializer = GroupExpenseSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        data = serializer.validated_data

        group_oid = _to_object_id(data["group_id"])
        if group_oid is None:
            return Response(
                {"detail": "Invalid group_id."},
                status=status.HTTP_400_BAD_REQUEST,
            )

        # --- Load the group ----------------------------------------------
        try:
            group = _groups_collection().find_one({"_id": group_oid})
        except (ServerSelectionTimeoutError, ConnectionFailure):
            logger.exception("Mongo unavailable fetching group")
            return _mongo_unavailable_response()
        except PyMongoError:
            logger.exception("Mongo read failure fetching group")
            return Response(
                {"detail": "Could not load group."},
                status=status.HTTP_500_INTERNAL_SERVER_ERROR,
            )

        if not group:
            return Response(
                {"detail": "Group not found."},
                status=status.HTTP_404_NOT_FOUND,
            )

        members = set(group.get("members", []))
        if user_id not in members:
            return Response({"detail": "You are not a member of this group."}, status=status.HTTP_403_FORBIDDEN)

        payer = data["paid_by"]
        participants = data["participants"]

        if payer not in members:
            return Response(
                {"detail": "paid_by is not a member of this group."},
                status=status.HTTP_400_BAD_REQUEST,
            )
        unknown = [p for p in participants if p not in members]
        if unknown:
            return Response(
                {"detail": f"Participants not in group: {unknown}"},
                status=status.HTTP_400_BAD_REQUEST,
            )

        # --- Equal-share math --------------------------------------------
        total = Decimal(data["amount"])
        shares = _split_amount(total, len(participants))
        participant_shares = dict(zip(participants, shares))

        # --- Persist the expense -----------------------------------------
        now = datetime.now(timezone.utc)
        expense_doc = {
            "group_id": group_oid,
            "amount": float(total),
            "category": data["category"],
            "description": data.get("description", ""),
            "paid_by": payer,
            "participants": participants,
            "shares": {p: float(s) for p, s in participant_shares.items()},
            "created_at": now,
        }

        try:
            expense_result = _expenses_collection().insert_one(expense_doc)
        except (ServerSelectionTimeoutError, ConnectionFailure):
            logger.exception("Mongo unavailable inserting group expense")
            return _mongo_unavailable_response()
        except PyMongoError:
            logger.exception("Mongo write failure inserting group expense")
            return Response(
                {"detail": "Could not record expense."},
                status=status.HTTP_500_INTERNAL_SERVER_ERROR,
            )

        # --- Update the running balance ledger ---------------------------
        balances = group.get("balances") or _empty_balance_matrix(list(members))
        balances = _apply_split_to_balances(balances, payer, participant_shares)

        try:
            _groups_collection().update_one(
                {"_id": group_oid},
                {"$set": {"balances": balances, "updated_at": now}},
            )
        except (ServerSelectionTimeoutError, ConnectionFailure):
            logger.exception("Mongo unavailable updating group balances")
            return _mongo_unavailable_response()
        except PyMongoError:
            logger.exception("Mongo write failure updating group balances")
            return Response(
                {"detail": "Expense saved but balance update failed."},
                status=status.HTTP_500_INTERNAL_SERVER_ERROR,
            )

        return Response(
            {
                "expense_id": str(expense_result.inserted_id),
                "group_id": str(group_oid),
                "amount": float(total),
                "shares": {p: float(s) for p, s in participant_shares.items()},
                "balances": balances,
            },
            status=status.HTTP_201_CREATED,
        )

class SettlementListView(APIView):
    permission_classes = [AllowAny]

    def get(self, request: Request) -> Response:
        user_id = _get_user_id_from_request(request)
        if not user_id:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)
        
        docs = list(_settlements_collection().find({
            "$or": [{"payer_id": user_id}, {"payee_id": user_id}]
        }))
        
        for doc in docs:
            doc["id"] = str(doc.pop("_id"))
        
        return Response(docs, status=status.HTTP_200_OK)

    def post(self, request: Request) -> Response:
        user_id = _get_user_id_from_request(request)
        if not user_id:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)
        
        data = request.data
        peer_id = data.get("peerId")
        amount = data.get("amount")
        payment_method = data.get("paymentMethod")
        utr = data.get("utr")
        group_id = data.get("groupId")
        
        document = {
            "payer_id": user_id,
            "payee_id": peer_id,
            "amount": amount,
            "paymentMethod": payment_method,
            "utr": utr,
            "group_id": group_id,
            "status": "pending_approval",
            "direction": "outbound",
            "created_at": datetime.now(timezone.utc),
        }
        result = _settlements_collection().insert_one(document)
        document["id"] = str(result.inserted_id)
        document.pop("_id", None)
        
        return Response(document, status=status.HTTP_201_CREATED)

class SettlementDetailView(APIView):
    permission_classes = [AllowAny]

    def patch(self, request: Request, pk: str) -> Response:
        user_id = _get_user_id_from_request(request)
        if not user_id:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)
        
        obj_id = _to_object_id(pk)
        if not obj_id:
            return Response({"detail": "Invalid ID"}, status=status.HTTP_400_BAD_REQUEST)
            
        new_status = request.data.get("status")
        if new_status not in {"completed", "disputed"}:
            return Response(
                {"detail": "Status must be 'completed' or 'disputed'."},
                status=status.HTTP_400_BAD_REQUEST,
            )
            
        doc = _settlements_collection().find_one({"_id": obj_id})
        if not doc:
            return Response({"detail": "Not found"}, status=status.HTTP_404_NOT_FOUND)

        if doc.get("payee_id") != user_id:
            return Response({"detail": "Only the receiver can confirm or dispute this settlement."}, status=status.HTTP_403_FORBIDDEN)

        _settlements_collection().update_one(
            {"_id": obj_id},
            {"$set": {"status": new_status, "updated_at": datetime.now(timezone.utc)}}
        )
        
        doc = _settlements_collection().find_one({"_id": obj_id})
        if doc:
            doc["id"] = str(doc.pop("_id"))
            return Response(doc, status=status.HTTP_200_OK)
        
        return Response({"detail": "Not found"}, status=status.HTTP_404_NOT_FOUND)

class GroupListView(APIView):
    permission_classes = [AllowAny]

    def get(self, request: Request) -> Response:
        user_id = _get_user_id_from_request(request)
        if not user_id:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)
        
        # Find groups where user_id is in the members list
        docs = list(_groups_collection().find({"members": user_id}))
        for doc in docs:
            doc["id"] = str(doc.pop("_id"))
            member_ids = [ObjectId(member_id) for member_id in doc.get("members", []) if ObjectId.is_valid(member_id)]
            users = list(get_db()["users"].find({"_id": {"$in": member_ids}}, {"name": 1, "email": 1}))
            doc["member_details"] = [
                {"id": str(user["_id"]), "name": user.get("name", "Member"), "email": user.get("email", "")}
                for user in users
            ]
        return Response(docs, status=status.HTTP_200_OK)


class GroupExpenseListView(APIView):
    """Return group bills for a member, newest first."""

    permission_classes = [AllowAny]

    def get(self, request: Request, pk: str) -> Response:
        user_id = _get_user_id_from_request(request)
        if not user_id:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)

        group_id = _to_object_id(pk)
        if not group_id:
            return Response({"detail": "Invalid group ID"}, status=status.HTTP_400_BAD_REQUEST)

        group = _groups_collection().find_one({"_id": group_id})
        if not group or user_id not in group.get("members", []):
            return Response({"detail": "Group not found"}, status=status.HTTP_404_NOT_FOUND)

        docs = list(_expenses_collection().find({"group_id": group_id}).sort("created_at", -1))
        for doc in docs:
            doc["id"] = str(doc.pop("_id"))
            doc["group_id"] = str(doc["group_id"])
        return Response(docs, status=status.HTTP_200_OK)

class SubscriptionTrackerView(APIView):
    permission_classes = [AllowAny]

    def get(self, request: Request) -> Response:
        user_id = _get_user_id_from_request(request)
        if not user_id:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)
            
        docs = list(_subscriptions_collection().find({"user_id": user_id}))
        for doc in docs:
            doc["id"] = str(doc.pop("_id"))
        return Response(docs, status=status.HTTP_200_OK)

    def post(self, request: Request) -> Response:
        user_id = _get_user_id_from_request(request)
        if not user_id:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)
            
        data = request.data
        document = {
            "user_id": user_id,
            "name": data.get("name"),
            "cost": float(data.get("cost", 0)),
            "splitCount": int(data.get("splitCount", 1)),
            "emoji": data.get("emoji", "🍿"),
            "nextBilling": data.get("nextBilling", ""),
            "active": data.get("active", True),
            "created_at": datetime.now(timezone.utc),
        }
        
        result = _subscriptions_collection().insert_one(document)
        document["id"] = str(result.inserted_id)
        document.pop("_id", None)
        return Response(document, status=status.HTTP_201_CREATED)

class SubscriptionDetailView(APIView):
    permission_classes = [AllowAny]

    def delete(self, request: Request, pk: str) -> Response:
        user_id = _get_user_id_from_request(request)
        if not user_id:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)
            
        obj_id = _to_object_id(pk)
        if not obj_id:
            return Response({"detail": "Invalid ID"}, status=status.HTTP_400_BAD_REQUEST)
            
        result = _subscriptions_collection().delete_one({"_id": obj_id, "user_id": user_id})
        if result.deleted_count == 1:
            return Response(status=status.HTTP_204_NO_CONTENT)
        return Response({"detail": "Not found"}, status=status.HTTP_404_NOT_FOUND)

class BudgetView(APIView):
    permission_classes = [AllowAny]

    def get(self, request: Request) -> Response:
        user_id = _get_user_id_from_request(request)
        if not user_id:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)
            
        # Get the budget
        doc = _budgets_collection().find_one({"user_id": user_id})
        limit = doc.get("limit", 0) if doc else 0
        
        # Calculate dynamically spent this month
        now = datetime.now(timezone.utc)
        start_of_month = datetime(now.year, now.month, 1, tzinfo=timezone.utc)
        
        # Sum up expenses where user is the payer in the current month
        expenses = _expenses_collection().find({
            "paid_by": user_id,
            "created_at": {"$gte": start_of_month}
        })
        spent = sum(e.get("amount", 0) for e in expenses)
        
        return Response({
            "limit": limit,
            "spent": spent,
            "category": doc.get("category", "General") if doc else "General"
        }, status=status.HTTP_200_OK)

    def post(self, request: Request) -> Response:
        # We'll use POST/PUT interchangeably for setting the limit
        user_id = _get_user_id_from_request(request)
        if not user_id:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)
            
        data = request.data
        limit = float(data.get("limit", 0))
        category = data.get("category", "General")
        
        _budgets_collection().update_one(
            {"user_id": user_id},
            {"$set": {
                "limit": limit,
                "category": category,
                "updated_at": datetime.now(timezone.utc)
            }},
            upsert=True
        )
        
        return self.get(request)
    
        return self.post(request)

class PersonalExpenseView(APIView):
    permission_classes = [AllowAny]

    def post(self, request: Request) -> Response:
        user_id = _get_user_id_from_request(request)
        if not user_id:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)
            
        data = request.data
        document = {
            "paid_by": user_id,
            "title": data.get("title", "Personal Expense"),
            "amount": float(data.get("amount", 0)),
            "category": data.get("category", "misc"),
            "created_at": datetime.now(timezone.utc),
            "type": "personal"
        }
        
        result = _expenses_collection().insert_one(document)
        document["id"] = str(result.inserted_id)
        document.pop("_id", None)
        return Response(document, status=status.HTTP_201_CREATED)

class IncomeView(APIView):
    permission_classes = [AllowAny]

    def post(self, request: Request) -> Response:
        user_id = _get_user_id_from_request(request)
        if not user_id:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)
            
        data = request.data
        document = {
            "user_id": user_id,
            "source": data.get("source", "Income"),
            "amount": float(data.get("amount", 0)),
            "created_at": datetime.now(timezone.utc),
        }
        
        result = _incomes_collection().insert_one(document)
        document["id"] = str(result.inserted_id)
        document.pop("_id", None)
        return Response(document, status=status.HTTP_201_CREATED)

class BalanceSummaryView(APIView):
    permission_classes = [AllowAny]

    def get(self, request: Request) -> Response:
        user_id = _get_user_id_from_request(request)
        if not user_id:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)
        
        total_income = sum(
            inc.get("amount", 0) 
            for inc in _incomes_collection().find({"user_id": user_id})
        )
        
        credit_owed = 0.0
        debt_owed = 0.0
        groups = _groups_collection().find({"members": user_id})
        for group in groups:
            balances = group.get("balances", {})
            for debtor, creditors in balances.items():
                if debtor == user_id:
                    for creditor, amount in creditors.items():
                        debt_owed += float(amount)
                else:
                    if user_id in creditors:
                        credit_owed += float(creditors[user_id])
                        
        total_personal_expenses = sum(
            exp.get("amount", 0)
            for exp in _expenses_collection().find({"paid_by": user_id, "type": "personal"})
        )
        
        subscriptions = _subscriptions_collection().find({"user_id": user_id, "active": True})
        subscription_cost = sum(
            float(sub.get("cost", 0)) / max(int(sub.get("splitCount", 1)), 1)
            for sub in subscriptions
        )
        
        net_balance = total_income + credit_owed - (total_personal_expenses + subscription_cost + debt_owed)
        
        return Response({
            "netBalance": round(net_balance, 2),
            "creditPosition": round(credit_owed, 2),
            "debtPosition": round(debt_owed, 2),
        }, status=status.HTTP_200_OK)

class InsightsView(APIView):
    permission_classes = [AllowAny]

    def get(self, request: Request) -> Response:
        user_id = _get_user_id_from_request(request)
        if not user_id:
            return Response({"detail": "Unauthorized"}, status=status.HTTP_401_UNAUTHORIZED)
            
        now = datetime.now(timezone.utc)
        start_of_month = datetime(now.year, now.month, 1, tzinfo=timezone.utc)
        
        expenses = _expenses_collection().find({
            "paid_by": user_id,
            "created_at": {"$gte": start_of_month}
        })
        
        categories = {}
        total_spent = 0.0
        
        for exp in expenses:
            amount = float(exp.get("amount", 0))
            # Determine category: group expenses usually have 'title', personal have 'category'
            # Fallback to 'misc' if not provided for personal
            cat = exp.get("category", "misc") if exp.get("type") == "personal" else "misc"
            
            categories[cat] = categories.get(cat, 0.0) + amount
            total_spent += amount
            
        # Also add subscriptions to rent/misc if you want, but for now just the expenses
        
        # Format for frontend
        formatted_categories = []
        color_map = {
            'food': '#F43F5E',
            'commute': '#10B981',
            'academics': '#3B82F6',
            'rent': '#8B5CF6',
            'shopping': '#F59E0B',
            'entertainment': '#EC4899',
            'misc': '#64748B'
        }
        
        name_map = {
            'food': '🍔 Food & Drinks',
            'commute': '🚗 Commute & Travel',
            'academics': '📚 Academics & Books',
            'rent': '🏠 Rent & Hostel',
            'shopping': '🛍️ Shopping & Lifestyle',
            'entertainment': '🍿 Entertainment & Movies',
            'misc': '✨ Others / Misc'
        }
        
        for k, v in categories.items():
            formatted_categories.append({
                "name": name_map.get(k, k),
                "spent": round(v, 2),
                "total": max(round(v, 2), 5000), # Mock budget per category just for visual filling
                "color": color_map.get(k, '#64748B')
            })
            
        return Response({
            "totalSpent": round(total_spent, 2),
            "categories": formatted_categories
        }, status=status.HTTP_200_OK)
