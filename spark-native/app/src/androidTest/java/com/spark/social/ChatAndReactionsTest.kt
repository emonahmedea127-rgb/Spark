package com.spark.social
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt
@RunWith(AndroidJUnit4::class)
class ChatAndReactionsTest {
    @get:Rule val rule=createAndroidComposeRule<MainActivity>()
    @Test fun typedMessageStaysAboveSoftwareKeyboard() {
        val command=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("settings put secure show_ime_with_hard_keyboard 1")
        ParcelFileDescriptor.AutoCloseInputStream(command).use {it.readBytes()}
        rule.runOnUiThread {rule.activity.setContent {SparkTheme(false) {
            Scaffold {padding->Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                KeyboardAwareChatColumn {
                    Text("Private conversation")
                    Spacer(Modifier.weight(1f))
                    var text by remember {mutableStateOf("")}
                    MessageComposer(text,{text=it},false,false,{},{})
                }
            }}
        }}}
        rule.onNodeWithText("Message…").performClick()
        rule.onNode(hasSetTextAction()).performTextInput("এই লেখাটি দেখা যাচ্ছে")
        rule.waitUntil(8000) {ViewCompat.getRootWindowInsets(rule.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())==true}
        rule.onNodeWithText("এই লেখাটি দেখা যাচ্ছে").assertIsDisplayed()
        val bottom=rule.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInWindow.bottom
        var keyboardTop=0
        rule.runOnUiThread {
            val decor=rule.activity.window.decorView
            keyboardTop=decor.height-(ViewCompat.getRootWindowInsets(decor)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom?:0)
        }
        assertTrue("Message field is hidden under keyboard: $bottom > $keyboardTop",bottom.roundToInt()<=keyboardTop+1)
    }
    @Test fun reactionListShowsNamesFiltersAndOpensProfile() {
        lateinit var vm:SparkViewModel
        var closed=false
        val people=listOf(json("user_id" to "one","reaction" to "like","person" to json("id" to "one","display_name" to "Ayon")),json("user_id" to "two","reaction" to "love","person" to json("id" to "two","display_name" to "Emon")))
        rule.runOnUiThread {
            vm=SparkViewModel(rule.activity.application)
            rule.activity.setContent {SparkTheme(false) {ReactionPeopleDialog(vm,"post",people,onClose={closed=true},loadPeople={filter,_->people.filter {filter.isBlank()||it.s("reaction")==filter}})}}
        }
        rule.onNodeWithText("Ayon").assertIsDisplayed();rule.onNodeWithText("Emon").assertIsDisplayed()
        rule.onNodeWithText("❤️ 1").performClick()
        rule.onNodeWithText("Ayon").assertDoesNotExist()
        rule.onNodeWithText("Emon").performClick()
        rule.runOnIdle {assertTrue(closed);assertEquals("Profile",vm.page.name);assertEquals("two",vm.page.id)}
    }
}
