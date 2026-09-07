package com.example.sociva.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [
    UserEntity::class,
    PostEntity::class,
    PostReactionEntity::class,
    CommentEntity::class,
    CommentReactionEntity::class,
    StoryEntity::class,
    ReelEntity::class,
    ConversationEntity::class,
    ConversationMemberEntity::class,
    MessageEntity::class,
    NotificationEntity::class,
    FriendRequestEntity::class,
    FriendshipEntity::class,
    FollowEntity::class,
    PageEntity::class,
    GroupEntity::class,
    ReportEntity::class,
    RelationshipEntity::class,
    PostTagEntity::class,
    CommentMentionEntity::class,
    UserSettingsEntity::class,
    BlockedUserEntity::class,
    ProfileViewEntity::class,
    PostViewEntity::class,
    VideoWatchEventEntity::class,
    PageAdminEntity::class,
    GroupMemberEntity::class,
    GroupJoinRequestEntity::class,
    PageFollowerEntity::class
  ],
  version = 14,
  exportSchema = false
)
abstract class SocivaDatabase : RoomDatabase() {
  abstract fun socivaDao(): SocivaDao

  companion object {
    @Volatile
    private var INSTANCE: SocivaDatabase? = null

    fun getDatabase(context: Context): SocivaDatabase {
      return INSTANCE ?: synchronized(this) {
        val instance = Room.databaseBuilder(
          context.applicationContext,
          SocivaDatabase::class.java,
          "sociva_database"
        )
          .fallbackToDestructiveMigration()
          .build()
        INSTANCE = instance
        instance
      }
    }
  }
}
