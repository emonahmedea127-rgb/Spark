@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.spark.social

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONObject

data class ProfileField(val kind:String,val label:String,val icon:ImageVector,val hint:String="",val privateDefault:Boolean=false)
data class ProfileSection(val title:String,val fields:List<ProfileField>)
val profileSections=listOf(
    ProfileSection("Category",listOf(ProfileField("category","Category",Icons.Outlined.Category,"Digital creator, artist…"),ProfileField("ai_creator","AI creator",Icons.Outlined.AutoAwesome,"Yes or No"))),
    ProfileSection("Personal details",listOf(ProfileField("city","Current city",Icons.Outlined.LocationOn),ProfileField("hometown","Hometown",Icons.Outlined.Home),ProfileField("birthday","Birthday",Icons.Outlined.Cake,"Day, month and year",true),ProfileField("relationship","Relationship",Icons.Outlined.FavoriteBorder),ProfileField("family","Family",Icons.Outlined.PeopleOutline,"Name and relationship",true),ProfileField("gender","Gender",Icons.Outlined.PersonOutline),ProfileField("languages","Languages",Icons.Outlined.Language))),
    ProfileSection("Links",listOf(ProfileField("link","Websites, blogs, portfolios",Icons.Outlined.Link,"https://…"))),
    ProfileSection("Communities",listOf(ProfileField("community","Communities",Icons.Outlined.Groups))),
    ProfileSection("Offers",listOf(ProfileField("offer","Promotions or affiliate links",Icons.Outlined.LocalOffer))),
    ProfileSection("Work",listOf(ProfileField("work","Work experience",Icons.Outlined.WorkOutline,"Company or organisation"))),
    ProfileSection("Education",listOf(ProfileField("education","School, college or university",Icons.Outlined.School,"School or university name"))),
    ProfileSection("Hobbies",listOf(ProfileField("hobby","Hobbies",Icons.Outlined.Palette))),
    ProfileSection("Interests",listOf(ProfileField("music","Music",Icons.Outlined.MusicNote),ProfileField("tv","TV shows",Icons.Outlined.Tv),ProfileField("film","Films",Icons.Outlined.Movie),ProfileField("game","Games",Icons.Outlined.SportsEsports),ProfileField("sport","Sports teams and athletes",Icons.Outlined.SportsSoccer))),
    ProfileSection("Travel",listOf(ProfileField("travel","Places",Icons.Outlined.Place))),
    ProfileSection("Contact info",listOf(ProfileField("social","Social media",Icons.Outlined.AlternateEmail),ProfileField("phone","Phone number",Icons.Outlined.Phone,privateDefault=true),ProfileField("email","Email address",Icons.Outlined.Email,privateDefault=true),ProfileField("media_kit","Media kit",Icons.Outlined.Description)))
)
fun audienceLabel(value:String)=when(value){"public"->"Public";"friends"->"Friends";else->"Only me"}
@Composable fun AudienceLine(value:String) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        Icon(when(value){"public"->Icons.Outlined.Public;"friends"->Icons.Outlined.PeopleOutline;else->Icons.Outlined.Lock},null,Modifier.size(14.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        Text(audienceLabel(value),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun EditProfileScreen(vm:SparkViewModel) {
    var selected by remember {mutableStateOf<ProfileField?>(null)}
    var entry by remember {mutableStateOf<JSONObject?>(null)}
    var basic by remember {mutableStateOf<String?>(null)}
    var photoField by rememberSaveable {mutableStateOf("avatar_path")}
    var cropUri by remember {mutableStateOf<Uri?>(null)}
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){cropUri=it}
    cropUri?.let {ProfilePhotoCropper(vm,it,photoField){cropUri=null}}
    Rows(vm,"profile-editor",{vm.api.rows("profile_details","owner_id=eq.${vm.api.userId}&order=created_at.asc,id.asc")}) {details->
        ProfileEditorContent(vm,vm.me?:JSONObject(),details,onPhoto={photoField=it;pick.launch("image/*")},onName={basic="display_name"},onBio={basic="bio"},onEdit={field,row->selected=field;entry=row})
    }
    basic?.let {field->IdentityEditor(field,vm.me?.s(field).orEmpty(),onClose={basic=null}) {value->
        vm.api.update("profiles","id=eq.${vm.api.userId}",identityPatch(field,value))
        vm.me=vm.api.ensureProfile();vm.refresh()
    }}
    selected?.let {field->ProfileDetailDialog(field,entry,onClose={selected=null},onSave={title,detail,visibility,pinned,metadata->
        val body=json("title" to title,"detail" to detail,"visibility" to visibility,"pinned" to pinned,"metadata" to metadata)
        if(entry==null)vm.api.insert("profile_details",body.put("owner_id",vm.api.userId).put("kind",field.kind))
        else vm.api.update("profile_details","id=eq.${entry!!.id()}",body)
        vm.refresh()
    },onDelete=entry?.let {row->{vm.api.delete("profile_details","id=eq.${row.id()}");vm.refresh()}},api=vm.api)}
}
@Composable fun ProfileEditorContent(vm:SparkViewModel,profile:JSONObject,details:List<JSONObject>,onPhoto:(String)->Unit,onName:()->Unit,onBio:()->Unit,onEdit:(ProfileField,JSONObject?)->Unit) {
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=32.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(310.dp)) {
                Box(Modifier.fillMaxWidth().height(238.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    if(profile.s("cover_path").isNotBlank())PrivateImage(vm,profile.s("cover_path"),Modifier.fillMaxSize())
                    else Icon(Icons.Outlined.Landscape,null,Modifier.align(Alignment.Center).size(64.dp),tint=MaterialTheme.colorScheme.outline)
                    IconButton(onClick={onPhoto("cover_path")},modifier=Modifier.align(Alignment.BottomEnd).padding(12.dp).background(MaterialTheme.colorScheme.surface,CircleShape)) {Icon(Icons.Outlined.PhotoCamera,"Change cover photo")}
                }
                Surface(Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(78.dp),shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp)){}
                Box(Modifier.align(Alignment.BottomCenter).border(5.dp,MaterialTheme.colorScheme.surface,CircleShape).padding(5.dp)) {
                    Avatar(vm,profile,158){onPhoto("avatar_path")}
                    IconButton(onClick={onPhoto("avatar_path")},modifier=Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.surface,CircleShape).border(1.dp,MaterialTheme.colorScheme.outlineVariant,CircleShape)) {Icon(Icons.Outlined.PhotoCamera,"Change profile picture")}
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal=16.dp)) {
                ProfileSectionHeading("Intro")
                ProfileEditorRow(Icons.Outlined.PersonOutline,"Name",profile.s("display_name"),"public",onName)
                ProfileEditorRow(Icons.Outlined.WavingHand,"Bio",profile.s("bio").ifBlank{"Introduce yourself"},"public",onBio)
                Row(Modifier.fillMaxWidth().padding(vertical=14.dp),verticalAlignment=Alignment.Top) {
                    Icon(Icons.Outlined.PushPin,null,Modifier.size(26.dp));Column(Modifier.padding(start=16.dp)) {
                        Text("Pinned details",fontWeight=FontWeight.Bold,fontSize=17.sp)
                        Text(details.filter{it.optBoolean("pinned")}.joinToString(" · "){it.s("title")}.ifBlank{"Choose “Pin to intro” while editing a detail."},color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        profileSections.forEach {section->
            item(key="section:${section.title}") {
                var expanded by rememberSaveable {mutableStateOf(true)}
                Column(Modifier.padding(horizontal=16.dp)) {
                    Row(Modifier.fillMaxWidth().clickable{expanded=!expanded}.padding(top=20.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text(section.title,Modifier.weight(1f),fontWeight=FontWeight.Bold,fontSize=21.sp)
                        Icon(if(expanded)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,"Toggle ${section.title}")
                    }
                    if(expanded)section.fields.forEach {field->
                        val rows=details.filter{it.s("kind")==field.kind}
                        rows.forEach {row->ProfileEditorRow(field.icon,row.s("title"),row.s("detail"),row.s("visibility"),{onEdit(field,row)})}
                        Row(Modifier.fillMaxWidth().clickable{onEdit(field,null)}.padding(vertical=15.dp),verticalAlignment=Alignment.CenterVertically) {
                            Icon(field.icon,null,Modifier.size(26.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if(rows.isEmpty())field.label else "Add ${field.label.lowercase()}",Modifier.weight(1f).padding(horizontal=16.dp),fontSize=17.sp,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(Icons.Outlined.Add,"Add ${field.label}",tint=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun ProfileSectionHeading(title:String){Text(title,Modifier.padding(top=18.dp,bottom=8.dp),fontWeight=FontWeight.Bold,fontSize=21.sp)}
@Composable private fun ProfileEditorRow(icon:ImageVector,title:String,detail:String,audience:String,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=13.dp),verticalAlignment=Alignment.Top) {
        Icon(icon,null,Modifier.size(26.dp));Column(Modifier.weight(1f).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,fontWeight=FontWeight.SemiBold,fontSize=17.sp)
            if(detail.isNotBlank())Text(detail,fontSize=15.sp)
            AudienceLine(audience)
        }
        Icon(Icons.Outlined.Edit,"Edit $title",Modifier.size(24.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun ProfileAbout(vm:SparkViewModel,id:String) {
    Rows(vm,"profile-about:$id",{vm.api.rows("profile_details","owner_id=eq.$id&order=pinned.desc,created_at.asc&limit=200")}) {details->
        if(details.isNotEmpty())Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Text("At a glance",Modifier.weight(1f),fontSize=21.sp,fontWeight=FontWeight.Bold);TextButton(onClick={vm.go("About",id)}){Text("See all")}}
            val pinned=details.filter{it.optBoolean("pinned")}
            val visible=(if(pinned.isNotEmpty())pinned else details).filter{it.s("kind")!="category"}.groupBy{it.s("kind")}.entries.take(7)
            visible.forEach { (kind,entries)->
                val field=profileSections.flatMap{it.fields}.firstOrNull{it.kind==kind}
                val prefix=when(kind){"city"->"Lives in ";"hometown"->"From ";"work"->"Works at ";"education"->"Studied at ";"birthday"->"Born ";"hobby"->"Enjoys ";else->""}
                val title=prefix+entries.first().s("title")+if(entries.size>1)" and ${entries.size-1} more" else ""
                Row(Modifier.fillMaxWidth().clickable{vm.go("About",id)}.padding(vertical=10.dp),verticalAlignment=Alignment.Top){Icon(field?.icon?:Icons.Outlined.Info,null,Modifier.size(25.dp));Text(title,Modifier.padding(start=14.dp),fontSize=16.sp)}
            }
        }
    }
}
@Composable fun ProfileAboutScreen(vm:SparkViewModel,id:String) {
    Rows(vm,"about:$id",{vm.api.rows("profile_details","owner_id=eq.$id&order=created_at.asc,id.asc")}) {details->
        LazyColumn(contentPadding=PaddingValues(16.dp)) {
            if(details.isEmpty())item{Empty("No details to show","Profile details appear here when they are shared with you.")}
            profileSections.forEach {section->
                val entries=details.filter{row->section.fields.any{it.kind==row.s("kind")}}
                if(entries.isNotEmpty())item {ProfileSectionHeading(section.title);entries.forEach {row->
                    val field=section.fields.first{it.kind==row.s("kind")}
                    Row(Modifier.padding(vertical=14.dp)){Icon(field.icon,null,Modifier.size(26.dp));Column(Modifier.padding(start=16.dp)) {Text(row.s("title"),fontSize=17.sp,fontWeight=FontWeight.SemiBold);if(row.s("detail").isNotBlank())Text(row.s("detail"));AudienceLine(row.s("visibility"))}}
                }}
            }
        }
    }
}
