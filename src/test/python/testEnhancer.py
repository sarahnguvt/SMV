import unittest

from smvbasetest import SmvBaseTest

import pyspark
from pyspark.context import SparkContext
from pyspark.sql import SQLContext, HiveContext
from pyspark.sql.functions import col

class DfHelperTest(SmvBaseTest):
    def test_smvGroupBy(self):
        return "TODO implement"

    def test_smvHashSample_with_string(self):
        df = self.createDF("k:String", "a;b;c;d;e;f;g;h;i;j;k")
        r1 = df.unionAll(df).smvHashSample('k', 0.3)
        expect = self.createDF("k:String", "a;g;i;a;g;i")
        self.should_be_same(expect, r1)

    def test_smvHashSample_with_column(self):
        df = self.createDF("k:String", "a;b;c;d;e;f;g;h;i;j;k")
        r1 = df.unionAll(df).smvHashSample(col('k'), 0.3)
        expect = self.createDF("k:String", "a;g;i;a;g;i")
        self.should_be_same(expect, r1)

    def test_dedupByKey(self):
        schema = "a:Integer; b:Double; c:String"
        df = self.createDF(
            schema,
            """1,2.0,hello;
            1,3.0,hello;
            2,10.0,hello2;
            2,11.0,hello3"""
        )
        r1 = df.smvDedupByKey("a").orderBy("a")
        expect = self.createDF(
            schema,
            """1,2.0,hello;
            2,10.0,hello2"""
        )
        self.should_be_same(expect, r1)

    def test_smvSelectMinus_with_string(self):
        schema = "k:String;v1:Integer;v2:Integer"
        df = self.createDF(schema, "a,1,2;b,2,3")
        r1 = df.smvSelectMinus("v1")
        expect = self.createDF("k:String;v2:Integer", "a,2;b,3")
        self.should_be_same(expect, r1)

    def test_smvSelectMinus_with_column(self):
        schema = "k:String;v1:Integer;v2:Integer"
        df = self.createDF(schema, "a,1,2;b,2,3")
        r1 = df.smvSelectMinus(col("v1"))
        expect = self.createDF("k:String;v2:Integer", "a,2;b,3")
        self.should_be_same(expect, r1)

    def test_smvSelectPlus(self):
        df = self.createDF("k:String;v:Integer", "a,1;b,2")
        r1 = df.smvSelectPlus((col('v') + 1).alias("v2"))
        expect = self.createDF("k:String;v:Integer;v2:Integer", "a,1,2;b,2,3")
        self.should_be_same(expect, r1)

    def test_smvEdd(self):
        import smv
        df = self.createDF("k:String;v:Integer", "a,1;b,2")
        res = smv._smvEdd(df)
        self.assertEqual(res, """k                    Non-Null Count         2
k                    Min Length             1
k                    Max Length             1
k                    Approx Distinct Count  2
v                    Non-Null Count         2
v                    Average                1.5
v                    Standard Deviation     0.7071067811865476
v                    Min                    1.0
v                    Max                    2.0""")

    def test_smvHist(self):
        import smv
        df = self.createDF("k:String;v:Integer", "a,1;b,2")
        res = smv._smvHist(df, "k")
        self.assertEqual(res, """Histogram of k: String sort by Key
key                      count      Pct    cumCount   cumPct
a                            1   50.00%           1   50.00%
b                            1   50.00%           2  100.00%
-------------------------------------------------""")

    def test_smvConcatHist(self):
        import smv
        df = self.createDF("k:String;v:String", "a,1;b,2")
        res = smv._smvConcatHist(df, ["k", "v"])
        self.assertEqual(res, """Histogram of k_v: String sort by Key
key                      count      Pct    cumCount   cumPct
a_1                          1   50.00%           1   50.00%
b_2                          1   50.00%           2  100.00%
-------------------------------------------------""")

    def test_smvFreqHist(self):
        import smv
        df = self.createDF("k:String;v:String", "a,1;b,2;a,3")
        res = smv._smvFreqHist(df, "k")
        self.assertEqual(res, """Histogram of k: String sorted by Frequency
key                      count      Pct    cumCount   cumPct
a                            2   66.67%           2   66.67%
b                            1   33.33%           3  100.00%
-------------------------------------------------""")

    def test_smvCountHist(self):
        import smv
        df = self.createDF("k:String;v:String", "a,1;b,2;a,3")
        res = smv._smvCountHist(df, ["k"], 1)
        self.assertEqual(res, """Histogram of N_k: with BIN size 1.0
key                      count      Pct    cumCount   cumPct
1.0                          1   50.00%           1   50.00%
2.0                          1   50.00%           2  100.00%
-------------------------------------------------""")

    def test_smvBinHist(self):
        import smv
        df = self.createDF("k:String;v:Integer", "a,10;b,200;a,30")
        res = smv._smvBinHist(df, ("v", 100))
        self.assertEqual(res, """Histogram of v: with BIN size 100.0
key                      count      Pct    cumCount   cumPct
0.0                          2   66.67%           2   66.67%
200.0                        1   33.33%           3  100.00%
-------------------------------------------------""")

class ColumnHelperTest(unittest.TestCase):
    def test_smvMonth(self):
        return "TODO implement"
