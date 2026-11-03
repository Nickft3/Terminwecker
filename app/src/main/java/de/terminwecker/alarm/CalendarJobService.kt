package de.terminwecker.alarm

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.CalendarContract
import java.util.concurrent.TimeUnit

/**
 * Reagiert auf Änderungen im Kalender (Termin verschoben, gelöscht, neu) und plant die Wecker neu.
 * Zusätzlich läuft alle paar Stunden eine Sicherheits-Neuplanung.
 */
class CalendarJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        AlarmScheduler.rescheduleAsync(this) {
            jobFinished(params, false)
            // Inhalts-Trigger gelten nur einmal und müssen danach neu angemeldet werden.
            if (params.jobId == JOB_CONTENT) scheduleContentJob(this)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = params.jobId == JOB_CONTENT

    companion object {
        private const val JOB_CONTENT = 4201
        private const val JOB_PERIODIC = 4202

        fun ensureScheduled(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java)
            if (js.getPendingJob(JOB_CONTENT) == null) scheduleContentJob(context)
            if (js.getPendingJob(JOB_PERIODIC) == null) {
                js.schedule(
                    JobInfo.Builder(JOB_PERIODIC, ComponentName(context, CalendarJobService::class.java))
                        .setPeriodic(TimeUnit.HOURS.toMillis(6))
                        .setPersisted(true)
                        .build(),
                )
            }
        }

        private fun scheduleContentJob(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java)
            js.schedule(
                JobInfo.Builder(JOB_CONTENT, ComponentName(context, CalendarJobService::class.java))
                    .addTriggerContentUri(
                        JobInfo.TriggerContentUri(
                            CalendarContract.CONTENT_URI,
                            JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
                        ),
                    )
                    .setTriggerContentUpdateDelay(3_000)
                    .setTriggerContentMaxDelay(30_000)
                    .build(),
            )
        }
    }
}
