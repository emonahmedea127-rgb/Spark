package com.spark.social
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfilePickersTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 @Test fun categoryUsesChoicesAndPreservesPrivateAudience() {
  var saved:List<Any>?=null
  val field=profileSections.flatMap{it.fields}.first{it.kind=="category"}
  rule.runOnUiThread{rule.activity.setContent{SparkTheme(false){ProfileDetailDialog(field,json("id" to "entry","title" to "Artist","visibility" to "private","metadata" to JSONObject()),{}, {title,_,audience,_,_->saved=listOf(title,audience)})}}}
  rule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
  rule.onNodeWithText("Artist").performClick()
  rule.onNodeWithTag("choice:Digital creator").performClick()
  rule.onNodeWithText("Digital creator").assertIsDisplayed()
  rule.onNodeWithText("Save").performClick()
  rule.runOnIdle{assertEquals(listOf("Digital creator","private"),saved)}
 }
 @Test fun locationSearchSelectsCoordinatesAndConfirmsPin() {
  var chosen:MapPlace?=null
  rule.runOnUiThread{rule.activity.setContent{SparkTheme(false){LocationPicker(null,{}, {chosen=it},search={listOf(MapPlace("Gazipur, Bangladesh",24.0023,90.4264))},resolve={_,_->"Gazipur, Bangladesh"},loadTiles=false)}}}
  rule.onNodeWithText("Use this location").assertIsNotEnabled()
  rule.onNode(hasSetTextAction()).performTextInput("Gazipur")
  rule.onNodeWithContentDescription("Search places").performClick()
  rule.onNodeWithText("Gazipur, Bangladesh").performClick()
  rule.onNodeWithText("Use this location").assertIsEnabled().performClick()
  rule.runOnIdle{assertNotNull(chosen);assertEquals(24.0023,chosen!!.latitude,.00001);assertEquals(90.4264,chosen!!.longitude,.00001)}
 }
 @Test fun draggingMapInvalidatesOldPlaceNameAndUsesNewPin() {
  var chosen:MapPlace?=null
  rule.runOnUiThread{rule.activity.setContent{SparkTheme(false){LocationPicker(MapPlace("Old place",23.8,90.4),{}, {chosen=it},resolve={_,_->"New area"},loadTiles=false)}}}
  rule.onNodeWithTag("location-map").performTouchInput{swipeLeft()}
  rule.onNodeWithText("Old place").assertDoesNotExist()
  rule.onNodeWithText("Use this location").performClick()
  rule.runOnIdle{assertEquals("New area",chosen!!.name);assertTrue(chosen!!.longitude>90.4)}
 }
 @Test fun mapProjectionHandlesDateLineAndLatitudeBounds() {
  listOf(24.0 to 90.0,-33.0 to -70.0,85.0 to 179.99).forEach{(lat,lon)->
   val world=mapWorld(lat,lon,13);val result=worldPlace(world.first,world.second,13)
   assertEquals(lat,result.latitude,.000001);assertEquals(lon,result.longitude,.000001)
  }
  assertTrue(worldPlace(-100.0,-100.0,3).latitude<=85.051129)
  assertTrue(worldPlace(-100.0,-100.0,3).longitude in -180.0..180.0)
 }
}
