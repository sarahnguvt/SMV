import org.apache.spark.sql.functions._
import org.tresamigos.smv._

// create the init object "i" rather than create initialization at top level
// because shell would launch a separate command for each evalutaion which
// slows down startup considerably.
// keeping object name short to make the contents easy to access.
SmvApp.init(Seq("-m", "None").toArray, Option(sc))

object i {
  import org.apache.spark.sql.DataFrame
  import org.apache.spark.rdd.RDD
  import java.io.{File, PrintWriter}

  val app = SmvApp.app
  val sqlContext = app.sqlContext

  //-------- some helpful functions
  def smvSchema(df: DataFrame) = SmvSchema.fromDataFrame(df)

  def df(ds: SmvDataSet) = {
    app.resolveRDD(ds)
  }

  // deprecated, should use df instead!!!
  def s(ds: SmvDataSet) = df(ds)

  /** open file using full path */
  def open(path: String, ca: CsvAttributes = null) ={
    /** isFullPath = true to avoid prepending data_dir */
    val file = SmvCsvFile(path, ca, None, true)
    file.rdd
  }

  implicit class ShellSrddHelper(df: DataFrame) {
    def save(path: String) = {
      // TODO: why are we creating SmvDFHelper explicitly here?
      var helper = new org.tresamigos.smv.SmvDFHelper(df)
      helper.saveAsCsvWithSchema(path, CsvAttributes.defaultCsvWithHeader)
    }

    def savel(path: String) = {
      var res = df.collect.map{r => r.mkString(",")}.mkString("\n")
      val pw = new PrintWriter(new File(path))
      pw.println(res)
      pw.close()
    }
  }

  implicit class ShellRddHelper(rdd: RDD[String]) {
    def savel(path: String) = {
      var res = rdd.collect.mkString("\n")
      val pw = new PrintWriter(new File(path))
      pw.println(res)
      pw.close()
    }
  }

  def discoverSchema(path: String, n: Int = 100000, ca: CsvAttributes = CsvAttributes.defaultCsvWithHeader) = {
    implicit val csvAttributes=ca
    val helper = new SchemaDiscoveryHelper(sqlContext)
    val schema = helper.discoverSchemaFromFile(path, n)
    val outpath = SmvSchema.dataPathToSchemaPath(path) + ".toBeReviewed"
    schema.saveToHDFSFile(outpath)
  }

  // TODO: this should just be a direct helper on ds as it is probably common.
  def dumpEdd(ds: SmvDataSet) = i.s(ds).edd.addBaseTasks().dump

  def lsStage = app.stages.stageNames.foreach(println)

  def ls(stageName: String = null): Unit = {
    def dsInPackage(name: String, prefix: String = "") = {
      val dss = app.stages.findStage(name).allDatasets.sortBy(_.name)
      val printStrs = dss.map{
        case ds: SmvOutput => prefix + "(O)" + ds.name
        case ds: SmvModuleLink => prefix + "(L)" + ds.name
        case ds: SmvFile => prefix + "(F)" + ds.name
        case ds => prefix + "   " + ds.name
      }
      printStrs.foreach(println)
    }

    if(stageName != null) {
      dsInPackage(stageName)
    }else{
      app.stages.stageNames.foreach{n =>
        println("\n" + n + ":")
        dsInPackage(n, "  ")
      }
    }
  }

  def ls: Unit = ls()

  def ancestors(ds: SmvDataSet) = {
    val stage = app.stages.findStageForDataSet(ds)
    println(stage.name + ":")
    stage.ancestors(ds).map{d => stage.datasetBaseName(d)}.foreach(l => println("  " + l))
  }

  def descendants(ds: SmvDataSet) = {
    val stage = app.stages.findStageForDataSet(ds)
    println(stage.name + ":")
    stage.descendants(ds).map{d => stage.datasetBaseName(d)}.foreach(l => println("  " + l))
  }
}

import i._