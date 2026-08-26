# API quick test

After `python app.py`, open:

`http://127.0.0.1:5000/api/health`

Expected:

```json
{
  "success": true,
  "service": "SmartHealthcare API",
  "status": "online",
  "version": "1.0.0"
}
```

For protected endpoints, login first and send:

`Authorization: Bearer <token>`

The Android app handles this automatically.
