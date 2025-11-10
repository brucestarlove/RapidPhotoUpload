## Extra Phase 7: Image Deletion & Restoration - Implementation Summary

### Database
- Created `V6__add_soft_delete_to_photos.sql` migration with `deleted_at` column and indexes

### Domain Model
- Updated `Photo` entity with:
  - `deletedAt` field
  - `markDeleted()` method
  - `restore()` method
  - `isDeleted()` helper method

### Repositories
- Updated `PhotoQueryRepository` to exclude soft-deleted photos in all queries
- Added `findByUserIdAndDeletedAtIsNotNull()` for trash view
- Added `findByUserIdAndDeletedAtBefore()` for permanent deletion queries
- Updated `PhotoRepository` and `JpaPhotoRepository` with permanent deletion support

### Handlers
- `DeletePhotoHandler` - Soft deletes photos
- `RestorePhotoHandler` - Restores soft-deleted photos
- `PermanentDeleteHandler` - Permanently deletes photos after 7-day retention (with S3 cleanup)
- `ListTrashHandler` - Lists soft-deleted photos with tags and thumbnails
- `S3CleanupService` - Handles S3 object deletion (original + thumbnails)

### API Endpoints
- `DELETE /commands/photos/{photoId}` - Soft delete photo
- `POST /commands/photos/{photoId}/restore` - Restore photo from trash
- `DELETE /commands/photos/{photoId}/permanent` - Permanently delete photo (7-day check)
- `GET /queries/photos/trash` - List deleted photos (trash view)

### DTOs
- Updated `PhotoListItem` to include optional `deletedAt` field

### Query Updates
- Updated `ListPhotosHandler` to exclude deleted photos (via repository queries)
- Updated `GetPhotoMetadataHandler` to return 404 for deleted photos

All code compiles successfully. The implementation follows the plan: soft-deleted photos are excluded from normal queries, can be restored, and S3 objects are only deleted on permanent deletion after the 7-day retention period.
