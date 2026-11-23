package com.jia.academy.app.catalogue

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private data class PublicSearchHit(val type:String,val slug:String,val title:String,val summary:String,val categorySlug:String?)
private data class PublicSearchPage(val hits:List<PublicSearchHit>,val page:Int,val totalPages:Int,val totalItems:Int)

@Composable
fun PublicSearchScreen(apiBaseUrl:String,modifier:Modifier=Modifier) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("ALL") }
    var page by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<PublicSearchPage?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val recent=remember { mutableStateListOf<String>().apply {
        context.getSharedPreferences("public_search_history",0).getString("queries","").orEmpty()
            .split("\n").filter(String::isNotBlank).take(8).forEach(::add)
    } }
    fun search(term:String=query,targetPage:Int=0,targetType:String=type) {
        val clean=term.trim()
        if(clean.length !in 2..80){error="Enter 2–80 characters to search.";return}
        query=clean;page=targetPage;type=targetType;loading=true;error=null
        scope.launch {
            val response=withContext(Dispatchers.IO) {
                runCatching {
                    val encoded=URLEncoder.encode(clean,"UTF-8")
                    val url=URL(apiBaseUrl.trimEnd('/')+"/api/v1/public/academies/JIA/search?q="+encoded+"&type="+targetType+"&page="+targetPage+"&size=20")
                    val connection=url.openConnection() as HttpURLConnection
                    connection.requestMethod="GET";connection.connectTimeout=10000;connection.readTimeout=15000
                    connection.setRequestProperty("Accept","application/json")
                    try {
                        val code=connection.responseCode
                        if(code !in 200..299) throw IllegalStateException("Search failed (HTTP $code)")
                        val json=JSONObject(connection.inputStream.bufferedReader().use{it.readText()})
                        val array=json.getJSONArray("items")
                        val hits=(0 until array.length()).map { i ->
                            val item=array.getJSONObject(i)
                            PublicSearchHit(item.getString("type"),item.getString("slug"),item.getString("title"),
                                item.optString("summary",""),item.optString("categorySlug").takeIf(String::isNotBlank))
                        }
                        PublicSearchPage(hits,json.getInt("page"),json.getInt("totalPages"),json.getInt("totalItems"))
                    } finally { connection.disconnect() }
                }
            }
            response.onSuccess { result=it
                if(clean !in recent){recent.add(0,clean);while(recent.size>8)recent.removeAt(recent.lastIndex)
                    context.getSharedPreferences("public_search_history",0).edit().putString("queries",recent.joinToString("\n")).apply()}
            }.onFailure { error=it.message ?: "Unable to search. Check your connection and retry." }
            loading=false
        }
    }
    Column(modifier.fillMaxSize().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Search academy content",style=MaterialTheme.typography.headlineSmall)
        Text("Browse published lessons and question prompts without signing in.",style=MaterialTheme.typography.bodyMedium)
        OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),label={Text("Search Java, Spring, SQL…")},singleLine=true)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("ALL","LESSON","QUESTION","TECHNOLOGY","CATEGORY").forEach { option ->
                FilterChip(selected=type==option,onClick={search(query,0,option)},label={Text(option.lowercase().replaceFirstChar(Char::uppercase))})
            }
        }
        Button(onClick={search()},enabled=!loading&&query.trim().length>=2,modifier=Modifier.fillMaxWidth()){Text("Search")}
        if(recent.isNotEmpty()&&result==null) {
            Text("Recent searches",style=MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){recent.take(4).forEach{TextButton(onClick={search(it)}){Text(it)}}}
        }
        if(loading) CircularProgressIndicator()
        error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        result?.let { data ->
            Text("${data.totalItems} results",style=MaterialTheme.typography.titleMedium)
            if(data.hits.isEmpty()) Text("No matches. Try another keyword or content type.")
            LazyColumn(modifier=Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                items(data.hits,key={it.type+":"+it.slug}){hit->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(hit.type.lowercase().replaceFirstChar(Char::uppercase),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                            Text(hit.title,style=MaterialTheme.typography.titleMedium)
                            if(hit.summary.isNotBlank())Text(hit.summary,style=MaterialTheme.typography.bodyMedium)
                            Text("Open from catalogue · ${hit.slug}",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(onClick={search(query,(data.page-1).coerceAtLeast(0))},enabled=data.page>0&&!loading){Text("Previous")}
                Text("Page ${data.page+1} of ${maxOf(1,data.totalPages)}")
                TextButton(onClick={search(query,data.page+1)},enabled=data.page+1<data.totalPages&&!loading){Text("Next")}
            }
        }
    }
}
