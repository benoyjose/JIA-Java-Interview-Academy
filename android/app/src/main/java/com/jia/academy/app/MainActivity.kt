package com.jia.academy.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class Technology(val id: String, val slug: String, val name: String, val description: String?)

class CatalogueRepository {
    suspend fun getTechnologies(baseUrl: String): List<Technology> = withContext(Dispatchers.IO) {
        require(baseUrl.isNotBlank()) {
            "Configure JIA_API_BASE_URL in ~/.gradle/gradle.properties to connect to the JIA API."
        }
        val connection = (URL(baseUrl.trimEnd('/') + "/api/v1/technologies")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) error("JIA API returned HTTP $status")
            val json = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            (0 until json.length()).map { index ->
                val item = json.getJSONObject(index)
                Technology(
                    id = item.getString("id"),
                    slug = item.getString("slug"),
                    name = item.getString("name"),
                    description = item.optString("description").takeIf(String::isNotBlank)
                )
            }
        } finally {
            connection.disconnect()
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { JiaHomeScreen() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JiaHomeScreen() {
    val repository = remember { CatalogueRepository() }
    var technologies by remember { mutableStateOf<List<Technology>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatching { repository.getTechnologies(BuildConfig.JIA_API_BASE_URL) }
            .onSuccess { technologies = it }
            .onFailure { error = it.message ?: "Unable to load Java topics." }
        loading = false
    }

    Scaffold(topBar = { TopAppBar(title = { Text("JIA") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Java Interview Academy", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Quick explanations. Practical code. Interview-ready answers.",
                style = MaterialTheme.typography.bodyLarge
            )
            Text("Explore Java topics", style = MaterialTheme.typography.titleLarge)
            when {
                loading -> CircularProgressIndicator()
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                technologies.isEmpty() -> Text("No published topics are available yet.")
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(technologies, key = { it.id }) { technology ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(technology.name, style = MaterialTheme.typography.titleMedium)
                                technology.description?.let {
                                    Spacer(Modifier.padding(top = 4.dp))
                                    Text(it, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
