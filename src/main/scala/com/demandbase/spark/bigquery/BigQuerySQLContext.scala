package com.demandbase.spark.bigquery


import java.math.BigInteger

import com.google.cloud.hadoop.io.bigquery.{AvroBigQueryInputFormat, _}
import com.demandbase.spark.bigquery.converters.SchemaConverters
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.hadoop.io.LongWritable
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.{DataFrame, Row, SQLContext}
import org.slf4j.LoggerFactory

/**
  * Created by samelamin on 12/08/2017.
  */
 class BigQuerySQLContext(sqlContext: SQLContext)  extends Serializable {
  lazy val bq = BigQueryClient.getInstance(sqlContext)
  @transient
  lazy val hadoopConf = sqlContext.sparkSession.sparkContext.hadoopConfiguration
  private val logger = LoggerFactory.getLogger(classOf[BigQueryClient])

  /**
    * Set whether to allow schema updates
    */
  def setAllowSchemaUpdates(value: Boolean = true): Unit = {
    hadoopConf.set(bq.ALLOW_SCHEMA_UPDATES, value.toString)
  }

  /**
    * Set whether to use the Standard SQL Dialect
    */
  def useStandardSQLDialect(value: Boolean = true): Unit = {
    hadoopConf.set(bq.USE_STANDARD_SQL_DIALECT, value.toString)
  }
  /**
    * Set GCP project ID for BigQuery.
    */
  def setBigQueryProjectId(projectId: String): Unit = {
    hadoopConf.set(BigQueryConfiguration.PROJECT_ID_KEY, projectId)
  }

  def setGSProjectId(projectId: String): Unit = {
    // Also set project ID for GCS connector
    hadoopConf.set("fs.gs.project.id", projectId)
  }

  def setBQTableTimestampColumn(timestampColumn: String): Unit = {
    hadoopConf.set("timestamp_column", timestampColumn)
  }

  def setBQTimePartitioningField(timestampColumn: String): Unit = {
    hadoopConf.set("time_partitioning_column", timestampColumn)
  }

  /**
    * Set GCS bucket for temporary BigQuery files.
    */
  def setBigQueryGcsBucket(gcsBucket: String): Unit = {
    hadoopConf.set(BigQueryConfiguration.GCS_BUCKET_KEY, gcsBucket)
    sqlContext.sparkSession.conf.set(BigQueryConfiguration.GCS_BUCKET_KEY, gcsBucket)
  }

  /**
    * Set BigQuery dataset location, e.g. US, EU.
    */
  def setBigQueryDatasetLocation(location: String): Unit = {
    hadoopConf.set(bq.STAGING_DATASET_LOCATION, location)
  }

  /**
    * Set GCP JSON key file.
    */
  def setGcpJsonKeyFile(jsonKeyFile: String): Unit = {
    hadoopConf.set("google.cloud.auth.service.account.json.keyfile", jsonKeyFile)
    hadoopConf.set("mapred.bq.auth.service.account.json.keyfile", jsonKeyFile)
    hadoopConf.set("fs.gs.auth.service.account.json.keyfile", jsonKeyFile)
  }

  def setGcpJsonKeyText(jsonKeyText: String): Unit = {
    hadoopConf.set("google.cloud.auth.service.account.json.keytext", jsonKeyText)
    hadoopConf.set("mapred.bq.auth.service.account.json.keytext", jsonKeyText)
    hadoopConf.set("fs.gs.auth.service.account.json.keytext", jsonKeyText)
  }

  //// XXX Allow to set private key directly !!!!!

  /**
    * Set GCP pk12 key file.
    */
  def setGcpPk12KeyFile(pk12KeyFile: String): Unit = {
    hadoopConf.set("google.cloud.auth.service.account.keyfile", pk12KeyFile)
    hadoopConf.set("mapred.bq.auth.service.account.keyfile", pk12KeyFile)
    hadoopConf.set("fs.gs.auth.service.account.keyfile", pk12KeyFile)
  }

  /**
    *  Instead of performing the query at analyze time
    *   ( which bigQuerySelect does )
    *   find the schema , and then do a mapPartitions
    * @param sqlQuery
    * @return
    */
  def bigQuerySelectAlternative( sqlQuery : String ) : DataFrame = {
    val schema = ""

    /**
      * Pseudocode for this method
      */


    ???
  }

  def bigQuerySelect(sqlQuery: String): DataFrame = {
    /// XXX
    /// Instead of Doing the query here ...
    //// Create a view , and have it as part of the DataFrame process
    bq.selectQuery(sqlQuery)
    val tableData = sqlContext.sparkSession.sparkContext.newAPIHadoopRDD(
      hadoopConf,
      classOf[AvroBigQueryInputFormat],
      classOf[LongWritable],
      classOf[GenericData.Record]).map(x=>x._2)
    val schemaString = tableData.map(_.getSchema.toString).first()
    val schema = new Schema.Parser().parse(schemaString)
    val structType = SchemaConverters.avroToSqlType(schema).dataType.asInstanceOf[StructType]
    val converter = SchemaConverters.createConverterToSQL(schema)
      .asInstanceOf[GenericData.Record => Row]
    val result = sqlContext.createDataFrame(tableData.map(converter), structType)
    hadoopConf.set(BigQueryConfiguration.TEMP_GCS_PATH_KEY, null)
    result
  }

  def runDMLQuery(runDMLQuery:String):Unit = {
    bq.runDMLQuery(runDMLQuery)
  }

  def getLatestBQModifiedTime(tableReference: String): Option[BigInteger] = {
    bq.getLatestModifiedTime(BigQueryStrings.parseTableReference(tableReference))
  }

  def getBigQuerySchema(tableReference: String): StructType = {
    SchemaConverters.BQToSQLSchema(bq.getTableSchema(BigQueryStrings.parseTableReference(tableReference)))
  }


  /**
    * Load a BigQuery table as a [[DataFrame]].
    */
  def bigQueryTable(tableSpec: String): DataFrame = {
    val tableRef = BigQueryStrings.parseTableReference(tableSpec)
    Console.out.println(s" TABLE REF FOR TABLE SPEC ${tableSpec} is ${tableRef}")
    BigQueryConfiguration.configureBigQueryInput(
      hadoopConf, tableRef.getProjectId, tableRef.getDatasetId, tableRef.getTableId)
    val tableData = sqlContext.sparkContext.newAPIHadoopRDD(
      hadoopConf,
      classOf[AvroBigQueryInputFormat],
      classOf[LongWritable],
      classOf[GenericData.Record]).map(x=>x._2)
    val schemaString = tableData.map( gd => gd.getSchema.toString).first()

    Console.out.println(s" SCHEMA STRING = ${schemaString}")
    val schema = new Schema.Parser().parse(schemaString)
    val structType = SchemaConverters.avroToSqlType(schema).dataType.asInstanceOf[StructType]

    Console.out.println(s"  SPARK SQL SCHEMA = ${structType.treeString}")
    val converter = SchemaConverters.createConverterToSQL(schema)
      .asInstanceOf[GenericData.Record => Row]
    sqlContext.createDataFrame(tableData.map(converter), structType)
  }
}
