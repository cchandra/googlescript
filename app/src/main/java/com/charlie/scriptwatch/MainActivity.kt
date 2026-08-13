package com.charlie.scriptwatch

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { ScriptWatchApp(this) } }
    }
}

private val BASE_SCOPES = listOf(
    "https://www.googleapis.com/auth/script.processes",
    "https://www.googleapis.com/auth/script.metrics"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptWatchApp(activity: Activity) {
    val store = remember { ScriptStore(activity) }
    val api = remember { AppsScriptApi() }
    val scope = rememberCoroutineScope()
    var scripts by remember { mutableStateOf(store.load()) }
    var selected by remember { mutableStateOf<ScriptConfig?>(scripts.firstOrNull()) }
    var accessToken by remember { mutableStateOf<String?>(null) }
    var authStatus by remember { mutableStateOf("Not connected") }
    var processes by remember { mutableStateOf<List<ScriptProcess>>(emptyList()) }
    var metrics by remember { mutableStateOf(MetricsSummary()) }
    var message by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var pendingAuthAction by remember { mutableStateOf<((String) -> Unit)?>(null) }

    fun handleAuthorization(result: AuthorizationResult) {
        val token = result.accessToken
        if (token.isNullOrBlank()) {
            authStatus = "Authorization returned no token"
        } else {
            accessToken = token
            authStatus = "Connected"
            pendingAuthAction?.invoke(token)
            pendingAuthAction = null
        }
    }

    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            runCatching {
                Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(result.data!!)
            }.onSuccess(::handleAuthorization)
             .onFailure { authStatus = it.message ?: "Authorization failed" }
        }
    }

    fun authorizeFor(script: ScriptConfig?, after: (String) -> Unit = {}) {
        val extra = script?.extraScopes.orEmpty().split(',', '\n', ' ')
            .map { it.trim() }.filter { it.startsWith("https://") }
        val scopes = (BASE_SCOPES + extra).distinct().map(::Scope)
        val request = AuthorizationRequest.builder().setRequestedScopes(scopes).build()
        pendingAuthAction = after
        Identity.getAuthorizationClient(activity).authorize(request)
            .addOnSuccessListener { r ->
                if (r.hasResolution()) {
                    authLauncher.launch(IntentSenderRequest.Builder(r.pendingIntent!!.intentSender).build())
                } else handleAuthorization(r)
            }
            .addOnFailureListener { authStatus = it.message ?: "Authorization failed" }
    }

    fun refresh(script: ScriptConfig) {
        val token = accessToken
        if (token == null) {
            authorizeFor(script) { refresh(script) }
            return
        }
        scope.launch {
            message = "Refreshing…"
            api.listProcesses(token, script.scriptId).onSuccess { processes = it }.onFailure { message = it.message ?: "Failed" }
            api.getMetrics(token, script.scriptId).onSuccess { metrics = it }.onFailure { message = it.message ?: "Failed" }
            message = "Updated"
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("ScriptWatch") }, actions = { TextButton(onClick = { authorizeFor(selected) }) { Text(authStatus) } }) },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }) { Text("+") } }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize()) {
            Text("Google Apps Script control center", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (scripts.isEmpty()) {
                Text("Add a script to start monitoring it.")
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(scripts) { s ->
                        ElevatedCard(onClick = { selected = s; refresh(s) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(s.name, fontWeight = FontWeight.Bold)
                                    Text(if (selected == s) "SELECTED" else "")
                                }
                                Text("Function: ${s.functionName}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item {
                        selected?.let { s ->
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { refresh(s) }) { Text("Refresh") }
                                Button(onClick = {
                                    fun execute(token: String) {
                                        scope.launch {
                                            message = "Running ${s.functionName}…"
                                            api.runFunction(token, s.deploymentId, s.functionName)
                                                .onSuccess { message = "Success: $it"; refresh(s) }
                                                .onFailure { message = "Run failed: ${it.message}" }
                                        }
                                    }
                                    accessToken?.let(::execute) ?: authorizeFor(s, ::execute)
                                }) { Text("Run now") }
                                OutlinedButton(onClick = {
                                    scripts = scripts.filterNot { it == s }
                                    store.save(scripts)
                                    selected = scripts.firstOrNull()
                                }) { Text("Delete") }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                MetricCard("7-day runs", metrics.total.toString(), Modifier.weight(1f))
                                MetricCard("Failures", metrics.failed.toString(), Modifier.weight(1f))
                            }
                            if (message.isNotBlank()) Text(message, modifier = Modifier.padding(vertical = 10.dp))
                            Text("Recent executions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            processes.take(20).forEach { p -> ProcessRow(p) }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddScriptDialog(onDismiss = { showAdd = false }, onAdd = { s ->
            scripts = scripts + s
            store.save(scripts)
            selected = s
            showAdd = false
        })
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) { Column(Modifier.padding(14.dp)) { Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(label) } }
}

@Composable
private fun ProcessRow(p: ScriptProcess) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(p.functionName, fontWeight = FontWeight.Medium)
            Text("${p.type} • ${p.startTime}", style = MaterialTheme.typography.bodySmall)
        }
        AssistChip(onClick = {}, label = { Text(p.status) })
    }
}

@Composable
private fun AddScriptDialog(onDismiss: () -> Unit, onAdd: (ScriptConfig) -> Unit) {
    var name by remember { mutableStateOf("") }
    var scriptId by remember { mutableStateOf("") }
    var deploymentId by remember { mutableStateOf("") }
    var fn by remember { mutableStateOf("") }
    var scopes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Apps Script") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") })
                OutlinedTextField(scriptId, { scriptId = it }, label = { Text("Script ID") })
                OutlinedTextField(deploymentId, { deploymentId = it }, label = { Text("API executable deployment ID") })
                OutlinedTextField(fn, { fn = it }, label = { Text("Function to run") })
                OutlinedTextField(scopes, { scopes = it }, label = { Text("Extra OAuth scopes (optional)") }, minLines = 2)
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank() && scriptId.isNotBlank(), onClick = {
                onAdd(ScriptConfig(name.trim(), scriptId.trim(), deploymentId.trim(), fn.trim(), scopes.trim()))
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
