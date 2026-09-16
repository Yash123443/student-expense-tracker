# Security Policy

## Reporting a vulnerability

Please do not open a public issue for a suspected vulnerability. Contact the
repository owner privately with a description, affected component, and steps to
reproduce. Allow time for the issue to be assessed and fixed before disclosure.

## Secrets and credentials

- Never commit `backend/.env`, MongoDB connection strings, JWT secrets, API keys,
  keystores, or signing keys.
- Start from [`backend/.env.example`](backend/.env.example) and keep real values
  in local environment variables or a deployment secret manager.
- Rotate a credential immediately if it is exposed in a commit, issue, log, or
  chat. Removing it in a later commit does not make it safe.
- Use a separate MongoDB database user with least-privilege access for each
  environment, and restrict Atlas network access where possible.

## Deployment guidance

- Use HTTPS in production; never send login credentials or JWTs over HTTP.
- Set `DJANGO_DEBUG=False`, configure `ALLOWED_HOSTS`, and restrict CORS origins
  before deploying.
- Use strong, unique values for `JWT_SECRET` and `DJANGO_SECRET_KEY`, stored in
  the deployment platform's secret manager.
