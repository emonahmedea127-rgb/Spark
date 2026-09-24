@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.spark.social

import android.app.DatePickerDialog
import android.net.Uri
import android.util.Patterns
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

@Composable fun ProfileDetailDialog(field:ProfileField,entry:JSONObject?,onClose:()->Unit,onSave:suspend(String,String,String,Boolean,JSONObject)->Unit,onDelete:(suspend()->Unit)?=null,api:SparkApi?=null) {
 var title by rememberSaveable(field.kind,entry?.id()){mutableStateOf(entry?.s("title")?:"")}
 var metadataText by rememberSaveable(field.kind,entry?.id()){mutableStateOf(entry?.child("metadata")?.toString()?:"{}")}
 val metadata=remember(metadataText){JSONObject(metadataText)}
 fun set(key:String,value:Any){metadataText=JSONObject(metadataText).put(key,value).toString()}
 var visibility by rememberSaveable(field.kind,entry?.id()){mutableStateOf(entry?.s("visibility")?:if(field.privateDefault)"private" else "public")}
 var pinned by rememberSaveable(field.kind,entry?.id()){mutableStateOf(entry?.optBoolean("pinned")?:false)}
 var picker by remember{mutableStateOf<String?>(null)};var map by remember{mutableStateOf(false)}
 var error by remember{mutableStateOf<String?>(null)};var busy by remember{mutableStateOf(false)}
 var confirmDelete by remember{mutableStateOf(false)}
 val scope=rememberCoroutineScope();val context=LocalContext.current
 fun calendar(key:String){
  val old=runCatching{LocalDate.parse(metadata.s(key))}.getOrDefault(LocalDate.now().minusYears(if(key=="birthdate")18 else 0))
  DatePickerDialog(context,{_,year,month,day->val date=LocalDate.of(year,month+1,day);set(key,date.toString());if(key=="birthdate")title=date.format(DateTimeFormatter.ofPattern("d MMMM yyyy"))},old.year,old.monthValue-1,old.dayOfMonth).apply{datePicker.maxDate=System.currentTimeMillis();show()}
 }
 fun save(delete:Boolean=false){scope.launch {busy=true;error=null;try{
  if(delete)onDelete?.invoke() else {
   require(title.trim().isNotEmpty()){ "Choose ${field.label.lowercase()}." }
   require(title.trim().length<=160){"Keep this value under 160 characters."}
   if(field.kind in mapProfileKinds&&entry?.s("title")!=title)require(metadata.has("latitude")&&metadata.has("longitude")){"Choose a location on the map."}
   if(field.kind in listOf("link","media_kit"))require(Uri.parse(title.trim()).let{it.scheme in listOf("http","https")&&!it.host.isNullOrBlank()}){"Enter a full https:// address."}
   if(field.kind=="email")require(Patterns.EMAIL_ADDRESS.matcher(title.trim()).matches()){"Enter a valid email address."}
   val savedTitle=if(field.kind=="phone"){
    val number=title.replace(Regex("[\\s()-]"),"")
    require(number.matches(Regex("\\+?[0-9]{6,15}"))){"Enter a valid phone number."}
    if(number.startsWith("+"))number else metadata.s("country_code").ifBlank{"+880 Bangladesh"}.substringBefore(' ')+number.trimStart('0')
   }else title.trim()
   if(field.kind=="work"&&metadata.s("start").isNotBlank()&&metadata.s("end").isNotBlank()&&!metadata.optBoolean("current"))require(metadata.s("end")>=metadata.s("start")){"End date must be after start date."}
   val detail=profileDetailSummary(field.kind,metadata,entry?.s("detail")?:"")
   require(detail.length<=500){"Shorten the work or education details."}
   onSave(savedTitle,detail,visibility,pinned,metadata)
  };onClose()
 }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message?:"Couldn't save. Try again."}finally{busy=false}}}
 Dialog(onDismissRequest={if(!busy)onClose()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
  Surface(Modifier.fillMaxSize()){Scaffold(topBar={TopAppBar(title={Text(field.label,fontSize=21.sp,fontWeight=FontWeight.Bold)},navigationIcon={IconButton(enabled=!busy,onClick=onClose){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back")}},actions={TextButton(enabled=!busy&&title.isNotBlank(),onClick={save()}){Text(if(busy)"Saving…" else "Save",fontWeight=FontWeight.Bold)}})}) {padding->
   Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding().verticalScroll(rememberScrollState()).padding(18.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
    when {
     field.kind in mapProfileKinds->{
      SelectionRow(field.label,title.ifBlank{"Choose on map"},Icons.Outlined.Map,!busy){map=true}
      if(metadata.has("latitude"))Text("A selected map pin will be saved with the audience below.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
     }
     field.kind=="birthday"->SelectionRow("Date of birth",title.ifBlank{"Choose date"},Icons.Outlined.CalendarMonth,!busy){calendar("birthdate")}
     field.kind in profileChoices.keys||field.kind in listOf("family","community")->SelectionRow(field.label,title.ifBlank{"Choose ${field.label.lowercase()}"},field.icon,!busy){picker=field.kind}
     field.kind=="social"->{SelectionRow("Platform",metadata.s("platform").ifBlank{"Choose platform"},Icons.Outlined.AlternateEmail,!busy){picker="platform"};ProfileInput(title,{title=it},"Username or profile URL",busy,KeyboardType.Uri)}
     field.kind=="phone"->{SelectionRow("Country code",metadata.s("country_code").ifBlank{"+880 Bangladesh"},Icons.Outlined.Public,!busy){picker="country_code"};ProfileInput(title,{title=it},"Phone number",busy,KeyboardType.Phone)}
     field.kind=="email"->ProfileInput(title,{title=it},"Email address",busy,KeyboardType.Email)
     field.kind in listOf("link","media_kit")->ProfileInput(title,{title=it},"https:// website address",busy,KeyboardType.Uri)
     field.kind=="offer"->{SelectionRow("Offer type",metadata.s("offer_type").ifBlank{"Choose offer type"},Icons.Outlined.LocalOffer,!busy){picker="offer_type"};ProfileInput(title,{title=it},"Offer title",busy);ProfileInput(metadata.s("url"),{set("url",it)},"Link or discount code",busy)}
    }
    when(field.kind){
     "work"->{
      ProfileInput(metadata.s("role"),{set("role",it)},"Job title",busy)
      SelectionRow("Employment type",metadata.s("employment").ifBlank{"Choose type"},Icons.Outlined.WorkOutline,!busy){picker="employment"}
      SelectionRow("Start date",metadata.s("start").ifBlank{"Choose date"},Icons.Outlined.CalendarMonth,!busy){calendar("start")}
      Row(verticalAlignment=Alignment.CenterVertically){Switch(metadata.optBoolean("current"),{set("current",it)},enabled=!busy);Text("I currently work here",Modifier.padding(start=12.dp))}
      if(!metadata.optBoolean("current"))SelectionRow("End date",metadata.s("end").ifBlank{"Choose date"},Icons.Outlined.CalendarMonth,!busy){calendar("end")}
     }
     "education"->{SelectionRow("Qualification",metadata.s("degree").ifBlank{"Choose qualification"},Icons.Outlined.School,!busy){picker="degree"};ProfileInput(metadata.s("subject"),{set("subject",it)},"Field of study (optional)",busy);SelectionRow("Graduation year",metadata.s("year").ifBlank{"Choose year"},Icons.Outlined.CalendarMonth,!busy){picker="year"}}
     "family"->SelectionRow("Relationship",metadata.s("family_role").ifBlank{"Choose relationship"},Icons.Outlined.PeopleOutline,!busy){picker="family_role"}
    }
    HorizontalDivider()
    SelectionRow("Who can see this?",audienceLabel(visibility),when(visibility){"public"->Icons.Outlined.Public;"friends"->Icons.Outlined.PeopleOutline;else->Icons.Outlined.Lock},!busy){picker="audience"}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Checkbox(pinned,{pinned=it},enabled=!busy);Column(Modifier.padding(start=8.dp)){Text("Pin to intro",fontWeight=FontWeight.SemiBold);Text("Keeps the audience you choose",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
    error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
    if(onDelete!=null)OutlinedButton(enabled=!busy,onClick={confirmDelete=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.DeleteOutline,null);Text(" Delete detail")}
   }
  }}
  if(confirmDelete)AlertDialog(onDismissRequest={if(!busy)confirmDelete=false},title={Text("Delete this detail?")},text={Column{Text("This detail will be removed from your profile.");error?.let{Text(it,color=MaterialTheme.colorScheme.error)}}},confirmButton={TextButton(enabled=!busy,onClick={save(true)}){Text("Delete")}},dismissButton={TextButton(enabled=!busy,onClick={confirmDelete=false}){Text("Cancel")}})
  if(map)LocationPicker(if(metadata.has("latitude"))MapPlace(title,metadata.optDouble("latitude"),metadata.optDouble("longitude"))else null,{map=false},{place->title=place.name;set("latitude",place.latitude);set("longitude",place.longitude);set("source","map");map=false})
  picker?.let {kind->
   val options=when(kind){"audience"->listOf("Public","Friends","Only me");"year"->(LocalDate.now().year+10 downTo 1950).map{it.toString()};else->profileChoices[kind].orEmpty()}
   val value=when(kind){"audience"->audienceLabel(visibility);field.kind->title;else->metadata.s(kind)}
   ProfileOptionPicker(kind,if(kind==field.kind)field.label else kind.replace('_',' ').replaceFirstChar{it.uppercase()},value,options,api,onClose={picker=null}) {chosen,id->
    when(kind){"audience"->visibility=when(chosen){"Public"->"public";"Friends"->"friends";else->"private"};field.kind->{title=chosen;if(id.isNotBlank())set("reference_id",id)};else->set(kind,chosen)}
    picker=null
   }
  }
 }
}
@Composable private fun ProfileInput(value:String,onChange:(String)->Unit,label:String,busy:Boolean,type:KeyboardType=KeyboardType.Text){OutlinedTextField(value,onChange,label={Text(label)},enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=type),modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp),singleLine=true)}
@Composable fun SelectionRow(label:String,value:String,icon:androidx.compose.ui.graphics.vector.ImageVector,enabled:Boolean=true,onClick:()->Unit) {
 OutlinedCard(onClick=onClick,enabled=enabled,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp)){Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically){Icon(icon,null,Modifier.size(26.dp));Column(Modifier.weight(1f).padding(horizontal=14.dp)){Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,Modifier.padding(top=4.dp),fontWeight=FontWeight.SemiBold,fontSize=17.sp)};Icon(Icons.Outlined.ExpandMore,null)}}
}
@Composable fun ProfileOptionPicker(kind:String,label:String,current:String,options:List<String>,api:SparkApi?,onClose:()->Unit,onChoose:(String,String)->Unit) {
 var query by rememberSaveable{mutableStateOf("")};var online by remember{mutableStateOf<List<Pair<String,String>>>(emptyList())}
 var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
 val scope=rememberCoroutineScope()
 val canCustom=kind !in fixedProfileChoices&&kind !in setOf("audience","year","family_role","employment","degree","platform","country_code","offer_type","family","community")
 val canSearchOnline=api!=null&&kind in setOf("work","education","family","community")
 val choices=remember(options,online,current,query){((if(current.isNotBlank())listOf(current to "")else emptyList())+online+options.map{it to ""}).distinctBy{it.first.lowercase()}.filter{it.first.contains(query,true)}}
 Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)){Surface(Modifier.fillMaxSize()){Scaffold(topBar={TopAppBar(title={Text("Choose $label",fontSize=20.sp)},navigationIcon={IconButton(onClick=onClose){Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Back to editor")}})}){padding->Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()){
  if(options.size>6||canCustom||canSearchOnline)OutlinedTextField(query,{query=it;error=null},label={Text("Search $label")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp),leadingIcon={Icon(Icons.Outlined.Search,null)})
  if(canSearchOnline)TextButton(enabled=!busy&&query.trim().length>=2,onClick={scope.launch{busy=true;error=null;try{
   val term=query.trim().replace(Regex("[%_*(),]")," ").trim();val encoded=Uri.encode("%$term%")
   online=when(kind){
    "family"->api!!.rows("profiles","select=id,display_name&display_name=ilike.$encoded&limit=30").map{it.s("display_name") to it.id()}
    "community"->api!!.rows("communities","select=id,name&name=ilike.$encoded&limit=30").map{it.s("name") to it.id()}
    else->api!!.rows("profile_details","select=title&kind=eq.$kind&visibility=eq.public&title=ilike.$encoded&limit=50").map{it.s("title") to ""}
   };if(online.isEmpty())error="No matching Spark entries found."
  }catch(e:CancellationException){throw e}catch(_:Exception){error="Search couldn't load. Try again."}finally{busy=false}}},modifier=Modifier.padding(horizontal=12.dp)){Text(if(busy)"Searching…" else if(kind=="family")"Search people on Spark" else if(kind=="community")"Search Spark communities" else "Search shared profile entries")}
  error?.let{Text(it,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
  LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(vertical=8.dp)){
   items(choices,key={it.first}){(value,id)->ListItem(headlineContent={Text(value)},trailingContent={if(value==current)Icon(Icons.Outlined.Check,null,tint=MaterialTheme.colorScheme.primary)},modifier=Modifier.fillMaxWidth().clickable{onChoose(value,id)}.testTag("choice:$value"));HorizontalDivider(Modifier.padding(horizontal=16.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.4f))}
   if(choices.isEmpty())item{Text(if(canCustom)"No matching choice. You can add your own below." else "No matching options.",Modifier.padding(20.dp))}
   if(canCustom&&query.trim().isNotEmpty()&&choices.none{it.first.equals(query.trim(),true)})item{TextButton(onClick={onChoose(query.trim().take(160),"")},modifier=Modifier.padding(12.dp)){Icon(Icons.Outlined.Add,null);Text(" Add “${query.trim().take(60)}”")}}
  }
 }}}}
}
