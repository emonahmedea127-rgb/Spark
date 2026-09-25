package com.spark.social

import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileExperienceTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    private fun capture(name:String,dialog:Boolean=false) {
        val bitmap=if(dialog)InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() else rule.onRoot().captureToImage().asAndroidBitmap()
        val file=File(rule.activity.getExternalFilesDir(null),name)
        file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/$name")).use{it.readBytes()}
    }
    @Test fun nameEditorWritesOnlyName() {
        var saved=""
        rule.runOnUiThread{rule.activity.setContent{SparkTheme(false){IdentityEditor("display_name","Old name",{},onSave={saved=it})}}}
        rule.onAllNodes(hasSetTextAction()).assertCountEquals(1)
        rule.onNodeWithText("Bio").assertDoesNotExist()
        rule.onNode(hasSetTextAction()).performTextReplacement("New name")
        rule.onNodeWithText("Save").performClick()
        rule.runOnIdle{val patch=identityPatch("display_name",saved);assertEquals("New name",patch.s("display_name"));assertFalse(patch.has("bio"));assertEquals(1,patch.length())}
    }
    @Test fun bioEditorCanClearBioWithoutChangingName() {
        var saved:String?=null
        rule.runOnUiThread{rule.activity.setContent{SparkTheme(false){IdentityEditor("bio","My bio",{},onSave={saved=it})}}}
        rule.onNodeWithText("Name").assertDoesNotExist()
        capture("spark-bio-editor-v1.7.png",true)
        rule.onNode(hasSetTextAction()).performTextClearance()
        rule.onNodeWithText("Save").performClick()
        rule.runOnIdle{assertEquals("",saved);val patch=identityPatch("bio",saved!!);assertFalse(patch.has("display_name"));assertEquals("",patch.s("bio"))}
    }
    @Test fun identityValidationAndDiscard() {
        rule.runOnUiThread{rule.activity.setContent{SparkTheme(false){IdentityEditor("display_name","Spark Tester",{},onSave={})}}}
        rule.onNode(hasSetTextAction()).performTextClearance()
        rule.onNodeWithText("Save").assertIsNotEnabled()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.onNodeWithText("Discard changes?").assertIsDisplayed()
        rule.onNodeWithText("Keep editing").performClick()
        rule.onNode(hasSetTextAction()).assertExists()
    }
    @Test fun profileHeaderCountersAndButtonsNavigate() {
        lateinit var vm:SparkViewModel
        var story=false;var photo="";var posts=false
        rule.runOnUiThread{vm=SparkViewModel(rule.activity.application);rule.activity.setContent{SparkTheme(false){Column {
            ProfileHero(vm,json("id" to "me","display_name" to "Spark Tester","bio" to "Good moments, shared together."),true,"Digital creator",json("followers" to 12,"following" to 2,"posts" to 5),{}, {photo=it},{story=true},{posts=true})
        }}}}
        capture("spark-profile-v1.7.png")
        rule.onNodeWithText("12 followers").performClick()
        rule.runOnIdle{assertEquals("Connections",vm.page.name);assertEquals("followers",vm.page.title)}
        rule.onNodeWithText("2 following").performClick()
        rule.runOnIdle{assertEquals("following",vm.page.title)}
        rule.onNodeWithText("5 posts").performClick()
        rule.onNodeWithContentDescription("Change cover photo").performClick()
        rule.onNodeWithText(" Add to story").performClick()
        rule.runOnIdle{assertTrue(posts);assertTrue(story);assertEquals("cover_path",photo)}
        rule.onNodeWithContentDescription("Edit profile").performClick()
        rule.runOnIdle{assertEquals("Edit profile",vm.page.name)}
    }
    @Test fun editorNameAndBioHaveSeparateActions() {
        var field=""
        rule.runOnUiThread{val vm=SparkViewModel(rule.activity.application);rule.activity.setContent{SparkTheme(false){ProfileEditorContent(vm,json("display_name" to "Tester","bio" to "Bio text"),emptyList(),{}, {field="name"},{field="bio"},{_,_->})}}}
        rule.onNodeWithText("Name").performScrollTo().performClick()
        rule.runOnIdle{assertEquals("name",field)}
        rule.onNodeWithText("Bio").performScrollTo().performClick()
        rule.runOnIdle{assertEquals("bio",field)}
    }
}
