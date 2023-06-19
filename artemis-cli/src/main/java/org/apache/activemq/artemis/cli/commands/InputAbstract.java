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

package org.apache.activemq.artemis.cli.commands;

import java.util.Scanner;

import com.github.rvesse.airline.annotations.Option;

public class InputAbstract extends ActionAbstract {

   private Scanner scanner;

   private static boolean inputEnabled = false;

   /**
    * Test cases validating or using the CLI cannot deal with inputs,
    * so they are generally disabled, however the main method from the CLI will enable it back. */
   public static void enableInput() {
      inputEnabled = true;
   }

   @Option(name = "--silent", description = "Disable all the inputs, and make a best guess for any required input.")
   private boolean silentInput = false;

   public boolean isSilentInput() {
      return silentInput || !inputEnabled;
   }

   public void setSilentInput(boolean isSilent) {
      this.silentInput = isSilent;
   }

   protected boolean inputBoolean(String propertyName, String prompt, boolean silentDefault) {
      if (isSilentInput()) {
         return silentDefault;
      }

      Boolean booleanValue = null;
      do {
         String value = input(propertyName, prompt + ", valid values are Y, N, True, False", Boolean.toString(silentDefault));

         switch (value.toUpperCase().trim()) {
            case "TRUE":
            case "Y":
               booleanValue = Boolean.TRUE; break;

            case "FALSE":
            case "N":
               booleanValue = Boolean.FALSE; break;
         }
      }
      while (booleanValue == null);

      return booleanValue.booleanValue();
   }

   public int inputInteger(String propertyName, String prompt, String silentDefault) {

      Integer value = null;
      do {
         String input = input(propertyName, prompt, silentDefault);
         if (input == null || input.trim().equals("")) {
            input = "0";
         }

         try {
            value = Integer.parseInt(input);
         } catch (NumberFormatException e) {
            e.printStackTrace();
            value = null;
         }
      }
      while(value == null);

      return value.intValue();
   }

   protected String input(String propertyName, String prompt, String silentDefault) {
      return input(propertyName, prompt, silentDefault, false);
   }

   protected String input(String propertyName, String prompt, String silentDefault, boolean acceptNull) {
      if (isSilentInput()) {
         return silentDefault;
      }

      String inputStr;
      boolean valid = false;
      getActionContext().out.println();
      do {
         getActionContext().out.println(propertyName + ":");
         getActionContext().out.println(prompt);
         inputStr = scanner.nextLine();
         if (!acceptNull && inputStr.trim().equals("")) {
            getActionContext().out.println("Invalid Entry!");
         } else {
            valid = true;
         }
      }
      while (!valid);

      return inputStr.trim();
   }

   protected String inputPassword(String propertyName, String prompt, String silentDefault) {
      if (isSilentInput()) {
         return silentDefault;
      }

      String inputStr = "";
      boolean valid = false;
      getActionContext().out.println();
      do {
         getActionContext().out.println(propertyName + ": is mandatory with this configuration:");
         getActionContext().out.println(prompt);
         char[] chars = System.console().readPassword();

         // could be null if the user input something weird like Ctrl-d
         if (chars == null) {
            getActionContext().out.println("Invalid Entry!");
            continue;
         }

         inputStr = new String(chars);

         if (inputStr.trim().equals("")) {
            getActionContext().out.println("Invalid Entry!");
         } else {
            valid = true;
         }
      }
      while (!valid);

      return inputStr.trim();
   }

   @Override
   public Object execute(ActionContext context) throws Exception {
      super.execute(context);

      this.scanner = new Scanner(context.in);

      return null;
   }
}
