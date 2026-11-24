package com.jia.academy.app.assessment
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
/** Offline-first worker boundary. The authenticated API implementation is injected by the app's worker factory. */
class PracticeAttemptSyncWorker(context:Context,params:WorkerParameters,private val dao:PracticeAttemptDao,private val uploader:PracticeAttemptUploader):CoroutineWorker(context,params){
 override suspend fun doWork():Result {val events=dao.pending(75);if(events.isEmpty())return Result.success()
  return try{for(event in events){uploader.upload(event);dao.markSynced(event.eventId)};Result.success()}
  catch(_:Exception){events.forEach{dao.recordRetry(it.eventId)};if(runAttemptCount<5)Result.retry() else Result.failure()}
 }
}
fun interface PracticeAttemptUploader {suspend fun upload(event:PracticeAttemptEntity)}
