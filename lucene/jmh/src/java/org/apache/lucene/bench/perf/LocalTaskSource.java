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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.SuppressForbidden;

/** The type Local task source. */
// Serves up tasks from locally loaded list:
@SuppressForbidden(reason = "JMH uses std out for user output")
public class LocalTaskSource implements TaskSource {

  private final AtomicInteger nextTask = new AtomicInteger();
  private final Map<String, List<Task>> tasks;

  /**
   * Instantiates a new Local task source.
   *
   * @param indexState the index state
   * @param taskParser the task parser
   * @param tasksFile the tasks file
   * @param staticRandom the static random
   * @param numTaskPerCat the num task per cat
   * @param doPKLookup the do pk lookup
   * @throws IOException the io exception
   * @throws ParseException the parse exception
   */
  public LocalTaskSource(
      IndexState indexState,
      TaskParser taskParser,
      String tasksFile,
      Random staticRandom,
      int numTaskPerCat,
      boolean doPKLookup)
      throws IOException, ParseException {

    Map<String, List<Task>> loadedTasks = loadTasks(taskParser, tasksFile);

    for (List<Task> tasks : loadedTasks.values()) {
      Collections.shuffle(tasks, staticRandom);
      // FYI - not prunning
      // final List<Task> prunedTasks = pruneTasks(tasks, numTaskPerCat);
    }

    final IndexSearcher searcher = indexState.mgr.acquire();
    final int maxDoc;
    try {
      maxDoc = searcher.getIndexReader().maxDoc();
    } finally {
      indexState.mgr.release(searcher);
    }

    // Add PK tasks
    // System.out.println("WARNING: skip PK tasks");
    if (doPKLookup) {
      final int numPKTasks = (int) Math.min(maxDoc / 6000., numTaskPerCat);
      final Set<BytesRef> pkSeenIDs = new HashSet<BytesRef>();
      // final Set<Integer> pkSeenIntIDs = new HashSet<Integer>();
      for (List<Task> tasks : loadedTasks.values()) {
        for (int idx = 0; idx < numPKTasks; idx++) {
          tasks.add(new PKLookupTask(maxDoc, staticRandom, 4000, pkSeenIDs, idx));
          // prunedTasks.add(new PointsPKLookupTask(maxDoc, staticRandom, 4000, pkSeenIntIDs, idx));
        }
      }
      /*
      final Set<BytesRef> pkSeenSingleIDs = new HashSet<BytesRef>();
      for(int idx=0;idx<numPKTasks*100;idx++) {
        prunedTasks.add(new SinglePKLookupTask(maxDoc, staticRandom, pkSeenSingleIDs, idx));
      }
      */
    }
    tasks = loadedTasks;
    //    if (groupByCat) {
    //      repeatTasksGrouped(prunedTasks, taskRepeatCount, random);
    //    } else {
    //      repeatTasksShuffled(prunedTasks, taskRepeatCount, random);
    //    }
    System.out.println("TASK LEN=" + tasks.size());
  }

  //  private void repeatTasksShuffled(List<Task> someTasks, int taskRepeatCount, Random random) {
  //    // Copy the pruned tasks multiple times, shuffling the order each time:
  //    for(int iter = 0; iter < taskRepeatCount; iter++) {
  //      Collections.shuffle(someTasks, random);
  //      for(Task task : someTasks) {
  //        tasks.add(task.clone());
  //      }
  //    }
  //  }
  //
  //  private void repeatTasksGrouped(List<Task> someTasks, int taskRepeatCount, Random random) {
  //    Map<String, List<Task>> tasksByCategory = new HashMap<>();
  //    for (Task task : someTasks) {
  //      String category = task.getCategory();
  //      tasksByCategory.computeIfAbsent(category, c -> new ArrayList<>()).add(task);
  //    }
  //    for (String category : tasksByCategory.keySet()) {
  //      List<Task> categoryTasks = tasksByCategory.get(category);
  //      repeatTasksShuffled(categoryTasks, taskRepeatCount, random);
  //    }
  //  }
  //
  //  @Override
  //  public Map<String, List<Task>> getAllTasks() {
  //    return tasks;
  //  }

  // --Commented out by Inspection START (10/7/21, 12:37 AM):
  //  private static List<Task> pruneTasks(List<Task> tasks, int numTaskPerCat) {
  //    final Map<String, Integer> catCounts = new HashMap<String, Integer>();
  //    final List<Task> newTasks = new ArrayList<Task>();
  //    for (Task task : tasks) {
  //      final String cat = task.getCategory();
  //      Integer v = catCounts.get(cat);
  //      int catCount;
  //      if (v == null) {
  //        catCount = 0;
  //      } else {
  //        catCount = v.intValue();
  //      }
  //
  //      if (catCount >= numTaskPerCat) {
  //        // System.out.println("skip task cat=" + cat);
  //        continue;
  //      }
  //      catCount++;
  //      catCounts.put(cat, catCount);
  //      newTasks.add(task);
  //    }
  //
  //    return newTasks;
  //  }
  // --Commented out by Inspection STOP (10/7/21, 12:37 AM)

  @Override
  public Task nextTask(String category) {
    List<Task> catTasks = this.tasks.get(category);
    if (catTasks == null) {
      throw new IllegalArgumentException(
          "Category " + category + " not found in " + tasks.keySet());
    }
    int next = nextTask.getAndIncrement();
    if (next >= catTasks.size()) {
      nextTask.set(0);
      next = 0;
    }
    return catTasks.get(next);
  }

  //  @Override
  //  public void taskDone() {}

  /**
   * Load tasks map.
   *
   * @param taskParser the task parser
   * @param filePath the file path
   * @return the map
   * @throws IOException the io exception
   * @throws ParseException the parse exception
   */
  static Map<String, List<Task>> loadTasks(TaskParser taskParser, String filePath)
      throws IOException, ParseException {
    Map<String, List<Task>> taskMap = new HashMap<>();

    final BufferedReader taskFile =
        new BufferedReader(
            new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8), 16384);
    while (true) {
      String line = taskFile.readLine();
      if (line == null) {
        break;
      }
      line = line.trim();
      if (line.indexOf("#") == 0) {
        // Ignore comment lines
        continue;
      }
      if (line.length() == 0) {
        // Ignore blank lines
        continue;
      }
      Task task = taskParser.parseOneTask(line);

      taskMap.compute(
          task.getCategory(),
          (k, v) -> {
            if (v == null) {
              v = new ArrayList<>();
            }
            v.add(task);
            return v;
          });
    }
    taskFile.close();
    return taskMap;
  }
}
