/**
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

package org.apache.mahout.cf.taste.impl;

import junit.textui.TestRunner;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.correlation.ItemCorrelation;
import org.apache.mahout.cf.taste.correlation.UserCorrelation;
import org.apache.mahout.cf.taste.impl.common.RandomUtils;
import org.apache.mahout.cf.taste.impl.correlation.PearsonCorrelation;
import org.apache.mahout.cf.taste.impl.model.GenericDataModel;
import org.apache.mahout.cf.taste.impl.model.GenericItem;
import org.apache.mahout.cf.taste.impl.model.GenericPreference;
import org.apache.mahout.cf.taste.impl.model.GenericUser;
import org.apache.mahout.cf.taste.impl.neighborhood.NearestNUserNeighborhood;
import org.apache.mahout.cf.taste.impl.recommender.CachingRecommender;
import org.apache.mahout.cf.taste.impl.recommender.GenericItemBasedRecommender;
import org.apache.mahout.cf.taste.impl.recommender.GenericUserBasedRecommender;
import org.apache.mahout.cf.taste.impl.recommender.slopeone.SlopeOneRecommender;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Item;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.User;
import org.apache.mahout.cf.taste.neighborhood.UserNeighborhood;
import org.apache.mahout.cf.taste.recommender.Recommender;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>Generates load on the whole implementation, for profiling purposes mostly.</p>
 */
public final class LoadTest extends TasteTestCase {

  private static final Logger log = Logger.getLogger(LoadTest.class.getName());

  private static final int NUM_USERS = 1600;
  private static final int NUM_ITEMS = 800;
  private static final int NUM_PREFS = 20;
  private static final int NUM_THREADS = 4;

  private final Random random = RandomUtils.getRandom();

  @Override
  public void setUp() throws Exception {
    super.setUp();
    setLogLevel(Level.INFO);
  }

  public void testSlopeOneLoad() throws Exception {
    DataModel model = createModel();
    Recommender recommender = new CachingRecommender(new SlopeOneRecommender(model));
    doTestLoad(recommender, 30);
  }

  public void testItemLoad() throws Exception {
    DataModel model = createModel();
    ItemCorrelation itemCorrelation = new PearsonCorrelation(model);
    Recommender recommender = new CachingRecommender(new GenericItemBasedRecommender(model, itemCorrelation));
    doTestLoad(recommender, 120);
  }

  public void testUserLoad() throws Exception {
    DataModel model = createModel();
    UserCorrelation userCorrelation = new PearsonCorrelation(model);
    UserNeighborhood neighborhood = new NearestNUserNeighborhood(10, userCorrelation, model);
    Recommender recommender =
            new CachingRecommender(new GenericUserBasedRecommender(model, neighborhood, userCorrelation));
    doTestLoad(recommender, 20);
  }

  private DataModel createModel() {

    List<Item> items = new ArrayList<Item>(NUM_ITEMS);
    for (int i = 0; i < NUM_ITEMS; i++) {
      items.add(new GenericItem<String>(String.valueOf(i)));
    }

    List<User> users = new ArrayList<User>(NUM_USERS);
    for (int i = 0; i < NUM_USERS; i++) {
      int numPrefs = random.nextInt(NUM_PREFS) + 1;
      List<Preference> prefs = new ArrayList<Preference>(numPrefs);
      for (int j = 0; j < numPrefs; j++) {
        prefs.add(new GenericPreference(null, items.get(random.nextInt(NUM_ITEMS)), random.nextDouble()));
      }
      GenericUser<String> user = new GenericUser<String>(String.valueOf(i), prefs);
      users.add(user);
    }

    return new GenericDataModel(users);
  }

  private void doTestLoad(Recommender recommender, int allowedTimeSec)
          throws InterruptedException, ExecutionException {

    ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
    Collection<Future<?>> futures = new ArrayList<Future<?>>(NUM_THREADS);
    Callable<?> loadTask = new LoadWorker(recommender);

    long start = System.currentTimeMillis();
    for (int i = 0; i < NUM_THREADS; i++) {
      futures.add(executor.submit(loadTask));
    }
    for (Future<?> future : futures) {
      future.get();
    }
    long end = System.currentTimeMillis();
    long timeMS = end - start;
    log.info("Load test completed in " + timeMS + "ms");
    assertTrue(timeMS < 1000L * (long) allowedTimeSec);
  }

  private final class LoadWorker implements Callable<Object> {

    private final Recommender recommender;

    private LoadWorker(Recommender recommender) {
      this.recommender = recommender;
    }

    public Object call() throws TasteException {
      for (int i = 0; i < NUM_USERS / 2; i++) {
        recommender.recommend(String.valueOf(random.nextInt(NUM_USERS)), 10);
      }
      recommender.refresh();
      for (int i = NUM_USERS / 2; i < NUM_USERS; i++) {
        recommender.recommend(String.valueOf(random.nextInt(NUM_USERS)), 10);
      }
      return null;
    }
  }

  public static void main(String... args) {
    TestRunner.run(LoadTest.class);
  }

}