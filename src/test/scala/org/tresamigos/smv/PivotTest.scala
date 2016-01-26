/*
 * This file is licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.tresamigos.smv

import org.apache.spark.sql.catalyst.expressions._
import org.apache.spark.sql.functions._

class SmvPivotTest extends SmvTestUtil {
  test("Test creation of unique column names") {
    val df = createSchemaRdd("k:String; p1:String; p2:String; p3:String; v:String; v2:Float",
      """x,p1_1,p2A,p3X,5,8;
         x,p1_2,p2A,p3X,6,9;
         x,p1_1,p2B,p3X,7,10""")

    val res = SmvPivot.getBaseOutputColumnNames(df, Seq(Seq("p1", "p2", "p3")))
    assertUnorderedSeqEqual(res, Seq(
      "p1_1_p2A_p3X",
      "p1_1_p2B_p3X",
      "p1_2_p2A_p3X",
      "p1_2_p2B_p3X"))
  }

  test("Test creation of unique column names with missing/non-id data") {
    val df = createSchemaRdd("p1:String; p2:String; p3:String; v:String",
      """p1_1,p2/A,,5;
         p1_1,p2/A,p3X,6;
         ,p2/B,p3X,7""")

    val res = SmvPivot.getBaseOutputColumnNames(df, Seq(Seq("p1", "p2", "p3")))
    assertUnorderedSeqEqual(res, Seq(
      "p1_1_p2_B_p3X",
      "p1_1_p2_B",
      "p1_1_p2_A_p3X",
      "p1_1_p2_A",
      "p2_B_p3X",
      "p2_B",
      "p2_A_p3X",
      "p2_A"))
  }

  test("Test creation of unique column names with 1 pivot column") {
    val df = createSchemaRdd("p1:String; v:String","p1_1,5; p1_2, 6")

    val res = SmvPivot.getBaseOutputColumnNames(df, Seq(Seq("p1")))
    assertUnorderedSeqEqual(res, Seq(
      "p1_1",
      "p1_2"))
  }

  test("test smvPivot on DF") {
    val ssc = sqlContext; import ssc.implicits._
    val df = createSchemaRdd("id:Integer;month:String;product:String;count:Integer",
      "1,5_14,A,100;1,6_14,B,200;1,5_14,B,300")
    val res = df.smvPivot(Seq("month", "product"))("count")("5_14_A", "5_14_B", "6_14_A", "6_14_B")
    assertSrddDataEqual(res,
      "1,5_14,A,100,100,null,null,null;" +
      "1,6_14,B,200,null,null,null,200;" +
      "1,5_14,B,300,null,300,null,null")
  }

  test("test smvPivotSum on GD") {
    val ssc = sqlContext; import ssc.implicits._
    val df = createSchemaRdd("id:Integer;month:String;product:String;count:Integer",
      "1,5_14,A,100;1,6_14,B,200;1,5_14,B,300")
    val res = df.smvGroupBy('id).smvPivotSum(Seq("month", "product"))("count")("5_14_A", "5_14_B", "6_14_A", "6_14_B")
    assertSrddSchemaEqual(res, "id: Integer; count_5_14_A: Long; count_5_14_B: Long; count_6_14_A: Long; count_6_14_B: Long")
    assertSrddDataEqual(res,
      "1,100,300,0,200")
  }

  test("Test smvPivot with pivotColSets") {
    val ssc = sqlContext; import ssc.implicits._
    val df = createSchemaRdd("k1:String; k2:String; p:String; v1:Integer; v2:Float",
      "1,x,A,10,100.5;" +
      "1,y,A,10,100.5;" +
      "1,x,A,20,200.5;" +
      "1,x,A,10,200.5;" +
      "1,x,B,50,200.5;" +
      "2,x,A,60,500")

    val res = df.smvGroupBy('k1).smvPivot(Seq("k2"), Seq("k2", "p"))("v2")("x", "x_A", "y_B").agg(
      countDistinct("v2_x") as 'dist_cnt_v2_x,
      countDistinct("v2_x_A") as 'dist_cnt_v2_x_A,
      countDistinct("v2_y_B") as 'dist_cnt_v2_y_B
    )

    assertUnorderedSeqEqual(res.collect.map(_.toString), Seq(
      "[1,2,2,0]",
      "[2,1,1,0]"))

    val fieldNames = res.schema.fieldNames.toList
    assert(fieldNames === Seq(
      "k1",
      "dist_cnt_v2_x",
      "dist_cnt_v2_x_A",
      "dist_cnt_v2_y_B"))
  }

  test("Test pivot_sum function") {
    val df = createSchemaRdd("k:String; p1:String; p2:String; v:Integer",
      "1,p1/a,p2a,100;" +
      "1,p1!b,p2b,200;" +
      "1,p1/a,p2b,300;" +
      "1,p1!b,p2a,400;" +
      "1,p1/a,p2a,500;" + // same key as first row!
      "2,p1/a,p2a,600") // test with a single input row per key

    import df.sqlContext._, implicits._
    // val res = df.pivot_sum('k, Seq('p1, 'p2), Seq('v))
    val res = df.smvGroupBy('k).smvPivotSum(Seq("p1", "p2"))("v")()
    assertUnorderedSeqEqual(res.collect.map(_.toString), Seq(
      "[1,600,300,400,200]",
      "[2,600,0,0,0]"))

    val fieldNames = res.schema.fieldNames.toList
    assert(fieldNames === Seq("k", "v_p1_a_p2a", "v_p1_a_p2b", "v_p1_b_p2a", "v_p1_b_p2b"))
  }

  test("Test pivot_sum function with float value") {
    val df = createSchemaRdd("k:String; p:String; v1:Integer; v2:Float",
      "1,A,10,100.5;" +
      "1,A,20,200.5;" +
      "1,B,50,200.5;" +
      "2,A,60,500")

    import df.sqlContext._, implicits._
    // val res = df.pivot_sum('k, Seq('p), Seq('v1, 'v2))
    val res = df.smvGroupBy('k).smvPivotSum(Seq("p"))("v1", "v2")()
    assertUnorderedSeqEqual(res.collect.map(_.toString), Seq(
      "[1,30,50,301.0,200.5]",
      "[2,60,0,500.0,0.0]"))

    val fieldNames = res.schema.fieldNames.toList
    assert(fieldNames === Seq("k", "v1_A", "v1_B", "v2_A", "v2_B"))
  }
}

class SmvPivotCoalesceTest extends SmvTestUtil {
  test("smvPivotCoalesce should work on data frames without a intra-group ranking column") {
    val df = createSchemaRdd("k1:String;k2:String", "A,X;B,Y;C,Y;D,Z")
    val res = df.smvPivotCoalesce("k2")("k1")

    assertSrddSchemaEqual(res, "k2:String;k1_1:String;k1_2:String")
    assertSrddDataEqual(res, "X,A,null;Y,B,C;Z,D,null")
  }

  test("it should use the given ranking column if provided") {
    val df = createSchemaRdd("k1:String;k2:String;rank:Integer", "A,X,1;B,Y,2;C,Y,1;D,Z,1")
    val res = df.smvPivotCoalesce("k2")("k1", Some("rank"))

    assertSrddSchemaEqual(res, "k2:String;k1_1:String;k1_2:String")
    assertSrddDataEqual(res, "X,A,null;Y,C,B;Z,D,null")
  }
}
