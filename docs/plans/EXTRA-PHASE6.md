## Backend API Requirements for Web's Phase 3

### 1. Tagging System

**Add Tag to Photo**
- `POST /commands/photos/{photoId}/tags`
- Request Body: `{ "tag": "string" }`
- Response: `204 No Content` or `200 OK`
- Validates tag format (alphanumeric, spaces, hyphens, underscores, max 50 chars)
- Ensures photo ownership

**Remove Tag from Photo**
- `DELETE /commands/photos/{photoId}/tags/{tag}`
- URL-encode the tag name
- Response: `204 No Content`
- Ensures photo ownership

**Get All Tags (for autocomplete)**
- `GET /queries/tags`
- Response: `{ "tags": ["tag1", "tag2", ...] }`
- Returns all unique tags for the authenticated user
- Sorted alphabetically (optional but helpful)

### 2. Search Functionality

**Search Photos**
- `GET /queries/photos?search={query}`
- Searches by filename (case-insensitive, partial match)
- Combines with existing filters (`status`, `tag`, `page`, `size`)
- Returns same `PhotoListResponse` format

### 3. Enhanced Photo List Endpoint

**Current endpoint to enhance:**
- `GET /queries/photos`

**Query Parameters:**
- `page` (number, default: 0)
- `size` (number, default: 50)
- `status` (string: "COMPLETED", "PROCESSING", "UPLOADING", "QUEUED", "FAILED")
- `tag` (string) - filter by single tag
- `search` (string) - search by filename

**Response Format:**
```json
{
  "items": [
    {
      "photoId": "ph_...",
      "filename": "photo.jpg",
      "mimeType": "image/jpeg",
      "bytes": 1024000,
      "status": "COMPLETED",
      "width": 1920,
      "height": 1080,
      "thumbnailUrl": "https://s3...",
      "createdAt": "2025-01-09T12:00:00Z",
      "tags": ["vacation", "beach"]
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 150,
  "totalPages": 3
}
```

**Notes:**
- Include `tags` array in `PhotoSummary` items
- Support combining `status`, `tag`, and `search` parameters
- Pagination should work with all filters

### 4. Photo Metadata Endpoint

**Current endpoint (should already include tags):**
- `GET /queries/photos/{photoId}`

**Response should include:**
- `tags: string[]` - array of tag names
- `exifJson: Record<string, any>` - EXIF data (already supported)

### 5. Optional Enhancements (Nice to Have)

**Multiple Tag Filtering:**
- Support `tags` query parameter as comma-separated or array
- Example: `GET /queries/photos?tags=vacation,beach`
- Currently frontend filters multiple tags client-side, but backend support would be better

**Date Range Filtering:**
- Support `startDate` and `endDate` query parameters
- Filter by `createdAt` date range
- Currently frontend filters client-side

**Sorting:**
- Support `sortBy` parameter (e.g., "name", "date", "size")
- Support `sortOrder` parameter ("asc", "desc")
- Currently frontend sorts client-side

### Summary

**Required endpoints:**
1. `POST /commands/photos/{photoId}/tags` - Add tag
2. `DELETE /commands/photos/{photoId}/tags/{tag}` - Remove tag
3. `GET /queries/tags` - Get all tags
4. `GET /queries/photos?search={query}` - Search functionality
5. `GET /queries/photos` - Include `tags` in response items

**Current endpoints to verify:**
- `GET /queries/photos/{photoId}` - Should include `tags` array
- `GET /queries/photos` - Should support `tag` and `search` query parameters

All endpoints require JWT authentication. Tag operations should validate photo ownership.
