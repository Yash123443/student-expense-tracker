"""URL routes for the expenses app."""

from django.urls import path

from .views import (
    AddGroupExpenseView, 
    CreateGroupView, 
    SettlementListView, 
    SettlementDetailView,
    GroupListView,
    GroupExpenseListView,
    SubscriptionTrackerView,
    SubscriptionDetailView,
    BudgetView,
    PersonalExpenseView,
    IncomeView,
    BalanceSummaryView,
    InsightsView
)

app_name = "expenses"

urlpatterns = [
    path("groups/",             GroupListView.as_view(),       name="group-list"),
    path("groups/<str:pk>/expenses/", GroupExpenseListView.as_view(), name="group-expense-list"),
    path("groups/create/",      CreateGroupView.as_view(),     name="group-create"),
    path("expenses/add-group/", AddGroupExpenseView.as_view(), name="expense-add-group"),
    path("settlements/",        SettlementListView.as_view(),  name="settlement-list"),
    path("settlements/<str:pk>/", SettlementDetailView.as_view(), name="settlement-detail"),
    path("subscriptions/",      SubscriptionTrackerView.as_view(), name="subscription-list"),
    path("subscriptions/<str:pk>/", SubscriptionDetailView.as_view(), name="subscription-detail"),
    path("budgets/",            BudgetView.as_view(),          name="budget-detail"),
    path("personal/",           PersonalExpenseView.as_view(), name="expense-personal"),
    path("income/",             IncomeView.as_view(),          name="expense-income"),
    path("balance/",            BalanceSummaryView.as_view(),  name="expense-balance"),
    path("insights/",           InsightsView.as_view(),        name="expense-insights"),
]
