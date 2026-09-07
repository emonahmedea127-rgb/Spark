package com.example.sociva.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.sociva.data.model.Comment
import com.example.sociva.data.model.ReactionType
import com.example.sociva.ui.SocivaViewModel
import com.example.sociva.ui.components.ReactionPickerPopup
import com.example.sociva.ui.components.UserAvatar
import com.example.sociva.ui.components.VerifiedBadge
import com.example.sociva.ui.components.formatRelativeTime
import com.example.ui.theme.SocivaBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
  postId: String,
  viewModel: SocivaViewModel,
  onDismiss: () -> Unit
) {
  val comments by viewModel.getPostComments(postId).collectAsState(initial = emptyList())
  val currentUser by viewModel.currentUser.collectAsState()
  val allUsers by viewModel.allUsers.collectAsState()
  var newCommentText by remember { mutableStateOf("") }
  var replyingToComment by remember { mutableStateOf<Comment?>(null) }
  val focusRequester = remember { FocusRequester() }

  // Modals state
  var editingComment by remember { mutableStateOf<Comment?>(null) }
  var deletingComment by remember { mutableStateOf<Comment?>(null) }
  var reportingComment by remember { mutableStateOf<Comment?>(null) }
  var hiddenCommentIds by remember { mutableStateOf(setOf<String>()) }
  val snackbarHostState = remember { SnackbarHostState() }

  val totalCommentsCount = comments.sumOf { 1 + it.repliesCount }

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .fillMaxHeight(0.85f)
          .navigationBarsPadding()
          .imePadding()
      ) {
        // Header
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Comments ($totalCommentsCount)",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
          )

          IconButton(
            onClick = onDismiss,
            modifier = Modifier.testTag("close_comments_button")
          ) {
            Icon(Icons.Default.Close, contentDescription = "Close")
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

        // Comments List
        val visibleComments = comments.filter { it.id !in hiddenCommentIds }

        if (visibleComments.isEmpty()) {
          Box(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .padding(24.dp),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
              )
              Text(
                text = "No comments yet",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Be the first to share your thoughts!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier
              .weight(1f)
              .fillMaxWidth()
              .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            items(visibleComments, key = { it.id }) { comment ->
              CommentThread(
                comment = comment,
                currentUserId = currentUser?.id,
                hiddenCommentIds = hiddenCommentIds,
                onReactionSelect = { targetComment, reaction ->
                  viewModel.reactToComment(targetComment.id, reaction)
                },
                onReactionRemove = { targetComment ->
                  viewModel.removeCommentReaction(targetComment.id)
                },
                onReplyClick = { targetComment ->
                  replyingToComment = targetComment
                  try {
                    focusRequester.requestFocus()
                  } catch (e: Exception) {
                    // Ignore focus exceptions if not attached
                  }
                },
                onEditClick = { targetComment ->
                  editingComment = targetComment
                },
                onDeleteClick = { targetComment ->
                  deletingComment = targetComment
                },
                onReportClick = { targetComment ->
                  reportingComment = targetComment
                },
                onHideClick = { targetComment ->
                  hiddenCommentIds = hiddenCommentIds + targetComment.id
                },
                onAuthorClick = { authorId ->
                  onDismiss()
                  viewModel.navigateToProfile(authorId)
                }
              )
            }
          }
        }

        // Replying To Banner
        AnimatedVisibility(
          visible = replyingToComment != null,
          enter = expandVertically() + fadeIn(),
          exit = shrinkVertically() + fadeOut()
        ) {
          replyingToComment?.let { target ->
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.weight(1f)
                ) {
                  Icon(
                    imageVector = Icons.Default.Reply,
                    contentDescription = null,
                    tint = SocivaBlue,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "Replying to ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Text(
                    text = "@${target.authorName}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = SocivaBlue
                  )
                }

                IconButton(
                  onClick = { replyingToComment = null },
                  modifier = Modifier.size(24.dp)
                ) {
                  Icon(
                    Icons.Default.Close,
                    contentDescription = "Cancel reply",
                    modifier = Modifier.size(16.dp)
                  )
                }
              }
            }
          }
        }

        // Bottom Input Field with @Mention Support
        val lastAtIndex = newCommentText.lastIndexOf('@')
        val mentionQuery = if (lastAtIndex >= 0) {
          val substring = newCommentText.substring(lastAtIndex + 1)
          if (!substring.contains(' ') && substring.length <= 20) substring else null
        } else null

        val matchingUsers = remember(mentionQuery, allUsers) {
          if (mentionQuery == null) emptyList()
          else allUsers.filter {
            it.username.contains(mentionQuery, ignoreCase = true) ||
            it.fullName.contains(mentionQuery, ignoreCase = true)
          }.take(5)
        }

        Surface(
          tonalElevation = 6.dp,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.fillMaxWidth()) {
            if (matchingUsers.isNotEmpty()) {
              LazyRow(
                modifier = Modifier
                  .fillMaxWidth()
                  .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                  .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                items(matchingUsers) { u ->
                  Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.clickable {
                      val beforeAt = newCommentText.substring(0, lastAtIndex)
                      newCommentText = "$beforeAt@${u.username} "
                    }
                  ) {
                    Row(
                      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                      UserAvatar(avatarUrl = u.avatarUrl, name = u.fullName, size = 20.dp)
                      Text(u.fullName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                      Text("@${u.username}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  }
                }
              }
            }

            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              UserAvatar(
                avatarUrl = currentUser?.avatarUrl,
                name = currentUser?.fullName ?: "Me",
                size = 36.dp
              )

              OutlinedTextField(
                value = newCommentText,
                onValueChange = { newCommentText = it },
                placeholder = {
                  Text(
                    if (replyingToComment != null) "Reply to @${replyingToComment?.authorName}..."
                    else "Write a comment..."
                  )
                },
                trailingIcon = {
                  IconButton(
                    onClick = {
                      if (!newCommentText.endsWith("@")) {
                        newCommentText = if (newCommentText.isEmpty() || newCommentText.endsWith(" ")) {
                          "$newCommentText@"
                        } else {
                          "$newCommentText @"
                        }
                      }
                    },
                    modifier = Modifier.size(28.dp)
                  ) {
                    Text("@", fontWeight = FontWeight.ExtraBold, color = SocivaBlue, fontSize = 16.sp)
                  }
                },
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                  .weight(1f)
                  .focusRequester(focusRequester)
                  .testTag("comment_input_field"),
                maxLines = 3
              )

              IconButton(
                onClick = {
                  val text = newCommentText.trim()
                  if (text.isNotBlank()) {
                    val targetParent = replyingToComment
                    if (targetParent != null) {
                      val prefix = if (!text.startsWith("@")) "@${targetParent.authorName} " else ""
                      val parentId = targetParent.parentCommentId ?: targetParent.id
                      viewModel.addComment(postId, prefix + text, parentId)
                      replyingToComment = null
                    } else {
                      viewModel.addComment(postId, text, null)
                    }
                    newCommentText = ""
                  }
                },
                enabled = newCommentText.isNotBlank(),
                modifier = Modifier
                  .size(42.dp)
                  .clip(CircleShape)
                  .background(
                    if (newCommentText.isNotBlank()) SocivaBlue
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                  )
                  .testTag("comment_submit_button")
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Send,
                  contentDescription = "Post Comment",
                  tint = if (newCommentText.isNotBlank()) Color.White
                  else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          }
        }
      }

      // Snackbar Host
      SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(bottom = 70.dp)
      )

      // Edit Comment Dialog
      if (editingComment != null) {
        val target = editingComment!!
        var editText by remember(target.id) { mutableStateOf(target.content) }

        AlertDialog(
          onDismissRequest = { editingComment = null },
          title = { Text("Edit Comment") },
          text = {
            OutlinedTextField(
              value = editText,
              onValueChange = { editText = it },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("edit_comment_input"),
              minLines = 2,
              maxLines = 6
            )
          },
          confirmButton = {
            Button(
              onClick = {
                val trimmed = editText.trim()
                if (trimmed.isNotBlank()) {
                  viewModel.editComment(target.id, trimmed)
                  editingComment = null
                }
              },
              modifier = Modifier.testTag("save_edit_comment_button")
            ) {
              Text("Save")
            }
          },
          dismissButton = {
            TextButton(onClick = { editingComment = null }) {
              Text("Cancel")
            }
          }
        )
      }

      // Delete Comment Confirmation Dialog
      if (deletingComment != null) {
        val target = deletingComment!!
        AlertDialog(
          onDismissRequest = { deletingComment = null },
          title = { Text("Delete Comment") },
          text = {
            Text("Are you sure you want to delete this comment? This will also remove any replies.")
          },
          confirmButton = {
            Button(
              onClick = {
                viewModel.deleteComment(target.id, postId)
                deletingComment = null
              },
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
              modifier = Modifier.testTag("confirm_delete_comment_button")
            ) {
              Text("Delete", color = MaterialTheme.colorScheme.onError)
            }
          },
          dismissButton = {
            TextButton(onClick = { deletingComment = null }) {
              Text("Cancel")
            }
          }
        )
      }

      // Report Comment Dialog
      if (reportingComment != null) {
        val target = reportingComment!!
        var selectedReason by remember { mutableStateOf("Inappropriate content") }
        val reasons = listOf(
          "Inappropriate content",
          "Spam or misleading",
          "Harassment or hate speech",
          "Violence or harmful behavior",
          "False information"
        )

        AlertDialog(
          onDismissRequest = { reportingComment = null },
          title = { Text("Report Comment") },
          text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(
                text = "Why are you reporting this comment?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(8.dp))
              reasons.forEach { reason ->
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { selectedReason = reason }
                    .padding(vertical = 4.dp, horizontal = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  RadioButton(
                    selected = selectedReason == reason,
                    onClick = { selectedReason = reason }
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(text = reason, style = MaterialTheme.typography.bodyMedium)
                }
              }
            }
          },
          confirmButton = {
            Button(
              onClick = {
                viewModel.reportComment(target.id, target.content, selectedReason)
                reportingComment = null
              }
            ) {
              Text("Submit Report")
            }
          },
          dismissButton = {
            TextButton(onClick = { reportingComment = null }) {
              Text("Cancel")
            }
          }
        )
      }
    }
  }
}

