package com.spark.social

import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileDiscoveryTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun capture(name:String) {
        val bitmap=rule.onRoot().captureToImage().asAndroidBitmap()
        val file=File(rule.activity.getExternalFilesDir(null),name)
        file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        val fd=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/$name")
        ParcelFileDescriptor.AutoCloseInputStream(fd).use{it.readBytes()}
    }
    @Test fun editorSectionsOpenCorrectFieldAndCamera() {
        var selected="";var photo=""
        rule.runOnUiThread {val vm=SparkViewModel(rule.activity.application);rule.activity.setContent {SparkTheme(false) {
            ProfileEditorContent(vm,json("display_name" to "Spark Tester","bio" to "Hello"),emptyList(),{photo=it},{},{field,_->selected=field.kind})
        }}}
        capture("spark-editor-v1.5.png")
        rule.onNodeWithContentDescription("Change cover photo").performClick()
        rule.runOnIdle{assertEquals("cover_path",photo)}
        rule.onNodeWithContentDescription("Change profile picture").performClick()
        rule.runOnIdle{assertEquals("avatar_path",photo)}
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("School, college or university"))
        rule.onNodeWithText("School, college or university").performClick()
        rule.runOnIdle{assertEquals("education",selected)}
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Phone number"))
        rule.onNodeWithText("Phone number").performClick()
        rule.runOnIdle{assertEquals("phone",selected)}
    }
    @Test fun phoneDefaultsPrivateAndSavesAudienceAndPin() {
        var saved:List<Any>?=null
        var closed=false
        val field=profileSections.flatMap{it.fields}.first{it.kind=="phone"}
        rule.runOnUiThread {rule.activity.setContent {SparkTheme(false) {
            ProfileDetailDialog(field,null,{closed=true},{title,detail,audience,pinned->saved=listOf(title,detail,audience,pinned)})
        }}}
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("01700000000")
        rule.onNode(isSelected()).assertExists()
        rule.onNodeWithText("Pin to intro").performScrollTo()
        rule.onNode(isToggleable()).performClick()
        rule.onNodeWithText("Save").performClick()
        rule.runOnIdle {assertEquals(listOf("01700000000","","private",true),saved);assertTrue(closed)}
    }
    @Test fun suggestionsSendRemoveAndOpenProfile() {
        var sent="";var removed=""
        lateinit var vm:SparkViewModel
        val people=listOf(json("id" to "a","display_name" to "Ayon","reason" to "3 mutual friends"),json("id" to "b","display_name" to "Borna","reason" to "Education in common"))
        rule.runOnUiThread {vm=SparkViewModel(rule.activity.application);rule.activity.setContent {SparkTheme(false) {
            PeopleSuggestions(vm,true,load={people},sendRequest={sent=it.id()},dismiss={removed=it.id()})
        }}}
        rule.onNodeWithText("3 mutual friends").assertIsDisplayed()
        capture("spark-suggestions-v1.5.png")
        rule.onNodeWithText("Ayon").performClick()
        rule.runOnIdle{assertEquals("a",vm.page.id)}
        rule.onAllNodesWithText("Add friend")[0].performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Ayon").assertDoesNotExist()
        rule.onNodeWithText("Remove").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Borna").assertDoesNotExist()
        rule.runOnIdle{assertEquals("a",sent);assertEquals("b",removed)}
    }
}
