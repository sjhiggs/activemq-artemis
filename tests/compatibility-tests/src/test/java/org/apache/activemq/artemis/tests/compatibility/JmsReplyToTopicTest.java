/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.compatibility;

import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.JAKARTAEE;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.ONE_FOUR;
import static org.apache.activemq.artemis.tests.compatibility.GroovyRun.SNAPSHOT;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.activemq.artemis.tests.compatibility.base.VersionedBase;
import org.apache.activemq.artemis.tests.extensions.parameterized.ParameterizedTestExtension;
import org.apache.activemq.artemis.tests.extensions.parameterized.Parameters;
import org.apache.activemq.artemis.utils.FileUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ParameterizedTestExtension.class)
public class JmsReplyToTopicTest extends VersionedBase {

   @Parameters(name = "server={0}, producer={1}, consumer={2}")
   public static Collection getParameters() {
      List<Object[]> combinations = new ArrayList<>();
      combinations.add(new Object[]{SNAPSHOT, ONE_FOUR, SNAPSHOT});
      combinations.add(new Object[]{SNAPSHOT, SNAPSHOT, ONE_FOUR});
      combinations.add(new Object[]{SNAPSHOT, SNAPSHOT, SNAPSHOT});
      combinations.add(new Object[]{JAKARTAEE, JAKARTAEE, JAKARTAEE});
      combinations.add(new Object[]{JAKARTAEE, JAKARTAEE, SNAPSHOT});
      combinations.add(new Object[]{JAKARTAEE, SNAPSHOT, JAKARTAEE});
      combinations.add(new Object[]{JAKARTAEE, JAKARTAEE, ONE_FOUR});
      combinations.add(new Object[]{JAKARTAEE, ONE_FOUR, JAKARTAEE});
      return combinations;
   }

   public JmsReplyToTopicTest(String server, String sender, String receiver) throws Exception {
      super(server, sender, receiver);
   }


   @BeforeEach
   public void setUp() throws Throwable {
      FileUtil.deleteDirectory(serverFolder);
   }

   @AfterEach
   public void stopTest() throws Exception {
      execute(serverClassloader, "server.stop()");
   }

   @TestTemplate
   public void testJmsReplyToTopic() throws Throwable {
      evaluate(serverClassloader, "jmsReplyToTopic/artemisServer.groovy", serverFolder.getAbsolutePath(), server);

      CountDownLatch consumerCreated = new CountDownLatch(1);
      CountDownLatch receiverLatch = new CountDownLatch(1);
      CountDownLatch senderLatch = new CountDownLatch(1);

      setVariable(receiverClassloader, "latch", receiverLatch);
      setVariable(receiverClassloader, "consumerCreated", consumerCreated);

      AtomicInteger errors = new AtomicInteger(0);
      Thread t1 = new Thread(() -> {
         try {
            if (JAKARTAEE.equals(receiver)) {
               evaluate(receiverClassloader, "jakartaReplyToTopic/receiveMessages.groovy", receiver);
            } else {
               evaluate(receiverClassloader, "jmsReplyToTopic/receiveMessages.groovy", receiver);
            }
         } catch (Throwable e) {
            e.printStackTrace();
            errors.incrementAndGet();
         }
      });
      t1.start();

      assertTrue(consumerCreated.await(10, TimeUnit.SECONDS));

      setVariable(senderClassloader, "senderLatch", senderLatch);
      Thread t2 = new Thread(() -> {
         try {
            if (JAKARTAEE.equals(sender)) {
               evaluate(senderClassloader, "jakartaReplyToTopic/sendMessagesAddress.groovy", sender);
            } else {
               evaluate(senderClassloader, "jmsReplyToTopic/sendMessagesAddress.groovy", sender);
            }
         } catch (Throwable e) {
            e.printStackTrace();
            errors.incrementAndGet();
         }
      });
      t2.start();

      try {
         assertTrue(senderLatch.await(10, TimeUnit.SECONDS), "Sender did not get message from topic");
         assertTrue(receiverLatch.await(10, TimeUnit.SECONDS), "Receiver did not receive messages");
      } finally {

         t1.join(TimeUnit.SECONDS.toMillis(1));
         t2.join(TimeUnit.SECONDS.toMillis(1));

         if (t1.isAlive()) {
            t1.interrupt();
         }

         if (t2.isAlive()) {
            t2.interrupt();
         }
      }

   }

}