/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq;

import javax.jms.DeliveryMode;

import org.apache.activemq.test.JmsTopicSendReceiveTest;

/**
 * https://issues.apache.org/jira/browse/ARTEMIS-189
 */
public class JmsDurableQueueWildcardSendReceiveTest extends JmsTopicSendReceiveTest {

   // Set up the test with a queue and persistent delivery mode.
   @Override
   protected void setUp() throws Exception {
      topic = false;
      deliveryMode = DeliveryMode.PERSISTENT;
      super.setUp();
   }

   @Override
   protected String getConsumerSubject() {
      return "FOO.>";
   }

   @Override
   protected String getProducerSubject() {
      return "FOO.BAR.HUMBUG";
   }
}
