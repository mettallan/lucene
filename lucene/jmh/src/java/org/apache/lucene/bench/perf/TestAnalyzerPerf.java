/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.bench.perf;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.ja.JapaneseAnalyzer;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterFilter;
import org.apache.lucene.analysis.ngram.EdgeNGramTokenFilter;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.SuppressForbidden;

// TODO
//   - back-test

// javac -d /l/util/build -cp build/core/classes/java:build/analysis/common/classes/java
// /l/util/src/main/perf/TestAnalyzerPerf.java; java -cp
// /l/util/build:build/core/classes/java:build/analysis/common/classes/java perf.TestAnalyzerPerf
// /lucenedata/enwiki/enwiki-20130102-lines.txt

/** The type Test analyzer perf. */
@SuppressForbidden(reason = "JMH uses std out for user output")
public class TestAnalyzerPerf {

  /** Instantiates a new Test analyzer perf. */
  public TestAnalyzerPerf() {}

  private static void testAnalyzer(String desc, File wikiLinesFile, Analyzer a) throws Exception {
    testAnalyzer(desc, wikiLinesFile, a, 10000, 100000);
  }

  private static void testAnalyzer(
      String desc, File wikiLinesFile, Analyzer a, int warmupCount, int runCount) throws Exception {
    System.out.println("\nTEST: " + desc);

    // 64 KB buffer
    InputStream is = new FileInputStream(wikiLinesFile);
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8), 1 << 16);

    long startTime = System.currentTimeMillis();
    long sumTime = 0;
    long hash = 0;
    long tokenCount = 0;
    int totCount = warmupCount + runCount;
    for (int i = 0; i < totCount; i++) {

      boolean isWarmup = i < warmupCount;

      if (i % 10000 == 0) {
        System.out.println(
            String.format(
                Locale.ROOT,
                "%.1f sec: %d...",
                (System.currentTimeMillis() - startTime) / 1000.0,
                i));
      }
      String s = reader.readLine();
      long t0 = System.nanoTime();
      TokenStream ts = a.tokenStream("field", new StringReader(s));
      ts.reset();

      CharTermAttribute termAtt = ts.getAttribute(CharTermAttribute.class);
      PositionIncrementAttribute posIncAtt;
      if (ts.hasAttribute(PositionIncrementAttribute.class)) {
        posIncAtt = ts.getAttribute(PositionIncrementAttribute.class);
      } else {
        posIncAtt = null;
      }
      OffsetAttribute offsetAtt;
      if (ts.hasAttribute(OffsetAttribute.class)) {
        offsetAtt = ts.getAttribute(OffsetAttribute.class);
      } else {
        offsetAtt = null;
      }

      while (ts.incrementToken()) {
        hash += 31 * ArrayUtil.hashCode(termAtt.buffer(), 0, termAtt.length());
        if (posIncAtt != null) {
          hash += 31 * posIncAtt.getPositionIncrement();
        }
        if (offsetAtt != null) {
          hash += 31 * offsetAtt.startOffset();
          hash += 31 * offsetAtt.endOffset();
        }
        if (isWarmup == false) {
          tokenCount++;
        }
      }
      ts.end();
      ts.close();

      if (isWarmup == false) {
        sumTime += System.nanoTime() - t0;
      }
    }
    reader.close();

    System.out.println(
        String.format(
            Locale.ROOT,
            "%s time=%.2f msec hash=%d tokens=%d",
            desc,
            (sumTime / 1000000.0),
            hash,
            tokenCount));
  }

  private static class LowerCaseAnalyzer extends Analyzer {

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
      Tokenizer src = new WhitespaceTokenizer();
      TokenStream tok = new LowerCaseFilter(src);
      return new TokenStreamComponents(src, tok);
    }
  }

  private static class EdgeNGramsAnalyzer extends Analyzer {

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
      Tokenizer src = new WhitespaceTokenizer();
      TokenStream tok = new EdgeNGramTokenFilter(src, 1, 3, false);
      return new TokenStreamComponents(src, tok);
    }
  }

  private static class ShinglesAnalyzer extends Analyzer {

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
      Tokenizer src = new WhitespaceTokenizer();
      // TokenStream tok = new ShingleFilter(src, 2, 2);
      TokenStream tok = new ShingleFilter(src, 2, 2);
      return new TokenStreamComponents(src, tok);
    }
  }

  private static class WDFAnalyzer extends Analyzer {

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
      Tokenizer src = new WhitespaceTokenizer();
      int flags = 0;
      flags |= WordDelimiterFilter.GENERATE_WORD_PARTS;
      flags |= WordDelimiterFilter.GENERATE_NUMBER_PARTS;
      flags |= WordDelimiterFilter.SPLIT_ON_CASE_CHANGE;
      flags |= WordDelimiterFilter.SPLIT_ON_NUMERICS;
      flags |= WordDelimiterFilter.STEM_ENGLISH_POSSESSIVE;
      TokenStream tok = new WordDelimiterFilter(src, flags, null);
      return new TokenStreamComponents(src, tok);
    }
  }

  /**
   * The entry point of application.
   *
   * @param args the input arguments
   * @throws Exception the exception
   */
  public static void main(String[] args) throws Exception {
    File wikiLinesFile = new File(args[0]);
    String lang = "en";
    if (args.length > 1) {
      lang = args[1];
    }
    if (lang.equals("en")) {
      testAnalyzer("Standard", wikiLinesFile, new StandardAnalyzer(CharArraySet.EMPTY_SET));
      testAnalyzer("LowerCase", wikiLinesFile, new LowerCaseAnalyzer());
      testAnalyzer("EdgeNGrams", wikiLinesFile, new EdgeNGramsAnalyzer());
      testAnalyzer("Shingles", wikiLinesFile, new ShinglesAnalyzer());
      testAnalyzer("WordDelimiterFilter", wikiLinesFile, new WDFAnalyzer());
    } else if (lang.equals("ja")) {
      testAnalyzer("Japanese", wikiLinesFile, new JapaneseAnalyzer());
    } else {
      throw new IllegalArgumentException("Unknown lang: " + lang);
    }
  }
}
