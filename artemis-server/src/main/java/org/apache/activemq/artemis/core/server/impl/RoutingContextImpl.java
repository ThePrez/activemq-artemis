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
package org.apache.activemq.artemis.core.server.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.RouteContextList;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.transaction.Transaction;

public final class RoutingContextImpl implements RoutingContext {

   // The pair here is Durable and NonDurable
   private final Map<SimpleString, RouteContextList> map = new HashMap<>();

   private Transaction transaction;

   private int queueCount;

   private SimpleString address;

   private SimpleString previousAddress;

   private RoutingType previousRoutingType;

   private RoutingType routingType;

   Boolean reusable = null;

   volatile int version;

   private final Executor executor;

   public RoutingContextImpl(final Transaction transaction) {
      this(transaction, null);
   }

   public RoutingContextImpl(final Transaction transaction, Executor executor) {
      this.transaction = transaction;
      this.executor = executor;
   }

   @Override
   public boolean isReusable() {
      return reusable != null && reusable;
   }

   @Override
   public int getPreviousBindingsVersion() {
      return version;
   }

   @Override
   public SimpleString getPreviousAddress() {
      return previousAddress;
   }

   @Override
   public void setReusable(boolean reusable) {
      this.reusable = reusable;
   }
   @Override
   public RoutingContext setReusable(boolean reusable, int previousBindings) {
      this.version = previousBindings;
      this.previousAddress = address;
      this.previousRoutingType = routingType;
      if (this.reusable != null && !this.reusable.booleanValue()) {
         // cannot set to Reusable once it was set to false
         return this;
      }
      this.reusable = reusable;
      return this;
   }

   @Override
   public void clear() {
      map.clear();

      queueCount = 0;

      this.version = 0;

      this.reusable = null;
   }

   @Override
   public void addQueue(final SimpleString address, final Queue queue) {

      RouteContextList listing = getContextListing(address);

      if (queue.isDurableMessage()) {
         listing.getDurableQueues().add(queue);
      } else {
         listing.getNonDurableQueues().add(queue);
      }

      queueCount++;
   }

   @Override
   public void processReferences(final List<MessageReference> refs, final boolean direct) {
      internalprocessReferences(refs, direct);
   }

   private void internalprocessReferences(final List<MessageReference> refs, final boolean direct) {
      for (MessageReference ref : refs) {
         ref.getQueue().addTail(ref, direct);
      }
   }


   @Override
   public void addQueueWithAck(SimpleString address, Queue queue) {
      addQueue(address, queue);
      RouteContextList listing = getContextListing(address);
      listing.addAckedQueue(queue);
   }

   @Override
   public boolean isAlreadyAcked(SimpleString address, Queue queue) {
      RouteContextList listing = map.get(address);
      return listing == null ? false : listing.isAlreadyAcked(queue);
   }

   @Override
   public boolean isReusable(Message message, int version) {
      return isReusable() && queueCount > 0 && address.equals(previousAddress) && previousRoutingType == routingType && getPreviousBindingsVersion() == version;
   }

   @Override
   public void setAddress(SimpleString address) {
      this.address = address;
   }

   @Override
   public void setRoutingType(RoutingType routingType) {
      this.routingType = routingType;
   }

   @Override
   public SimpleString getAddress(Message message) {
      if (address == null && message != null) {
         return message.getAddressSimpleString();
      }
      return address;
   }

   @Override
   public SimpleString getAddress() {
      return address;
   }

   @Override
   public RoutingType getRoutingType() {
      return routingType;
   }

   @Override
   public RoutingType getPreviousRoutingType() {
      return previousRoutingType;
   }

   @Override
   public RouteContextList getContextListing(SimpleString address) {
      RouteContextList listing = map.get(address);
      if (listing == null) {
         listing = new ContextListing();
         map.put(address, listing);
      }
      return listing;
   }

   @Override
   public Transaction getTransaction() {
      return transaction;
   }

   @Override
   public void setTransaction(final Transaction tx) {
      transaction = tx;
   }

   @Override
   public List<Queue> getNonDurableQueues(SimpleString address) {
      return getContextListing(address).getNonDurableQueues();
   }

   @Override
   public List<Queue> getDurableQueues(SimpleString address) {
      return getContextListing(address).getDurableQueues();
   }

   @Override
   public int getQueueCount() {
      return queueCount;
   }

   @Override
   public Map<SimpleString, RouteContextList> getContexListing() {
      return this.map;
   }

   private static class ContextListing implements RouteContextList {

      private final List<Queue> durableQueue = new ArrayList<>(1);

      private final List<Queue> nonDurableQueue = new ArrayList<>(1);

      private final List<Queue> ackedQueues = new ArrayList<>();

      @Override
      public int getNumberOfDurableQueues() {
         return durableQueue.size();
      }

      @Override
      public int getNumberOfNonDurableQueues() {
         return nonDurableQueue.size();
      }

      @Override
      public List<Queue> getDurableQueues() {
         return durableQueue;
      }

      @Override
      public List<Queue> getNonDurableQueues() {
         return nonDurableQueue;
      }

      @Override
      public void addAckedQueue(Queue queue) {
         ackedQueues.add(queue);
      }

      @Override
      public boolean isAlreadyAcked(Queue queue) {
         return ackedQueues.contains(queue);
      }
   }

}
