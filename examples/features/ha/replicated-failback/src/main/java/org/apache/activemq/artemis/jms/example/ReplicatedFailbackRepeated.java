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

import javax.json.*;
import javax.management.MBeanServerConnection;
import javax.management.MBeanServerInvocationHandler;
import javax.management.ObjectName;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.StringReader;
import java.util.HashMap;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.util.ServerUtil;
import org.apache.activemq.artemis.utils.JsonLoader;

public class ReplicatedFailbackRepeated {

   private Process server0;
   private Process server1;
   private Process server2;
   private Process server3;
   private Process server4;
   private Process server5;

   private static final String JMX_URL_SERVER0 = "service:jmx:rmi:///jndi/rmi://0.0.0.0:1099/jmxrmi";
   private static final String JMX_URL_SERVER1 = "service:jmx:rmi:///jndi/rmi://0.0.0.0:1199/jmxrmi";
   private static final String JMX_URL_SERVER2 = "service:jmx:rmi:///jndi/rmi://0.0.0.0:1299/jmxrmi";
   private static final String JMX_URL_SERVER3 = "service:jmx:rmi:///jndi/rmi://0.0.0.0:1399/jmxrmi";
   private static final String JMX_URL_SERVER4 = "service:jmx:rmi:///jndi/rmi://0.0.0.0:1499/jmxrmi";
   private static final String JMX_URL_SERVER5 = "service:jmx:rmi:///jndi/rmi://0.0.0.0:1599/jmxrmi";

   private static int LIVE_STATUS_TIMEOUT = 120000;
   private static int CLUSTER_STATUS_TIMEOUT = 120000;

   public static void main(final String[] args) throws Exception {
      new ReplicatedFailbackRepeated().runTest(args[0], new Integer(args[1]));
   }

   private void runTest(String baseDir, Integer numTests) throws Exception {

      try {

         //this is the master/slave pair being tested
         server0 = ServerUtil.startServer(baseDir + "/target/server0", "server0", 0, 30000);
         server1 = ServerUtil.startServer(baseDir + "/target/server1", "server1", 1, 0);

         //remainder of servers are only for quorum/cluster testing
         server2 = ServerUtil.startServer(baseDir + "/target/server2", "server2", 2, 30000);
         server3 = ServerUtil.startServer(baseDir + "/target/server3", "server3", 3, 0);
         server4 = ServerUtil.startServer(baseDir + "/target/server4", "server4", 4, 30000);
         server5 = ServerUtil.startServer(baseDir + "/target/server5", "server5", 5, 0);

         //Test 0: Initial State Checks
         assertBrokerLive(JMX_URL_SERVER0, "server0", LIVE_STATUS_TIMEOUT);
         assertBrokerBackup(JMX_URL_SERVER1, "server1", LIVE_STATUS_TIMEOUT);
         assertBrokerLive(JMX_URL_SERVER2, "server2", LIVE_STATUS_TIMEOUT);
         assertBrokerBackup(JMX_URL_SERVER3, "server3", LIVE_STATUS_TIMEOUT);
         assertBrokerLive(JMX_URL_SERVER4, "server4", LIVE_STATUS_TIMEOUT);
         assertBrokerBackup(JMX_URL_SERVER5, "server5", LIVE_STATUS_TIMEOUT);

         for (int i = 0; i < new Integer(numTests); i++) {

            System.out.println("------------------------------\ncurrent test iterator:: " + i + "\n------------------------------");

            //allow server1 to fully replicate with server0 before killing server0 again
            assertNumClusterBrokers(3, 3, CLUSTER_STATUS_TIMEOUT, JMX_URL_SERVER1, "server1");
            assertNumClusterBrokers(3, 3, CLUSTER_STATUS_TIMEOUT, JMX_URL_SERVER0, "server0");


            //TEST 1: kill the master, server0 is now unavailable, server1 becomes live
            ServerUtil.killServer(server0);
            assertBrokerLive(JMX_URL_SERVER1, "server1", LIVE_STATUS_TIMEOUT);
            assertNumClusterBrokers(3, 2, CLUSTER_STATUS_TIMEOUT, JMX_URL_SERVER1, "server1");


            //TEST 2: start up the master, server0 should be live, server1 should be backup
            server0 = ServerUtil.startServer(baseDir + "/target/server0", "server0", 0, 120000);
            assertNumClusterBrokers(3, 3, CLUSTER_STATUS_TIMEOUT, JMX_URL_SERVER1, "server1");
            assertNumClusterBrokers(3, 3, CLUSTER_STATUS_TIMEOUT, JMX_URL_SERVER0, "server0");

            assertBrokerLive(JMX_URL_SERVER0, "server0", LIVE_STATUS_TIMEOUT);
            assertBrokerBackup(JMX_URL_SERVER1, "server1", LIVE_STATUS_TIMEOUT);

         }

      } finally {

         ServerUtil.killServer(server0);
         ServerUtil.killServer(server1);
         ServerUtil.killServer(server2);
         ServerUtil.killServer(server3);
         ServerUtil.killServer(server4);
         ServerUtil.killServer(server5);
      }
   }

