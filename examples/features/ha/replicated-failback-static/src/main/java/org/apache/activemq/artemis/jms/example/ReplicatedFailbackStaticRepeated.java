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

public class ReplicatedFailbackStaticRepeated {

   private Process server0;
   private Process server1;

   private static final String JMX_URL_SERVER0 = "service:jmx:rmi:///jndi/rmi://localhost:1099/jmxrmi";
   private static final String JMX_URL_SERVER1 = "service:jmx:rmi:///jndi/rmi://localhost:1199/jmxrmi";

   public static void main(final String[] args) throws Exception {

      new ReplicatedFailbackStaticRepeated().runTest(args[0], args[1]);
   }

   private void runTest(String server0Path, String server1Path) throws Exception {

      try {
         server0 = ServerUtil.startServer(server0Path, ReplicatedFailbackStaticRepeated.class.getSimpleName() + "0", 0, 30000);
         server1 = ServerUtil.startServer(server1Path, ReplicatedFailbackStaticRepeated.class.getSimpleName() + "1", 1, 10000);

         // Step 1. Get an initial context for looking up JNDI for both servers

         for (int replicat = 0; replicat < 1000; replicat++) {
            System.out.println("Replicat iterator:: " + replicat);
            //TEST 2: kill the master, server0 is now unavailable, server1 becomes live
            ServerUtil.killServer(server0);

            if (!ServerUtil.waitForServerToStart(1, 6000)) {
               throw new RuntimeException("Server1 did not become live");
            }

            //TEST 3: start up the master, server0 should be live, server1 should be backup
            server0 = ServerUtil.startServer(server0Path, ReplicatedFailbackStaticRepeated.class.getSimpleName() + "0", 0, 30000);

            if (!ServerUtil.waitForServerToStart(0, 5000)) {
               throw new RuntimeException("Server 0 did not start");
            }

            Boolean server0Live = isBrokerLive(JMX_URL_SERVER0);

            if (server0Live == null) {
               throw new RuntimeException("Server0 is started but JMX is not");
            }

            Thread.sleep(15000);

            Boolean server1Live = isBrokerLive(JMX_URL_SERVER1);

            if (server0Live.booleanValue() && !server1Live.booleanValue()) {
               // OK
            } else {
               throw new RuntimeException("server0Live=" + server1Live + " and server1Live=" + server0Live);
            }
         }
      } finally {

         ServerUtil.killServer(server0);
         ServerUtil.killServer(server1);
      }
   }

   public Boolean isBrokerLive(String jmxUrl) {
      Boolean isLive = null;
      try {
         isLive = isBrokerLiveInternal(jmxUrl);
      } catch (Exception e) {
         System.out.println("Cannot connect " + jmxUrl);
         e.printStackTrace();
         System.out.println("jmx attempt failed: " + e.getMessage());
         return false;
      } 
      return isLive;
   }

   private Boolean isBrokerLiveInternal(String jmxUrl) throws Exception {

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
