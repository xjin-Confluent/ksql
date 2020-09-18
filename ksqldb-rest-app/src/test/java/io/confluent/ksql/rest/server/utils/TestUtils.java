/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"); you may not use
 * this file except in compliance with the License.  You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.rest.server.utils;

import com.google.common.collect.Range;
import io.confluent.ksql.rest.entity.CommandId;
import io.confluent.ksql.rest.server.computation.Command;
import io.confluent.ksql.util.Pair;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class TestUtils {
  private static final int MIN_EPHEMERAL_PORT = 1024;
  private static final int MAX_EPHEMERAL_PORT = 65535;
  private static final int PORT_RANGE_INTERVAL = 650; // allows for 1000 random ports
  private static final AtomicInteger CURRENT_MIN_PORT = new AtomicInteger(MIN_EPHEMERAL_PORT);

  private static final int MAX_PORT_TRIES = 10;

  public static List<Pair<CommandId, Command>> getAllPriorCommandRecords() {
    final List<Pair<CommandId, Command>> priorCommands = new ArrayList<>();

    final Command csCommand = new Command("CREATE STREAM pageview "
                                    + "(viewtime bigint, pageid varchar, userid varchar) "
        + "WITH (kafka_topic='pageview_topic_json', value_format='json');",
        Collections.emptyMap(), Collections.emptyMap(), Optional.empty());
    final CommandId csCommandId =  new CommandId(CommandId.Type.STREAM, "_CSASStreamGen", CommandId.Action.CREATE);
    priorCommands.add(new Pair<>(csCommandId, csCommand));

    final Command csasCommand = new Command("CREATE STREAM user1pv "
        + " AS select * from pageview WHERE userid = 'user1';",
        Collections.emptyMap(), Collections.emptyMap(), Optional.empty());

    final CommandId csasCommandId =  new CommandId(CommandId.Type.STREAM, "_CSASGen", CommandId.Action.CREATE);
    priorCommands.add(new Pair<>(csasCommandId, csasCommand));


    final Command ctasCommand = new Command("CREATE TABLE user1pvtb "
                                      + " AS select * from pageview window tumbling(size 5 "
                                      + "second) WHERE userid = "
        + "'user1' group by pageid;",
        Collections.emptyMap(), Collections.emptyMap(), Optional.empty());

    final CommandId ctasCommandId =  new CommandId(CommandId.Type.TABLE, "_CTASGen", CommandId.Action.CREATE);
    priorCommands.add(new Pair<>(ctasCommandId, ctasCommand));

    return priorCommands;
  }

  /**
   *
   * Find a free port.
   *
   * <p>Note: Has a inherent race condition:
   * after finding the free port it releases it so the caller can use it. This opens up a window in
   * which another application can grab the free port, causing the test to fail.
   *
   * <p>Use only where there is no alternative. Jetty, for example, can allocate its own free
   * port. Where you do use it, ensure you do so in a loop that will retry if the port is no longer
   * free by the time the test comes to using it.
   *
   * Since this method retries within a range and chooses at random, conflicts are hopefully rare.
   * Also, since it tries at random over the range, it hopefully minimizes the chance of a race
   * occurring. Finally it also tries a new random range for each call to findFreeLocalPort to
   * even further reduce the chance of collision if all tests use this method.
   */
  public static int findFreeLocalPort() {
    final Range<Integer> portRange = getNextEphemeralPortRange();
    for (int i = 0; i < MAX_PORT_TRIES; i++) {
      final int portToTry =
          ThreadLocalRandom.current().nextInt(portRange.lowerEndpoint(), portRange.upperEndpoint());
      try (
          ServerSocket socket = new ServerSocket(portToTry);
      ) {
        return socket.getLocalPort();
      } catch (IOException e) {
        // In use
      }
    }
    throw new RuntimeException("Couldn't find free port");
  }


  private static Range<Integer> getNextEphemeralPortRange() {
    final int minEndpoint = CURRENT_MIN_PORT.getAndAdd(PORT_RANGE_INTERVAL);
    if (minEndpoint + PORT_RANGE_INTERVAL > MAX_EPHEMERAL_PORT) {
      throw new AssertionError(
          "Ran out of free ports. This can be remedied by decreasing the PORT_RANGE_INTERVAL " +
              "at added risk of port conflicts.");
    }
    return Range.closedOpen(minEndpoint, minEndpoint + PORT_RANGE_INTERVAL);
  }
}
