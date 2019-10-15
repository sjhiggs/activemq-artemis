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

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.util.ServerUtil;

import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.util.HashMap;

public class ReplicatedFailbackRepeated {

   private Process server0;
   private Process server1;
   private Process server2;
   private Process server3;
   private Process server4;
   private Process server5;
   private Process server6;
   private Process server7;
   private Process server8;

   private static final String JMX_URL_SERVER0 = "service:jmx:rmi:///jndi/rmi://0.0.0.0:1099/jmxrmi";
   private static final String JMX_URL_SERVER1 = "service:jmx:rmi:///jndi/rmi://0.0.0.0:1199/jmxrmi";
   private static final String JMX_URL_SERVER2 = "service:jmx:rmi:///jndi/rmi://0.0.0.0:1299/jmxrmi";
   private static final String JMX_URL_SERVER3 = "service:jmx:rmi:///jndi/rmi://0.0.0.0:1399/jmxrmi";
   private static final String JMX_URL_SERVER4 = "service:jmx:rmi:///jndi/rmi://0.0.0.0:1499/jmxrmi";
   private static final String JMX_URL_SERVER5 = "service:jmx:rmi:///jndi/rmi://0.0.0.0:1599/jmxrmi";
   private static final String JMX_URL_SERVER6 = "service:jmx:rmi:///jndi/rmi://0.0.0.0:1699/jmxrmi";
   private static final String JMX_URL_SERVER7 = "service:jmx:rmi:///jndi/rmi://0.0.0.0:1799/jmxrmi";
   private static final String JMX_URL_SERVER8 = "service:jmx:rmi:///jndi/rmi://0.0.0.0:1899/jmxrmi";

   private static int JMX_TIMEOUT = 120000;

   public static void main(final String[] args) throws Exception {
      new ReplicatedFailbackRepeated().runTest(args[0], new Integer(args[1]));
   }

   private void runTest(String baseDir, Integer numTests) throws Exception {

      try {

         //this is the master/slave pair being tested
         server0 = ServerUtil.startServer(baseDir+"/target/server0", "server0", 0, 30000);
         server1 = ServerUtil.startServer(baseDir+"/target/server1", "server1", 1, 30000);

         //remainder of servers are only for quorum/cluster testing
         server2 = ServerUtil.startServer(baseDir+"/target/server2", "server2", 2, 30000);
         server3 = ServerUtil.startServer(baseDir+"/target/server3", "server3", 3, 30000);
         server4 = ServerUtil.startServer(baseDir+"/target/server4", "server4", 4, 30000);
         server5 = ServerUtil.startServer(baseDir+"/target/server5", "server5", 5, 30000);
         server6 = ServerUtil.startServer(baseDir+"/target/server6", "server6", 6, 30000);
         server7 = ServerUtil.startServer(baseDir+"/target/server7", "server7", 7, 30000);
         server8 = ServerUtil.startServer(baseDir+"/target/server8", "server8", 8, 30000);

         //Test 0: Initial State Checks
         assertBrokerLive(JMX_URL_SERVER0, "server0", JMX_TIMEOUT);
         assertBrokerBackup(JMX_URL_SERVER1, "server1", JMX_TIMEOUT);
         assertBrokerLive(JMX_URL_SERVER2, "server2", JMX_TIMEOUT);
         assertBrokerBackup(JMX_URL_SERVER3, "server3", JMX_TIMEOUT);
         assertBrokerLive(JMX_URL_SERVER4, "server4", JMX_TIMEOUT);
         assertBrokerBackup(JMX_URL_SERVER5, "server5", JMX_TIMEOUT);
         assertBrokerLive(JMX_URL_SERVER6, "server6", JMX_TIMEOUT);
         assertBrokerBackup(JMX_URL_SERVER7, "server7", JMX_TIMEOUT);
         assertBrokerLive(JMX_URL_SERVER8, "server8", JMX_TIMEOUT);

         for(int i=0; i < new Integer(numTests); i++) {

            System.out.println("current test iterator:: " + i);

            //allow server1 to fully replicate with server0 before killing server0 again
            System.out.println("waiting for 30 seconds");
            Thread.sleep(30000);

            //TEST 1: kill the master, server0 is now unavailable, server1 becomes live
            ServerUtil.killServer(server0);
            assertBrokerLive(JMX_URL_SERVER1, "server1", JMX_TIMEOUT);

            System.out.println("waiting for 5 seconds");
            Thread.sleep(5000);
            
            //TEST 2: start up the master, server0 should be live, server1 should be backup
            server0 = ServerUtil.startServer(baseDir+"/target/server0", "server0", 0, 120000);
            assertBrokerLive(JMX_URL_SERVER0, "server0", JMX_TIMEOUT);
            assertBrokerBackup(JMX_URL_SERVER1, "server1", JMX_TIMEOUT);
            
         }

      } finally {

         ServerUtil.killServer(server0);
         ServerUtil.killServer(server1);
         ServerUtil.killServer(server2);
         ServerUtil.killServer(server3);
         ServerUtil.killServer(server4);
         ServerUtil.killServer(server5);
         ServerUtil.killServer(server6);
         ServerUtil.killServer(server7);
         ServerUtil.killServer(server8);
      }
   }

