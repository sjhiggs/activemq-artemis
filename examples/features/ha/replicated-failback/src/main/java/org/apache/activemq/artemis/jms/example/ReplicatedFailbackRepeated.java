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
package org.apache.activemq.artemis.jms.example;

import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.api.core.management.ResourceNames;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.management.JMSManagementHelper;
import org.apache.activemq.artemis.core.management.impl.ActiveMQServerControlImpl;
import org.apache.activemq.artemis.util.ServerUtil;

import javax.jms.*;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import javax.naming.InitialContext;
import java.util.HashMap;
import java.util.Hashtable;

public class ReplicatedFailbackRepeated {

   private Process server0;
   private Process server1;

   private static final String JMX_URL_SERVER0 = "service:jmx:rmi:///jndi/rmi://localhost:1099/jmxrmi";
   private static final String JMX_URL_SERVER1 = "service:jmx:rmi:///jndi/rmi://localhost:1199/jmxrmi";

   public static void main(final String[] args) throws Exception {

      new ReplicatedFailbackRepeated().runTest(args[0], args[1]);
   }

   private void runTest(String server0Path, String server1Path) throws Exception {

      try {
         server0 = ServerUtil.startServer(server0Path, ReplicatedFailbackRepeated.class.getSimpleName() + "0", 0, 30000);
         server1 = ServerUtil.startServer(server1Path, ReplicatedFailbackRepeated.class.getSimpleName() + "1", 1, 10000);

         // Step 1. Get an initial context for looking up JNDI for both servers

         InitialContext initialContext = null;
         InitialContext initialContext1 = null;

         Hashtable<String, Object> properties = new Hashtable<>();
         properties.put("java.naming.factory.initial", "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
         properties.put("connectionFactory.ConnectionFactory", "tcp://localhost:61616");
         initialContext = new InitialContext(properties);

         properties = new Hashtable<>();
         properties.put("java.naming.factory.initial", "org.apache.activemq.artemis.jndi.ActiveMQInitialContextFactory");
         properties.put("connectionFactory.ConnectionFactory", "tcp://localhost:61617");
         initialContext1 = new InitialContext(properties);

//         Thread.sleep(100000);
         Boolean server0Live = isBrokerLive(JMX_URL_SERVER0);
         Boolean server1Live = isBrokerLive(JMX_URL_SERVER1);
         System.out.println("TEST 1: server0Live: " + server0Live + " :: server1Live: " + server1Live);

         ServerUtil.killServer(server0);
         Thread.sleep(5000);
         //server0Live = isBrokerLive(JMX_URL_SERVER0);
         server1Live = isBrokerLive(JMX_URL_SERVER1);
         System.out.println("TEST 2: server1Live: " + server1Live);

         server0 = ServerUtil.startServer(server0Path, ReplicatedFailbackRepeated.class.getSimpleName() + "0", 0, 30000);
         Thread.sleep(30000);
         server0Live = isBrokerLive(JMX_URL_SERVER0, 3);
         server1Live = isBrokerLive(JMX_URL_SERVER1, 3);
         System.out.println("TEST 3: server0Live: " + server0Live + " :: server1Live: " + server1Live);

      } finally {

         //ServerUtil.killServer(server0);
         //ServerUtil.killServer(server1);
      }
   }

   private Boolean isBrokerLive(String jmxUrl, int numAttempts) {
      Boolean isLive = null;
      int curr = 0;
      while((isLive == null) && (curr < numAttempts)) {
         try {
            isLive = isBrokerLive(jmxUrl);
         } catch (Exception e) {
            System.out.println("jmx attempt failed: " + e.getMessage());
            System.out.flush();
            try {
               Thread.sleep(1000000);
            } catch (InterruptedException ex) {
               ex.printStackTrace();
            }
         } finally {
            curr++;
         }
      }

      return isLive;
   }

   private Boolean isBrokerLive(String jmxUrl) throws Exception {

      System.out.println("querying JMX server: " + jmxUrl);
      HashMap env = new HashMap();
      String[] creds = {"guest", "guest"};
      env.put(JMXConnector.CREDENTIALS, creds);
      JMXConnector connector = JMXConnectorFactory.connect(new JMXServiceURL(jmxUrl), env);
      MBeanServerConnection mbsc = connector.getMBeanServerConnection();
      ObjectName on = ObjectNameBuilder.DEFAULT.getActiveMQServerObjectName();
      ActiveMQServerControl serverControl = MBeanServerInvocationHandler.newProxyInstance(mbsc, on, ActiveMQServerControl.class, false);
      return !serverControl.isBackup();

   }
}
