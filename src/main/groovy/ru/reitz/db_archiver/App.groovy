package ru.reitz.db_archiver

import groovy.transform.CompileStatic
import org.quartz.CronScheduleBuilder
import org.quartz.JobBuilder
import org.quartz.JobDetail
import org.quartz.Scheduler
import org.quartz.SchedulerFactory
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import org.quartz.impl.StdSchedulerFactory

@CompileStatic
class App {
  static void main(String[] args) {
    SchedulerFactory schedulerFactory = new StdSchedulerFactory()
    Scheduler scheduler = schedulerFactory.getScheduler()

    JobDetail job = JobBuilder.newJob(DbArchiver.class)
        .withIdentity("db-archive-task", "db-archive")
        .build()

    Trigger trigger = TriggerBuilder.newTrigger()
        .withIdentity("db-archive-trigger", "db-archive")
        .withSchedule(CronScheduleBuilder.cronSchedule("0 0 1/4 ? * * *")) //every 4 hour starting at 01:00
        .build()

    scheduler.scheduleJob(job, trigger)
    scheduler.start()
  }
}