@Composable
fun CommentThread(
  comment: Comment,
  currentUserId: String?,
  hiddenCommentIds: Set<String>,
  onReactionSelect: (Comment, ReactionType) -> Unit,
  onReactionRemove: (Comment) -> Unit,
  onReplyClick: (Comment) -> Unit,
  onEditClick: (Comment) -> Unit,
  onDeleteClick: (Comment) -> Unit,
  onReportClick: (Comment) -> Unit,
  onHideClick: (Comment) -> Unit,
  onAuthorClick: (String) -> Unit
) {
  var showReplies by remember { mutableStateOf(true) }
  val visibleReplies = comment.replies.filter { it.id !in hiddenCommentIds }

  Column(modifier = Modifier.fillMaxWidth()) {
    // Top-level comment
    CommentItem(
      comment = comment,
      isReply = false,
      isMine = comment.authorId == currentUserId,
      onReactionSelect = { reaction -> onReactionSelect(comment, reaction) },
      onReactionRemove = { onReactionRemove(comment) },
      onReplyClick = { onReplyClick(comment) },
      onEditClick = { onEditClick(comment) },
      onDeleteClick = { onDeleteClick(comment) },
      onReportClick = { onReportClick(comment) },
      onHideClick = { onHideClick(comment) },
      onAuthorClick = { onAuthorClick(comment.authorId) }
    )

    // Expand/Collapse replies toggle button
    if (visibleReplies.isNotEmpty()) {
      Row(
        modifier = Modifier
          .padding(start = 48.dp, top = 6.dp)
          .clip(RoundedCornerShape(12.dp))
          .clickable { showReplies = !showReplies }
          .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(
          imageVector = if (showReplies) Icons.Default.ExpandLess else Icons.Default.SubdirectoryArrowRight,
          contentDescription = null,
          tint = SocivaBlue,
          modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = if (showReplies) "Hide replies"
          else "View ${visibleReplies.size} ${if (visibleReplies.size == 1) "reply" else "replies"}",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = SocivaBlue
        )
      }
    }

    // Nested replies list
    AnimatedVisibility(
      visible = showReplies && visibleReplies.isNotEmpty(),
      enter = expandVertically() + fadeIn(),
      exit = shrinkVertically() + fadeOut()
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 38.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        visibleReplies.forEach { reply ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            // Visual thread connector line
            Box(
              modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            )

            Box(modifier = Modifier.weight(1f)) {
              CommentItem(
                comment = reply,
                isReply = true,
                isMine = reply.authorId == currentUserId,
                onReactionSelect = { reaction -> onReactionSelect(reply, reaction) },
                onReactionRemove = { onReactionRemove(reply) },
                onReplyClick = { onReplyClick(reply) },
                onEditClick = { onEditClick(reply) },
                onDeleteClick = { onDeleteClick(reply) },
                onReportClick = { onReportClick(reply) },
                onHideClick = { onHideClick(reply) },
                onAuthorClick = { onAuthorClick(reply.authorId) }
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommentItem(
  comment: Comment,
  isReply: Boolean,
  isMine: Boolean,
  onReactionSelect: (ReactionType) -> Unit,
  onReactionRemove: () -> Unit,
  onReplyClick: () -> Unit,
  onEditClick: () -> Unit,
  onDeleteClick: () -> Unit,
  onReportClick: () -> Unit,
  onHideClick: () -> Unit,
  onAuthorClick: () -> Unit
) {
  var showReactionPicker by remember { mutableStateOf(false) }
  var showOptionsMenu by remember { mutableStateOf(false) }

  val avatarSize = if (isReply) 30.dp else 38.dp
  val isEdited = comment.updatedAt > comment.timestamp + 1000L

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    UserAvatar(
      avatarUrl = comment.authorAvatar,
      name = comment.authorName,
      size = avatarSize,
      onClick = onAuthorClick
    )

    Column(modifier = Modifier.weight(1f)) {
      // Comment bubble
      Box(
        modifier = Modifier
          .clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
          .padding(horizontal = 12.dp, vertical = 8.dp)
      ) {
        Column {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier
                .weight(1f)
                .clickable { onAuthorClick() }
            ) {
              Text(
                text = comment.authorName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              if (comment.isAuthorVerified) {
                Spacer(modifier = Modifier.width(4.dp))
                VerifiedBadge(size = 12.dp)
              }
            }

            // Options 3-dots icon
            Box {
              IconButton(
                onClick = { showOptionsMenu = true },
                modifier = Modifier
                  .size(24.dp)
                  .testTag("comment_options_${comment.id}")
              ) {
                Icon(
                  imageVector = Icons.Default.MoreVert,
                  contentDescription = "Comment options",
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(16.dp)
                )
              }

              DropdownMenu(
                expanded = showOptionsMenu,
                onDismissRequest = { showOptionsMenu = false }
              ) {
                if (isMine) {
                  DropdownMenuItem(
                    text = { Text("Edit comment") },
                    leadingIcon = {
                      Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    onClick = {
                      showOptionsMenu = false
                      onEditClick()
                    },
                    modifier = Modifier.testTag("edit_comment_${comment.id}")
                  )
                  DropdownMenuItem(
                    text = { Text("Delete comment", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                      Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    },
                    onClick = {
                      showOptionsMenu = false
                      onDeleteClick()
                    },
                    modifier = Modifier.testTag("delete_comment_${comment.id}")
                  )
                } else {
                  DropdownMenuItem(
                    text = { Text("Report comment") },
                    leadingIcon = {
                      Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    onClick = {
                      showOptionsMenu = false
                      onReportClick()
                    }
                  )
                  DropdownMenuItem(
                    text = { Text("Hide comment") },
                    leadingIcon = {
                      Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    onClick = {
                      showOptionsMenu = false
                      onHideClick()
                    }
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(2.dp))

          // Comment content with highlighted @mentions
          val formattedContent = buildAnnotatedString {
            val words = comment.content.split(" ")
            words.forEachIndexed { index, word ->
              if (word.startsWith("@")) {
                withStyle(
                  style = SpanStyle(
                    color = SocivaBlue,
                    fontWeight = FontWeight.Bold
                  )
                ) {
                  append(word)
                }
              } else {
                append(word)
              }
              if (index < words.size - 1) append(" ")
            }
          }

          Text(
            text = formattedContent,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
      }

      // Actions footer row
      Box(modifier = Modifier.fillMaxWidth()) {
        Row(
          modifier = Modifier.padding(start = 8.dp, top = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Timestamp
          Text(
            text = buildString {
              append(formatRelativeTime(comment.timestamp))
              if (isEdited) append(" • edited")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
          )

          // Reaction Button (Combined click: click toggles, long-click opens picker)
          val myReaction = comment.myReaction
          val reactionTextColor = if (myReaction != null) Color(myReaction.colorHex)
          else MaterialTheme.colorScheme.onSurfaceVariant

          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .combinedClickable(
                onClick = {
                  if (myReaction != null) {
                    onReactionRemove()
                  } else {
                    onReactionSelect(ReactionType.LIKE)
                  }
                },
                onLongClick = {
                  showReactionPicker = true
                }
              )
              .padding(horizontal = 4.dp, vertical = 2.dp)
              .testTag("reaction_button_${comment.id}")
          ) {
            Text(
              text = if (myReaction != null) "${myReaction.emoji} ${myReaction.label}" else "Like",
              style = MaterialTheme.typography.labelSmall,
              fontWeight = if (myReaction != null) FontWeight.Bold else FontWeight.SemiBold,
              color = reactionTextColor,
              fontSize = 12.sp
            )
          }

          // Reaction Count Pill (if any reactions exist)
          if (comment.reactionsCount > 0) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
              modifier = Modifier
                .clickable { showReactionPicker = true }
                .testTag("reaction_count_${comment.id}")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
              ) {
                Text(
                  text = myReaction?.emoji ?: "❤️",
                  fontSize = 11.sp
                )
                Text(
                  text = "${comment.reactionsCount}",
                  style = MaterialTheme.typography.labelSmall,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  fontSize = 11.sp
                )
              }
            }
          }

          // Reply Button
          Text(
            text = "Reply",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .clickable { onReplyClick() }
              .padding(horizontal = 4.dp, vertical = 2.dp)
              .testTag("reply_button_${comment.id}")
          )
        }

        // Reaction Picker Popup Overlay
        if (showReactionPicker) {
          Popup(
            alignment = Alignment.TopStart,
            offset = IntOffset(x = 10, y = -120),
            onDismissRequest = { showReactionPicker = false },
            properties = PopupProperties(focusable = true, dismissOnClickOutside = true)
          ) {
            ReactionPickerPopup(
              onSelectReaction = { reaction ->
                onReactionSelect(reaction)
                showReactionPicker = false
              },
              onDismiss = { showReactionPicker = false }
            )
          }
        }
      }
    }
  }
}