   private void assertNumClusterBrokers(int numLiveExpected, int numBackupExpected, int timeout, String jmxUrl, String serverName) throws InterruptedException {
      long realTimeout = System.currentTimeMillis() + timeout;
      while (System.currentTimeMillis() < realTimeout) {
         try {
            String topology = listBrokerTopology(jmxUrl, serverName, 10_000);
            JsonArray clusterNetworkArray = readJson(topology);
            int numLiveActual = getNumLiveBrokers(clusterNetworkArray);
            int numBackupActual = getNumBackupBrokers(clusterNetworkArray);
            if(numLiveActual != numLiveExpected || numBackupActual != numBackupExpected) {
               System.out.println("received cluster topology, but not at expected state: " +
                       numLiveExpected + "/" + numLiveActual + "(live expected/actual) :: " +
                       numBackupExpected + "/" + numBackupActual + "(backup expected/actual)");
               Thread.sleep(1000);
            } else {
               System.out.println("cluster topology test passed: " + topology);
               return;
            }

         } catch (Exception e) {
            //ignore, wait for timeout
            System.out.println("waiting for JMX server to retrieve cluster topology: " + e.getMessage());
            Thread.sleep(1000);
         }
      }
      throw new RuntimeException("Error!  timed out waiting for cluster topology");
   }

   private int getNumLiveBrokers(JsonArray clusterNetworkArray)  {
      return getNumBrokers(clusterNetworkArray, "live");
   }

   private int getNumBackupBrokers(JsonArray clusterNetworkArray) {
      return getNumBrokers(clusterNetworkArray, "backup");
   }

   private int getNumBrokers(JsonArray clusterNetworkArray, String status) {
      int numBrokers = 0;
      for(JsonValue jsonValue : clusterNetworkArray) {
         if(jsonValue.getValueType() == JsonValue.ValueType.OBJECT && ((JsonObject) jsonValue).containsKey(status)) {
            numBrokers++;
         }
      }
      return numBrokers;
   }

   private JsonArray readJson(String topology) {
      System.out.println("parsing topology: " + topology);
      JsonReader parser = JsonLoader.createReader(new StringReader(topology));
      JsonStructure struct = parser.read();
      if(struct.getValueType() != JsonValue.ValueType.ARRAY) {
         throw new RuntimeException("json value was not of an expected ARRAY type, input was: " + topology);
      }
      JsonArray jsonArray = (JsonArray) struct;
      return jsonArray;
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
   private void assertBrokerState(String jmxUrl,
                                  String serverName,
                                  Boolean expectLive,
                                  int timeout) throws InterruptedException {
      long realTimeout = System.currentTimeMillis() + timeout;
      while (System.currentTimeMillis() < realTimeout) {
         try {
            boolean isLive = isBrokerLiveInternal(jmxUrl, serverName);
            if (expectLive == false && isLive) {
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
            if (msg != null && msg.contains("Broker is not started")) {
               throw new RuntimeException("JMX connected, but could not query mbean:" + e.getMessage());
            } else {
               throw new RuntimeException("was able to connect to JMX server, but encountered unexpected error: " + e.getMessage());
            }
         } finally {
            connector.close();
         }
      } catch (Exception e) {
         throw new RuntimeException("unable to connect to JMX server: " + jmxUrl + " :: " + e.getMessage());
      }
   }

   private String listBrokerTopology(String jmxUrl, String serverName, int timeout) throws InterruptedException {

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
            return serverControl.listNetworkTopology();
         } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("Broker is not started")) {
               throw new RuntimeException("JMX connected, but could not query mbean:" + e.getMessage());
            } else {
               throw new RuntimeException("was able to connect to JMX server, but encountered unexpected error: " + e.getMessage());
            }
         } finally {
            connector.close();
         }
      } catch (Exception e) {
            throw new RuntimeException("unable to connect to JMX server: " + jmxUrl + " :: " + e.getMessage());
      }
   }

}
