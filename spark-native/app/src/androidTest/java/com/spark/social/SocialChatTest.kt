package com.spark.social
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray

@RunWith(AndroidJUnit4::class)
class SocialChatTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 @Test fun tappingCommentOpensReactionsAndReply(){
  var reply=false
  rule.runOnUiThread{val vm=SparkViewModel(rule.activity.application);rule.activity.setContent{SparkTheme(false){CommentRow(vm,json("id" to "c","author_id" to "other","body" to "Tap this comment","author" to json("display_name" to "Member"),"likes" to JSONArray()),{reply=true},{})}}}
  rule.onNodeWithText("Tap this comment").performClick()
  listOf("like","love","haha","wow","sad","angry").forEach{rule.onNodeWithContentDescription("Comment react $it").assertIsDisplayed()}
  rule.onNodeWithText(" Edit comment").assertDoesNotExist()
  rule.onNodeWithText(" Reply").performClick()
  rule.runOnIdle{assertTrue(reply)}
 }
 @Test fun ownCommentOffersEditAndDelete(){
  var action=""
  rule.runOnUiThread{rule.activity.setContent{SparkTheme(false){CommentActionSheet(true,{action=it},{action="reply"},{action="edit"},{action="delete"},{})}}}
  rule.onNodeWithText(" Edit comment").performClick();rule.runOnIdle{assertEquals("edit",action)}
  rule.onNodeWithText(" Delete comment").performClick();rule.runOnIdle{assertEquals("delete",action)}
 }
 @Test fun ownMessageOffersEditAndUnsend(){
  var action=""
  rule.runOnUiThread{rule.activity.setContent{SparkTheme(false){MessageActions(true,true,{action="edit"},{action="unsend"},{})}}}
  rule.onNodeWithText(" Edit message").performClick();rule.runOnIdle{assertEquals("edit",action)}
  rule.onNodeWithText(" Unsend for everyone").performClick();rule.runOnIdle{assertEquals("unsend",action)}
 }
 @Test fun receiptsShowActualSeenAndOpenedTime(){
  val m=json("seen_at" to "2026-09-25T12:30:00Z")
  assertTrue(messageReceipt(m).startsWith("Seen · "));assertTrue(messageReceipt(m).contains("25 Sep"))
  m.put("view_once",true).put("opened_at","2026-09-25T12:31:00Z");assertTrue(messageReceipt(m).startsWith("Opened · "))
  m.put("unsent_at","2026-09-25T12:32:00Z");assertEquals("Unsent",messageReceipt(m));assertEquals("Sent",messageReceipt(json()))
 }
 @Test fun openedViewOncePhotoCannotBeOpenedAgain(){
  rule.runOnUiThread{val vm=SparkViewModel(rule.activity.application);rule.activity.setContent{SparkTheme(false){ChatMessage(vm,json("id" to "m","sender_id" to "other","body" to "View once photo","view_once" to true,"opened_at" to "2026-09-25T12:31:00Z"))}}}
  rule.onNodeWithText("Opened").assertIsNotEnabled()
  rule.onNodeWithText("Open photo once").assertDoesNotExist()
 }
}
