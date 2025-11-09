# RapidPhotoUpload Test UI

Simple HTML/JavaScript UI for testing authentication endpoints.

## Usage

### Option 1: Open directly in browser

1. Update the API URL in the UI (defaults to `http://localhost:8080`)
2. Open `index.html` in your browser
3. Test register and login flows

### Option 2: Use a local server (recommended)

**Python:**
```bash
cd test-ui
python3 -m http.server 8000
# Open http://localhost:8000
```

**Node.js (http-server):**
```bash
npx http-server test-ui -p 8000
# Open http://localhost:8000
```

**VS Code:**
- Install "Live Server" extension
- Right-click `index.html` → "Open with Live Server"

## Features

- ✅ Register new account
- ✅ Login with existing account
- ✅ Display JWT token after successful auth
- ✅ Show user info (ID, email, token expiration)
- ✅ Configurable API base URL
- ✅ Error handling and validation

## Testing

1. **Start your Spring Boot app:**
   ```bash
   mvn spring-boot:run
   ```

2. **Open the test UI** (using one of the methods above)

3. **Test Registration:**
   - Enter email and password (min 8 chars)
   - Click "Create Account"
   - Token will be displayed on success

4. **Test Login:**
   - Enter the same email/password
   - Click "Sign In"
   - Token will be displayed

## API Endpoints

- `POST /api/auth/register` - Create new account
- `POST /api/auth/login` - Login with credentials

Both endpoints return:
```json
{
  "userId": "...",
  "email": "...",
  "token": "JWT_TOKEN_HERE",
  "tokenType": "Bearer",
  "expiresIn": 86400000
}
```

## CORS

The backend has CORS enabled for:
- `http://localhost:3000`
- `http://localhost:8080`
- `http://localhost:5500` (VS Code Live Server)
- `file://` (local HTML files)

If you need to add more origins, update `SecurityConfig.java`.

