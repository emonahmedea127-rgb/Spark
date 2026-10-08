@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.spark.social

import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

internal val reactionEmoji=linkedMapOf("like" to "👍","love" to "❤️","haha" to "😆","wow" to "😮","sad" to "😢","angry" to "😠")
internal fun compactCount(n:Int):String=when {
    n>=1_000_000->String.format(java.util.Locale.US,"%.1fM",n/1_000_000f).replace(".0M","M")
    n>=1_000->String.format(java.util.Locale.US,"%.1fK",n/1_000f).replace(".0K","K")
    else->n.toString()
}

@Composable fun PostActions(vm:SparkViewModel,post:JSONObject,white:Boolean=false,commentCount:Int=post.rows("comments").firstOrNull()?.optInt("count")?:0,onComments:()->Unit={vm.go("Comments",post.id())}) {
    val context=LocalContext.current
    var reactions by remember(post) { mutableStateOf(post.rows("reactions")) }
    var picking by remember { mutableStateOf(false) }
    var showPeople by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val mine=reactions.firstOrNull { it.s("user_id")==vm.api.userId }?.s("reaction")
    val ink=if(white)Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    fun react(value:String) {
        if(saving)return
        saving=true
        vm.work {
            try {
                val query="post_id=eq.${post.id()}&user_id=eq.${vm.api.userId}"
                if(mine==value)vm.api.delete("reactions",query)
                else if(mine==null)vm.api.insert("reactions",json("post_id" to post.id(),"user_id" to vm.api.userId,"reaction" to value))
                else vm.api.update("reactions",query,json("reaction" to value))
                if(mine!=value)vm.sound(if(value=="like")SparkSounds.Event.LIKE else SparkSounds.Event.REACT)
                reactions=reactions.filterNot { it.s("user_id")==vm.api.userId } +
                    if(mine==value)emptyList() else listOf(json("user_id" to vm.api.userId,"reaction" to value))
                post.put("reactions",JSONArray(reactions));picking=false
            }finally { saving=false }
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically) {
        Box {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).combinedClickable(enabled=!saving,onClick={react(mine?:"like")},onLongClick={picking=true},onLongClickLabel="Choose a reaction"),contentAlignment=Alignment.Center) {
                if(mine!=null&&mine!="like")Text(reactionEmoji[mine]?:"👍",fontSize=22.sp,modifier=Modifier.semantics { contentDescription="Your reaction: $mine" })
                else Icon(Icons.Outlined.ThumbUp,"Like; hold to choose reaction",Modifier.size(25.dp),tint=if(mine!=null)Blue else ink)
                }
                Box(Modifier.heightIn(min=48.dp).clickable {showPeople=true}.padding(end=14.dp).semantics {contentDescription="See who reacted"},contentAlignment=Alignment.Center) {
                    Text(" ${compactCount(reactions.size)}",color=if(mine!=null)Blue else ink,fontSize=15.sp)
                }
            }
            DropdownMenu(expanded=picking,onDismissRequest={picking=false}) {
                Row(Modifier.padding(horizontal=4.dp)) { reactionEmoji.forEach { (value,emoji)->
                    Box(Modifier.size(46.dp).clickable(enabled=!saving) { react(value) }.semantics { contentDescription="React $value" },contentAlignment=Alignment.Center) { Text(emoji,fontSize=29.sp) }
                } }
            }
        }
        Row(Modifier.heightIn(min=48.dp).clickable(onClick=onComments).padding(end=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Icon(Icons.Outlined.ChatBubbleOutline,"Comments",Modifier.size(25.dp),tint=ink)
            Text(" ${compactCount(commentCount)}",color=ink,fontSize=15.sp)
        }
        IconButton(onClick={context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain";putExtra(Intent.EXTRA_TEXT,"${post.child("author").s("display_name")} on Spark:\n${post.s("body")}") },"Share post"))}) { Icon(Icons.Outlined.Share,"Share post",tint=ink,modifier=Modifier.size(25.dp)) }
        Spacer(Modifier.weight(1f))
        val top=reactions.groupingBy { it.s("reaction") }.eachCount().entries.sortedByDescending { it.value }.take(3)
        if(top.isNotEmpty())Row(Modifier.heightIn(min=48.dp).clickable { showPeople=true }.semantics { contentDescription="Reactions: "+top.joinToString { "${it.key} ${it.value}" } },verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy((-4).dp)) {
            top.forEach { Text(reactionEmoji[it.key]?:"👍",fontSize=20.sp) }
        }else IconButton(onClick={picking=true}) { Icon(Icons.Outlined.AddReaction,"Choose a reaction",tint=ink,modifier=Modifier.size(23.dp)) }
    }
    if(showPeople)ReactionPeopleDialog(vm,post.id(),reactions,onClose={showPeople=false})
}

@Composable private fun Caption(body:String) {
    val text=remember(body) { buildAnnotatedString {
        append(body)
        Regex("#[\\p{L}\\p{N}_]+").findAll(body).forEach { match->addStyle(SpanStyle(color=Blue,fontWeight=FontWeight.SemiBold),match.range.first,match.range.last+1) }
    } }
    Text(text,Modifier.fillMaxWidth().padding(start=12.dp,end=12.dp,bottom=10.dp),fontSize=17.sp)
}

@Composable fun PostDiscussion(vm:SparkViewModel,post:JSONObject) {
    var preview by remember(post) { mutableStateOf(post.rows("preview").firstOrNull()) }
    var count by remember(post) { mutableIntStateOf(post.rows("comments").firstOrNull()?.optInt("count")?:0) }
    var reply by remember { mutableStateOf<JSONObject?>(null) }
    PostActions(vm,post,commentCount=count)
    if(post.s("media_path").isNotBlank()&&post.s("body").isNotBlank())Caption(post.s("body"))
    preview?.let { comment->CommentRow(vm,comment,onReply={reply=comment},onDeleted={preview=null;count=(count-1).coerceAtLeast(0);vm.refresh()}) }
    CommentComposer(vm,post.id(),reply,onCancelReply={reply=null}) { created->
        preview=created;reply=null;count++
        post.put("preview",JSONArray(listOf(created)))
        post.put("comments",JSONArray(listOf(json("count" to count))))
    }
}

@Composable fun CommentActionSheet(own:Boolean,onReact:(String)->Unit,onReply:()->Unit,onEdit:()->Unit,onDelete:()->Unit,onClose:()->Unit) {
    ModalBottomSheet(onDismissRequest=onClose) {
        Column(Modifier.fillMaxWidth().padding(horizontal=16.dp).navigationBarsPadding()) {
            Text("Comment",fontSize=20.sp,fontWeight=FontWeight.Bold)
            Row(Modifier.fillMaxWidth().padding(vertical=16.dp),horizontalArrangement=Arrangement.SpaceEvenly){reactionEmoji.forEach{(value,emoji)->Text(emoji,Modifier.clickable{onReact(value)}.padding(7.dp).semantics{contentDescription="Comment react $value"},fontSize=28.sp)}}
            TextButton(onClick=onReply,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Reply,null);Text(" Reply")}
            if(own){TextButton(onClick=onEdit,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Edit,null);Text(" Edit comment")};TextButton(onClick=onDelete,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.DeleteOutline,null);Text(" Delete comment")}}
            Spacer(Modifier.height(16.dp))
        }
    }
}
@Composable fun CommentRow(vm:SparkViewModel,comment:JSONObject,onReply:()->Unit,onDeleted:()->Unit) {
    var reactions by remember(comment){mutableStateOf(comment.rows("likes"))}
    var body by remember(comment){mutableStateOf(comment.s("body"))}
    var edited by remember(comment){mutableStateOf(comment.s("edited_at").isNotBlank())}
    var busy by remember{mutableStateOf(false)}
    var menu by remember{mutableStateOf(false)}
    var editing by remember{mutableStateOf(false)}
    var deleting by remember{mutableStateOf(false)}
    val mine=reactions.firstOrNull{it.s("user_id")==vm.api.userId}?.s("reaction")?.ifBlank{"like"}
    val author=comment.child("author")
    fun react(value:String){if(!busy){busy=true;vm.work{try{
        val filter="comment_id=eq.${comment.id()}&user_id=eq.${vm.api.userId}"
        if(mine==value)vm.api.delete("comment_likes",filter)
        else if(mine==null)vm.api.insert("comment_likes",json("comment_id" to comment.id(),"user_id" to vm.api.userId,"reaction" to value))
        else vm.api.update("comment_likes",filter,json("reaction" to value))
        if(mine!=value)vm.sound(if(value=="like")SparkSounds.Event.LIKE else SparkSounds.Event.REACT)
        reactions=reactions.filterNot{it.s("user_id")==vm.api.userId}+if(mine==value)emptyList() else listOf(json("user_id" to vm.api.userId,"reaction" to value))
        comment.put("likes",JSONArray(reactions));menu=false
    }finally{busy=false}}}}
    Row(Modifier.fillMaxWidth().padding(start=12.dp,end=6.dp,top=4.dp)) {
        Box(Modifier.border(2.dp,Blue,CircleShape).padding(3.dp)){Avatar(vm,author,32){vm.go("Profile",comment.s("author_id"))}}
        Column(Modifier.weight(1f).padding(start=8.dp)) {
            Text(author.s("display_name").ifBlank{"Spark member"},fontSize=13.sp,fontWeight=FontWeight.Bold,modifier=Modifier.clickable{vm.go("Profile",comment.s("author_id"))})
            val parent=comment.child("parent")
            if(comment.s("parent_id").isNotBlank())Text(if(parent.s("body").isBlank())"Reply" else "↳ ${parent.child("author").s("display_name")}: ${parent.s("body")}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(body,Modifier.fillMaxWidth().combinedClickable(onClick={menu=true},onLongClick={menu=true}).padding(vertical=4.dp),fontSize=17.sp,lineHeight=24.sp)
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(ago(comment.s("created_at"))+if(edited)" · Edited" else "",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick=onReply,contentPadding=PaddingValues(horizontal=12.dp)){Text("Reply",fontSize=12.sp,fontWeight=FontWeight.SemiBold)}
                Spacer(Modifier.weight(1f))
                Row(Modifier.heightIn(min=48.dp).combinedClickable(enabled=!busy,onClick={react(mine?:"like")},onLongClick={menu=true}).padding(horizontal=10.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(reactions.isNotEmpty())Text(compactCount(reactions.size),Modifier.padding(end=5.dp),fontSize=12.sp)
                    if(mine!=null&&mine!="like")Text(reactionEmoji[mine]?:"👍",fontSize=20.sp)
                    else Icon(Icons.Outlined.ThumbUp,if(mine!=null)"Unlike comment" else "Like comment",Modifier.size(19.dp),tint=if(mine!=null)Blue else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if(menu)CommentActionSheet(comment.s("author_id")==vm.api.userId,{react(it)},{menu=false;onReply()},{menu=false;editing=true},{menu=false;deleting=true},{menu=false})
    if(editing)TextForm("Edit comment",listOf("Comment" to body),vm,{editing=false}){values->
        val value=values[0].trim();require(value.length in 1..3000){"Write between 1 and 3000 characters."}
        vm.api.update("comments","id=eq.${comment.id()}",json("body" to value));body=value;edited=true;comment.put("body",value);editing=false
    }
    if(deleting)Confirm("Delete comment?","This also removes its replies.",{deleting=false}){if(!busy){busy=true;vm.work{try{vm.api.delete("comments","id=eq.${comment.id()}");deleting=false;onDeleted()}finally{busy=false}}}}
}

@Composable fun CommentComposer(vm:SparkViewModel,post:String,reply:JSONObject?,onCancelReply:()->Unit,onSent:(JSONObject)->Unit) {
    var body by rememberSaveable(post) { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var emojiMenu by remember { mutableStateOf(false) }
    val focus=remember { FocusRequester() }
    LaunchedEffect(reply?.id()) { if(reply!=null)focus.requestFocus() }
    Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=6.dp)) {
        if(reply!=null)Row(verticalAlignment=Alignment.CenterVertically) {
            Text("Replying to ${reply.child("author").s("display_name")}",Modifier.weight(1f),fontSize=12.sp,color=Blue)
            IconButton(onClick=onCancelReply) { Icon(Icons.Outlined.Close,"Cancel reply",Modifier.size(18.dp)) }
        }
        Row(verticalAlignment=Alignment.CenterVertically) {
            Avatar(vm,vm.me?:JSONObject(),36) { vm.go("Profile",vm.api.userId) }
            Spacer(Modifier.width(8.dp))
            TextField(value=body,onValueChange={if(it.length<=3000)body=it},enabled=!busy,placeholder={Text("Write a comment…",fontSize=15.sp)},
                modifier=Modifier.weight(1f).focusRequester(focus),shape=RoundedCornerShape(28.dp),maxLines=5,
                colors=TextFieldDefaults.colors(focusedContainerColor=MaterialTheme.colorScheme.surfaceContainerHigh,unfocusedContainerColor=MaterialTheme.colorScheme.surfaceContainerHigh,focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,disabledIndicatorColor=Color.Transparent),
                trailingIcon={
                    if(body.isNotBlank())IconButton(enabled=!busy,onClick={
                        val text=body.trim();val replying=reply
                        busy=true
                        vm.work { try {
                            val created=vm.api.insert("comments",json("post_id" to post,"author_id" to vm.api.userId,"body" to text,"parent_id" to replying?.id())).first()
                            created.put("author",vm.me?:JSONObject()).put("likes",JSONArray())
                            if(replying!=null)created.put("parent",json("body" to replying.s("body"),"author" to replying.child("author")))
                            body="";onSent(created)
                        }finally {busy=false} }
                    }) { if(busy)CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp) else Icon(Icons.AutoMirrored.Outlined.Send,"Send comment",tint=Blue) }
                    else Box {
                        IconButton(onClick={emojiMenu=true},enabled=!busy) { Icon(Icons.Outlined.AddCircleOutline,"Add emoji") }
                        DropdownMenu(expanded=emojiMenu,onDismissRequest={emojiMenu=false}) { Row(Modifier.padding(8.dp)) { listOf("❤️","😂","👍","😍","🔥").forEach { emoji->Text(emoji,Modifier.clickable {body+=emoji;emojiMenu=false;focus.requestFocus()}.padding(8.dp),fontSize=24.sp) } } }
                    }
                })
        }
    }
}

@Composable fun CommentsScreen(vm:SparkViewModel,post:String) {
    var comments by remember(post) { mutableStateOf(emptyList<JSONObject>()) }
    var reply by remember(post) { mutableStateOf<JSONObject?>(null) }
    var initialReplyHandled by remember(post){mutableStateOf(false)}
    var offset by remember(post) { mutableIntStateOf(0) }
    var more by remember(post) { mutableStateOf(true) }
    var loading by remember(post) { mutableStateOf(false) }
    var failed by remember(post) { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    suspend fun load(reset:Boolean) {
        if(loading)return
        loading=true;failed=false
        try {
            val next=vm.api.comments(post,if(reset)0 else offset)
            comments=if(reset)next else (comments+next).distinctBy {it.id()}
            offset=if(reset)next.size else offset+next.size;more=next.size==50
        }catch(e:CancellationException) { throw e }catch(e:Exception) {failed=true;vm.notice=e.message}finally {loading=false}
    }
    LaunchedEffect(post,vm.revision) { load(true) }
    LaunchedEffect(comments,vm.page.title) {
        if(vm.page.title.isNotBlank()&&!initialReplyHandled)comments.firstOrNull{it.id()==vm.page.title}?.let {
            reply=it;initialReplyHandled=true
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f)) {
            item { Text("Comments",Modifier.padding(16.dp),fontWeight=FontWeight.Bold,fontSize=20.sp) }
            items(comments,key={it.id()}) { c->CommentRow(vm,c,onReply={reply=c},onDeleted={vm.refresh()}) }
            if(loading)item { Box(Modifier.fillMaxWidth().padding(20.dp),contentAlignment=Alignment.Center) { CircularProgressIndicator() } }
            if(failed)item { TextButton(onClick={scope.launch {load(comments.isEmpty())}},modifier=Modifier.fillMaxWidth()) { Text("Couldn't load comments. Retry") } }
            else if(!loading&&comments.isEmpty())item { Empty("Join the conversation","Be the first to leave a comment.",Icons.Outlined.Comment) }
            else if(!loading&&more)item { TextButton(onClick={scope.launch {load(false)}},modifier=Modifier.fillMaxWidth()) { Text("More comments") } }
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        CommentComposer(vm,post,reply,onCancelReply={reply=null}) { created->comments=listOf(created)+comments;offset++;reply=null;vm.refresh() }
    }
}
