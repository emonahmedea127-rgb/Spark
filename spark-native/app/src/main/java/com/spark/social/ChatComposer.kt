package com.spark.social
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun KeyboardAwareChatColumn(content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().imePadding(),content=content)
}
@Composable internal fun MessageComposer(text:String,onTextChange:(String)->Unit,busy:Boolean,hasAttachment:Boolean,onAttach:()->Unit,onSend:()->Unit) {
    Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
        IconButton(enabled=!busy,onClick=onAttach) {Icon(Icons.Outlined.AddPhotoAlternate,"Attach media")}
        OutlinedTextField(value=text,onValueChange=onTextChange,enabled=!busy,placeholder={Text("Message…")},modifier=Modifier.weight(1f),maxLines=4,shape=RoundedCornerShape(24.dp),colors=OutlinedTextFieldDefaults.colors(focusedTextColor=MaterialTheme.colorScheme.onSurface,unfocusedTextColor=MaterialTheme.colorScheme.onSurface,cursorColor=Blue))
        IconButton(enabled=!busy&&(text.isNotBlank()||hasAttachment),onClick=onSend) {Icon(Icons.AutoMirrored.Outlined.Send,"Send",tint=Blue)}
    }
}
