package com.spark.social
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import org.json.JSONArray
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DiscussionUiTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun show() {
        rule.runOnUiThread {
            val vm=SparkViewModel(rule.activity.application)
            vm.me=json("display_name" to "Emon Ahmed")
            val comment=json("id" to "comment","author_id" to "member","body" to "চমৎকার! আরও এমন ভিডিও চাই 😆","created_at" to "2026-09-23T00:00:00Z","author" to json("display_name" to "Spark member"),"likes" to JSONArray())
            val post=json("id" to "post","body" to "আজকের মজার মুহূর্ত 😁\n#funnyreels","media_path" to "fixture","comments" to JSONArray(listOf(json("count" to 15))),"preview" to JSONArray(listOf(comment)),"reactions" to JSONArray((0 until 35).map {json("user_id" to "user$it","reaction" to if(it%2==0)"haha" else "like")}))
            rule.activity.setContent { MaterialTheme {Surface {Column(Modifier.fillMaxWidth().padding(top=16.dp)) {PostDiscussion(vm,post)} } } }
        }
    }
    @Test fun referenceLayoutShowsCountsCommentAndComposer() {
        show()
        rule.onNodeWithText(" 35").assertIsDisplayed()
        rule.onNodeWithText(" 15").assertIsDisplayed()
        rule.onNodeWithText("Spark member").assertIsDisplayed()
        rule.onNodeWithText("Write a comment…").assertIsDisplayed()
        rule.onNodeWithContentDescription("Like comment").assertIsDisplayed()
        val bitmap=rule.onRoot().captureToImage().asAndroidBitmap()
        val screenshot=File(rule.activity.getExternalFilesDir(null),"spark-discussion-v1.3.png")
        screenshot.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        // connectedAndroidTest uninstalls the app afterwards; preserve the screenshot outside its directory.
        val output=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cp ${screenshot.absolutePath} /data/local/tmp/spark-discussion-v1.3.png")
        ParcelFileDescriptor.AutoCloseInputStream(output).use {it.readBytes()}
    }
    @Test fun longPressOffersSixReactions() {
        show()
        rule.onNodeWithContentDescription("Like; hold to choose reaction").performTouchInput {longClick()}
        listOf("like","love","haha","wow","sad","angry").forEach {rule.onNodeWithContentDescription("React $it").assertIsDisplayed()}
    }
    @Test fun replyAndEmojiComposerAreInteractive() {
        show()
        rule.onNodeWithText("Reply",substring=false).performClick()
        rule.onNodeWithText("Replying to Spark member").assertIsDisplayed()
        rule.onNodeWithContentDescription("Cancel reply").performClick()
        rule.onNodeWithContentDescription("Add emoji").performClick()
        rule.onNodeWithText("🔥").performClick()
        rule.onNodeWithContentDescription("Send comment").assertIsDisplayed()
        rule.onNode(hasSetTextAction()).assertTextContains("🔥")
    }
}
