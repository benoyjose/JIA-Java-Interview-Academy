package com.jia.academy.app.assessment
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
/** WorkManager-compatible constructor; dependencies are installed by the Application worker configuration. */
class PracticeAttemptSyncWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
 override suspend fun doWork():Result {
  val dependencies=PracticeAttemptSyncDependencies.resolve(applicationContext)?:return Result.retry()
  val (dao,uploader)=dependencies
  val events=dao.pending(75);if(events.isEmpty())return Result.success()
  return try{for(event in events){uploader.upload(event);dao.markSynced(event.eventId)};Result.success()}
  catch(_:Exception){events.forEach{dao.recordRetry(it.eventId)};if(runAttemptCount<5)Result.retry() else Result.failure()}
 }
}
object PracticeAttemptSyncDependencies {
 @Volatile private var provider:((Context)->Pair<PracticeAttemptDao,PracticeAttemptUploader>)?=null
 fun install(factory:(Context)->Pair<PracticeAttemptDao,PracticeAttemptUploader>){provider=factory}
 fun resolve(context:Context)=provider?.invoke(context)
}
fun interface PracticeAttemptUploader {suspend fun upload(event:PracticeAttemptEntity)}
