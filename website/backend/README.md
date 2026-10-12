# Echo Backend

WebSocket authentication server for Echo Client.

## Configuration

Edit `config.json`:
```json
{
  "httpPort": 3001,
  "wsPort": 6767,
  "mongodbUri": "mongodb://localhost:27017/echo-client",
  "jwtSecret": "your-secret-key-change-this-in-production",
  "rateLimit": {
    "maxAttempts": 6,
    "windowMs": 300000,
    "lockoutMs": 900000,
    "cleanupIntervalMs": 60000
  },
  "session": {
    "heartbeatInterval": 30000,
    "sessionTimeout": 180000
  },
  "security": {
    "enableHwidBinding": true,
    "logFailedAttempts": true
  }
}
```

**Note:** For remote MongoDB, update `mongodbUri`:
```
"mongodbUri": "mongodb://username:password@host:port/database"
```

## Protocol

**New account:**
```bash
node newacc.js -u 'username' -p 'password'
```

**Login:**
```json
{"type": "login", "username": "user", "password": "pass", "hwid": "hwid"}
```

**Response:**
```json
{"type": "login_response", "success": true, "uid": 123, "username": "user"}
```

**Kick:**
```json
{"type": "kick", "message": "reason"}
```

## Deployment

```bash
npm install -g pm2
pm2 start server.js --name echo-backend
pm2 save
```
