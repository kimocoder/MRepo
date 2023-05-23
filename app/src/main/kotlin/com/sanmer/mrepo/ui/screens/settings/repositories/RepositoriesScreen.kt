package com.sanmer.mrepo.ui.screens.settings.repositories

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sanmer.mrepo.R
import com.sanmer.mrepo.app.Const
import com.sanmer.mrepo.database.entity.Repo
import com.sanmer.mrepo.database.entity.toRepo
import com.sanmer.mrepo.ui.animate.slideInBottomToTop
import com.sanmer.mrepo.ui.animate.slideInTopToBottom
import com.sanmer.mrepo.ui.animate.slideOutBottomToTop
import com.sanmer.mrepo.ui.animate.slideOutTopToBottom
import com.sanmer.mrepo.ui.component.NavigateUpTopBar
import com.sanmer.mrepo.ui.component.PageIndicator
import com.sanmer.mrepo.ui.utils.isScrollingUp
import com.sanmer.mrepo.ui.utils.navigateBack
import com.sanmer.mrepo.ui.utils.none
import com.sanmer.mrepo.viewmodel.RepositoriesViewModel

@Composable
fun RepositoriesScreen(
    viewModel: RepositoriesViewModel = hiltViewModel(),
    navController: NavController
) {
    val list by viewModel.list.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val listSate = rememberLazyListState()
    val showFab = listSate.isScrollingUp()

    BackHandler { navController.navigateBack() }

    var value = Const.MY_REPO_URL.toRepo()
    var message: String? by remember { mutableStateOf(null) }

    var failure by remember { mutableStateOf(false) }
    if (failure) {
        FailureDialog(
            repo = value,
            message = message,
            onClose = { failure = false }
        )
    }

    var add by remember { mutableStateOf(false) }
    if (add) {
        AddDialog(
            onClose = { add = false }
        ) {
            viewModel.insert(it) { repo, e ->
                value = repo
                failure = true
                message = e.message
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopBar(
                scrollBehavior = scrollBehavior,
                navController = navController
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = showFab,
                enter = fadeIn() + slideInBottomToTop(),
                exit = fadeOut() + slideOutTopToBottom()
            ) {
                FloatingButton { add = true }
            }
        },
        contentWindowInsets = WindowInsets.none
    ) { innerPadding ->
        Box(
            modifier = Modifier.padding(innerPadding)
        ) {
            if (list.isEmpty()) {
                PageIndicator(
                    icon = R.drawable.hierarchy_outline,
                    text = R.string.repo_empty
                )
            }

            RepoList(
                list = list,
                state = listSate
            )

            AnimatedVisibility(
                visible = viewModel.progress,
                enter = slideInTopToBottom(),
                exit = slideOutBottomToTop()
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    strokeCap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun RepoList(
    list: List<Repo>,
    state: LazyListState
) = LazyColumn(
    state = state,
    modifier = Modifier.fillMaxSize()
) {
    item {
        NotificationItem()
    }

    items(
        items = list,
        key = { it.url }
    ) { repo ->
        RepositoryItem(repo = repo)
    }
}

@Composable
private fun NotificationItem() = Surface(
    modifier = Modifier.padding(all = 16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(15.dp)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.information_bold),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(30.dp),
        )

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = stringResource(id = R.string.repo_notification_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(5.dp))
            Text(
                text = stringResource(id = R.string.repo_notification_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddDialog(
    onClose: () -> Unit,
    onAdd: (String) -> Unit
) {
    var url by rememberSaveable { mutableStateOf("") }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        shape = RoundedCornerShape(20.dp),
        onDismissRequest = onClose,
        title = { Text(text = stringResource(id = R.string.repo_add_dialog_title)) },
        text = {
            OutlinedTextField(
                modifier = Modifier
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.bodyLarge,
                value = url,
                onValueChange = { url = it },
                placeholder = { Text(text = "https://your-repo.com/") },
                singleLine = false,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions {
                    defaultKeyboardAction(ImeAction.Done)
                    focusManager.clearFocus()
                },
                shape = RoundedCornerShape(15.dp)
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (url.isBlank()) return@TextButton
                    if (!url.endsWith("/")) url += "/"

                    onAdd(url)
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    onClose()
                }
            ) {
                Text(
                    text = stringResource(id = R.string.repo_add_dialog_add)
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onClose()
                    keyboardController?.hide()
                    focusManager.clearFocus()
                }
            ) {
                Text(
                    text = stringResource(id = R.string.dialog_cancel)
                )
            }
        }
    )
}

@Composable
private fun TopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    navController: NavController
) = NavigateUpTopBar(
    title = R.string.page_repositories,
    actions = {

        val context = LocalContext.current
        IconButton(
            onClick = {
                Toast.makeText(context, "Coming soon!", Toast.LENGTH_SHORT).show()
            }
        ) {
            Icon(
                painter = painterResource(id = R.drawable.sort_outline),
                contentDescription = null
            )
        }
    },
    scrollBehavior = scrollBehavior,
    navController = navController
)

@Composable
private fun FloatingButton(
    onClick: () -> Unit
) = FloatingActionButton(
    onClick = onClick,
    contentColor = MaterialTheme.colorScheme.onPrimary,
    containerColor = MaterialTheme.colorScheme.primary
) {
    Icon(
        modifier = Modifier.size(30.dp),
        painter = painterResource(id = R.drawable.add_outline),
        contentDescription = null
    )
}