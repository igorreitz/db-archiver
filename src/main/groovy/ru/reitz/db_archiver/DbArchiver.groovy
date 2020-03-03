package ru.reitz.db_archiver

import groovy.sql.Sql
import groovy.transform.CompileStatic
import groovy.transform.Memoized
import groovy.util.logging.Log4j2
import org.quartz.DisallowConcurrentExecution
import org.quartz.Job
import org.quartz.JobExecutionContext

import java.sql.Timestamp
import java.time.LocalDate

@Log4j2
@CompileStatic
@DisallowConcurrentExecution
class DbArchiver implements Job {
  final Properties properties

  DbArchiver() {
    properties = new Properties().tap {
      load(
          getClass().getResourceAsStream("/application-${System.getenv('MODE')?.toLowerCase()}.properties") ?:
              getClass().getResourceAsStream("/application.properties")
      )
    }
  }

  @Override
  void execute(JobExecutionContext context) {
    log.info 'Job started'

    Sql.withInstance properties.getProperty('url'), properties, { Sql sql ->
      // Узнаем самую старую дату
      final endDate = (
          sql.firstRow(
              "SELECT min(create_date) FROM <table_name>"
          )[0] as Timestamp
      ).toLocalDate()

      log.info "The oldest created date is ${endDate.format('yyyy-MM-dd')}"

      (endDate..<LocalDate.now().minusMonths(2)).each { date ->
        log.info "Processing ${date}..."

        final month = date.monthValue.toString().padLeft(2, '0')
        final year = date.year.toString()

        // Создадим архивную таблицу, если надо
        final tableName = createTableName sql, year, month

        // Обработаем все данные за дату date
        int inserts = 0
        int deletes = 0

        while (true) {
          final ids = sql.rows("""
              SELECT 
                id 
              FROM <table_name> 
              WHERE create_date = ${date} AND state IN ('FAILED', 'FINISHED') 
              LIMIT ${properties['row-limit'] as Integer}"""
          ).collect { it.get('id') as Integer }

          if (ids.empty) {
            break
          }

          // Если выборка не пустая, перенесем данные в архивную таблицу
          sql.withTransaction {
            inserts += sql.executeUpdate """
              INSERT INTO ${Sql.expand tableName} 
              SELECT * FROM <table_name> 
              WHERE ID IN (${Sql.expand ids.join(',')})
            """
            deletes += sql.executeUpdate """
              DELETE FROM <table_name> 
              WHERE ID IN (${Sql.expand ids.join(',')})
            """
          }
        }

        log.info "$inserts/$deletes rows were inserted/deleted on $date"
      }

      log.info 'Job finished'
    }
  }

  @Memoized
  @SuppressWarnings('GrMethodMayBeStatic')
  private String createTableName(Sql sql, String year, String month) {
    final tableName = "<table_name>_$year$month" as String
    log.info "Table ${tableName} will be created if not exists..."

    sql.execute """
      CREATE TABLE IF NOT EXISTS ${Sql.expand tableName} 
      SELECT * FROM <table_name> WHERE FALSE
    """

    tableName
  }

  static void main(String[] args) {
    new DbArchiver().execute(null)
  }

}