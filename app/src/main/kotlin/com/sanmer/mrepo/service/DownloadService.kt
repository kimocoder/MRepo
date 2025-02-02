package com.sanmer.mrepo.service

import android.Manifest
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Parcel
import android.os.Parcelable
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.sanmer.mrepo.R
import com.sanmer.mrepo.app.utils.NotificationUtils
import com.sanmer.mrepo.network.NetworkUtils
import com.sanmer.mrepo.repository.UserPreferencesRepository
import com.sanmer.mrepo.utils.extensions.parcelable
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.FileNotFoundException
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : LifecycleService() {
    private val context: Context by lazy { applicationContext }
    private val tasks = mutableListOf<TaskItem>()

    @Inject lateinit var userPreferencesRepository: UserPreferencesRepository

    init {
        lifecycleScope.launch {
            while (isActive) {
                delay(10_000L)
                if (tasks.isEmpty()) stopSelf()
            }
        }

        progressFlow.drop(1)
            .sample(500)
            .flowOn(Dispatchers.IO)
            .onEach { (item, progress) ->
                if (progress != 0f) {
                    notifyProgress(item, progress)
                }
            }
            .launchIn(lifecycleScope)
    }

    override fun onCreate() {
        Timber.d("DownloadService onCreate")
        super.onCreate()
        setForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleScope.launch {
            val item = intent?.taskItemOrNull ?: return@launch
            val downloadPath = userPreferencesRepository.data
                .first().downloadPath
                .let {
                    if (!it.exists()) it.mkdirs()
                    DocumentFile.fromFile(it)
                }

            val df = downloadPath.createFile("*/*", item.filename)
            if (df == null) {
                notifyFailure(item, null)
                return@launch
            }

            val output = try {
                checkNotNull(
                    contentResolver.openOutputStream(df.uri)
                )
            } catch (e: FileNotFoundException) {
                notifyFailure(item, e)
                return@launch
            }

            val listener = object : IDownloadListener {
                override fun getProgress(value: Float) {
                    progressFlow.value = item to value
                    listeners[item]?.getProgress(value)
                }

                override fun onSuccess() {
                    notifySuccess(item)

                    progressFlow.value = item to 0f
                    listeners[item]?.onSuccess()
                    tasks.remove(item)
                }

                override fun onFailure(e: Throwable) {
                    notifyFailure(item, e)

                    progressFlow.value = item to 0f
                    listeners[item]?.onFailure(e)
                    tasks.remove(item)
                }
            }

            tasks.add(item)
            NetworkUtils.downloader(
                url = item.url,
                output = output,
                onProgress = {
                    listener.getProgress(it)
                }
            ).onSuccess {
                listener.onSuccess()
            }.onFailure {
                listener.onFailure(it)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        Timber.d("DownloadService onDestroy")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun setForeground() {
        val notification = NotificationCompat.Builder(this, NotificationUtils.CHANNEL_ID_DOWNLOAD)
            .setSmallIcon(R.drawable.launcher_outline)
            .setContentTitle(getString(R.string.notification_name_download))
            .setSilent(true)
            .setOngoing(true)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .build()

        startForeground(NotificationUtils.NOTIFICATION_ID_DOWNLOAD, notification)
    }

    private fun buildNotification(
        title: String?,
        desc: String?,
        silent: Boolean = false,
        ongoing: Boolean = false,
    ) = NotificationCompat.Builder(context, NotificationUtils.CHANNEL_ID_DOWNLOAD)
        .setSmallIcon(R.drawable.launcher_outline)
        .setContentTitle(title)
        .setSubText(desc)
        .setSilent(silent)
        .setOngoing(ongoing)
        .setGroup(GROUP_KEY)

    private fun notify(id: Int, notification: Notification) {
        val notificationId = NotificationUtils.NOTIFICATION_ID_DOWNLOAD + id
        NotificationManagerCompat.from(context).apply {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return

            notify(notificationId, notification)
        }
    }

    private fun notifyProgress(item: TaskItem, progress: Float) {
        val notification = buildNotification(
            title = item.title,
            desc = item.desc,
            silent = true,
            ongoing = true
        ).apply {
            setProgress(100, (progress * 100).toInt(), false)
        }

        notify(item.taskId, notification.build())
    }

    private fun notifySuccess(item: TaskItem) {
        val message = context.getString(R.string.message_download_success)
        val notification = buildNotification(
            title = item.title,
            desc = item.desc,
            silent = true
        ).apply {
            setContentText(message)
        }

        notify(item.taskId, notification.build())
    }

    private fun notifyFailure(item: TaskItem, e: Throwable?) {
        val message = e?.message ?: context.getString(R.string.unknown_error)
        val notification = buildNotification(
            title = item.title,
            desc = item.desc,
            silent = false
        ).apply {
            setContentText(message)
        }

        notify(item.taskId, notification.build())
    }

    data class TaskItem(
        val key: String,
        val url: String,
        val filename: String,
        val title: String?,
        val desc: String?,
        val taskId: Int = System.currentTimeMillis().toInt(),
    ) : Parcelable {
        constructor(parcel: Parcel) : this(
            key = checkNotNull(parcel.readString()),
            url = checkNotNull(parcel.readString()),
            filename = checkNotNull(parcel.readString()),
            title = parcel.readString(),
            desc = parcel.readString(),
            taskId = parcel.readInt()
        )

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(key)
            parcel.writeString(url)
            parcel.writeString(filename)
            parcel.writeString(title)
            parcel.writeString(desc)
            parcel.writeInt(taskId)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object {
            @JvmField
            val CREATOR = object : Parcelable.Creator<TaskItem> {
                override fun createFromParcel(parcel: Parcel): TaskItem {
                    return TaskItem(parcel)
                }

                override fun newArray(size: Int): Array<TaskItem?> {
                    return arrayOfNulls(size)
                }
            }

            fun empty() = TaskItem(
                key = "",
                url = "",
                filename = "",
                title = null,
                desc = null,
                taskId = -1
            )
        }
    }

    interface IDownloadListener {
        fun getProgress(value: Float)
        fun onSuccess()
        fun onFailure(e: Throwable)
    }

    companion object {
        private const val GROUP_KEY = "DOWNLOAD_SERVICE_GROUP_KEY"
        private const val PARAM_TASK_ITEM = "TASK_ITEM"
        private val Intent.taskItemOrNull: TaskItem? get() =
            parcelable(PARAM_TASK_ITEM)

        private val listeners = hashMapOf<TaskItem, IDownloadListener>()
        private val progressFlow = MutableStateFlow(TaskItem.empty() to 0f)

        fun getProgressByKey(key: String): Flow<Float>  {
            return progressFlow.filter { (item, _) ->
                item.key == key
            }.map { (_, progress) ->
                progress
            }
        }

        fun start(
            context: Context,
            task: TaskItem,
            listener: IDownloadListener
        ) {
            val intent = Intent(context, DownloadService::class.java)
            intent.putExtra(PARAM_TASK_ITEM, task)

            listeners[task] = listener
            context.startService(intent)
        }
    }
}