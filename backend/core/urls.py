from django.contrib import admin
from django.urls import include, path
from authentication.views import ProfileView
from expenses.views import SettlementListView, SettlementDetailView

urlpatterns = [
    path("admin/", admin.site.urls),
    path("api/auth/",     include("authentication.urls")),
    path("api/expenses/", include("expenses.urls")),
    path("api/profile/",  ProfileView.as_view(), name="core-profile"),
    path("api/settlements/", SettlementListView.as_view(), name="core-settlements"),
    path("api/settlements/<str:pk>/", SettlementDetailView.as_view(), name="core-settlement-detail"),
]