"""URL routes for the authentication app.

Endpoints to be implemented in views.py:
- POST /api/auth/register/  -> create a new user
- POST /api/auth/login/     -> exchange credentials for a JWT
- POST /api/auth/refresh/   -> refresh a JWT
- GET  /api/auth/me/        -> return the current user profile
"""

from django.urls import path

from . import views


app_name = "authentication"

urlpatterns = [
    path("register/", views.RegisterView.as_view(), name="register"),
    path("login/",    views.LoginView.as_view(),    name="login"),
    path("refresh/",  views.RefreshView.as_view(),  name="refresh"),
    path("me/",       views.MeView.as_view(),       name="me"),
    path("profile/",  views.ProfileView.as_view(),  name="profile"),
    path("users/search/", views.UserSearchView.as_view(), name="user-search"),
    path("friends/add/", views.FriendAddView.as_view(), name="friend-add"),
    path("friends/list/", views.FriendListView.as_view(), name="friend-list"),
]
