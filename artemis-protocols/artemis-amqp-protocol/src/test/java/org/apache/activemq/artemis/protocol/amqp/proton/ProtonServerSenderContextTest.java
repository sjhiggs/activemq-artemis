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
package org.apache.activemq.artemis.protocol.amqp.proton;

import org.apache.activemq.artemis.core.server.AddressQueryResult;
import org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback;
import org.apache.activemq.artemis.protocol.amqp.exceptions.ActiveMQAMQPNotFoundException;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.engine.Sender;
import org.junit.Test;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProtonServerSenderContextTest {

   @Test(expected = ActiveMQAMQPNotFoundException.class)
   public void testAcceptsNullSourceAddressWhenInitialising() throws Exception {
      Sender mockSender = mock(Sender.class);
      AMQPConnectionContext mockConnContext = mock(AMQPConnectionContext.class);

      AMQPSessionCallback mockSessionCallback = mock(AMQPSessionCallback.class);

      AddressQueryResult queryResult = new AddressQueryResult(null, Collections.emptySet(), 0, false, false, false, false, 0);
      when(mockSessionCallback.addressQuery(any(), any(), anyBoolean())).thenReturn(queryResult);
      ProtonServerSenderContext sc = new ProtonServerSenderContext( mockConnContext, mockSender, null, mockSessionCallback);

      Source source = new Source();
      source.setAddress(null);
      when(mockSender.getRemoteSource()).thenReturn(source);


      sc.initialise();
   }

}