   //throws a RuntimeException if broker is not live
   private void assertBrokerLive(String jmxUrl, String serverName, int timeout) throws InterruptedException {
       assertBrokerState(jmxUrl, serverName, true, timeout);
   }

   //throws a RuntimeException if broker is not a backup
   private void assertBrokerBackup(String jmxUrl, String serverName, int timeout) throws InterruptedException {
       assertBrokerState(jmxUrl, serverName, false, timeout);
   }

   //throws exception if broker is not in expected state, will keep trying until timeout for JMX & MBean availability
   private void assertBrokerState(String jmxUrl, String serverName, Boolean expectLive, int timeout) throws InterruptedException {
       long realTimeout = System.currentTimeMillis() + timeout;
       while (System.currentTimeMillis() < realTimeout) {
           try {
               boolean isLive = isBrokerLiveInternal(jmxUrl, serverName);
               if (expectLive == false && isLive ) {
                   throw new RuntimeException("Server " + serverName + " was expected to be backup, but was not");
               }
               if (expectLive == true && isLive == false) {
                   throw new RuntimeException("Server " + serverName + " was expected to be live, but was not");
               }

               //all checks passed, break out of while loop and return
               return;
           } catch (Exception e) {
               //ignore, wait for timeout
               System.out.println("waiting for JMX server: " + e.getMessage());
               Thread.sleep(1000);
           }
       }
       throw new RuntimeException("Error!  timed out waiting for backup broker");
   }

   private Boolean isBrokerLiveInternal(String jmxUrl, String serverName) {
      try {
         ObjectName ON_SERVER = ObjectNameBuilder.create(ActiveMQDefaultConfiguration.getDefaultJmxDomain(), serverName, true).getActiveMQServerObjectName();
         System.out.println("querying JMX server: " + jmxUrl);
         HashMap env = new HashMap();
         String[] creds = {"guest", "guest"};
         env.put(JMXConnector.CREDENTIALS, creds);
         JMXConnector connector = JMXConnectorFactory.connect(new JMXServiceURL(jmxUrl), env);
         try {
            MBeanServerConnection mbsc = connector.getMBeanServerConnection();
            ActiveMQServerControl serverControl = MBeanServerInvocationHandler.newProxyInstance(mbsc, ON_SERVER, ActiveMQServerControl.class, false);
            return !serverControl.isBackup();
         } catch (Exception e) {
            String msg = e.getMessage();
            if(msg != null && msg.contains("Broker is not started")) {
               throw new RuntimeException("JMX connected, but could not query mbean:"  + e.getMessage());
            } else {
               throw new RuntimeException("was able to connect to JMX server, but encountered unexpected error: " + e.getMessage());
            }
         }
      } catch (Exception e) {
         throw new RuntimeException("unable to connect to JMX server: " + jmxUrl + " :: " + e.getMessage());
      }
   }
}
